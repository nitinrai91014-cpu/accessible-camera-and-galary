/*
 * Accessible Camera and Gallery
 * Copyright (C) 2026 Sujan Rai, Blind Tech Nexus team.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.blindtechnexus.accessiblecamera.domain.usecase

import com.blindtechnexus.accessiblecamera.core.util.StorageInfo
import com.blindtechnexus.accessiblecamera.domain.model.CameraState
import javax.inject.Inject

/**
 * Decides whether a capture may start, and explains why not.
 *
 * The camera screen, the volume key handler and voice commands all ask this one object, so
 * they can never disagree about whether a shutter press is allowed. Pure Kotlin, no Android
 * dependencies beyond the plain storage snapshot, so it is unit tested on the JVM.
 */
class CaptureReadinessUseCase @Inject constructor() {

    enum class Reason(val spoken: String) {
        READY("Ready to capture"),
        BUSY("Please wait, the app is still saving"),
        CAMERA_NOT_READY("The camera is not ready yet"),
        LOW_STORAGE("Storage is almost full. Free some space before capturing"),
        NO_CAMERA("This device does not have a usable camera")
    }

    data class Decision(val allowed: Boolean, val reason: Reason) {
        val spokenMessage: String get() = reason.spoken
    }

    /** Numbers only, so the same decision can be made in a test. */
    data class Inputs(
        val state: CameraState,
        val captureInFlight: Boolean,
        val availableBytes: Long,
        val hasCamera: Boolean
    )

    fun evaluate(inputs: Inputs): Decision = when {
        !inputs.hasCamera -> Decision(false, Reason.NO_CAMERA)
        inputs.captureInFlight -> Decision(false, Reason.BUSY)
        inputs.state == CameraState.CAPTURING || inputs.state == CameraState.SAVING ->
            Decision(false, Reason.BUSY)
        inputs.state != CameraState.READY && inputs.state != CameraState.FOCUSING ->
            Decision(false, Reason.CAMERA_NOT_READY)
        inputs.availableBytes in 0 until StorageInfo.CRITICAL_BYTES ->
            Decision(false, Reason.LOW_STORAGE)
        else -> Decision(true, Reason.READY)
    }

    /** Convenience wrapper the view model calls. */
    fun evaluate(
        state: CameraState,
        captureInFlight: Boolean,
        storage: StorageInfo.Snapshot,
        hasCamera: Boolean
    ): Decision = evaluate(
        Inputs(
            state = state,
            captureInFlight = captureInFlight,
            availableBytes = storage.availableBytes,
            hasCamera = hasCamera
        )
    )
}
