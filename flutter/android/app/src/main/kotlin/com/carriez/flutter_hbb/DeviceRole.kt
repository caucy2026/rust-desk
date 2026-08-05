package com.carriez.flutter_hbb

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display

object DeviceRole {
    /** A relay-capable PAD must currently expose at least two usable displays. */
    fun isDualScreenPad(context: Context): Boolean {
        val manager = context.applicationContext
            .getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        return manager.displays.count { it.state != Display.STATE_OFF } >= 2
    }
}
