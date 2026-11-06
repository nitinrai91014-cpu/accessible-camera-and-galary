/*
 * Accessible Camera and Gallery
 * Copyright (C) 2026 Sujan Rai, Blind Tech Nexus team.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.blindtechnexus.accessiblecamera.data.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.blindtechnexus.accessiblecamera.domain.model.VoiceCommand
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Optional voice commands using the on-device [SpeechRecognizer].
 *
 * The recogniser is only created and started after the user explicitly enables voice
 * commands and taps the listen button, so the microphone is never always listening.
 * Results are parsed locally by [VoiceCommand.parse].
 */
@Singleton
class OnDeviceSpeechRecognizer @Inject constructor(
    private val context: Context
) {

    private var recognizer: SpeechRecognizer? = null
    private var isListeningInternal = false
    private var continuousMode = false
    private var onCommand: ((VoiceCommand, String) -> Unit)? = null
    private var onListeningChanged: ((Boolean) -> Unit)? = null

    private val _state = MutableStateFlow(
        SpeechRecognizerState(
            available = isSpeechRecognitionAvailable(context),
            onDeviceSupported = isOnDeviceRecognitionSupported(context)
        )
    )
    val state: StateFlow<SpeechRecognizerState> = _state.asStateFlow()

    data class SpeechRecognizerState(
        val available: Boolean = false,
        val onDeviceSupported: Boolean = false,
        val listening: Boolean = false,
        val lastCommand: VoiceCommand? = null,
        val lastUtterance: String? = null,
        val error: String? = null
    )

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _state.value = _state.value.copy(listening = true, error = null)
            onListeningChanged?.invoke(true)
        }

        override fun onBeginningOfSpeech() = Unit

        override fun onRmsChanged(rmsdB: Float) = Unit

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() {
            _state.value = _state.value.copy(listening = false)
            onListeningChanged?.invoke(false)
        }

        override fun onError(error: Int) {
            val message = describeError(error)
            _state.value = _state.value.copy(
                listening = false,
                error = message
            )
            onListeningChanged?.invoke(false)
            if (continuousMode && (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)) {
                restartListening()
            }
        }

        override fun onResults(results: Bundle?) {
            val texts = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val best = texts?.firstOrNull()
            if (!best.isNullOrBlank()) {
                val command = VoiceCommand.parse(best)
                _state.value = _state.value.copy(
                    listening = false,
                    lastUtterance = best,
                    lastCommand = command,
                    error = null
                )
                onCommand?.invoke(command, best)
            } else {
                _state.value = _state.value.copy(listening = false)
            }
            onListeningChanged?.invoke(false)
            if (continuousMode) restartListening()
        }

        override fun onPartialResults(partialResults: Bundle?) = Unit

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    fun setCallbacks(
        command: (VoiceCommand, String) -> Unit,
        listeningChanged: (Boolean) -> Unit
    ) {
        onCommand = command
        onListeningChanged = listeningChanged
    }

    fun startListening(continuous: Boolean = true) {
        if (!isSpeechRecognitionAvailable(context)) {
            _state.value = _state.value.copy(
                available = false,
                error = "Speech recognition is not available on this device"
            )
            return
        }
        if (isListeningInternal) return
        continuousMode = continuous
        val created = recognizer ?: runCatching { SpeechRecognizer.createSpeechRecognizer(context) }
            .onFailure { Log.w(TAG, "Could not create speech recognizer", it) }
            .getOrNull()
        if (created == null) {
            _state.value = _state.value.copy(error = "Speech recognition could not start")
            return
        }
        recognizer = created
        created.setRecognitionListener(listener)
        createIntent()?.let { intent ->
            isListeningInternal = true
            _state.value = _state.value.copy(listening = true, error = null)
            runCatching { created.startListening(intent) }
                .onFailure {
                    isListeningInternal = false
                    _state.value = _state.value.copy(listening = false, error = "Speech recognition could not start")
                }
        } ?: run {
            _state.value = _state.value.copy(error = "Speech recognition is not available on this device")
        }
    }

    private fun createIntent(): Intent? {
        if (!isSpeechRecognitionAvailable(context)) return null
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, java.util.Locale.getDefault().toLanguageTag())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Prefer the on-device recogniser when the user has downloaded the model.
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
        }
    }

    private fun restartListening() {
        // Small delay avoids the recogniser rejecting an immediate restart.
        Thread {
            runCatching { Thread.sleep(350) }
            isListeningInternal = false
            startListening(continuousMode)
        }.apply { isDaemon = true }.start()
    }

    fun stopListening() {
        continuousMode = false
        isListeningInternal = false
        runCatching { recognizer?.stopListening() }
        runCatching { recognizer?.cancel() }
        _state.value = _state.value.copy(listening = false)
        onListeningChanged?.invoke(false)
    }

    fun destroy() {
        continuousMode = false
        isListeningInternal = false
        runCatching { recognizer?.destroy() }
        recognizer = null
        _state.value = _state.value.copy(listening = false)
    }

    private fun describeError(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "Microphone audio error"
        SpeechRecognizer.ERROR_CLIENT -> "Speech recognition client error"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required for voice commands"
        SpeechRecognizer.ERROR_NETWORK -> "Network error"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
        SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognised"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recogniser is busy"
        SpeechRecognizer.ERROR_SERVER -> "Speech server error"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech heard"
        else -> "Speech recognition error"
    }

    companion object {
        private const val TAG = "OnDeviceSpeechRecognizer"

        fun isSpeechRecognitionAvailable(context: Context): Boolean =
            runCatching { SpeechRecognizer.isRecognitionAvailable(context) }.getOrDefault(false)

        fun isOnDeviceRecognitionSupported(context: Context): Boolean =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(context) }
                    .getOrDefault(false)
    }
}
