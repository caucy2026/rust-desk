package com.carriez.flutter_hbb

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display

object DeviceRole {
    fun isDualScreenPad(context: Context): Boolean {
        val manager = context.applicationContext
            .getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        return findUsableSecondaryDisplayId(manager) != null
    }

    fun findOppositeDisplayId(context: Context, sourceDisplayId: Int): Int {
        val manager = context.applicationContext
            .getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        return if (sourceDisplayId != Display.DEFAULT_DISPLAY) {
            val defaultDisplay = manager.getDisplay(Display.DEFAULT_DISPLAY)
            if (defaultDisplay != null && defaultDisplay.isValid &&
                defaultDisplay.state == Display.STATE_ON
            ) {
                Display.DEFAULT_DISPLAY
            } else {
                sourceDisplayId
            }
        } else {
            findUsableSecondaryDisplayId(manager) ?: sourceDisplayId
        }
    }

    fun findUsableSecondaryDisplayId(context: Context): Int? {
        val manager = context.applicationContext
            .getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        return findUsableSecondaryDisplayId(manager)
    }

    private fun findUsableSecondaryDisplayId(manager: DisplayManager): Int? {
        return manager.displays.firstOrNull { display ->
            display.displayId != Display.DEFAULT_DISPLAY &&
                display.isValid &&
                display.state == Display.STATE_ON &&
                display.flags and Display.FLAG_PRESENTATION != 0
        }?.displayId
    }

    /**
     * This predicate is the product contract. Do not broaden it with model,
     * manufacturer or chipset aliases: every condition below is required.
     */
    fun isKemiOwnedPad(context: Context): Boolean {
        if (!context.packageManager.hasSystemFeature("huanglong.product.type.stb")) {
            return false
        }
        if (Build.BRAND != "huanglong" || Build.DEVICE != "hi3781v730") return false

        val manager = context.applicationContext
            .getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        return manager.displays.any { display ->
            val mode = display.mode
            display.displayId != Display.DEFAULT_DISPLAY &&
                display.state != Display.STATE_OFF &&
                mode.physicalWidth == 1920 &&
                mode.physicalHeight == 1280 &&
                display.flags and Display.FLAG_PRESENTATION != 0
        }
    }
}
