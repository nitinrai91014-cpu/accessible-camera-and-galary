/*
 * Accessible Camera and Gallery
 * Copyright (C) 2026 Sujan Rai, Blind Tech Nexus team.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.blindtechnexus.accessiblecamera

import com.blindtechnexus.accessiblecamera.domain.model.AppSettings
import com.blindtechnexus.accessiblecamera.domain.model.CameraCapabilities
import com.blindtechnexus.accessiblecamera.domain.model.CameraMode
import com.blindtechnexus.accessiblecamera.domain.model.CameraState
import com.blindtechnexus.accessiblecamera.domain.model.SortOrder
import com.blindtechnexus.accessiblecamera.domain.model.ThemeMode
import com.blindtechnexus.accessiblecamera.domain.model.TimerDuration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards the small rules that keep the interface safe and predictable. */
class SettingsAndCapabilityTest {

    @Test
    fun `camera state machine only allows capture when ready or focusing`() {
        assertTrue(CameraState.READY.canCapturePhoto)
        assertTrue(CameraState.FOCUSING.canCapturePhoto)
        assertFalse(CameraState.CAPTURING.canCapturePhoto)
        assertFalse(CameraState.SAVING.canCapturePhoto)
        assertFalse(CameraState.INITIALIZING.canCapturePhoto)
        assertFalse(CameraState.ERROR.canCapturePhoto)
    }

    @Test
    fun `recording can only be stopped while recording or paused`() {
        assertTrue(CameraState.RECORDING.canStopRecording)
        assertTrue(CameraState.PAUSED.canStopRecording)
        assertFalse(CameraState.READY.canStopRecording)
        assertFalse(CameraState.SAVING.canStopRecording)
    }

    @Test
    fun `busy states are treated as blocking`() {
        assertTrue(CameraState.INITIALIZING.isBusy)
        assertTrue(CameraState.CAPTURING.isBusy)
        assertTrue(CameraState.SAVING.isBusy)
        assertFalse(CameraState.READY.isBusy)
    }

    @Test
    fun `unavailable modes always carry a spoken reason`() {
        val capabilities = CameraCapabilities(
            hasAnyCamera = true,
            supportedModes = setOf(CameraMode.PHOTO),
            modeUnavailableReasons = mapOf(
                CameraMode.NIGHT to "This device has no night mode camera extension."
            )
        )
        assertTrue(capabilities.reasonFor(CameraMode.NIGHT).contains("night"))
        // Every mode without an explicit reason still returns a usable sentence.
        CameraMode.entries.forEach { mode ->
            assertTrue(capabilities.reasonFor(mode).isNotBlank())
        }
        assertFalse(capabilities.isModeAvailable(CameraMode.VIDEO))
        assertTrue(capabilities.isModeAvailable(CameraMode.PHOTO))
    }

    @Test
    fun `location tagging is off by default and voice guidance is on`() {
        assertFalse(AppSettings.Defaults.geotaggingEnabled)
        assertTrue(AppSettings.Defaults.voiceGuidanceEnabled)
        assertFalse(AppSettings.Defaults.voiceCommandsEnabled)
        assertTrue(AppSettings.Defaults.hapticsEnabled)
        assertEquals(TimerDuration.OFF, AppSettings.Defaults.timerDefault)
    }

    @Test
    fun `theme cycle reaches both high contrast variants`() {
        val seen = mutableSetOf<ThemeMode>()
        var mode = ThemeMode.SYSTEM
        repeat(ThemeMode.entries.size) {
            seen += mode
            mode = mode.next
        }
        assertTrue(seen.contains(ThemeMode.HIGH_CONTRAST_DARK))
        assertTrue(seen.contains(ThemeMode.HIGH_CONTRAST_LIGHT))
    }

    @Test
    fun `sort order cycles through every option and returns`() {
        var order = SortOrder.NEWEST_FIRST
        val seen = mutableSetOf<SortOrder>()
        repeat(SortOrder.entries.size) {
            seen += order
            order = order.next
        }
        assertEquals(SortOrder.entries.size, seen.size)
        assertEquals(SortOrder.NEWEST_FIRST, order)
    }

    @Test
    fun `empty capabilities explain that no camera exists`() {
        assertFalse(CameraCapabilities.Empty.hasAnyCamera)
        assertTrue(CameraCapabilities.Empty.reasonFor(CameraMode.PHOTO).contains("No camera"))
    }
}
