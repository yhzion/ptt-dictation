package com.ptt.dictation.ws

import com.ptt.dictation.model.PttMessage
import kotlinx.coroutines.flow.StateFlow

enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED }

interface WebSocketClient {
    val connectionState: StateFlow<ConnectionState>

    fun connect(url: String)

    fun disconnect()

    fun send(message: PttMessage)

    fun setListener(listener: MessageListener)
}

interface MessageListener {
    fun onMessage(message: PttMessage)

    fun onError(error: String)
}
