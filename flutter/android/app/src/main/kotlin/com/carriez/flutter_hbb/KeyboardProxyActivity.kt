package com.carriez.flutter_hbb

import android.app.Activity
import android.app.ActivityManager
import android.app.ActivityOptions
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Display
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnAttach

class KeyboardProxyActivity : Activity() {
    companion object {
        private const val TAG = "KeyboardProxyActivity"
        private const val EXTRA_REQUEST_ID = "request_id"
        private const val EXTRA_SESSION_ID = "session_id"
        private const val EXTRA_TARGET_DISPLAY_ID = "target_display_id"
        private const val IME_RETRY_DELAY_MS = 350L
        private const val IME_LOSS_CLASSIFY_DELAY_MS = 120L
        private const val IME_RESTORE_DELAY_MS = 80L
        private const val SOURCE_POINTER_GRACE_MS = 2_200L
        private const val PRIMARY_MOUSE_GUARD_INTERVAL_MS = 48L
        private const val PRIMARY_MOUSE_GUARD_MAX_MS = 650L
        private const val PRIMARY_MOUSE_GUARD_AFTER_UP_MS = 180L
        private const val MAX_IME_REQUEST_ATTEMPTS = 16
        private const val FINISH_AFTER_HIDE_TIMEOUT_MS = 2_000L
        private const val DUPLICATE_COMMIT_WINDOW_MS = 250L

        fun launch(
            source: Activity,
            requestId: Long,
            sessionId: String,
            targetDisplayId: Int
        ) {
            val intent = Intent(source, KeyboardProxyActivity::class.java).apply {
                putExtra(EXTRA_REQUEST_ID, requestId)
                putExtra(EXTRA_SESSION_ID, sessionId)
                putExtra(EXTRA_TARGET_DISPLAY_ID, targetDisplayId)
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
            val sourceDisplayId = source.display?.displayId ?: Display.DEFAULT_DISPLAY
            if (sourceDisplayId == targetDisplayId) {
                // On a single-display device, use the source Activity directly.
                // Some ROMs do not reliably honor launchDisplayId=0.
                source.startActivity(intent)
                return
            }
            intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK
            )
            val options = ActivityOptions.makeBasic().apply {
                launchDisplayId = targetDisplayId
            }
            source.startActivity(intent, options.toBundle())
        }
    }

