package com.carriez.flutter_hbb

/** Version ordering used by the client download page and self-updater. */
object ClientVersionPolicy {
    fun compare(left: String, right: String): Int {
        val leftParts = numericParts(left)
        val rightParts = numericParts(right)
        val count = maxOf(leftParts.size, rightParts.size)
        for (index in 0 until count) {
            val result = leftParts.getOrElse(index) { 0L }.compareTo(
                rightParts.getOrElse(index) { 0L },
            )
            if (result != 0) return result
        }
        return 0
    }

    fun isUpdateAvailable(remote: String, installed: String): Boolean =
        remote.isNotBlank() && installed.isNotBlank() && compare(remote, installed) > 0

    private fun numericParts(version: String): List<Long> =
        Regex("\\d+").findAll(version).map { it.value.toLongOrNull() ?: 0L }.toList()
}
