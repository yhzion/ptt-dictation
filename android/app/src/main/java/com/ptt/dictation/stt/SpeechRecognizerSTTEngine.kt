package com.ptt.dictation.stt

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

class SpeechRecognizerSTTEngine(
    private val context: Context,
) : STTEngine {
    private var recognizer: SpeechRecognizer? = null
    private var listener: STTListener? = null
    private var generation = 0

    override fun startListening() {
        Log.d(TAG, "startListening called")
        destroyRecognizer()
        val gen = ++generation
        recognizer =
            SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(createRecognitionListener(gen))
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
        Log.d(TAG, "stopListening called")
        recognizer?.stopListening()
        // Do NOT destroy here — onResults/onError will handle cleanup
    }

    override fun setListener(listener: STTListener) {
        this.listener = listener
    }

    override fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    private fun destroyRecognizer() {
        recognizer?.destroy()
        recognizer = null
    }

    private fun createRecognitionListener(gen: Int) =
        object : RecognitionListener {
            private fun isStale() = gen != generation

            override fun onPartialResults(partialResults: Bundle?) {
                if (isStale()) return
                val texts =
                    partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!texts.isNullOrEmpty()) {
                    Log.d(TAG, "onPartialResults: ${texts[0]}")
                    listener?.onPartialResult(texts[0])
                }
            }

            override fun onResults(results: Bundle?) {
                if (isStale()) {
                    Log.d(TAG, "onResults: STALE (gen=$gen, current=$generation)")
                    return
                }
                val texts =
                    results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                Log.d(TAG, "onResults: ${texts?.firstOrNull()}")
                destroyRecognizer()
                if (!texts.isNullOrEmpty()) {
                    listener?.onFinalResult(texts[0])
                }
            }

            override fun onError(error: Int) {
                if (isStale()) {
                    Log.d(TAG, "onError: STALE (gen=$gen, current=$generation)")
                    return
                }
                val msg =
                    when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "No match"
                        SpeechRecognizer.ERROR_NETWORK -> "Network error"
                        SpeechRecognizer.ERROR_AUDIO -> "Audio error"
                        else -> "Error code: $error"
                    }
                Log.e(TAG, "onError: $error ($msg)")
                destroyRecognizer()
                listener?.onError(error, msg)
            }

            override fun onReadyForSpeech(params: Bundle?) {
                Log.d(TAG, "onReadyForSpeech")
            }

            override fun onBeginningOfSpeech() {
                Log.d(TAG, "onBeginningOfSpeech")
            }

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                Log.d(TAG, "onEndOfSpeech")
            }

            override fun onEvent(
                eventType: Int,
                params: Bundle?,
            ) {}
        }

    companion object {
        private const val TAG = "SpeechRecognizerSTT"
    }
}
