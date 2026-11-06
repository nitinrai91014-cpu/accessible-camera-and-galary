/*
 * Accessible Camera and Gallery
 * Copyright (C) 2026 Sujan Rai, Blind Tech Nexus team.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.blindtechnexus.accessiblecamera.core.util

import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log
import com.blindtechnexus.accessiblecamera.domain.model.AudioCue

/**
 * Plays short, generated audio cues without shipping any audio assets.
 * Tones are deliberately distinct so each action can be identified by ear.
 */
class TonePlayer {

    private var generator: ToneGenerator? = null
    private val tag = "TonePlayer"
    private val lock = Any()

    private fun ensureGenerator(): ToneGenerator? = synchronized(lock) {
        if (generator == null) {
            runCatching {
                ToneGenerator(AudioManager.STREAM_MUSIC, 70).also { tone ->
                    generator = tone
                }
            }.onFailure { Log.w(tag, "Tone generator unavailable", it) }.getOrNull()
        } else {
            generator
        }
    }

    fun play(cue: AudioCue) {
        val tone = ensureGenerator() ?: return
        val (toneType, duration) = when (cue) {
            AudioCue.SHUTTER -> ToneGenerator.TONE_PROP_BEEP to 90
            AudioCue.RECORDING_START -> ToneGenerator.TONE_PROP_ACK to 200
            AudioCue.RECORDING_STOP -> ToneGenerator.TONE_PROP_BEEP2 to 180
            AudioCue.FOCUS_LOCK -> ToneGenerator.TONE_PROP_PROMPT to 70
            AudioCue.SUCCESS -> ToneGenerator.TONE_PROP_ACK to 150
            AudioCue.ERROR -> ToneGenerator.TONE_SUP_ERROR to 400
            AudioCue.TICK -> ToneGenerator.TONE_PROP_BEEP to 50
            AudioCue.ITEM_SELECTED -> ToneGenerator.TONE_PROP_PROMPT to 60
            AudioCue.TRASH -> ToneGenerator.TONE_PROP_BEEP2 to 120
        }
        runCatching { tone.startTone(toneType, duration) }
    }

    fun release() {
        synchronized(lock) {
            runCatching { generator?.release() }
            generator = null
        }
    }

    companion object {
        /** Attributes used if a tone needs to be routed with media volume. */
        val attributes: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
    }
}
