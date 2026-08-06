package com.carriez.flutter_hbb

import android.app.Activity
import android.app.ActivityManager
import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Display
import com.hjq.permissions.XXPermissions
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.android.FlutterActivityLaunchConfigs.BackgroundMode
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import java.lang.ref.WeakReference

/**
 * Hosts the mobile file-transfer card on the display opposite the active
 * remote-control Activity. A separate Flutter engine keeps its transfer FFI
 * session and navigation lifecycle independent from the video session.
 */
class FileTransferActivity : FlutterActivity() {
    companion object {
        private const val TAG = "FileTransferActivity"
        private const val CHANNEL = "fileTransferChannel"
        private const val EXTRA_EXPECTED_DISPLAY_ID = "expected_display_id"
        private const val EXTRA_PEER_ID = "peer_id"
        private const val EXTRA_PASSWORD = "password"
        private const val EXTRA_SHARED_PASSWORD = "shared_password"
        private const val EXTRA_FORCE_RELAY = "force_relay"
        private const val EXTRA_CONN_TOKEN = "conn_token"
        private const val LAUNCH_REQUEST_CODE = 0x4b46
        private var liveActivity = WeakReference<FileTransferActivity>(null)

        fun launchOnOppositeDisplay(
            source: Activity,
            peerId: String,
            password: String?,
            isSharedPassword: Boolean?,
            forceRelay: Boolean,
            connToken: String?
        ): Map<String, Any> {
            val displayManager =
                source.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val sourceDisplayId = source.display?.displayId ?: Display.DEFAULT_DISPLAY
            val targetDisplayId = if (sourceDisplayId != Display.DEFAULT_DISPLAY) {
                Display.DEFAULT_DISPLAY
            } else {
                displayManager.displays.firstOrNull {
                    it.displayId != Display.DEFAULT_DISPLAY && it.state == Display.STATE_ON
                }?.displayId ?: sourceDisplayId
            }
            if (targetDisplayId == sourceDisplayId) {
                Log.i(TAG, "No opposite display; keep same-display transfer fallback")
                return mapOf(
                    "accepted" to false,
                    "sourceDisplayId" to sourceDisplayId,
                    "targetDisplayId" to targetDisplayId
                )
            }
            if (!canRestoreCrossDisplayTools(source)) {
                Log.w(TAG, "Cross-display restore permission is required for file transfer")
                return mapOf(
                    "accepted" to false,
                    "reason" to "cross_display_permission_required",
                    "sourceDisplayId" to sourceDisplayId,
                    "targetDisplayId" to targetDisplayId
                )
            }

            val existing = liveActivity.get()
            val reusing = existing != null &&
                !existing.isFinishing &&
                !existing.isDestroyed &&
                existing.display?.displayId == targetDisplayId &&
                existing.peerId == peerId

            return try {
                if (reusing && existing != null) {
                    existing.reactivate(
                        password,
                        isSharedPassword,
                        forceRelay,
                        connToken
                    )
                    Log.i(
                        TAG,
                        "Reuse file transfer source=$sourceDisplayId target=$targetDisplayId " +
                            "task=${existing.taskId} peer=$peerId"
                    )
                    return mapOf(
                        "accepted" to true,
                        "reused" to true,
                        "sourceDisplayId" to sourceDisplayId,
                        "targetDisplayId" to targetDisplayId
                    )
                }
                val intent = NewEngineIntentBuilder(FileTransferActivity::class.java)
                    .backgroundMode(BackgroundMode.transparent)
                    .build(source)
                    .apply {
                        putExtra(EXTRA_EXPECTED_DISPLAY_ID, targetDisplayId)
                        putExtra(EXTRA_PEER_ID, peerId)
                        password?.let { putExtra(EXTRA_PASSWORD, it) }
                        isSharedPassword?.let { putExtra(EXTRA_SHARED_PASSWORD, it) }
                        putExtra(EXTRA_FORCE_RELAY, forceRelay)
                        connToken?.let { putExtra(EXTRA_CONN_TOKEN, it) }
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                    }
                val options = ActivityOptions.makeBasic().apply {
                    launchDisplayId = targetDisplayId
                }
                val pendingIntent = PendingIntent.getActivity(
                    source,
                    LAUNCH_REQUEST_CODE + targetDisplayId,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                pendingIntent.send(
                    source,
                    0,
                    null,
                    null,
                    null,
                    null,
                    options.toBundle()
                )
                Log.i(
                    TAG,
                    "Launch file transfer source=$sourceDisplayId target=$targetDisplayId peer=$peerId"
                )
                mapOf(
                    "accepted" to true,
                    "reused" to reusing,
                    "sourceDisplayId" to sourceDisplayId,
                    "targetDisplayId" to targetDisplayId
                )
            } catch (error: Exception) {
                Log.e(TAG, "Unable to launch opposite-display file transfer", error)
                mapOf(
                    "accepted" to false,
                    "sourceDisplayId" to sourceDisplayId,
                    "targetDisplayId" to targetDisplayId
                )
            }
        }
    }

    private var expectedDisplayId = Display.DEFAULT_DISPLAY
    private var peerId = ""
    private var password: String? = null
    private var isSharedPassword: Boolean? = null
    private var forceRelay = false
    private var connToken: String? = null
    private var fileTransferChannel: MethodChannel? = null
    private var platformChannel: MethodChannel? = null
    private val taskRestoreHandler = Handler(Looper.getMainLooper())
    private var taskRestoreDeadlineMs = 0L
    private var taskRestoreAttempt = 0
    private val restoreParkedTask = object : Runnable {
        override fun run() {
            if (isFinishing || isDestroyed || hasWindowFocus()) return
            taskRestoreAttempt++
            try {
                val activityManager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
                activityManager.moveTaskToFront(
                    taskId,
                    ActivityManager.MOVE_TASK_NO_USER_ACTION
                )
                Log.i(
                    TAG,
                    "Restore parked file transfer attempt=$taskRestoreAttempt task=$taskId " +
                        "display=${display?.displayId} peer=$peerId"
                )
            } catch (error: Exception) {
                Log.w(TAG, "Unable to restore parked file transfer task=$taskId", error)
            }
            if (!hasWindowFocus() && SystemClock.elapsedRealtime() < taskRestoreDeadlineMs) {
                taskRestoreHandler.postDelayed(this, 350L)
            }
        }
    }

    override fun getDartEntrypointFunctionName(): String =
        "crossDisplayFileTransferMain"

    override fun onCreate(savedInstanceState: Bundle?) {
        expectedDisplayId = intent.getIntExtra(
            EXTRA_EXPECTED_DISPLAY_ID,
            Display.DEFAULT_DISPLAY
        )
        peerId = intent.getStringExtra(EXTRA_PEER_ID).orEmpty()
        password = intent.getStringExtra(EXTRA_PASSWORD)
        isSharedPassword = if (intent.hasExtra(EXTRA_SHARED_PASSWORD)) {
            intent.getBooleanExtra(EXTRA_SHARED_PASSWORD, false)
        } else {
            null
        }
        forceRelay = intent.getBooleanExtra(EXTRA_FORCE_RELAY, false)
        connToken = intent.getStringExtra(EXTRA_CONN_TOKEN)
        super.onCreate(savedInstanceState)

        val actualDisplayId = display?.displayId ?: Display.DEFAULT_DISPLAY
        if (actualDisplayId != expectedDisplayId || peerId.isEmpty()) {
            Log.e(
                TAG,
                "Reject file transfer actual=$actualDisplayId expected=$expectedDisplayId " +
                    "hasPeer=${peerId.isNotEmpty()}"
            )
            finish()
            return
        }
        Log.i(TAG, "File transfer Activity ready display=$actualDisplayId peer=$peerId")
        liveActivity = WeakReference(this)
    }

    override fun onNewIntent(newIntent: Intent) {
        super.onNewIntent(newIntent)
        setIntent(newIntent)
        val requestedPeerId = newIntent.getStringExtra(EXTRA_PEER_ID).orEmpty()
        val requestedDisplayId = newIntent.getIntExtra(
            EXTRA_EXPECTED_DISPLAY_ID,
            Display.DEFAULT_DISPLAY
        )
        val actualDisplayId = display?.displayId ?: Display.DEFAULT_DISPLAY
        if (requestedPeerId != peerId || requestedDisplayId != actualDisplayId) {
            Log.e(
                TAG,
                "Reject file transfer relaunch currentPeer=$peerId requestedPeer=$requestedPeerId " +
                    "actual=$actualDisplayId expected=$requestedDisplayId"
            )
            return
        }
        password = newIntent.getStringExtra(EXTRA_PASSWORD)
        isSharedPassword = if (newIntent.hasExtra(EXTRA_SHARED_PASSWORD)) {
            newIntent.getBooleanExtra(EXTRA_SHARED_PASSWORD, false)
        } else {
            null
        }
        forceRelay = newIntent.getBooleanExtra(EXTRA_FORCE_RELAY, false)
        connToken = newIntent.getStringExtra(EXTRA_CONN_TOKEN)
        Log.i(TAG, "Received file transfer relaunch task=$taskId peer=$peerId display=$actualDisplayId")
    }

    private fun reactivate(
        newPassword: String?,
        newIsSharedPassword: Boolean?,
        newForceRelay: Boolean,
        newConnToken: String?
    ) {
        password = newPassword
        isSharedPassword = newIsSharedPassword
        forceRelay = newForceRelay
        connToken = newConnToken
        taskRestoreHandler.removeCallbacks(restoreParkedTask)
        taskRestoreAttempt = 0
        taskRestoreDeadlineMs = SystemClock.elapsedRealtime() + 7_000L
        restoreParkedTask.run()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            taskRestoreHandler.removeCallbacks(restoreParkedTask)
            if (taskRestoreAttempt > 0) {
                Log.i(
                    TAG,
                    "File transfer task restored attempts=$taskRestoreAttempt " +
                        "task=$taskId display=${display?.displayId} peer=$peerId"
                )
            }
        }
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        fileTransferChannel = MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            CHANNEL
        ).also { channel ->
            channel.setMethodCallHandler { call, result ->
                when (call.method) {
                    "get_file_transfer_params" -> result.success(
                        mapOf(
                            "peer_id" to peerId,
                            "password" to (password ?: ""),
                            "is_shared_password" to isSharedPassword,
                            "force_relay" to forceRelay,
                            "conn_token" to (connToken ?: ""),
                            "target_display_id" to expectedDisplayId
                        )
                    )
                    "finish_activity" -> {
                        finish()
                        result.success(true)
                    }
                    else -> result.notImplemented()
                }
            }
        }

