package com.ptt.dictation.stt

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

class SpeechRecognizerSTTEngine(
    private val context: Context,
) : STTEngine {
    private var recognizer: SpeechRecognizer? = null
    private var listener: STTListener? = null

    override fun startListening() {
        recognizer =
            SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(createRecognitionListener())
            }
        val intent =
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                )
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
            }
        recognizer?.startListening(intent)
    }

    override fun stopListening() {
        recognizer?.stopListening()
        recognizer?.destroy()
        recognizer = null
    }

    override fun setListener(listener: STTListener) {
        this.listener = listener
    }

    override fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    private fun createRecognitionListener() =
        object : RecognitionListener {
            override fun onPartialResults(partialResults: Bundle?) {
                val texts =
                    partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!texts.isNullOrEmpty()) {
                    listener?.onPartialResult(texts[0])
                }
            }

            override fun onResults(results: Bundle?) {
                val texts =
                    results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!texts.isNullOrEmpty()) {
                    listener?.onFinalResult(texts[0])
                }
            }

            override fun onError(error: Int) {
                val msg =
                    when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "No match"
                        SpeechRecognizer.ERROR_NETWORK -> "Network error"
                        SpeechRecognizer.ERROR_AUDIO -> "Audio error"
                        else -> "Error code: $error"
                    }
                listener?.onError(error, msg)
            }

            override fun onReadyForSpeech(params: Bundle?) {}

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {}

            override fun onEvent(
                eventType: Int,
                params: Bundle?,
            ) {}
        }
}
