package com.carriez.flutter_hbb

import android.os.Build
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent

/**
 * Captures Android mouse secondary-button events before the framework can
 * reinterpret them as Back. The caller enables this only while its display is
 * showing a remote-control page.
 */
class PhysicalMouseRightButtonForwarder(
    private val emit: (String) -> Unit,
) {
    private var active = false
    private var secondaryDown = false

    fun setActive(value: Boolean) {
        if (active && !value) {
            releaseIfNeeded()
        }
        active = value
    }

    fun handleMotionEvent(event: MotionEvent): Boolean {
        if (!active || !isMouseSource(event.source)) return false

        val action = event.actionMasked
        val actionButton = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            event.actionButton
        } else {
            0
        }
        val secondaryInState =
            event.buttonState and MotionEvent.BUTTON_SECONDARY != 0

        val isDown =
            (action == MotionEvent.ACTION_DOWN && secondaryInState) ||
                (action == MotionEvent.ACTION_BUTTON_PRESS &&
                    (actionButton == MotionEvent.BUTTON_SECONDARY || secondaryInState))
        val isUp =
            (action == MotionEvent.ACTION_UP && secondaryDown) ||
                (action == MotionEvent.ACTION_BUTTON_RELEASE &&
                    (actionButton == MotionEvent.BUTTON_SECONDARY || secondaryDown)) ||
                (action == MotionEvent.ACTION_CANCEL && secondaryDown)

        // Some Android mouse firmwares occasionally omit BUTTON_RELEASE when
        // focus moves between displays. Reconcile our state from the next mouse
        // event so the remote side can never remain stuck in right-button-down.
        val secondaryNoLongerPressed =
            secondaryDown &&
                !secondaryInState &&
                action != MotionEvent.ACTION_DOWN &&
                action != MotionEvent.ACTION_BUTTON_PRESS

        if (isDown) {
            if (!secondaryDown) {
                secondaryDown = true
                emit("down")
            }
            return true
        }
        if (isUp || secondaryNoLongerPressed) {
            releaseIfNeeded()
            return isUp
        }
        return false
    }

    fun handleKeyEvent(event: KeyEvent): Boolean {
        if (!active ||
            event.keyCode != KeyEvent.KEYCODE_BACK ||
            !isMouseSource(event.source)
        ) {
            return false
        }

        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (!secondaryDown && event.repeatCount == 0) {
                    secondaryDown = true
                    emit("down")
                }
            }
            KeyEvent.ACTION_UP -> releaseIfNeeded()
        }
        return true
    }

    fun releaseIfNeeded() {
        if (secondaryDown) {
            secondaryDown = false
            emit("up")
        }
    }

    private fun isMouseSource(source: Int): Boolean =
        source and InputDevice.SOURCE_MOUSE == InputDevice.SOURCE_MOUSE ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                source and InputDevice.SOURCE_MOUSE_RELATIVE ==
                InputDevice.SOURCE_MOUSE_RELATIVE)
}
