/*
 * Accessible Camera and Gallery
 * Copyright (C) 2026 Sujan Rai, Blind Tech Nexus team.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.blindtechnexus.accessiblecamera.data.voice

import android.content.Context
import com.blindtechnexus.accessiblecamera.core.util.AccessibilityUtils
import com.blindtechnexus.accessiblecamera.core.util.HapticPlayer
import com.blindtechnexus.accessiblecamera.core.util.TonePlayer
import com.blindtechnexus.accessiblecamera.domain.model.AnnouncementPriority
import com.blindtechnexus.accessiblecamera.domain.model.AudioCue
import com.blindtechnexus.accessiblecamera.domain.model.HapticCue
import com.blindtechnexus.accessiblecamera.domain.repository.VoiceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates speech, tones and haptics.
 *
 * Rules implemented here:
 * - One queue only, owned by [TextToSpeechEngine].
 * - Critical messages interrupt everything and are also delivered as a live region.
 * - Normal messages do not interrupt a critical message in flight.
 * - Rapid value changes are debounced by the caller through [announceValue].
 */
@Singleton
class VoiceRepositoryImpl @Inject constructor(
    private val context: Context,
    private val engine: TextToSpeechEngine
) : VoiceRepository {

    private val tonePlayer = TonePlayer()
    private val hapticPlayer = HapticPlayer(context)
    private val hapticsFlow = MutableStateFlow(true)
    private val audioFlow = MutableStateFlow(true)
    private val guidanceFlow = MutableStateFlow(true)
    private var criticalInFlightUntil = 0L
    private val recentValues = mutableMapOf<String, Long>()

    override val isSpeaking: Flow<Boolean> = engine.isSpeaking

    val isEngineReady: Flow<Boolean> = engine.isReady

    fun initialize() {
        engine.initialize()
    }

    fun configure(rate: Float, pitch: Float) {
        engine.setRate(rate)
        engine.setPitch(pitch)
    }

    fun setFeatureToggles(haptics: Boolean, audioCues: Boolean, voiceGuidance: Boolean) {
        hapticsFlow.value = haptics
        audioFlow.value = audioCues
        guidanceFlow.value = voiceGuidance
    }

    override fun setRate(rate: Float) = engine.setRate(rate)

    override fun setPitch(pitch: Float) = engine.setPitch(pitch)

    override fun announce(text: String, priority: AnnouncementPriority) {
        if (text.isBlank()) return
        val now = System.currentTimeMillis()
        if (priority == AnnouncementPriority.CRITICAL) {
            engine.stop()
            criticalInFlightUntil = now + 2500L
            engine.speak(text)
            engine.speak(text)
            return
        }
        // Never interrupt an error that is still being spoken.
        if (now < criticalInFlightUntil) {
            engine.enqueue(text)
            return
        }
        if (!guidanceFlow.value) return
        engine.speak(text)
    }

    /**
     * Announces a rapidly changing value such as zoom. Repeated calls for the same key
     * within [debounceMillis] replace the previous message instead of queueing.
     */
    fun announceValue(key: String, text: String, debounceMillis: Long = 350L) {
        if (!guidanceFlow.value) return
        val now = System.currentTimeMillis()
        val last = recentValues[key] ?: 0L
        if (now - last < debounceMillis) {
            recentValues[key] = now
            return
        }
        recentValues[key] = now
        engine.speak(text)
    }

    override fun stop() {
        engine.stop()
    }

    override fun speakCue(cue: AudioCue) = playCue(cue)

    override fun playCue(cue: AudioCue) {
        if (!audioFlow.value) return
        tonePlayer.play(cue)
    }

    override fun vibrate(cue: HapticCue) {
        if (!hapticsFlow.value) return
        hapticPlayer.play(cue)
    }

    /** True when TalkBack is active and the app should avoid duplicate speech. */
    fun shouldDeferToScreenReader(): Boolean =
        AccessibilityUtils.isScreenReaderActive(context)

    fun shutdown() {
        engine.shutdown()
        tonePlayer.release()
    }
}
