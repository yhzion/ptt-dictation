package com.ptt.dictation.stt

class ThrottleDeduper(private val intervalMs: Long = 200) {
    private var lastText: String? = null
    private var lastEmitTime: Long = 0

    fun shouldEmit(
        text: String,
        currentTimeMs: Long = System.currentTimeMillis(),
    ): Boolean {
        if (text.isEmpty()) return false
        if (text != lastText || (currentTimeMs - lastEmitTime) >= intervalMs) {
            lastText = text
            lastEmitTime = currentTimeMs
            return true
        }
        return false
    }

    fun reset() {
        lastText = null
        lastEmitTime = 0
    }
}