        platformChannel = MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            "mChannel"
        ).also { channel ->
            channel.setMethodCallHandler { call, result ->
                when (call.method) {
                    "check_permission" -> {
                        val permission = call.arguments as? String
                        result.success(
                            permission != null && XXPermissions.isGranted(this, permission)
                        )
                    }
                    "request_permission" -> {
                        val permission = call.arguments as? String
                        if (permission == null) {
                            result.success(false)
                        } else {
                            requestPermission(this, permission, channel)
                            result.success(true)
                        }
                    }
                    START_ACTION -> {
                        val action = call.arguments as? String
                        if (action == null) {
                            result.success(false)
                        } else {
                            startAction(this, action)
                            result.success(true)
                        }
                    }
                    "enable_soft_keyboard" -> result.success(true)
                    "get_available_storage_bytes" -> {
                        val path = (call.arguments as? Map<*, *>)?.get("path") as? String
                        result.success(path?.let(AndroidStorageSpace::availableBytes))
                    }
                    "install_local_apk" -> {
                        val arguments = call.arguments as? Map<*, *>
                        result.success(
                            AndroidSelfUpdater.launchLocalApk(
                                this@FileTransferActivity,
                                arguments?.get("path") as? String ?: "",
                                arguments?.get("openPermissionSettings") == true,
                            )
                        )
                    }
                    "share_local_file" -> {
                        val path = (call.arguments as? Map<*, *>)?.get("path") as? String ?: ""
                        result.success(AndroidSelfUpdater.shareLocalFile(this@FileTransferActivity, path))
                    }
                    GET_VALUE -> {
                        if (call.arguments == KEY_IS_SUPPORT_VOICE_CALL) {
                            result.success(isSupportVoiceCall())
                        } else {
                            result.error("-1", "No such key", null)
                        }
                    }
                    else -> result.notImplemented()
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations && !isFinishing) {
            // Keep the task parked. On this dual-screen Android 12 build, once HOME owns
            // the target display, creating a replacement Activity from the other display
            // is rejected as a background cross-display start. The next explicit tap
            // restores this existing task through REORDER_TASKS instead.
            Log.i(TAG, "File transfer host stopped; keep parked task=$taskId peer=$peerId")
        }
    }

    override fun onDestroy() {
        taskRestoreHandler.removeCallbacks(restoreParkedTask)
        if (liveActivity.get() === this) liveActivity.clear()
        fileTransferChannel?.setMethodCallHandler(null)
        platformChannel?.setMethodCallHandler(null)
        fileTransferChannel = null
        platformChannel = null
        super.onDestroy()
    }
}
