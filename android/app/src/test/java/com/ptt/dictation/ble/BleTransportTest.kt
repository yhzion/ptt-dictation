package com.ptt.dictation.ble

import com.ptt.dictation.model.PttMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Test

class BleTransportTest {
    @Test
    fun `initial connection state is DISCONNECTED`() {
        val transport = FakeBleTransport()
        assertEquals(ConnectionState.DISCONNECTED, transport.connectionState.value)
    }

    @Test
    fun `encodeMessage produces valid JSON for PTT_START`() {
        val message = PttMessage.pttStart("client-1", "session-1")
        val json = BleMessageEncoder.encode(message)
        assert(json.contains("PTT_START"))
        assert(json.contains("session-1"))
    }

    @Test
    fun `encodeMessage produces valid JSON for PARTIAL`() {
        val message = PttMessage.partial("client-1", "session-1", 1, "안녕", 0.8)
        val json = BleMessageEncoder.encode(message)
        assert(json.contains("PARTIAL"))
        assert(json.contains("안녕"))
    }

    @Test
    fun `encodeMessage produces valid JSON for FINAL`() {
        val message = PttMessage.finalResult("client-1", "session-1", "안녕하세요", 0.95)
        val json = BleMessageEncoder.encode(message)
        assert(json.contains("FINAL"))
        assert(json.contains("안녕하세요"))
    }
}

class FakeBleTransport : PttTransport {
    override val connectionState: StateFlow<ConnectionState> =
        MutableStateFlow(ConnectionState.DISCONNECTED)

    override fun startScanning() {}

    override fun connect(deviceId: String) {}

    override fun disconnect() {}

    override fun send(message: PttMessage) {}

    override fun setListener(listener: PttTransportListener) {}
}
