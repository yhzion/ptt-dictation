package com.ptt.dictation.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `serialize HELLO message`() {
        val msg = PttMessage.hello("phone-01", "Galaxy S23", "Google")
        val str = json.encodeToString(PttMessage.serializer(), msg)
        assertTrue(str.contains(""""type":"HELLO""""))
        assertTrue(str.contains(""""clientId":"phone-01""""))
    }

    @Test
    fun `serialize PTT_START message`() {
        val msg = PttMessage.pttStart("phone-01", "s-abc")
        val str = json.encodeToString(PttMessage.serializer(), msg)
        assertTrue(str.contains(""""type":"PTT_START""""))
        assertTrue(str.contains(""""sessionId":"s-abc""""))
    }

    @Test
    fun `serialize PARTIAL message`() {
        val msg = PttMessage.partial("phone-01", "s-abc", 3, "안녕하세요", 0.7)
        val str = json.encodeToString(PttMessage.serializer(), msg)
        assertTrue(str.contains(""""type":"PARTIAL""""))
        assertTrue(str.contains(""""text":"안녕하세요""""))
        assertTrue(str.contains(""""seq":3"""))
    }

    @Test
    fun `serialize FINAL message`() {
        val msg = PttMessage.finalResult("phone-01", "s-abc", "최종 텍스트", 0.95)
        val str = json.encodeToString(PttMessage.serializer(), msg)
        assertTrue(str.contains(""""type":"FINAL""""))
        assertTrue(str.contains(""""text":"최종 텍스트""""))
    }

    @Test
    fun `serialize HEARTBEAT message`() {
        val msg = PttMessage.heartbeat("phone-01")
        val str = json.encodeToString(PttMessage.serializer(), msg)
        assertTrue(str.contains(""""type":"HEARTBEAT""""))
    }

    @Test
    fun `deserialize ACK message`() {
        val raw = """{"type":"ACK","clientId":"phone-01","payload":{"ackType":"HELLO"}}"""
        val msg = json.decodeFromString(PttMessage.serializer(), raw)
        assertEquals("ACK", msg.type)
        assertEquals("phone-01", msg.clientId)
    }

    @Test
    fun `roundtrip serialization`() {
        val original = PttMessage.hello("phone-01", "Pixel", "Google")
        val str = json.encodeToString(PttMessage.serializer(), original)
        val parsed = json.decodeFromString(PttMessage.serializer(), str)
        assertEquals(original.type, parsed.type)
        assertEquals(original.clientId, parsed.clientId)
    }
}
