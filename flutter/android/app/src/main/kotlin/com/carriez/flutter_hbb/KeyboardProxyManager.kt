package com.carriez.flutter_hbb

import android.app.Activity
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import io.flutter.plugin.common.MethodChannel
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicLong

object KeyboardProxyManager : DisplayManager.DisplayListener, DefaultLifecycleObserver {
    private const val TAG = "KeyboardProxyManager"
    private const val OPEN_TIMEOUT_MS = 8_000L

    private val mainHandler = Handler(Looper.getMainLooper())
    private val requestIds = AtomicLong(0)

    private var state = "hidden"
    private var requestId = 0L
    private var sessionId = ""
    private var sourceDisplayId = Display.DEFAULT_DISPLAY
    private var targetDisplayId = Display.DEFAULT_DISPLAY
    private var channel: MethodChannel? = null
    private var proxyActivity = WeakReference<KeyboardProxyActivity>(null)
    private var displayManager: DisplayManager? = null
    private var preparingRequestId = 0L

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    private val openTimeout = Runnable {
        if (state == "opening") close("open_timeout")
    }

    @Synchronized
    fun prepare(source: Activity, methodChannel: MethodChannel): Boolean {
        channel = methodChannel
        return state == "hidden"
    }

    @Synchronized
    fun open(source: Activity, methodChannel: MethodChannel, requestedSessionId: String): Map<String, Any> {
        if (state != "hidden") {
            channel = methodChannel
            publishState("open_rejected_busy")
            return mapOf("accepted" to false, "requestId" to requestId)
        }

        val manager = source.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val sourceId = source.display?.displayId ?: Display.DEFAULT_DISPLAY
        val targetId = findTargetDisplay(manager, sourceId)
        val newRequestId = requestIds.incrementAndGet()

        requestId = newRequestId
        sessionId = requestedSessionId
        sourceDisplayId = sourceId
        targetDisplayId = targetId
        channel = methodChannel
        state = "opening"
        displayManager = manager
        manager.registerDisplayListener(this, mainHandler)
        publishState("open_requested")

        mainHandler.removeCallbacks(openTimeout)
        mainHandler.postDelayed(openTimeout, OPEN_TIMEOUT_MS)
        val existing = proxyActivity.get()
        if (existing != null && !existing.isFinishing && !existing.isDestroyed &&
            existing.display?.displayId == targetId
        ) {
            existing.activate(newRequestId, requestedSessionId)
        } else if (preparingRequestId == 0L) {
            launchIfCurrent(source, newRequestId, requestedSessionId, targetId)
        }
        return mapOf("accepted" to true, "requestId" to newRequestId)
    }

    @Synchronized
    fun close(reason: String = "close_requested", expectedRequestId: Long? = null) {
        if (expectedRequestId != null && expectedRequestId != requestId) return
        if (state == "hidden") return

        state = "closing"
        publishState(reason)
        mainHandler.removeCallbacks(openTimeout)
        val activity = proxyActivity.get()
        if (activity != null) activity.hideIme(reason) else finishHidden(reason, false)
    }

    @Synchronized
    fun onActivityReady(activity: KeyboardProxyActivity, activityRequestId: Long, actualDisplayId: Int): Boolean {
        val preparedActivity = activityRequestId == preparingRequestId
        val openingActivity = activityRequestId == requestId && state == "opening"
        if (actualDisplayId != targetDisplayId || (!preparedActivity && !openingActivity)) {
            Log.e(
                TAG,
                "Reject request=$activityRequestId display=$actualDisplayId, " +
                    "expected request=$requestId prepare=$preparingRequestId display=$targetDisplayId state=$state"
            )
            return false
        }
        proxyActivity = WeakReference(activity)
        if (preparedActivity) preparingRequestId = 0L
        if (state == "opening") {
            mainHandler.post { activity.activate(requestId, sessionId) }
        }
        return true
    }

    @Synchronized
    fun onImeVisibilityChanged(activityRequestId: Long, visible: Boolean) {
        if (activityRequestId != requestId || state == "hidden") return
        if (visible && state == "opening") {
            mainHandler.removeCallbacks(openTimeout)
            state = "visible"
            publishState("ime_visible")
        } else if (!visible && state == "visible") {
            close("user_hidden", activityRequestId)
        }
    }

