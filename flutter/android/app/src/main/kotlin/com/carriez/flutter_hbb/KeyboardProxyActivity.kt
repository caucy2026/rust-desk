package com.carriez.flutter_hbb

import android.app.Activity
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
    private var finishReason = "activity_destroyed"
    private lateinit var rootView: FrameLayout
    private lateinit var editText: EditText
    private var ignoreTextChange = false
    private var imeRequestAttempts = 0
    private var lastLoggedImeVisible: Boolean? = null
    private var lastLoggedImeBottom = -1
    private var lastForwardedText = ""
    private var lastForwardedSource = ""
    private var lastForwardedAtMs = 0L
    private var active = false
    private var closeRequested = false
    private var releaseRequested = false
    private val finishAfterHideTimeout = Runnable { completeHide() }
    private val requestIme = object : Runnable {
        override fun run() {
            if (!active || closeRequested || isFinishing || isDestroyed || !::editText.isInitialized) return
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
            Log.i(
                TAG,
                "IME request accepted=$accepted attempt=$imeRequestAttempts " +
                    "windowFocus=${hasWindowFocus()} viewFocus=${editText.isFocused} " +
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestId = intent.getLongExtra(EXTRA_REQUEST_ID, 0L)
        sessionId = intent.getStringExtra(EXTRA_SESSION_ID).orEmpty()
        val targetDisplayId = intent.getIntExtra(EXTRA_TARGET_DISPLAY_ID, -1)
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
        ViewCompat.setOnApplyWindowInsetsListener(insetsView) { _, insets ->
            reportImeInsets(insets)
            insets
        }

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
        imeRequestAttempts = 0
        lastForwardedText = ""
        lastForwardedSource = ""
        lastForwardedAtMs = 0L
        editText.removeCallbacks(requestIme)
        editText.removeCallbacks(finishAfterHideTimeout)
        editText.isFocusable = true
        editText.isFocusableInTouchMode = true
        editText.requestFocus()
        window.decorView.requestFocus()
        ViewCompat.requestApplyInsets(window.decorView)
        editText.post(requestIme)
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
                    "windowFocus=${hasWindowFocus()} display=${display?.displayId} request=$requestId"
            )
        }
        if (visible) editText.removeCallbacks(requestIme)
        KeyboardProxyManager.onImeVisibilityChanged(requestId, visible)
        if (!visible && closeRequested) completeHide()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && active && !closeRequested && ::editText.isInitialized) {
            editText.removeCallbacks(requestIme)
            editText.post(requestIme)
        }
    }

    fun hideIme(reason: String) {
        finishReason = reason
        active = false
        closeRequested = true
        if (::editText.isInitialized) {
            editText.removeCallbacks(requestIme)
            editText.removeCallbacks(finishAfterHideTimeout)
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

    override fun onDestroy() {
        if (::editText.isInitialized) {
            editText.removeCallbacks(requestIme)
            editText.removeCallbacks(finishAfterHideTimeout)
        }
        KeyboardProxyManager.onActivityDestroyed(this, finishReason)
        super.onDestroy()
    }
}
