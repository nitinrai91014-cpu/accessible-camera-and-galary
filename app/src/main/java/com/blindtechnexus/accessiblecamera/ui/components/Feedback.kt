/*
 * Accessible Camera and Gallery
 * Copyright (C) 2026 Sujan Rai, Blind Tech Nexus team.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.blindtechnexus.accessiblecamera.ui.components

import androidx.compose.runtime.staticCompositionLocalOf
import com.blindtechnexus.accessiblecamera.domain.model.AudioCue
import com.blindtechnexus.accessiblecamera.domain.model.HapticCue

/**
 * Anything a composable can use to give non-visual feedback.
 * Implemented by a small controller that talks to the voice repository.
 */
interface FeedbackSink {
    fun haptic(cue: HapticCue)
    fun tone(cue: AudioCue)
    fun announce(text: String)
    fun announceValue(key: String, text: String)

    companion object {
        val None = object : FeedbackSink {
            override fun haptic(cue: HapticCue) = Unit
            override fun tone(cue: AudioCue) = Unit
            override fun announce(text: String) = Unit
            override fun announceValue(key: String, text: String) = Unit
        }
    }
}

/** Provides feedback actions to any composable without threading callbacks everywhere. */
val LocalFeedback = staticCompositionLocalOf<FeedbackSink> { FeedbackSink.None }
