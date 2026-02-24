package com.ptt.dictation.ws

import com.ptt.dictation.model.PttMessage
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test

class FakeWebSocketClient : WebSocketClient {
    override val connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val sentMessages = mutableListOf<PttMessage>()
    var currentListener: MessageListener? = null

    override fun connect(url: String) {
        connectionState.value = ConnectionState.CONNECTED
    }

    override fun disconnect() {
        connectionState.value = ConnectionState.DISCONNECTED
    }

    override fun send(message: PttMessage) {
        sentMessages.add(message)
    }

    override fun setListener(listener: MessageListener) {
        this.currentListener = listener
    }
}

class WebSocketManagerTest {
    @Test
    fun `connect changes state to CONNECTED`() {
        val client = FakeWebSocketClient()
        assertEquals(ConnectionState.DISCONNECTED, client.connectionState.value)
        client.connect("ws://localhost:9876")
        assertEquals(ConnectionState.CONNECTED, client.connectionState.value)
    }

    @Test
    fun `disconnect changes state to DISCONNECTED`() {
        val client = FakeWebSocketClient()
        client.connect("ws://localhost:9876")
        client.disconnect()
        assertEquals(ConnectionState.DISCONNECTED, client.connectionState.value)
    }

    @Test
    fun `send records messages`() {
        val client = FakeWebSocketClient()
        client.connect("ws://localhost:9876")
        val msg = PttMessage.hello("phone-01", "Galaxy", "Google")
        client.send(msg)
        assertEquals(1, client.sentMessages.size)
        assertEquals("HELLO", client.sentMessages[0].type)
    }
}
