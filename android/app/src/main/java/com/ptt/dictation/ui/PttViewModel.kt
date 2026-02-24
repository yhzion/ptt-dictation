package com.ptt.dictation.ui

import com.ptt.dictation.ws.ConnectionState

enum class PttMode { IDLE, LISTENING }

data class PttUiState(
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val isPttPressed: Boolean = false,
    val mode: PttMode = PttMode.IDLE,
    val serverHost: String = "192.168.1.1",
    val serverPort: Int = 9876,
    val partialText: String = "",
) {
    val canPtt: Boolean get() = connectionState == ConnectionState.CONNECTED

    val wsUrl: String get() = "ws://$serverHost:$serverPort"
}
