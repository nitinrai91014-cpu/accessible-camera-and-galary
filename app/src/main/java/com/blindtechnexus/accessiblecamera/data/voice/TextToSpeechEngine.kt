/*
 * Accessible Camera and Gallery
 * Copyright (C) 2026 Sujan Rai, Blind Tech Nexus team.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.blindtechnexus.accessiblecamera.data.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin wrapper around the platform [TextToSpeech] engine.
 *
 * The engine exposes a single queue: callers never talk to TextToSpeech directly, which
 * keeps the app from interleaving announcements from different screens.
 */
@Singleton
class TextToSpeechEngine @Inject constructor(
    private @ApplicationContext val context: Context
) : TextToSpeech.OnInitListener {

    private companion object {
        const val TAG = "TextToSpeechEngine"
    }

    @Volatile
    private var tts: TextToSpeech? = null

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    private var rate: Float = 1.0f
    private var pitch: Float = 1.0f
    private val utteranceIds = mutableSetOf<String>()
    private var pendingUtteranceId: String? = null

    /** Initialises the engine. Safe to call more than once. */
    fun initialize() {
        if (tts != null) return
        runCatching { tts = TextToSpeech(context.applicationContext, this) }
            .onFailure { Log.w(TAG, "TextToSpeech could not be created", it) }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val engine = tts ?: return
            runCatching {
                engine.setSpeechRate(rate)
                engine.setPitch(pitch)
                val result = engine.setLanguage(Locale.getDefault())
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    engine.setLanguage(Locale.US)
                }
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _isSpeaking.value = true
                    }

                    override fun onDone(utteranceId: String?) {
                        utteranceId?.let { utteranceIds.remove(it) }
                        if (utteranceIds.isEmpty()) _isSpeaking.value = false
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        utteranceId?.let { utteranceIds.remove(it) }
                        if (utteranceIds.isEmpty()) _isSpeaking.value = false
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        utteranceId?.let { utteranceIds.remove(it) }
                        if (utteranceIds.isEmpty()) _isSpeaking.value = false
                        _errors.tryEmit("Speech engine error $errorCode")
                    }
                })
                _isReady.value = true
            }.onFailure {
                Log.w(TAG, "Speech engine configuration failed", it)
                _isReady.value = false
            }
        } else {
            Log.w(TAG, "TextToSpeech initialisation failed with status $status")
            _isReady.value = false
            _errors.tryEmit("Voice guidance is not available on this device")
        }
    }

    fun setRate(value: Float) {
        rate = value
        runCatching { tts?.setSpeechRate(value) }
    }

    fun setPitch(value: Float) {
        pitch = value
        runCatching { tts?.setPitch(value) }
    }

    /** Enqueues speech. Returns false when the engine is not ready. */
    fun speak(text: String): Boolean {
        val engine = tts
        if (engine == null || !_isReady.value || text.isBlank()) return false
        val id = "utt-${System.currentTimeMillis()}-${utteranceIds.size}"
        utteranceIds.add(id)
        val mode = if (pendingUtteranceId == null) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        val result = runCatching {
            engine.speak(text, mode, null, id)
        }.getOrDefault(TextToSpeech.ERROR)
        return if (result == TextToSpeech.SUCCESS) {
            pendingUtteranceId = id
            true
        } else {
            utteranceIds.remove(id)
            false
        }
    }

    /** Adds speech after whatever is currently being spoken. */
    fun enqueue(text: String): Boolean {
        val engine = tts
        if (engine == null || !_isReady.value || text.isBlank()) return false
        val id = "utt-${System.currentTimeMillis()}-queue"
        utteranceIds.add(id)
        val result = runCatching { engine.speak(text, TextToSpeech.QUEUE_ADD, null, id) }
            .getOrDefault(TextToSpeech.ERROR)
        return result == TextToSpeech.SUCCESS
    }

    fun stop() {
        runCatching { tts?.stop() }
        utteranceIds.clear()
        pendingUtteranceId = null
        _isSpeaking.value = false
    }

    /** True when the device reports a usable speech engine. */
    fun isLanguageAvailable(): Boolean {
        val engine = tts ?: return false
        val result = runCatching { engine.isLanguageAvailable(Locale.getDefault()) }.getOrDefault(-1)
        return result >= TextToSpeech.LANG_AVAILABLE
    }

    fun shutdown() {
        stop()
        runCatching { tts?.shutdown() }
        tts = null
        _isReady.value = false
    }
}
