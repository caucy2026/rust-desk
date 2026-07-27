package com.carriez.flutter_hbb

import io.flutter.plugin.common.MethodChannel

/**
 * Cross-screen communication singleton for dual-screen RustDesk.
 *
 * 主屏 (Display 0) ↔ 副屏 (Display 2) 通信桥梁:
 *   - 主屏键盘输入 → SessionState → 副屏 MethodChannel → Flutter → FFI → 远程
 *   - 副屏 sessionId → SessionState → 主屏 (用于直接 FFI 调用)
 *
 * Reference: chip.md §2.7 — GlobeState 单例模式
 */
object SessionState {

    /** MethodChannel of RemoteActivity's Flutter engine (Display 2). */
    @Volatile
    var remoteMethodChannel: MethodChannel? = null

    /** The current remote session ID, set by RemoteActivity's Flutter. */
    @Volatile
    var currentSessionId: String? = null

    /** Whether the remote desktop connection is active. */
    @Volatile
    var isRemoteConnected: Boolean = false

    /**
     * Forward a key string from main screen to the remote side.
     * Called from MainActivity's MethodChannel handler.
     */
    fun forwardKeyString(text: String) {
        remoteMethodChannel?.invokeMethod("on_key_string", mapOf("text" to text))
    }

    /**
     * Forward a single key event (virtual key name) to the remote side.
     */
    fun forwardKeyEvent(keyName: String, down: Boolean) {
        remoteMethodChannel?.invokeMethod("on_key_event", mapOf(
            "key" to keyName,
            "down" to down
        ))
    }

    /**
     * Notify the main screen about remote connection state changes.
     */
    fun notifyConnectionState(connected: Boolean, sessionId: String?) {
        isRemoteConnected = connected
        currentSessionId = sessionId
        MainActivity.flutterMethodChannel?.invokeMethod("on_remote_state", mapOf(
            "connected" to connected,
            "sessionId" to (sessionId ?: "")
        ))
    }

    /**
     * Reset all state (called when remote disconnects or app exits).
     */
    fun reset() {
        remoteMethodChannel = null
        currentSessionId = null
        isRemoteConnected = false
    }
}
