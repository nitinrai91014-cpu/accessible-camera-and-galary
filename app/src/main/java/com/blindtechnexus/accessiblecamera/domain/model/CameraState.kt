/*
 * Accessible Camera and Gallery
 * Copyright (C) 2026 Sujan Rai, Blind Tech Nexus team.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.blindtechnexus.accessiblecamera.domain.model

/**
 * Single source of truth for the camera lifecycle. Every UI affordance derives its
 * enabled state from this value, so concurrent captures cannot be started.
 */
enum class CameraState(val spoken: String) {
    IDLE("Camera idle"),
    INITIALIZING("Camera starting"),
    READY("Camera ready"),
    FOCUSING("Focusing"),
    CAPTURING("Taking photo"),
    RECORDING("Recording"),
    PAUSED("Recording paused"),
    SAVING("Saving"),
    ERROR("Camera error");

    val isBusy: Boolean
        get() = this == INITIALIZING || this == CAPTURING || this == SAVING

    val canCapturePhoto: Boolean
        get() = this == READY || this == FOCUSING

    val canStartRecording: Boolean
        get() = this == READY || this == FOCUSING

    val canStopRecording: Boolean
        get() = this == RECORDING || this == PAUSED
}

/** High level user facing error with a retry path and a spoken message. */
data class CameraFailure(
    val code: FailureCode,
    val spokenMessage: String,
    val visibleMessage: String,
    val isRetryable: Boolean = true
)

enum class FailureCode {
    CAMERA_UNAVAILABLE,
    CAMERA_IN_USE,
    PERMISSION_DENIED,
    PERMISSION_PERMANENTLY_DENIED,
    LOW_STORAGE,
    RECORDING_INTERRUPTED,
    CAPTURE_FAILED,
    SAVE_FAILED,
    UNSUPPORTED_CONFIGURATION,
    TIMEOUT,
    UNKNOWN
}
