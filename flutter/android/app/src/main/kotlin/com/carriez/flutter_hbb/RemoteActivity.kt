package com.carriez.flutter_hbb

/**
 * RemoteActivity — 副屏 (Display 2) 专用 Activity
 *
 * 职责:
 *   1. 接收主屏传来的 peer_id，自动连接远程桌面
 *   2. 显示远程桌面画面 (RemotePage)
 *   3. 处理触摸事件 → 远程被控设备
 *   4. 双屏键盘代理: 在当前远程画面的对面屏幕启动原生输入代理
 *
 * Reference: chip.md §2.3 (副屏 Activity 启动), §2.4 (防呆机制)
 *
 * Inspired by droidVNC-NG: https://github.com/bk138/droidVNC-NG
 */

import android.app.ActivityOptions
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import com.hjq.permissions.XXPermissions
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class RemoteActivity : FlutterActivity() {

    companion object {
        const val TAG = "RemoteActivity"
        const val CHANNEL_TAG = "remoteChannel"
        const val EXTRA_PEER_ID = "peer_id"
        const val EXTRA_PASSWORD = "password"
        const val EXTRA_FORCE_RELAY = "force_relay"

        /** Request code for relaunching self on correct display. */
        private const val REQ_RELAUNCH = 2201
    }

    private var peerId: String = ""
    private var password: String? = null
    private var forceRelay: Boolean = false
    private var flutterMethodChannel: MethodChannel? = null
    private val physicalMouseRightButton by lazy {
        PhysicalMouseRightButtonForwarder(::forwardPhysicalMouseRightButton)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ===== 防呆: 检测是否被启动到错误的 Display =====
        // 本 Activity 应该在 Display 2 (副屏) 上运行。
        // 如果系统错误地将其恢复到主屏，则重新启动到副屏。
        val currentDisplayId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display?.displayId ?: Display.DEFAULT_DISPLAY
        } else {
            @Suppress("DEPRECATION")
            (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.displayId
        }

        if (currentDisplayId == Display.DEFAULT_DISPLAY) {
            Log.w(TAG, "防呆: RemoteActivity 被启动到主屏 Display 0, 迁回副屏 Display 2")
            relaunchOnDisplay2()
            return
        }

        Log.d(TAG, "RemoteActivity 启动在 Display $currentDisplayId")

        // 读取 Intent 参数
        peerId = intent.getStringExtra(EXTRA_PEER_ID) ?: ""
        password = intent.getStringExtra(EXTRA_PASSWORD)
        forceRelay = intent.getBooleanExtra(EXTRA_FORCE_RELAY, false)

        Log.d(TAG, "peerId=$peerId, password=${password != null}, forceRelay=$forceRelay")
    }

    /**
     * 防呆: 重新将自身启动到副屏 Display 2。
     */
    private fun relaunchOnDisplay2() {
        val intent = Intent(this, RemoteActivity::class.java).apply {
            putExtra(EXTRA_PEER_ID, peerId)
            password?.let { putExtra(EXTRA_PASSWORD, it) }
            putExtra(EXTRA_FORCE_RELAY, forceRelay)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }

        if (Build.VERSION.SDK_INT >= 26) {
            try {
                val options = ActivityOptions.makeBasic()
                val method = options.javaClass.getMethod(
                    "setLaunchDisplayId",
                    Int::class.javaPrimitiveType  // ⚠️ 注意: javaPrimitiveType 不是 javaObjectType
                )
                method.invoke(options, 2) // Display 2
                startActivity(intent, options.toBundle())
            } catch (e: Exception) {
                Log.e(TAG, "反射 setLaunchDisplayId 失败, 降级启动", e)
                startActivity(intent)
            }
        } else {
            startActivity(intent)
        }
        finish()
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        // ===== remoteChannel: 连接参数 / 生命周期 =====
        val channel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL_TAG)

        // 注册到 SessionState，供主屏通信
        SessionState.remoteMethodChannel = channel

        // 通过 MethodChannel 将连接参数传给 Flutter 端
        channel.setMethodCallHandler { call, result ->
            when (call.method) {
                "get_connection_params" -> {
                    // Flutter 端启动时调用此方法获取连接参数
                    result.success(mapOf(
                        "peer_id" to peerId,
                        "password" to (password ?: ""),
                        "force_relay" to forceRelay
                    ))
                }
                "notify_session_ready" -> {
                    // Flutter 端通知 session 已建立
                    val sessionId = call.argument<String>("sessionId") ?: ""
                    SessionState.notifyConnectionState(true, sessionId)
                    result.success(true)
                }
                "notify_session_closed" -> {
                    // Flutter 端通知 session 已关闭
                    SessionState.notifyConnectionState(false, null)
                    result.success(true)
                }
                "finish_activity" -> {
                    // Flutter 端请求关闭此 Activity
                    finish()
                    result.success(true)
                }
                else -> {
                    result.notImplemented()
                }
            }
        }

        // ===== mChannel: 双屏键盘代理 (Flutter 调用 gFFI.invokeMethod 走此通道) =====
        val mChannel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "mChannel")
        flutterMethodChannel = mChannel
        mChannel.setMethodCallHandler { call, result ->
            when (call.method) {
                "check_permission" -> {
                    if (call.arguments is String) {
                        result.success(XXPermissions.isGranted(context, call.arguments as String))
                    } else {
                        result.success(false)
                    }
                }
                "request_permission" -> {
                    if (call.arguments is String) {
                        requestPermission(context, call.arguments as String, mChannel)
                        result.success(true)
                    } else {
                        result.success(false)
                    }
                }
                START_ACTION -> {
                    if (call.arguments is String) {
                        startAction(context, call.arguments as String)
                        result.success(true)
                    } else {
                        result.success(false)
                    }
                }
                "enable_soft_keyboard" -> {
                    // RemoteActivity uses cross-display keyboard proxy on Android.
                    // Do not mutate window IME flags here; it can interfere with
                    // proxy focus/IME routing between displays.
                    result.success(true)
                }
                "set_remote_mouse_input_active" -> {
                    physicalMouseRightButton.setActive(call.arguments == true)
                    result.success(true)
                }
                "try_sync_clipboard" -> {
                    MainActivity.rdClipboardManager?.syncClipboard(true)
                    result.success(true)
                }
                GET_VALUE -> {
                    if (call.arguments is String) {
                        if (call.arguments == KEY_IS_SUPPORT_VOICE_CALL) {
                            result.success(isSupportVoiceCall())
                        } else {
                            result.error("-1", "No such key", null)
                        }
                    } else {
                        result.success(null)
                    }
                }
                "keyboard_proxy_open" -> {
                    result.success(
                        KeyboardProxyManager.open(
                            this@RemoteActivity,
                            mChannel,
                            call.argument<String>("sessionId").orEmpty()
                        )
                    )
                }
                "keyboard_proxy_prepare" -> {
                    result.success(
                        KeyboardProxyManager.prepare(
                            this@RemoteActivity,
                            mChannel
                        )
                    )
                }
                "keyboard_proxy_close" -> {
                    val expectedRequestId = call.argument<Number>("requestId")?.toLong()
                    KeyboardProxyManager.close("close_requested", expectedRequestId)
                    result.success(true)
                }
                "keyboard_proxy_release" -> {
                    KeyboardProxyManager.release()
                    result.success(true)
                }
                else -> {
                    // 其他 mChannel 调用在 RemoteActivity 中无需处理
                    result.notImplemented()
                }
            }
        }

        // 将连接参数立即发送给 Flutter 端 (在 Flutter 初始化完成后)
        Handler(Looper.getMainLooper()).postDelayed({
            channel.invokeMethod("init_params", mapOf(
                "peer_id" to peerId,
                "password" to (password ?: ""),
                "force_relay" to forceRelay
            ))
        }, 500)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (physicalMouseRightButton.handleMotionEvent(event)) return true
        return super.dispatchTouchEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (physicalMouseRightButton.handleMotionEvent(event)) return true
        return super.dispatchGenericMotionEvent(event)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (physicalMouseRightButton.handleKeyEvent(event)) return true
        return super.dispatchKeyEvent(event)
    }

    private fun forwardPhysicalMouseRightButton(type: String) {
        Log.i(TAG, "[PhysicalMouse] right $type on display ${display?.displayId}")
        flutterMethodChannel?.invokeMethod(
            "on_physical_mouse_button",
            mapOf("button" to "right", "type" to type),
        )
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        physicalMouseRightButton.setActive(false)
        flutterMethodChannel = null
        KeyboardProxyManager.release("activity_destroyed")
        SessionState.reset()
        super.onDestroy()
    }
}
