package com.carriez.flutter_hbb

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
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
        private const val IME_RETRY_DELAY_MS = 2_000L
        private const val MAX_IME_REQUEST_ATTEMPTS = 3
        private const val FINISH_AFTER_HIDE_TIMEOUT_MS = 2_000L

        fun launch(
            context: Context,
            requestId: Long,
            sessionId: String,
            targetDisplayId: Int
        ) {
            val intent = Intent(context, KeyboardProxyActivity::class.java).apply {
                putExtra(EXTRA_REQUEST_ID, requestId)
                putExtra(EXTRA_SESSION_ID, sessionId)
                putExtra(EXTRA_TARGET_DISPLAY_ID, targetDisplayId)
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION
                )
            }
            val options = ActivityOptions.makeBasic().apply {
                launchDisplayId = targetDisplayId
            }
            context.startActivity(intent, options.toBundle())
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
    private var active = false
    private var closeRequested = false
    private var releaseRequested = false
    private val finishAfterHideTimeout = Runnable { completeHide() }
    private val requestIme = object : Runnable {
        override fun run() {
            if (!active || closeRequested || isFinishing || isDestroyed || !::editText.isInitialized) return
            if (!hasWindowFocus()) {
                editText.postDelayed(this, IME_RETRY_DELAY_MS)
                return
            }

            if (!editText.isFocused) editText.requestFocus()
            val inputMethodManager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
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
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        rootView = FrameLayout(this).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            isFocusable = true
            isFocusableInTouchMode = true
        }
        editText = EditText(this).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setTextColor(android.graphics.Color.TRANSPARENT)
            isCursorVisible = false
            isFocusable = false
            inputType = EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE
            imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI

            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    if (ignoreTextChange) return
                    val committed = s?.toString().orEmpty()
                    if (committed.isEmpty() || BaseInputConnection.getComposingSpanStart(editableText) >= 0) return
                    KeyboardProxyManager.commitText(requestId, sessionId, committed)
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
        editText.removeCallbacks(requestIme)
        editText.removeCallbacks(finishAfterHideTimeout)
        editText.isFocusableInTouchMode = true
        editText.requestFocus()
        ViewCompat.requestApplyInsets(window.decorView)
        if (hasWindowFocus()) editText.post(requestIme)
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
