/*
 * Accessible Camera and Gallery
 * Copyright (C) 2026 Sujan Rai, Blind Tech Nexus team.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.blindtechnexus.accessiblecamera.core.util

import android.media.MediaActionSound
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Plays the platform camera sounds through [MediaActionSound].
 *
 * Using the system sounds instead of bundled audio assets means the shutter, start and stop
 * cues match the device exactly and respect the user's system volume and silent rules. The
 * sound resources are loaded once and reused so the first capture is never silent while a
 * file is being decoded.
 */
@Singleton
class SystemSoundPlayer @Inject constructor() {

    private companion object {
        const val TAG = "SystemSoundPlayer"
    }

    private val lock = Any()
    private var actionSound: MediaActionSound? = null

    /** Users can turn the shutter sound off in settings. On by default. */
    @Volatile
    var enabled: Boolean = true

    private fun ensureLoaded(): MediaActionSound? = synchronized(lock) {
        actionSound ?: runCatching {
            MediaActionSound().also { sound ->
                runCatching { sound.load(MediaActionSound.SHUTTER_CLICK) }
                runCatching { sound.load(MediaActionSound.START_VIDEO_RECORDING) }
                runCatching { sound.load(MediaActionSound.STOP_VIDEO_RECORDING) }
                actionSound = sound
            }
        }.onFailure { Log.w(TAG, "System camera sounds are unavailable", it) }.getOrNull()
    }

    /** Shutter click played when a photo has been captured. */
    fun playShutter() = play(MediaActionSound.SHUTTER_CLICK)

    /** System cue played when video recording starts. */
    fun playRecordingStart() = play(MediaActionSound.START_VIDEO_RECORDING)

    /** System cue played when video recording stops. */
    fun playRecordingStop() = play(MediaActionSound.STOP_VIDEO_RECORDING)

    private fun play(soundName: Int) {
        if (!enabled) return
        runCatching { ensureLoaded()?.play(soundName) }
            .onFailure { Log.w(TAG, "Could not play system camera sound", it) }
    }

    fun release() {
        synchronized(lock) {
            runCatching { actionSound?.release() }
            actionSound = null
        }
    }
}
