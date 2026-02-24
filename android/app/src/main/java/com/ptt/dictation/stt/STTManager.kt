package com.ptt.dictation.stt

interface STTListener {
    fun onPartialResult(text: String)

    fun onFinalResult(text: String)

    fun onError(
        errorCode: Int,
        message: String,
    )
}

interface STTEngine {
    fun startListening()

    fun stopListening()

    fun setListener(listener: STTListener)

    fun isAvailable(): Boolean
}
