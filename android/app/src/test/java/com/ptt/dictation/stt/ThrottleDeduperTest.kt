package com.ptt.dictation.stt

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThrottleDeduperTest {
    @Test
    fun `first text always passes`() {
        val td = ThrottleDeduper(intervalMs = 200)
        assertTrue(td.shouldEmit("hello", currentTimeMs = 0))
    }

    @Test
    fun `same text within interval is rejected`() {
        val td = ThrottleDeduper(intervalMs = 200)
        td.shouldEmit("hello", currentTimeMs = 0)
        assertFalse(td.shouldEmit("hello", currentTimeMs = 100))
    }

    @Test
    fun `same text after interval passes`() {
        val td = ThrottleDeduper(intervalMs = 200)
        td.shouldEmit("hello", currentTimeMs = 0)
        assertTrue(td.shouldEmit("hello", currentTimeMs = 250))
    }

    @Test
    fun `different text within interval passes`() {
        val td = ThrottleDeduper(intervalMs = 200)
        td.shouldEmit("hello", currentTimeMs = 0)
        assertTrue(td.shouldEmit("hello world", currentTimeMs = 50))
    }

    @Test
    fun `reset clears state`() {
        val td = ThrottleDeduper(intervalMs = 200)
        td.shouldEmit("hello", currentTimeMs = 0)
        td.reset()
        assertTrue(td.shouldEmit("hello", currentTimeMs = 50))
    }

    @Test
    fun `empty text is rejected`() {
        val td = ThrottleDeduper(intervalMs = 200)
        assertFalse(td.shouldEmit("", currentTimeMs = 0))
    }
}
