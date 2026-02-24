package com.ptt.dictation.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ptt.dictation.ble.ConnectionState
import com.ptt.dictation.ble.PttTransport
import com.ptt.dictation.ble.PttTransportListener
import com.ptt.dictation.model.PttMessage
import com.ptt.dictation.stt.STTEngine
import com.ptt.dictation.stt.STTListener
import com.ptt.dictation.stt.ThrottleDeduper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    val partialText: String = "",
) {
    val canPtt: Boolean get() = connectionState == ConnectionState.CONNECTED
}

class PttViewModel(
    private val transport: PttTransport,
    private val sttEngine: STTEngine,
    private val clientId: String = "android-${UUID.randomUUID().toString().take(8)}",
) : ViewModel() {
    private val _state = MutableStateFlow(PttUiState())
    val state: StateFlow<PttUiState> = _state.asStateFlow()

    private val throttleDeduper = ThrottleDeduper(intervalMs = 200)
    private var sessionId: String? = null
    private var partialSeq = 0
    private var shouldAutoReconnect = false
    private var reconnectJob: Job? = null
    private val accumulatedText = StringBuilder()

    init {
        transport.setListener(
            object : PttTransportListener {
                override fun onConnected() {
                    // Connection handled via connectionState flow
                }

                override fun onDisconnected() {
                    // Disconnection handled via connectionState flow
                }

                override fun onError(error: String) {
                    // No action needed for PoC
                }
            },
        )

        viewModelScope.launch {
            transport.connectionState.collect { connState ->
                val prevState = _state.value.connectionState
                _state.value = _state.value.copy(connectionState = connState)

                if (connState == ConnectionState.CONNECTED) {
                    reconnectJob?.cancel()
                    shouldAutoReconnect = true
                } else if (
                    prevState == ConnectionState.CONNECTED &&
                    connState == ConnectionState.DISCONNECTED &&
                    shouldAutoReconnect
                ) {
                    reconnectJob =
                        viewModelScope.launch {
                            delay(AUTO_RECONNECT_DELAY_MS)
                            if (shouldAutoReconnect) {
                                _state.value =
                                    _state.value.copy(
                                        connectionState = ConnectionState.CONNECTING,
                                    )
                                transport.startScanning()
                            }
                        }
                }
            }
        }

        sttEngine.setListener(
            object : STTListener {
                override fun onPartialResult(text: String) {
                    if (throttleDeduper.shouldEmit(text)) {
                        partialSeq++
                        val fullText = accumulatedText.toString() + text
                        _state.value = _state.value.copy(partialText = fullText)
                        sessionId?.let { sid ->
                            transport.send(
                                PttMessage.partial(clientId, sid, partialSeq, fullText, 0.5),
                            )
                        }
                    }
                }

                override fun onFinalResult(text: String) {
                    if (_state.value.isPttPressed) {
                        // Still holding — accumulate and restart STT
                        if (accumulatedText.isNotEmpty()) accumulatedText.append(" ")
                        accumulatedText.append(text)
                        _state.value =
                            _state.value.copy(partialText = accumulatedText.toString())
                        sttEngine.startListening()
                    } else {
                        // Released — send accumulated FINAL
                        if (accumulatedText.isNotEmpty()) accumulatedText.append(" ")
                        accumulatedText.append(text)
                        val finalText = accumulatedText.toString()
                        _state.value =
                            _state.value.copy(
                                partialText = "",
                                mode = PttMode.IDLE,
                            )
                        sessionId?.let { sid ->
                            transport.send(
                                PttMessage.finalResult(clientId, sid, finalText, 0.9),
                            )
                        }
                        sessionId = null
                        accumulatedText.clear()
                    }
                }

                override fun onError(
                    errorCode: Int,
                    message: String,
                ) {
                    if (_state.value.isPttPressed) {
                        // Still holding — restart STT
                        sttEngine.startListening()
                    } else {
                        // Released — send accumulated text if any
                        val finalText = accumulatedText.toString()
                        if (finalText.isNotEmpty()) {
                            sessionId?.let { sid ->
                                transport.send(
                                    PttMessage.finalResult(clientId, sid, finalText, 0.9),
                                )
                            }
                        }
                        _state.value =
                            _state.value.copy(
                                mode = PttMode.IDLE,
                                partialText = "",
                            )
                        sessionId = null
                        accumulatedText.clear()
                    }
                }
            },
        )
    }

    fun onConnect() {
        _state.value = _state.value.copy(connectionState = ConnectionState.CONNECTING)
        transport.startScanning()
    }

    fun onDisconnect() {
        shouldAutoReconnect = false
        reconnectJob?.cancel()
        transport.disconnect()
    }

    fun onPttPress() {
        if (!_state.value.canPtt) return
        sessionId = "s-${UUID.randomUUID().toString().take(8)}"
        partialSeq = 0
        throttleDeduper.reset()
        accumulatedText.clear()
        _state.value =
            _state.value.copy(
                isPttPressed = true,
                mode = PttMode.LISTENING,
                partialText = "",
            )
        transport.send(PttMessage.pttStart(clientId, sessionId!!))
        sttEngine.startListening()
    }

    fun onPttRelease() {
        _state.value = _state.value.copy(isPttPressed = false)
        sttEngine.stopListening()
    }

    override fun onCleared() {
        super.onCleared()
        transport.disconnect()
    }

    class Factory(
        private val transport: PttTransport,
        private val sttEngine: STTEngine,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = PttViewModel(transport, sttEngine) as T
    }

    companion object {
        private const val AUTO_RECONNECT_DELAY_MS = 2000L
    }
}
