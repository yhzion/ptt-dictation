package com.ptt.dictation.ws

import android.util.Log
import com.ptt.dictation.model.PttMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.TimeUnit

class OkHttpWebSocketClient(
    private val clientId: String,
    private val deviceModel: String,
) : WebSocketClient {
    private val client =
        OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()

    private val json = Json { ignoreUnknownKeys = true }

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ConnectionState> = _connectionState

    private var webSocket: WebSocket? = null
    private var listener: MessageListener? = null
    private var heartbeatTimer: Timer? = null
    private var onConnectedCallback: (() -> Unit)? = null

    fun setOnConnectedCallback(callback: () -> Unit) {
        onConnectedCallback = callback
    }

    override fun connect(url: String) {
        Log.d(TAG, "Connecting to $url")
        _connectionState.value = ConnectionState.CONNECTING
        val request = Request.Builder().url(url).build()
        webSocket =
            client.newWebSocket(
                request,
                object : WebSocketListener() {
                    override fun onOpen(
                        webSocket: WebSocket,
                        response: Response,
                    ) {
                        Log.d(TAG, "WebSocket opened, sending HELLO")
                        _connectionState.value = ConnectionState.CONNECTED
                        send(PttMessage.hello(clientId, deviceModel, "Google"))
                        startHeartbeat()
                        onConnectedCallback?.invoke()
                    }

                    override fun onMessage(
                        webSocket: WebSocket,
                        text: String,
                    ) {
                        Log.d(TAG, "Received: $text")
                        try {
                            val msg = json.decodeFromString(PttMessage.serializer(), text)
                            listener?.onMessage(msg)
                        } catch (e: Exception) {
                            listener?.onError("Parse error: ${e.message}")
                        }
                    }

                    override fun onFailure(
                        webSocket: WebSocket,
                        t: Throwable,
                        response: Response?,
                    ) {
                        Log.e(TAG, "WebSocket failure: ${t.message}", t)
                        stopHeartbeat()
                        _connectionState.value = ConnectionState.DISCONNECTED
                        listener?.onError("Connection failed: ${t.message}")
                    }

                    override fun onClosed(
                        webSocket: WebSocket,
                        code: Int,
                        reason: String,
                    ) {
                        Log.d(TAG, "WebSocket closed: $reason")
                        stopHeartbeat()
                        _connectionState.value = ConnectionState.DISCONNECTED
                    }
                },
            )
    }

    override fun disconnect() {
        stopHeartbeat()
        webSocket?.close(1000, "User disconnect")
        webSocket = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    override fun send(message: PttMessage) {
        val text = json.encodeToString(PttMessage.serializer(), message)
        Log.d(TAG, "Sending: ${message.type}")
        val result = webSocket?.send(text)
        Log.d(TAG, "Send result: $result")
    }

    override fun setListener(listener: MessageListener) {
        this.listener = listener
    }

    private fun startHeartbeat() {
        heartbeatTimer =
            Timer().apply {
                scheduleAtFixedRate(
                    object : TimerTask() {
                        override fun run() {
                            send(PttMessage.heartbeat(clientId))
                        }
                    },
                    5000,
                    5000,
                )
            }
    }

    private fun stopHeartbeat() {
        heartbeatTimer?.cancel()
        heartbeatTimer = null
    }

    companion object {
        private const val TAG = "PttWebSocket"
    }
}
