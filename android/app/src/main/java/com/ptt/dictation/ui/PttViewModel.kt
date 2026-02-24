package com.ptt.dictation.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ptt.dictation.model.PttMessage
import com.ptt.dictation.stt.STTEngine
import com.ptt.dictation.stt.STTListener
import com.ptt.dictation.stt.ThrottleDeduper
import com.ptt.dictation.ws.ConnectionState
import com.ptt.dictation.ws.MessageListener
import com.ptt.dictation.ws.WebSocketClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

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

class PttViewModel(
    private val wsClient: WebSocketClient,
    private val sttEngine: STTEngine,
    private val clientId: String = "android-${UUID.randomUUID().toString().take(8)}",
) : ViewModel() {
    private val _state = MutableStateFlow(PttUiState())
    val state: StateFlow<PttUiState> = _state.asStateFlow()

    private val throttleDeduper = ThrottleDeduper(intervalMs = 200)
    private var sessionId: String? = null
    private var partialSeq = 0

    init {
        wsClient.setListener(
            object : MessageListener {
                override fun onMessage(message: PttMessage) {
                    // ACK received - no action needed for PoC
                }

                override fun onError(error: String) {
                    // No action needed for PoC
                }
            },
        )

        viewModelScope.launch {
            wsClient.connectionState.collect { connState ->
                _state.value = _state.value.copy(connectionState = connState)
            }
        }

        sttEngine.setListener(
            object : STTListener {
                override fun onPartialResult(text: String) {
                    if (throttleDeduper.shouldEmit(text)) {
                        partialSeq++
                        _state.value = _state.value.copy(partialText = text)
                        sessionId?.let { sid ->
                            wsClient.send(
                                PttMessage.partial(clientId, sid, partialSeq, text, 0.5),
                            )
                        }
                    }
                }

                override fun onFinalResult(text: String) {
                    _state.value =
                        _state.value.copy(
                            partialText = "",
                            isPttPressed = false,
                            mode = PttMode.IDLE,
                        )
                    sessionId?.let { sid ->
                        wsClient.send(PttMessage.finalResult(clientId, sid, text, 0.9))
                    }
                    sessionId = null
                }

                override fun onError(
                    errorCode: Int,
                    message: String,
                ) {
                    _state.value =
                        _state.value.copy(
                            isPttPressed = false,
                            mode = PttMode.IDLE,
                            partialText = "",
                        )
                }
            },
        )
    }

    fun onServerHostChange(host: String) {
        _state.value = _state.value.copy(serverHost = host)
    }

    fun onServerPortChange(port: Int) {
        _state.value = _state.value.copy(serverPort = port)
    }

    fun onConnect() {
        _state.value = _state.value.copy(connectionState = ConnectionState.CONNECTING)
        wsClient.connect(_state.value.wsUrl)
    }

    fun onDisconnect() {
        wsClient.disconnect()
    }

    fun onPttPress() {
        if (!_state.value.canPtt) return
        sessionId = "s-${UUID.randomUUID().toString().take(8)}"
        partialSeq = 0
        throttleDeduper.reset()
        _state.value =
            _state.value.copy(
                isPttPressed = true,
                mode = PttMode.LISTENING,
                partialText = "",
            )
        wsClient.send(PttMessage.pttStart(clientId, sessionId!!))
        sttEngine.startListening()
    }

    fun onPttRelease() {
        _state.value = _state.value.copy(isPttPressed = false)
        sttEngine.stopListening()
    }

    override fun onCleared() {
        super.onCleared()
        wsClient.disconnect()
    }

    class Factory(
        private val wsClient: WebSocketClient,
        private val sttEngine: STTEngine,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = PttViewModel(wsClient, sttEngine) as T
    }
}
