/*
 * Accessible Camera and Gallery
 * Copyright (C) 2026 Sujan Rai, Blind Tech Nexus team.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.blindtechnexus.accessiblecamera.core.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.blindtechnexus.accessiblecamera.domain.model.HapticCue

/** Plays short haptic patterns so actions can be recognised by touch alone. */
class HapticPlayer(private val context: Context) {

    private val vibrator: Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }.getOrNull()

    fun play(cue: HapticCue) {
        val vibrator = vibrator ?: return
        if (!vibrator.hasVibrator()) return
        val pattern = patternFor(cue)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator.vibrate(
                    VibrationEffect.createWaveform(pattern.timings, pattern.amplitudes, -1)
                )
            } else {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern.timings, -1))
            }
        }
    }

    fun cancel() {
        runCatching { vibrator?.cancel() }
    }

    private data class Pattern(val timings: LongArray, val amplitudes: IntArray)

    private fun patternFor(cue: HapticCue): Pattern = when (cue) {
        HapticCue.TAP -> Pattern(longArrayOf(0, 18), intArrayOf(0, 70))
        HapticCue.CAPTURE -> Pattern(longArrayOf(0, 24, 40, 24), intArrayOf(0, 160, 0, 160))
        HapticCue.RECORDING_START -> Pattern(longArrayOf(0, 30, 60, 30, 60, 30), intArrayOf(0, 120, 0, 120, 0, 120))
        HapticCue.RECORDING_STOP -> Pattern(longArrayOf(0, 60), intArrayOf(0, 200))
        HapticCue.FOCUS_LOCK -> Pattern(longArrayOf(0, 16), intArrayOf(0, 110))
        HapticCue.ERROR -> Pattern(longArrayOf(0, 70, 60, 70), intArrayOf(0, 255, 0, 255))
        HapticCue.SUCCESS -> Pattern(longArrayOf(0, 20, 50, 20), intArrayOf(0, 140, 0, 140))
        HapticCue.LONG_PRESS -> Pattern(longArrayOf(0, 90), intArrayOf(0, 190))
        HapticCue.SELECTION -> Pattern(longArrayOf(0, 14), intArrayOf(0, 90))
    }
}
