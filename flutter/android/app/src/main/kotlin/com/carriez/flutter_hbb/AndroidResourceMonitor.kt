package com.carriez.flutter_hbb

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Debug
import android.os.Process
import android.os.SystemClock

/**
 * Lightweight, on-demand resource sampler for the Android client.
 *
 * Flutter calls this only while the resource dialog is visible. CPU usage is
 * calculated from process CPU-time deltas, so sampling once per second does not
 * create a permanent background monitor or hold the remote session open.
 */
object AndroidResourceMonitor {
    private var lastProcessCpuMs: Long? = null
    private var lastWallClockMs: Long? = null
    private val processStartedAtMs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        Process.getStartUptimeMillis()
    } else {
        // Android 5/6 do not expose the process start uptime. Keep a safe
        // monotonic fallback rather than reading restricted procfs fields.
        SystemClock.elapsedRealtime()
    }

    @Synchronized
    fun snapshot(context: Context): Map<String, Any> {
        val nowWallClockMs = SystemClock.elapsedRealtime()
        val nowProcessCpuMs = Process.getElapsedCpuTime()
        val previousWallClockMs = lastWallClockMs
        val previousProcessCpuMs = lastProcessCpuMs
        val sampleReady = previousWallClockMs != null && previousProcessCpuMs != null
        val elapsedWallClockMs = if (sampleReady) {
            (nowWallClockMs - previousWallClockMs!!).coerceAtLeast(1L)
        } else {
            0L
        }
        val elapsedProcessCpuMs = if (sampleReady) {
            (nowProcessCpuMs - previousProcessCpuMs!!).coerceAtLeast(0L)
        } else {
            0L
        }
        val cpuPercent = if (sampleReady) {
            elapsedProcessCpuMs.toDouble() * 100.0 / elapsedWallClockMs.toDouble()
        } else {
            0.0
        }
        lastWallClockMs = nowWallClockMs
        lastProcessCpuMs = nowProcessCpuMs

        val activityManager =
            context.applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val processMemory = activityManager
            .getProcessMemoryInfo(intArrayOf(Process.myPid()))
            .firstOrNull() ?: Debug.MemoryInfo()
        val totalPssBytes = processMemory.totalPss.toLong().coerceAtLeast(0L) * 1024L
        val nativePssBytes = processMemory.nativePss.toLong().coerceAtLeast(0L) * 1024L
        val dalvikPssBytes = processMemory.dalvikPss.toLong().coerceAtLeast(0L) * 1024L
        val otherPssBytes =
            (totalPssBytes - nativePssBytes - dalvikPssBytes).coerceAtLeast(0L)

        val runtime = Runtime.getRuntime()
        val javaHeapBytes = (runtime.totalMemory() - runtime.freeMemory()).coerceAtLeast(0L)
        val systemMemory = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(systemMemory)

        return mapOf(
            "sampleReady" to sampleReady,
            "cpuPercent" to cpuPercent,
            "memoryBytes" to totalPssBytes,
            "nativeMemoryBytes" to nativePssBytes,
            "dalvikMemoryBytes" to dalvikPssBytes,
            "otherMemoryBytes" to otherPssBytes,
            "javaHeapBytes" to javaHeapBytes,
            "javaHeapLimitBytes" to runtime.maxMemory(),
            "systemUsedMemoryBytes" to
                (systemMemory.totalMem - systemMemory.availMem).coerceAtLeast(0L),
            "systemAvailableMemoryBytes" to systemMemory.availMem.coerceAtLeast(0L),
            "systemTotalMemoryBytes" to systemMemory.totalMem.coerceAtLeast(0L),
            "lowMemory" to systemMemory.lowMemory,
            "logicalCoreCount" to runtime.availableProcessors(),
            "processUptimeSeconds" to
                ((nowWallClockMs - processStartedAtMs).coerceAtLeast(0L) / 1000L),
            "timestampMs" to System.currentTimeMillis()
        )
    }
}
