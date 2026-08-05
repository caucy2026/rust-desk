package com.carriez.flutter_hbb

import android.os.StatFs
import java.io.File

/** Reads usable bytes from the filesystem that owns [path]. */
object AndroidStorageSpace {
    fun availableBytes(path: String): Long? {
        if (path.isBlank()) return null
        var current: File? = File(path)
        while (current != null && !current.exists()) {
            current = current.parentFile
        }
        val existing = current ?: return null
        return runCatching { StatFs(existing.absolutePath).availableBytes }
            .getOrNull()
            ?.takeIf { it >= 0L }
    }
}
