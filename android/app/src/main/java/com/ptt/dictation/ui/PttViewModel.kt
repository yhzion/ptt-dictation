package com.ptt.dictation.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ptt.dictation.ble.ConnectionState
import com.ptt.dictation.ble.PttTransport
import com.ptt.dictation.ble.PttTransportListener
import com.ptt.dictation.model.PttMessage
import com.ptt.dictation.rules.NoopTextRuleService
import com.ptt.dictation.rules.TextRuleService
import com.ptt.dictation.stt.STTEngine
import com.ptt.dictation.stt.STTListener
import com.ptt.dictation.stt.ThrottleDeduper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

enum class PttMode { IDLE, LISTENING }

data class PttUiState(
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val isPttPressed: Boolean = false,
    val mode: PttMode = PttMode.IDLE,
    val partialText: String = "",
    val recognitionHapticTick: Int = 0,
) {
    val canPtt: Boolean get() = connectionState == ConnectionState.CONNECTED
}

class PttViewModel(
    private val transport: PttTransport,
    private val sttEngine: STTEngine,
    private val clientId: String = "android-${UUID.randomUUID().toString().take(8)}",
    private val textRuleService: TextRuleService = NoopTextRuleService(),
) : ViewModel() {
    private val _state = MutableStateFlow(PttUiState())
    val state: StateFlow<PttUiState> = _state.asStateFlow()

    private val throttleDeduper = ThrottleDeduper(intervalMs = PARTIAL_EMIT_INTERVAL_MS)
    private var sessionId: String? = null
    private var partialSeq = 0
    private var shouldAutoReconnect = true
    private var reconnectJob: Job? = null
    private var ruleSyncJob: Job? = null
    private val accumulatedText = StringBuilder()
    private var hasRecognitionHapticFired = false

    init {
        bootstrapRuleService()

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
                _state.value = _state.value.copy(connectionState = connState)

                if (connState == ConnectionState.CONNECTED) {
                    reconnectJob?.cancel()
                } else if (connState == ConnectionState.DISCONNECTED && shouldAutoReconnect) {
                    scheduleAutoReconnect()
                }
            }
        }

        sttEngine.setListener(
            object : STTListener {
                override fun onPartialResult(text: String) {
                    if (throttleDeduper.shouldEmit(text)) {
                        partialSeq++
                        val fullText = accumulatedText.toString() + text
                        val transformedText = textRuleService.apply(fullText)
                        _state.value = _state.value.copy(partialText = transformedText)
                        fireRecognitionHapticIfNeeded(transformedText)
                        sessionId?.let { sid ->
                            transport.send(
                                PttMessage.partial(clientId, sid, partialSeq, transformedText, 0.5),
                            )
                        }
                    }
                }

                override fun onFinalResult(text: String) {
                    if (_state.value.isPttPressed) {
                        // Still holding — accumulate and restart STT
                        if (accumulatedText.isNotEmpty()) accumulatedText.append(" ")
                        accumulatedText.append(text)
                        val transformedText = textRuleService.apply(accumulatedText.toString())
                        _state.value =
                            _state.value.copy(partialText = transformedText)
                        fireRecognitionHapticIfNeeded(transformedText)
                        sttEngine.startListening()
                    } else {
                        // Released — send accumulated FINAL
                        if (accumulatedText.isNotEmpty()) accumulatedText.append(" ")
                        accumulatedText.append(text)
                        val finalText = textRuleService.apply(accumulatedText.toString())
                        fireRecognitionHapticIfNeeded(finalText)
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
                        val finalText = textRuleService.apply(accumulatedText.toString())
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

        // Start scanning immediately on app launch.
        onConnect()
    }

    fun onConnect() {
        shouldAutoReconnect = true
        reconnectJob?.cancel()
        if (_state.value.connectionState != ConnectionState.DISCONNECTED) return
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
        hasRecognitionHapticFired = false
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
        sessionId?.let { sid ->
            transport.send(PttMessage.pttEnd(clientId, sid))
        }
        sttEngine.stopListening()
    }

    override fun onCleared() {
        super.onCleared()
        ruleSyncJob?.cancel()
        transport.disconnect()
    }

    class Factory(
        private val transport: PttTransport,
        private val sttEngine: STTEngine,
        private val textRuleService: TextRuleService = NoopTextRuleService(),
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            PttViewModel(
                transport = transport,
                sttEngine = sttEngine,
                textRuleService = textRuleService,
            ) as T
    }

    companion object {
        private const val AUTO_RECONNECT_DELAY_MS = 2000L
        private const val PARTIAL_EMIT_INTERVAL_MS = 100L
        private const val RULE_SYNC_INTERVAL_MS = 300_000L
    }

    private fun scheduleAutoReconnect() {
        if (reconnectJob?.isActive == true) return
        reconnectJob =
            viewModelScope.launch {
                delay(AUTO_RECONNECT_DELAY_MS)
                if (!shouldAutoReconnect) return@launch
                if (_state.value.connectionState != ConnectionState.DISCONNECTED) return@launch
                _state.value =
                    _state.value.copy(
                        connectionState = ConnectionState.CONNECTING,
                    )
                transport.startScanning()
            }
    }

    private fun fireRecognitionHapticIfNeeded(text: String) {
        if (hasRecognitionHapticFired) return
        if (text.isBlank()) return
        hasRecognitionHapticFired = true
        _state.value =
            _state.value.copy(
                recognitionHapticTick = _state.value.recognitionHapticTick + 1,
            )
    }

    private fun bootstrapRuleService() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                textRuleService.initialize()
                textRuleService.syncFromServerIfNeeded()
            }
        }

        ruleSyncJob =
            viewModelScope.launch(Dispatchers.IO) {
                while (isActive) {
                    delay(RULE_SYNC_INTERVAL_MS)
                    runCatching {
                        textRuleService.syncFromServerIfNeeded()
                    }
                }
            }
    }
}
