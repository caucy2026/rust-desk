package com.carriez.flutter_hbb

import android.app.Activity
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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
    private const val PREPARE_TIMEOUT_MS = 2_000L

    private val mainHandler = Handler(Looper.getMainLooper())
    private val requestIds = AtomicLong(0)

    private var state = "hidden"
    private var requestId = 0L
    private var sessionId = ""
    private var sourceDisplayId = Display.DEFAULT_DISPLAY
    private var targetDisplayId = Display.DEFAULT_DISPLAY
    private var channel: MethodChannel? = null
    private var ownerActivity = WeakReference<Activity>(null)
    private var ownerSessionId = ""
    private var proxyActivity = WeakReference<KeyboardProxyActivity>(null)
    private var displayManager: DisplayManager? = null
    private var preparingRequestId = 0L
    private var lastSourcePointerEventAtMs = 0L

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    private val openTimeout = Runnable {
        if (state == "opening") close("open_timeout")
    }
    private val prepareTimeout = Runnable { handlePrepareTimeout() }

    @Synchronized
    private fun handlePrepareTimeout() {
        if (preparingRequestId == 0L || proxyActivity.get() != null) return
        Log.w(TAG, "Keyboard proxy preparation timed out request=$preparingRequestId")
        preparingRequestId = 0L
        if (state == "opening") {
            val source = ownerActivity.get()
            if (source != null && !source.isFinishing && !source.isDestroyed) {
                launchIfCurrent(source, requestId, sessionId, targetDisplayId)
            } else {
                finishHidden("prepare_source_lost", false)
            }
        } else if (state == "hidden") {
            displayManager?.unregisterDisplayListener(this)
            displayManager = null
        }
    }

    @Synchronized
    fun prepare(
        source: Activity,
        methodChannel: MethodChannel,
        requestedSessionId: String = "",
        deferDefaultDisplay: Boolean = false
    ): Boolean {
        if (state != "hidden") {
            if (requestedSessionId.isNotEmpty() && requestedSessionId == ownerSessionId) {
                channel = methodChannel
                ownerActivity = WeakReference(source)
            }
            Log.w(
                TAG,
                "Ignore prepare while state=$state owner=$ownerSessionId requested=$requestedSessionId"
            )
            return false
        }

        channel = methodChannel
        ownerActivity = WeakReference(source)
        ownerSessionId = requestedSessionId

        val manager = source.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val sourceId = source.display?.displayId ?: Display.DEFAULT_DISPLAY
        if (deferDefaultDisplay && sourceId == Display.DEFAULT_DISPLAY) {
            Log.i(TAG, "Defer keyboard proxy preparation on default display until authentication")
            return true
        }
        val targetId = findTargetDisplay(manager, sourceId)
        val existing = proxyActivity.get()
        if (existing != null && !existing.isFinishing && !existing.isDestroyed &&
            existing.display?.displayId == targetId
        ) {
            Log.i(TAG, "Keyboard proxy already prepared on display=$targetId")
            return true
        }

        // A same-display Activity can be created reliably from the explicit keyboard tap,
        // so pre-launch only matters for the cross-display path. Android 12 may reject a
        // later Display 2 -> Display 0 Activity start as a background launch even while
        // the source Activity is visible. Prepare the reusable host while the remote page
        // is entering the foreground instead.
        if (sourceId == targetId || preparingRequestId != 0L) return true

        val prepareId = requestIds.incrementAndGet()
        preparingRequestId = prepareId
        sourceDisplayId = sourceId
        targetDisplayId = targetId
        displayManager = manager
        manager.registerDisplayListener(this, mainHandler)
        Log.i(TAG, "Preparing keyboard proxy source=$sourceId target=$targetId request=$prepareId")
        try {
            KeyboardProxyActivity.launch(source, prepareId, "", targetId)
            mainHandler.removeCallbacks(prepareTimeout)
            mainHandler.postDelayed(prepareTimeout, PREPARE_TIMEOUT_MS)
        } catch (error: Exception) {
            Log.e(TAG, "Failed to prepare keyboard proxy", error)
            preparingRequestId = 0L
            displayManager?.unregisterDisplayListener(this)
            displayManager = null
            return false
        }
        return true
    }

    @Synchronized
    fun open(source: Activity, methodChannel: MethodChannel, requestedSessionId: String): Map<String, Any> {
        if (state != "hidden") {
            if (requestedSessionId.isNotEmpty() && ownerSessionId.isNotEmpty() &&
                requestedSessionId != ownerSessionId
            ) {
                Log.w(
                    TAG,
                    "Release stale keyboard owner=$ownerSessionId for new session=$requestedSessionId"
                )
                release("superseded_session")
                return mapOf(
                    "accepted" to false,
                    "requestId" to requestId,
                    "reason" to "stale_session_released",
                    "retryAfterMs" to 2_200
                )
            }
            if (requestedSessionId.isNotEmpty() && requestedSessionId == ownerSessionId) {
                channel = methodChannel
                ownerActivity = WeakReference(source)
            }
            publishState("open_rejected_busy")
            return mapOf(
                "accepted" to false,
                "requestId" to requestId,
                "reason" to "busy"
            )
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
        ownerActivity = WeakReference(source)
        ownerSessionId = requestedSessionId
        state = "opening"
        lastSourcePointerEventAtMs = 0L
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
        } else {
            if (preparingRequestId != 0L) {
                // Keep the preparation request valid while promoting the explicit open.
                // Either launch may create the singleInstance host first; onActivityReady
                // accepts both request IDs and activates the current explicit request.
                Log.w(
                    TAG,
                    "Promote unfinished preparation=$preparingRequestId with open request=$newRequestId"
                )
            }
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
        if (preparedActivity || openingActivity) {
            preparingRequestId = 0L
            mainHandler.removeCallbacks(prepareTimeout)
        }
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
    fun onSourcePointerEvent(
        displayId: Int,
        primaryDown: Boolean,
        pointerUp: Boolean,
        secondary: Boolean
    ) {
        if ((state == "opening" || state == "visible") && displayId == sourceDisplayId) {
            lastSourcePointerEventAtMs = SystemClock.elapsedRealtime()
            proxyActivity.get()?.onSourcePointerGesture(primaryDown, pointerUp, secondary)
        }
    }

    @Synchronized
    fun onSourceSecondaryMouseEvent(displayId: Int) {
        if ((state == "opening" || state == "visible") && displayId == sourceDisplayId) {
            lastSourcePointerEventAtMs = SystemClock.elapsedRealtime()
            proxyActivity.get()?.onSourcePointerGesture(false, false, true)
        }
    }

    @Synchronized
    fun hadRecentSourcePointerEvent(activityRequestId: Long, withinMs: Long): Boolean {
        if (activityRequestId != requestId || state == "hidden" || lastSourcePointerEventAtMs == 0L) {
            return false
        }
        return SystemClock.elapsedRealtime() - lastSourcePointerEventAtMs <= withinMs
    }

    @Synchronized
    fun onImeHidden(activityRequestId: Long, reason: String) {
        if (activityRequestId != requestId || state != "closing") return
        val activity = proxyActivity.get()
        // Keep the transparent host alive. Recreating it from a foreground Activity on a
        // different display is rejected by Android 12's background-activity-start policy.
        // The parked host is non-focusable and non-touchable, so it does not block the
        // local screen while still allowing the next keyboard tap to reuse it.
        finishHidden(reason, activity != null)
        activity?.parkForReuse(reason)
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
    fun sendKey(
        activityRequestId: Long,
        activitySessionId: String,
        key: String,
        alt: Boolean = false,
        ctrl: Boolean = false,
        shift: Boolean = false,
        command: Boolean = false
    ) {
        if (activityRequestId != requestId || activitySessionId != sessionId || state != "visible") return
        channel?.invokeMethod(
            "keyboard_proxy_key",
            mapOf(
                "requestId" to requestId,
                "sessionId" to sessionId,
                "key" to key,
                "alt" to alt,
                "ctrl" to ctrl,
                "shift" to shift,
                "command" to command
            )
        )
    }

    @Synchronized
    fun onActivityDestroyed(activity: KeyboardProxyActivity, reason: String) {
        if (proxyActivity.get() !== activity) return
        proxyActivity.clear()
        preparingRequestId = 0L
        mainHandler.removeCallbacks(prepareTimeout)
        // Always release the channel/display listener. A prepared Activity may be
        // destroyed by HOME while Manager already reports hidden.
        finishHidden(reason, false)
    }

    @Synchronized
    fun onHostStopped(
        activity: KeyboardProxyActivity,
        activityRequestId: Long,
        reason: String,
    ) {
        if (proxyActivity.get() !== activity) return
        if (activityRequestId != requestId && activityRequestId != preparingRequestId) return
        Log.i(TAG, "Discard stopped keyboard host request=$activityRequestId reason=$reason")
        proxyActivity.clear()
        finishHidden(reason, false)
    }

    @Synchronized
    fun release(
        reason: String = "release_requested",
        expectedSessionId: String? = null,
        source: Activity? = null
    ) {
        if (!expectedSessionId.isNullOrEmpty() && ownerSessionId.isNotEmpty() &&
            expectedSessionId != ownerSessionId
        ) {
            Log.w(
                TAG,
                "Ignore stale release reason=$reason owner=$ownerSessionId expected=$expectedSessionId"
            )
            return
        }
        val owner = ownerActivity.get()
        if (source != null && owner != null && source !== owner) {
            Log.w(TAG, "Ignore release from stale Activity reason=$reason")
            return
        }
        mainHandler.removeCallbacks(openTimeout)
        mainHandler.removeCallbacks(prepareTimeout)
        preparingRequestId = 0L
        if (state != "hidden" && state != "closing") {
            state = "closing"
            publishState(reason)
        }
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
        mainHandler.removeCallbacks(prepareTimeout)
        preparingRequestId = 0L
        state = "hidden"
        publishState(reason)
        sessionId = ""
        lastSourcePointerEventAtMs = 0L
        if (keepPreparedActivity) {
            // Keep ownership and the channel with the parked host for this session.
        } else {
            proxyActivity.clear()
            displayManager?.unregisterDisplayListener(this)
            displayManager = null
            channel = null
            ownerActivity.clear()
            ownerSessionId = ""
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
            "sessionId" to if (sessionId.isNotEmpty()) sessionId else ownerSessionId,
            "reason" to reason
        )
        Log.i(TAG, "state=$state reason=$reason source=$sourceDisplayId target=$targetDisplayId request=$requestId")
        channel?.invokeMethod("keyboard_proxy_state", payload)
    }

    private fun findTargetDisplay(manager: DisplayManager, sourceId: Int): Int {
        if (sourceId != Display.DEFAULT_DISPLAY) return Display.DEFAULT_DISPLAY
        val secondaryDisplay = manager.displays.firstOrNull {
            it.displayId != Display.DEFAULT_DISPLAY && it.state == Display.STATE_ON
        }
        if (secondaryDisplay == null) {
            Log.i(TAG, "No usable secondary display; use source display=$sourceId for keyboard proxy")
            return sourceId
        }
        return secondaryDisplay.displayId
    }
}
