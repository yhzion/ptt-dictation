package com.ptt.dictation.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

@Serializable
data class PttMessage(
    val type: String,
    val clientId: String,
    val timestamp: Long? = null,
    val payload: JsonObject? = null,
) {
    companion object {
        fun hello(
            clientId: String,
            deviceModel: String,
            engine: String,
        ) = PttMessage(
            type = "HELLO",
            clientId = clientId,
            payload =
                buildJsonObject {
                    put("deviceModel", deviceModel)
                    put("engine", engine)
                    putJsonArray("capabilities") { add("WS") }
                },
        )

        fun pttStart(
            clientId: String,
            sessionId: String,
        ) = PttMessage(
            type = "PTT_START",
            clientId = clientId,
            payload = buildJsonObject { put("sessionId", sessionId) },
        )

        fun partial(
            clientId: String,
            sessionId: String,
            seq: Int,
            text: String,
            confidence: Double,
        ) = PttMessage(
            type = "PARTIAL",
            clientId = clientId,
            timestamp = System.currentTimeMillis(),
            payload =
                buildJsonObject {
                    put("sessionId", sessionId)
                    put("seq", seq)
                    put("text", text)
                    put("confidence", confidence)
                },
        )

        fun finalResult(
            clientId: String,
            sessionId: String,
            text: String,
            confidence: Double,
        ) = PttMessage(
            type = "FINAL",
            clientId = clientId,
            timestamp = System.currentTimeMillis(),
            payload =
                buildJsonObject {
                    put("sessionId", sessionId)
                    put("text", text)
                    put("confidence", confidence)
                },
        )

        fun heartbeat(clientId: String) =
            PttMessage(
                type = "HEARTBEAT",
                clientId = clientId,
            )
    }
}
