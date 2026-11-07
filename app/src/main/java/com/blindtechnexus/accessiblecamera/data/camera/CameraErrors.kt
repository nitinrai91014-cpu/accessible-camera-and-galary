/*
 * Accessible Camera and Gallery
 * Copyright (C) 2026 Sujan Rai, Blind Tech Nexus team.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.blindtechnexus.accessiblecamera.data.camera

import android.content.Context
import androidx.camera.core.CameraState
import androidx.camera.video.VideoRecordEvent
import com.blindtechnexus.accessiblecamera.domain.model.CameraFailure
import com.blindtechnexus.accessiblecamera.domain.model.FailureCode

/** Turns platform errors into human readable, properly spoken failures. */
object CameraErrors {

    fun fromCameraState(description: String): CameraFailure = CameraFailure(
        code = FailureCode.CAMERA_UNAVAILABLE,
        spokenMessage = "$description. Close other apps that use the camera, then try again.",
        visibleMessage = description,
        isRetryable = true
    )

    fun cameraInUse(): CameraFailure = CameraFailure(
        code = FailureCode.CAMERA_IN_USE,
        spokenMessage = "The camera is being used by another app. Close that app and try again.",
        visibleMessage = "Camera in use by another app",
        isRetryable = true
    )

    fun permissionDenied(): CameraFailure = CameraFailure(
        code = FailureCode.PERMISSION_DENIED,
        spokenMessage = "Camera permission is required to show the viewfinder. Allow camera access to continue.",
        visibleMessage = "Camera permission needed",
        isRetryable = true
    )

    fun permissionPermanentlyDenied(): CameraFailure = CameraFailure(
        code = FailureCode.PERMISSION_PERMANENTLY_DENIED,
        spokenMessage = "Camera permission is turned off for this app. Open app settings and allow camera access.",
        visibleMessage = "Camera permission turned off",
        isRetryable = false
    )

    fun lowStorage(availableMb: Long): CameraFailure = CameraFailure(
        code = FailureCode.LOW_STORAGE,
        spokenMessage = "Storage is nearly full. Free at least $availableMb megabytes before capturing.",
        visibleMessage = "Not enough storage space",
        isRetryable = true
    )

    fun captureFailed(reason: String?): CameraFailure = CameraFailure(
        code = FailureCode.CAPTURE_FAILED,
        spokenMessage = "The photo could not be taken. ${reason ?: "Please try again."}",
        visibleMessage = "Photo capture failed",
        isRetryable = true
    )

    fun saveFailed(reason: String?): CameraFailure = CameraFailure(
        code = FailureCode.SAVE_FAILED,
        spokenMessage = "The photo was taken but could not be saved. ${reason ?: "Please try again."}",
        visibleMessage = "Saving failed",
        isRetryable = true
    )

    fun unsupportedConfiguration(reason: String): CameraFailure = CameraFailure(
        code = FailureCode.UNSUPPORTED_CONFIGURATION,
        spokenMessage = "This camera configuration is not supported on this device. $reason",
        visibleMessage = "Unsupported camera setting",
        isRetryable = true
    )

    fun recordingInterrupted(): CameraFailure = CameraFailure(
        code = FailureCode.RECORDING_INTERRUPTED,
        spokenMessage = "Recording stopped unexpectedly. Your video up to this point has been saved.",
        visibleMessage = "Recording stopped",
        isRetryable = true
    )

    fun fromVideoError(error: Int): CameraFailure = when (error) {
        VideoRecordEvent.Finalize.ERROR_INSUFFICIENT_STORAGE -> lowStorage(100)
        VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE -> recordingInterrupted()
        VideoRecordEvent.Finalize.ERROR_INVALID_OUTPUT_OPTIONS -> unsupportedConfiguration(
            "The recorder rejected these output options."
        )
        VideoRecordEvent.Finalize.ERROR_ENCODING_FAILED -> CameraFailure(
            code = FailureCode.UNKNOWN,
            spokenMessage = "The video encoder failed. Try a lower resolution or frame rate.",
            visibleMessage = "Video encoding failed",
            isRetryable = true
        )
        VideoRecordEvent.Finalize.ERROR_RECORDER_ERROR -> CameraFailure(
            code = FailureCode.UNKNOWN,
            spokenMessage = "The recorder reported an error. Please try again.",
            visibleMessage = "Recorder error",
            isRetryable = true
        )
        VideoRecordEvent.Finalize.ERROR_NO_VALID_DATA -> CameraFailure(
            code = FailureCode.UNKNOWN,
            spokenMessage = "No video data was recorded. Check the microphone and try again.",
            visibleMessage = "No video data recorded",
            isRetryable = true
        )
        VideoRecordEvent.Finalize.ERROR_DURATION_LIMIT_REACHED -> CameraFailure(
            code = FailureCode.UNKNOWN,
            spokenMessage = "The recording reached the maximum duration and was saved.",
            visibleMessage = "Maximum duration reached",
            isRetryable = true
        )
        else -> CameraFailure(
            code = FailureCode.UNKNOWN,
            spokenMessage = "Recording failed with error code $error.",
            visibleMessage = "Recording failed",
            isRetryable = true
        )
    }

    fun describeCameraErrorCode(code: Int): String = when (code) {
        CameraState.ERROR_CAMERA_IN_USE -> "The camera is in use by another app"
        CameraState.ERROR_MAX_CAMERAS_IN_USE -> "Too many cameras are already open"
        CameraState.ERROR_OTHER_RECOVERABLE_ERROR -> "The camera reported a recoverable error"
        CameraState.ERROR_STREAM_CONFIG -> "The camera could not use this configuration"
        CameraState.ERROR_CAMERA_DISABLED -> "The camera is disabled by device policy"
        CameraState.ERROR_CAMERA_FATAL_ERROR -> "The camera reported a fatal error"
        CameraState.ERROR_DO_NOT_DISTURB_MODE_ENABLED -> "The camera is unavailable while do not disturb is on"
        else -> "The camera reported an unknown error"
    }

    fun friendlyStorageMessage(context: Context, availableBytes: Long): String {
        val mb = availableBytes / (1024 * 1024)
        return "Only $mb megabytes of storage remain"
    }
}