    @Synchronized
    fun onImeHidden(activityRequestId: Long, reason: String) {
        if (activityRequestId != requestId || state != "closing") return
        val activity = proxyActivity.get()
        finishHidden(reason, false)
        activity?.releaseAndFinish(reason)
    }

    @Synchronized
    fun commitText(activityRequestId: Long, activitySessionId: String, text: String) {
        if (activityRequestId != requestId || activitySessionId != sessionId || state != "visible" || text.isEmpty()) return
        Log.i(
            TAG,
            "commit_text request=$activityRequestId session=$activitySessionId len=${text.length}"
        )
        channel?.invokeMethod(
            "keyboard_proxy_commit_text",
            mapOf("requestId" to requestId, "sessionId" to sessionId, "text" to text)
        )
    }

    @Synchronized
    fun sendKey(activityRequestId: Long, activitySessionId: String, key: String) {
        if (activityRequestId != requestId || activitySessionId != sessionId || state != "visible") return
        channel?.invokeMethod(
            "keyboard_proxy_key",
            mapOf("requestId" to requestId, "sessionId" to sessionId, "key" to key)
        )
    }

    @Synchronized
    fun onActivityDestroyed(activity: KeyboardProxyActivity, reason: String) {
        if (proxyActivity.get() !== activity) return
        proxyActivity.clear()
        preparingRequestId = 0L
        if (state != "hidden") finishHidden(reason, false)
    }

    @Synchronized
    fun release(reason: String = "release_requested") {
        mainHandler.removeCallbacks(openTimeout)
        preparingRequestId = 0L
        val activity = proxyActivity.get()
        if (activity != null) {
            activity.releaseAndFinish(reason)
        } else {
            finishHidden(reason, false)
        }
    }

    override fun onDisplayAdded(displayId: Int) = Unit

    override fun onDisplayChanged(displayId: Int) {
        if (displayId == targetDisplayId && displayManager?.getDisplay(displayId)?.state != Display.STATE_ON) {
            release("display_removed")
        }
    }

    override fun onDisplayRemoved(displayId: Int) {
        if (displayId == targetDisplayId) release("display_removed")
    }

    override fun onStop(owner: LifecycleOwner) {
        release("app_backgrounded")
    }

    @Synchronized
    private fun finishHidden(reason: String, keepPreparedActivity: Boolean) {
        mainHandler.removeCallbacks(openTimeout)
        state = "hidden"
        publishState(reason)
        sessionId = ""
        if (!keepPreparedActivity) {
            proxyActivity.clear()
            displayManager?.unregisterDisplayListener(this)
            displayManager = null
            channel = null
        }
    }

    @Synchronized
    private fun launchIfCurrent(source: Activity, launchRequestId: Long, launchSessionId: String, launchDisplayId: Int) {
        if (launchRequestId != requestId || state != "opening") return
        try {
            KeyboardProxyActivity.launch(source, launchRequestId, launchSessionId, launchDisplayId)
        } catch (error: Exception) {
            Log.e(TAG, "Failed to launch keyboard proxy", error)
            finishHidden("launch_failed", false)
        }
    }

    private fun publishState(reason: String) {
        val payload = mapOf(
            "requestId" to requestId,
            "state" to state,
            "sourceDisplayId" to sourceDisplayId,
            "targetDisplayId" to targetDisplayId,
            "reason" to reason
        )
        Log.i(TAG, "state=$state reason=$reason source=$sourceDisplayId target=$targetDisplayId request=$requestId")
        channel?.invokeMethod("keyboard_proxy_state", payload)
    }

    private fun findTargetDisplay(manager: DisplayManager, sourceId: Int): Int {
        if (sourceId != Display.DEFAULT_DISPLAY) return Display.DEFAULT_DISPLAY
        return manager.displays.firstOrNull {
            it.displayId != Display.DEFAULT_DISPLAY && it.state == Display.STATE_ON
        }?.displayId ?: sourceId
    }
}