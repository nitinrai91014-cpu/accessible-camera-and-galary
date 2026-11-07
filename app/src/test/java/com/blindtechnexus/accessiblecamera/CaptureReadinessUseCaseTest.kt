/*
 * Accessible Camera and Gallery
 * Copyright (C) 2026 Sujan Rai, Blind Tech Nexus team.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.blindtechnexus.accessiblecamera

import com.blindtechnexus.accessiblecamera.core.util.StorageInfo
import com.blindtechnexus.accessiblecamera.domain.model.CameraState
import com.blindtechnexus.accessiblecamera.domain.usecase.CaptureReadinessUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Capture gating rules, verified without a device. */
class CaptureReadinessUseCaseTest {

    private val useCase = CaptureReadinessUseCase()
    private val plenty: Long = 4L * 1024L * 1024L * 1024L

    private fun decide(
        state: CameraState = CameraState.READY,
        inFlight: Boolean = false,
        bytes: Long = plenty,
        hasCamera: Boolean = true
    ) = useCase.evaluate(
        CaptureReadinessUseCase.Inputs(
            state = state,
            captureInFlight = inFlight,
            availableBytes = bytes,
            hasCamera = hasCamera
        )
    )

    @Test
    fun `ready camera allows capture`() {
        assertTrue(decide().allowed)
        assertEquals(CaptureReadinessUseCase.Reason.READY, decide().reason)
    }

    @Test
    fun `focusing still allows capture`() {
        assertTrue(decide(state = CameraState.FOCUSING).allowed)
    }

    @Test
    fun `second capture is refused while saving`() {
        val decision = decide(inFlight = true)
        assertFalse(decision.allowed)
        assertEquals(CaptureReadinessUseCase.Reason.BUSY, decision.reason)
    }

    @Test
    fun `capturing and saving states are refused`() {
        assertFalse(decide(state = CameraState.CAPTURING).allowed)
        assertFalse(decide(state = CameraState.SAVING).allowed)
    }

    @Test
    fun `idle and error states are refused with a clear reason`() {
        assertEquals(CaptureReadinessUseCase.Reason.CAMERA_NOT_READY, decide(state = CameraState.IDLE).reason)
        assertEquals(CaptureReadinessUseCase.Reason.CAMERA_NOT_READY, decide(state = CameraState.ERROR).reason)
    }

    @Test
    fun `critical storage blocks the shutter`() {
        val decision = decide(bytes = StorageInfo.CRITICAL_BYTES - 1)
        assertFalse(decision.allowed)
        assertEquals(CaptureReadinessUseCase.Reason.LOW_STORAGE, decision.reason)
        assertTrue(decision.spokenMessage.contains("Storage"))
    }

    @Test
    fun `storage just above the critical level is allowed`() {
        assertTrue(decide(bytes = StorageInfo.CRITICAL_BYTES).allowed)
    }

    @Test
    fun `no camera wins over every other reason`() {
        val decision = decide(state = CameraState.CAPTURING, bytes = 0, hasCamera = false)
        assertEquals(CaptureReadinessUseCase.Reason.NO_CAMERA, decision.reason)
    }
}