    private var requestId = 0L
    private var sessionId = ""
    private var expectedDisplayId = Display.DEFAULT_DISPLAY
    private var finishReason = "activity_destroyed"
    private lateinit var rootView: FrameLayout
    private lateinit var editText: EditText
    private var ignoreTextChange = false
    private var imeRequestAttempts = 0
    private var imeShowAccepted = false
    private var lastLoggedImeVisible: Boolean? = null
    private var lastLoggedImeBottom = -1
    private var lastForwardedText = ""
    private var lastForwardedSource = ""
    private var lastForwardedAtMs = 0L
    private var active = false
    private var closeRequested = false
    private var releaseRequested = false
    private var userLeavePending = false
    private var restoreImeInProgress = false
    private var primaryMouseGuardUntilMs = 0L
    private val finishAfterHideTimeout = Runnable { completeHide() }
    private val protectImeDuringPrimaryMouse = object : Runnable {
        override fun run() {
            if (!active || closeRequested || releaseRequested ||
                !::editText.isInitialized ||
                SystemClock.elapsedRealtime() > primaryMouseGuardUntilMs
            ) {
                return
            }
            keepImeStableDuringPrimaryMouse()
            editText.postDelayed(this, PRIMARY_MOUSE_GUARD_INTERVAL_MS)
        }
    }
    private val classifyImeHidden = Runnable {
        if (!active || closeRequested || releaseRequested ||
            !::editText.isInitialized || lastLoggedImeVisible != false
        ) {
            return@Runnable
        }
        val recentSourcePointer = KeyboardProxyManager.hadRecentSourcePointerEvent(
            requestId,
            SOURCE_POINTER_GRACE_MS
        )
        if (!editText.hasWindowFocus() || recentSourcePointer) {
            Log.i(
                TAG,
                "Restore IME after external focus loss display=${display?.displayId} " +
                    "windowFocus=${editText.hasWindowFocus()} sourcePointer=$recentSourcePointer request=$requestId"
            )
            restoreImeAfterExternalFocusLoss()
        } else {
            Log.i(TAG, "Confirmed user IME hide request=$requestId")
            KeyboardProxyManager.onImeVisibilityChanged(requestId, false)
        }
    }
    private val applyParkedWindowFlags = Runnable {
        if (!active && !closeRequested && !isFinishing && !isDestroyed) {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            )
            Log.i(TAG, "Applied parked window flags task=$taskId request=$requestId")
        }
    }
    private val requestIme = object : Runnable {
        override fun run() {
            if (!active || closeRequested || isFinishing || isDestroyed || !::editText.isInitialized) return
            val actualDisplayId = display?.displayId ?: Display.DEFAULT_DISPLAY
            if (actualDisplayId != expectedDisplayId) {
                Log.e(
                    TAG,
                    "Refuse IME on unexpected display=$actualDisplayId " +
                        "expected=$expectedDisplayId task=$taskId request=$requestId"
                )
                KeyboardProxyManager.release("display_mismatch")
                return
            }
            if (!editText.isFocused) {
                editText.requestFocus()
            }
            val inputMethodManager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            inputMethodManager.restartInput(editText)
            imeRequestAttempts++
            val accepted = inputMethodManager.showSoftInput(
                editText,
                InputMethodManager.SHOW_IMPLICIT
            )
            if (accepted) {
                imeShowAccepted = true
                ViewCompat.requestApplyInsets(window.decorView)
            }
            Log.i(
                TAG,
                "IME request accepted=$accepted attempt=$imeRequestAttempts " +
                    "windowFocus=${editText.hasWindowFocus()} viewFocus=${editText.isFocused} " +
                    "display=${display?.displayId} request=$requestId"
            )
            if (!accepted && imeRequestAttempts < MAX_IME_REQUEST_ATTEMPTS) {
                editText.postDelayed(this, IME_RETRY_DELAY_MS)
            }
        }
    }

    private fun forwardCommittedText(text: CharSequence?, source: String) {
        if (!active || closeRequested || releaseRequested) return
        val committed = text?.toString().orEmpty()
        if (committed.isEmpty()) return
        val now = SystemClock.elapsedRealtime()
        if (
            committed == lastForwardedText &&
            now - lastForwardedAtMs <= DUPLICATE_COMMIT_WINDOW_MS
        ) {
            Log.i(
                TAG,
                "skip_duplicate_commit_text src=$source lastSrc=$lastForwardedSource " +
                    "len=${committed.length} deltaMs=${now - lastForwardedAtMs} request=$requestId"
            )
            return
        }
        lastForwardedText = committed
        lastForwardedSource = source
        lastForwardedAtMs = now
        Log.i(
            TAG,
            "forward_commit_text src=$source len=${committed.length} request=$requestId"
        )
        KeyboardProxyManager.commitText(requestId, sessionId, committed)
    }

    private fun remoteKeyName(keyCode: Int): String? = when (keyCode) {
        in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z ->
            "VK_${('A'.code + keyCode - KeyEvent.KEYCODE_A).toChar()}"
        in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 ->
            "VK_${keyCode - KeyEvent.KEYCODE_0}"
        KeyEvent.KEYCODE_SPACE -> "VK_SPACE"
        KeyEvent.KEYCODE_TAB -> "VK_TAB"
        KeyEvent.KEYCODE_ENTER -> "VK_RETURN"
        KeyEvent.KEYCODE_DEL -> "VK_BACK"
        KeyEvent.KEYCODE_FORWARD_DEL -> "VK_DELETE"
        KeyEvent.KEYCODE_ESCAPE -> "VK_ESCAPE"
        KeyEvent.KEYCODE_DPAD_LEFT -> "VK_LEFT"
        KeyEvent.KEYCODE_DPAD_RIGHT -> "VK_RIGHT"
        KeyEvent.KEYCODE_DPAD_UP -> "VK_UP"
        KeyEvent.KEYCODE_DPAD_DOWN -> "VK_DOWN"
        else -> null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestId = intent.getLongExtra(EXTRA_REQUEST_ID, 0L)
        sessionId = intent.getStringExtra(EXTRA_SESSION_ID).orEmpty()
        val targetDisplayId = intent.getIntExtra(EXTRA_TARGET_DISPLAY_ID, -1)
        expectedDisplayId = targetDisplayId
        val actualDisplayId = display?.displayId ?: -1

        if (actualDisplayId != targetDisplayId) {
            finishReason = "launch_failed"
            finish()
            return
        }

        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
        )
        if (sessionId.isEmpty()) {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            )
        }
        rootView = FrameLayout(this).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            isFocusable = true
            isFocusableInTouchMode = true
            isClickable = true
        }
        editText = object : EditText(this) {
            override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
                val base = super.onCreateInputConnection(outAttrs)
                val host = this
                return object : InputConnectionWrapper(base, true) {
                    override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                        forwardCommittedText(text, "commitText")
                        val handled = super.commitText(text, newCursorPosition)
                        host.post {
                            if (!ignoreTextChange) {
                                ignoreTextChange = true
                                host.text?.clear()
                                ignoreTextChange = false
                            }
                        }
                        return handled
                    }

                    override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
                        Log.i(
                            TAG,
                            "set_composing_text len=${text?.length ?: 0} request=$requestId"
                        )
                        // Keep IME composition local; only final commitText is forwarded.
                        return super.setComposingText(text, newCursorPosition)
                    }

                    override fun finishComposingText(): Boolean {
                        val handled = super.finishComposingText()
                        val composed = host.text?.toString().orEmpty()
                        Log.i(
                            TAG,
                            "finish_composing_text len=${composed.length} request=$requestId"
                        )
                        if (composed.isNotEmpty()) {
                            forwardCommittedText(composed, "finishComposingText")
                            host.post {
                                if (!ignoreTextChange) {
                                    ignoreTextChange = true
                                    host.text?.clear()
                                    ignoreTextChange = false
                                }
                            }
                        }
                        return handled
                    }

                    override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                        val composingStart = BaseInputConnection.getComposingSpanStart(host.text)
                        Log.i(
                            TAG,
                            "delete_surrounding_text before=$beforeLength after=$afterLength " +
                                "composingStart=$composingStart request=$requestId"
                        )
                        // During composition: let IME manage pinyin/ composing text locally.
                        // Do NOT forward to remote — user is editing the composition, not remote content.
                        if (composingStart >= 0) {
                            return super.deleteSurroundingText(beforeLength, afterLength)
                        }
                        // No active composition: local EditText is empty (cleared after each commit).
                        // Forward delete to remote side for each character before the cursor (Backspace).
                        var forwarded = false
                        for (i in 0 until beforeLength) {
                            KeyboardProxyManager.sendKey(requestId, sessionId, "VK_BACK")
                            forwarded = true
                        }
                        // Forward-delete (Delete key, not Backspace) — rare on mobile but handle it.
                        for (i in 0 until afterLength) {
                            KeyboardProxyManager.sendKey(requestId, sessionId, "VK_DELETE")
                            forwarded = true
                        }
                        // Still call super so IME stays consistent; clear any text the IME
                        // may have set during this operation.
                        val superHandled = super.deleteSurroundingText(beforeLength, afterLength)
                        host.post {
                            if (!ignoreTextChange) {
                                ignoreTextChange = true
                                host.text?.clear()
                                ignoreTextChange = false
                            }
                        }
                        return superHandled || forwarded
                    }

                    override fun sendKeyEvent(event: KeyEvent?): Boolean {
                        if (event != null && event.action == KeyEvent.ACTION_DOWN &&
                            (event.isCtrlPressed || event.isAltPressed || event.isMetaPressed)
                        ) {
                            remoteKeyName(event.keyCode)?.let { key ->
                                Log.i(
                                    TAG,
                                    "forward_shortcut key=$key meta=${event.metaState} request=$requestId"
                                )
                                KeyboardProxyManager.sendKey(
                                    requestId,
                                    sessionId,
                                    key,
                                    alt = event.isAltPressed,
                                    ctrl = event.isCtrlPressed,
                                    shift = event.isShiftPressed,
                                    command = event.isMetaPressed
                                )
                                return true
                            }
                        }
                        if (event != null &&
                            event.action == KeyEvent.ACTION_DOWN &&
                            event.keyCode == KeyEvent.KEYCODE_DEL
                        ) {
                            val composingStart = BaseInputConnection.getComposingSpanStart(host.text)
                            Log.i(
                                TAG,
                                "send_key_event DEL composingStart=$composingStart request=$requestId"
                            )
                            // Only forward when not in composition (same logic as deleteSurroundingText).
                            if (composingStart < 0) {
                                KeyboardProxyManager.sendKey(requestId, sessionId, "VK_BACK")
                                return true
                            }
                        }
                        return super.sendKeyEvent(event)
                    }
                }
            }
        }.apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setTextColor(android.graphics.Color.TRANSPARENT)
            isCursorVisible = false
            isFocusable = true
            isFocusableInTouchMode = true
            inputType = EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE
            imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI

            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    if (ignoreTextChange) return
                    // Safety net for IMEs that bypass commitText callback.
                    val committed = s?.toString().orEmpty()
                    val composingStart = BaseInputConnection.getComposingSpanStart(editableText)
                    Log.i(
                        TAG,
                        "watcher_text_changed len=${committed.length} composingStart=$composingStart request=$requestId"
                    )
                    if (committed.isEmpty() || composingStart >= 0) return
                    forwardCommittedText(committed, "textWatcher")
                    ignoreTextChange = true
                    text.clear()
                    ignoreTextChange = false
                }

                override fun afterTextChanged(s: Editable?) = Unit
            })

            setOnEditorActionListener { _, actionId, event ->
                val enter = actionId == EditorInfo.IME_ACTION_DONE ||
                    actionId == EditorInfo.IME_ACTION_SEND ||
                    event?.keyCode == KeyEvent.KEYCODE_ENTER
                if (enter) KeyboardProxyManager.sendKey(requestId, sessionId, "VK_RETURN")
                enter
            }
            setOnKeyListener { _, keyCode, event ->
                if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                when (keyCode) {
                    KeyEvent.KEYCODE_DEL -> KeyboardProxyManager.sendKey(requestId, sessionId, "VK_BACK")
                    KeyEvent.KEYCODE_TAB -> KeyboardProxyManager.sendKey(requestId, sessionId, "VK_TAB")
                    else -> return@setOnKeyListener false
                }
                true
            }
        }
        rootView.addView(editText, FrameLayout.LayoutParams(1, 1))
        setContentView(
            rootView,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        val insetsView = window.decorView
        installInsetsListener(insetsView)

        rootView.requestFocus()
        insetsView.doOnAttach { ViewCompat.requestApplyInsets(it) }
        if (!KeyboardProxyManager.onActivityReady(this, requestId, actualDisplayId)) {
            finishReason = "launch_failed"
            finish()
            return
        }
    }

    fun activate(newRequestId: Long, newSessionId: String) {
        requestId = newRequestId
        sessionId = newSessionId
        finishReason = "activity_destroyed"
        active = true
        closeRequested = false
        releaseRequested = false
        userLeavePending = false
        restoreImeInProgress = false
        primaryMouseGuardUntilMs = 0L
        imeRequestAttempts = 0
        imeShowAccepted = false
        lastForwardedText = ""
        lastForwardedSource = ""
        lastForwardedAtMs = 0L
        window.decorView.removeCallbacks(applyParkedWindowFlags)
        window.clearFlags(
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        )
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
        )
        requestTaskFocus("activate")
        editText.removeCallbacks(requestIme)
        editText.removeCallbacks(classifyImeHidden)
        editText.removeCallbacks(finishAfterHideTimeout)
        editText.removeCallbacks(protectImeDuringPrimaryMouse)
        editText.isFocusable = true
        editText.isFocusableInTouchMode = true
        editText.requestFocus()
        window.decorView.requestFocus()
        ViewCompat.requestApplyInsets(window.decorView)
        editText.post(requestIme)
    }

    /**
     * Keep the cross-display IME host in front while a primary mouse or touchscreen
     * gesture is dispatched on the source display. Android otherwise moves focus
     * away from this Activity and starts hiding the IME before the delayed visibility
     * callback can classify the loss.
     *
     * Secondary-button events cancel the guard immediately. This is important for
     * devices that expose the right mouse button through both MotionEvent and
     * KeyEvent: moving the keyboard task during that down/up pair can swallow its up.
     */
    fun onSourcePointerGesture(primaryDown: Boolean, pointerUp: Boolean, secondary: Boolean) {
        if (!::editText.isInitialized) return
        if (secondary) {
            primaryMouseGuardUntilMs = 0L
            editText.removeCallbacks(protectImeDuringPrimaryMouse)
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (primaryDown) {
            primaryMouseGuardUntilMs = now + PRIMARY_MOUSE_GUARD_MAX_MS
            editText.removeCallbacks(protectImeDuringPrimaryMouse)
            // This method is called before the source Activity dispatches ACTION_DOWN.
            // Reassert the keyboard task now so Android never starts its hide animation.
            keepImeStableDuringPrimaryMouse()
            editText.postDelayed(
                protectImeDuringPrimaryMouse,
                PRIMARY_MOUSE_GUARD_INTERVAL_MS
            )
        } else if (pointerUp && primaryMouseGuardUntilMs > now) {
            primaryMouseGuardUntilMs = now + PRIMARY_MOUSE_GUARD_AFTER_UP_MS
            editText.removeCallbacks(protectImeDuringPrimaryMouse)
            editText.post(protectImeDuringPrimaryMouse)
        }
    }

    private fun keepImeStableDuringPrimaryMouse() {
        if (!active || closeRequested || releaseRequested || !::editText.isInitialized) return
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
        )
        requestTaskFocus("primary_mouse_guard")
        editText.isFocusable = true
        editText.isFocusableInTouchMode = true
        if (!editText.isFocused) editText.requestFocus()
        val inputMethodManager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        if (inputMethodManager.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)) {
            imeShowAccepted = true
        }
        ViewCompat.requestApplyInsets(window.decorView)
    }

    private fun requestTaskFocus(reason: String) {
        try {
            val actualDisplayId = display?.displayId ?: Display.DEFAULT_DISPLAY
            val activityManager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
            activityManager.moveTaskToFront(
                taskId,
                ActivityManager.MOVE_TASK_NO_USER_ACTION
            )
            Log.i(
                TAG,
                "Moved keyboard task to front reason=$reason task=$taskId " +
                    "display=$actualDisplayId expected=$expectedDisplayId request=$requestId"
            )
        } catch (error: Exception) {
            Log.w(
                TAG,
                "Unable to focus keyboard task reason=$reason task=$taskId request=$requestId",
                error
            )
        }
    }

    private fun installInsetsListener(view: android.view.View) {
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            reportImeInsets(insets)
            insets
        }
        view.viewTreeObserver.addOnWindowFocusChangeListener { hasFocus ->
            if (hasFocus && active && !closeRequested && ::editText.isInitialized) {
                editText.removeCallbacks(classifyImeHidden)
                editText.removeCallbacks(requestIme)
                editText.post(requestIme)
            }
        }
        ViewCompat.requestApplyInsets(view)
    }

    fun parkForReuse(reason: String) {
        finishReason = reason
        active = false
        closeRequested = false
        releaseRequested = false
        restoreImeInProgress = false
        imeRequestAttempts = 0
        imeShowAccepted = false
        primaryMouseGuardUntilMs = 0L
        if (!::editText.isInitialized) return
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
        )
        editText.removeCallbacks(requestIme)
        editText.removeCallbacks(classifyImeHidden)
        editText.removeCallbacks(finishAfterHideTimeout)
        editText.removeCallbacks(protectImeDuringPrimaryMouse)
        window.decorView.removeCallbacks(applyParkedWindowFlags)
        val lostWindowFocus = !editText.hasWindowFocus()
        editText.clearFocus()
        editText.isFocusable = false
        rootView.requestFocus()
        if (lostWindowFocus) {
            // Some IMEs return to the launcher while their hide animation is completing.
            // Reassert the already-existing task during that same user gesture, then make
            // the transparent window non-interactive after ActivityTaskManager has had a
            // chance to keep it at the front of the target display.
            window.clearFlags(
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            )
            requestTaskFocus("park_after_user_hide")
            window.decorView.postDelayed(applyParkedWindowFlags, 250L)
        } else {
            applyParkedWindowFlags.run()
        }
        Log.i(TAG, "Parked keyboard proxy for reuse display=${display?.displayId} request=$requestId reason=$reason")
    }

    private fun reportImeInsets(insets: WindowInsetsCompat) {
        val visible = insets.isVisible(WindowInsetsCompat.Type.ime())
        val bottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
        if (lastLoggedImeVisible != visible || lastLoggedImeBottom != bottom) {
            lastLoggedImeVisible = visible
            lastLoggedImeBottom = bottom
            Log.i(
                TAG,
                "IME insets visible=$visible bottom=$bottom " +
                    "windowFocus=${editText.hasWindowFocus()} display=${display?.displayId} request=$requestId"
            )
        }
        if (!active && !closeRequested) return
        if (visible && active && !imeShowAccepted) {
            Log.i(
                TAG,
                "Ignore stale IME visible before show acceptance request=$requestId"
            )
            return
        }
        if (visible) {
            editText.removeCallbacks(classifyImeHidden)
            editText.removeCallbacks(requestIme)
            restoreImeInProgress = false
            KeyboardProxyManager.onImeVisibilityChanged(requestId, true)
        } else if (active && !closeRequested) {
            // On this dual-screen Android build, clicking the remote display temporarily
            // removes focus from the IME host on the other display. Insets report hidden
            // before the window-focus callback arrives, so classify the cause after one
            // short grace period. The source Activity's real pointer-event timestamp is
            // authoritative because a per-display host can still report window focus.
            val recentSourcePointer = KeyboardProxyManager.hadRecentSourcePointerEvent(
                requestId,
                SOURCE_POINTER_GRACE_MS
            )
            if (recentSourcePointer && !restoreImeInProgress) {
                Log.i(
                    TAG,
                    "Restore IME immediately after source pointer request=$requestId"
                )
                restoreImeAfterExternalFocusLoss()
            } else if (!restoreImeInProgress) {
                editText.removeCallbacks(classifyImeHidden)
                editText.postDelayed(classifyImeHidden, IME_LOSS_CLASSIFY_DELAY_MS)
            }
        } else {
            KeyboardProxyManager.onImeVisibilityChanged(requestId, false)
        }
        if (!visible && closeRequested) completeHide()
    }

    private fun restoreImeAfterExternalFocusLoss() {
        if (!active || closeRequested || releaseRequested || !::editText.isInitialized) return
        restoreImeInProgress = true
        imeRequestAttempts = 0
        imeShowAccepted = false
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
        )
        requestTaskFocus("external_focus_loss")
        editText.isFocusable = true
        editText.isFocusableInTouchMode = true
        editText.requestFocus()
        window.decorView.requestFocus()
        ViewCompat.requestApplyInsets(window.decorView)
        editText.removeCallbacks(requestIme)
        editText.postDelayed(requestIme, IME_RESTORE_DELAY_MS)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && active && !closeRequested && ::editText.isInitialized) {
            userLeavePending = false
            editText.removeCallbacks(classifyImeHidden)
            editText.removeCallbacks(requestIme)
            editText.post(requestIme)
        }
    }

    fun hideIme(reason: String) {
        finishReason = reason
        active = false
        closeRequested = true
        restoreImeInProgress = false
        primaryMouseGuardUntilMs = 0L
        if (::editText.isInitialized) {
            window.setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
            )
            editText.removeCallbacks(requestIme)
            editText.removeCallbacks(classifyImeHidden)
            editText.removeCallbacks(finishAfterHideTimeout)
            editText.removeCallbacks(protectImeDuringPrimaryMouse)
            val inputMethodManager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            inputMethodManager.hideSoftInputFromWindow(editText.windowToken, 0)
            editText.clearFocus()
            editText.isFocusable = false
            rootView.requestFocus()
            ViewCompat.requestApplyInsets(window.decorView)
            editText.postDelayed(finishAfterHideTimeout, FINISH_AFTER_HIDE_TIMEOUT_MS)
        } else {
            completeHide()
        }
    }

    fun releaseAndFinish(reason: String) {
        releaseRequested = true
        if (lastLoggedImeVisible == true || active) {
            hideIme(reason)
        } else {
            finishReason = reason
            if (!isFinishing) finish()
        }
    }

    private fun completeHide() {
        if (::editText.isInitialized) editText.removeCallbacks(finishAfterHideTimeout)
        if (!closeRequested) return
        closeRequested = false
        if (releaseRequested) {
            if (!isFinishing) finish()
        } else {
            KeyboardProxyManager.onImeHidden(requestId, finishReason)
        }
    }

    override fun onBackPressed() {
        KeyboardProxyManager.close("user_hidden", requestId)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (active && !closeRequested && !releaseRequested) {
            // A pointer event on another display may produce this callback even though
            // this per-display Activity remains resumed. Confirm HOME from onStop().
            userLeavePending = true
            Log.i(TAG, "User leave pending task=$taskId request=$requestId")
        }
    }

    override fun onStop() {
        super.onStop()
        if (active && !closeRequested && !releaseRequested) {
            val reason = if (userLeavePending) "home_pressed" else "keyboard_host_stopped"
            Log.i(TAG, "Keyboard host stopped: release reason=$reason task=$taskId request=$requestId")
            KeyboardProxyManager.release(reason)
        }
    }

    override fun onDestroy() {
        if (::editText.isInitialized) {
            editText.removeCallbacks(requestIme)
            editText.removeCallbacks(classifyImeHidden)
            editText.removeCallbacks(finishAfterHideTimeout)
            editText.removeCallbacks(protectImeDuringPrimaryMouse)
        }
        window.decorView.removeCallbacks(applyParkedWindowFlags)
        KeyboardProxyManager.onActivityDestroyed(this, finishReason)
        super.onDestroy()
    }
}
