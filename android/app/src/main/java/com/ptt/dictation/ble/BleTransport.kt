package com.ptt.dictation.ble

import com.ptt.dictation.model.PttMessage
import com.ptt.dictation.ws.ConnectionState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json

interface PttTransportListener {
    fun onConnected()

    fun onDisconnected()

    fun onError(error: String)
}

interface PttTransport {
    val connectionState: StateFlow<ConnectionState>

    fun startScanning()

    fun connect(deviceId: String)

    fun disconnect()

    fun send(message: PttMessage)

    fun setListener(listener: PttTransportListener)
}

object BleMessageEncoder {
    fun encode(message: PttMessage): String {
        return Json.encodeToString(
            PttMessage.serializer(),
            message,
        )
    }
}
