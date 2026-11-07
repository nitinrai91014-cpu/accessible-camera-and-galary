/*
 * Accessible Camera and Gallery
 * Copyright (C) 2026 Sujan Rai, Blind Tech Nexus team.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.blindtechnexus.accessiblecamera.domain.model

/** Flash behaviour for both photo and torch use. */
enum class FlashMode(val label: String, val spoken: String) {
    OFF("Off", "Flash off"),
    AUTO("Auto", "Flash automatic"),
    ON("On", "Flash on"),
    TORCH("Torch on", "Torch on");

    val next: FlashMode
        get() = entries[(ordinal + 1) % entries.size]
}

/** How the camera focuses. */
enum class FocusMode(val label: String, val spoken: String) {
    AUTO("Continuous auto", "Continuous autofocus"),
    TAP("Tap to focus", "Tap to focus"),
    LOCKED("Locked", "Focus locked"),
    MANUAL("Manual", "Manual focus");

    val next: FocusMode get() = entries[(ordinal + 1) % entries.size]
}

/** Exposure behaviour. */
enum class ExposureMode(val label: String, val spoken: String) {
    AUTO("Automatic", "Automatic exposure"),
    LOCKED("Locked", "Exposure locked"),
    MANUAL("Manual", "Manual exposure");

    val next: ExposureMode get() = entries[(ordinal + 1) % entries.size]
}

/** White balance behaviour. */
enum class WhiteBalanceMode(val label: String, val spoken: String) {
    AUTO("Automatic", "Automatic white balance"),
    INCANDESCENT("Incandescent", "Incandescent white balance"),
    DAYLIGHT("Daylight", "Daylight white balance"),
    FLUORESCENT("Fluorescent", "Fluorescent white balance"),
    CLOUDY("Cloudy", "Cloudy white balance"),
    SHADE("Shade", "Shade white balance"),
    LOCKED("Locked", "White balance locked");

    val next: WhiteBalanceMode get() = entries[(ordinal + 1) % entries.size]
}

/** Metering region for auto exposure. */
enum class MeteringMode(val label: String, val spoken: String) {
    CENTER_WEIGHTED("Centre weighted", "Centre weighted metering"),
    MATRIX("Matrix", "Matrix metering"),
    SPOT("Spot", "Spot metering");

    val next: MeteringMode get() = entries[(ordinal + 1) % entries.size]
}

enum class StabilizationMode(val label: String, val spoken: String) {
    OFF("Off", "Stabilization off"),
    STANDARD("Standard", "Standard stabilization"),
    HIGH("High", "High stabilization"),
    OPTICAL("Optical", "Optical stabilization");

    val next: StabilizationMode get() = entries[(ordinal + 1) % entries.size]
}

enum class PhotoFileFormat(val label: String, val spoken: String) {
    JPEG("JPEG", "JPEG only"),
    RAW_JPEG("RAW and JPEG", "RAW plus JPEG");

    val next: PhotoFileFormat get() = entries[(ordinal + 1) % entries.size]
}

/** Current face guidance result produced by ML Kit. */
sealed interface FaceGuidance {
    data object Disabled : FaceGuidance
    data object Unavailable : FaceGuidance
    data class NoFace(val hint: String) : FaceGuidance
    data class MultipleFaces(val count: Int) : FaceGuidance
    data class Aligned(val message: String) : FaceGuidance
    data class Misaligned(val message: String, val severity: Int) : FaceGuidance
}

/** Everything the photo and video screens need to draw and speak. */
data class CameraUiState(
    val state: CameraState = CameraState.IDLE,
    val capabilities: CameraCapabilities = CameraCapabilities.Empty,
    val selectedMode: CameraMode = CameraMode.PHOTO,
    val activeLens: LensInfo? = null,
    val availableLenses: List<LensInfo> = emptyList(),
    val flashMode: FlashMode = FlashMode.OFF,
    val torchEnabled: Boolean = false,
    val timer: TimerDuration = TimerDuration.OFF,
    val countdownSecondsRemaining: Int? = null,
    val zoomRatio: Float = 1f,
    val supportsZoom: Boolean = true,
    val exposureCompensation: Int = 0,
    val exposureRange: IntRange = -6..6,
    val exposureMode: ExposureMode = ExposureMode.AUTO,
    val focusMode: FocusMode = FocusMode.AUTO,
    val meteringMode: MeteringMode = MeteringMode.CENTER_WEIGHTED,
    val whiteBalanceMode: WhiteBalanceMode = WhiteBalanceMode.AUTO,
    val manualIso: Int = 0,
    val isoRange: IntRange = 100..3200,
    val manualShutterNanos: Long = 0L,
    val shutterRangeNanos: LongRange = 1_000_000L..100_000_000L,
    val manualFocusDistance: Float = 0f,
    val stabilization: StabilizationMode = StabilizationMode.STANDARD,
    val photoFormat: PhotoFileFormat = PhotoFileFormat.JPEG,
    val aspectRatio: AspectRatioOption = AspectRatioOption(4, 3, "4:3"),
    val photoResolution: ResolutionOption? = null,
    val videoQuality: VideoQualityOption? = null,
    val videoFrameRate: Int = 30,
    val videoCodec: VideoCodecOption? = null,
    val videoBitrate: Int = 0,
    val audioEnabled: Boolean = true,
    val isRecording: Boolean = true,
    val recordingDurationMillis: Long = 0L,
    val recordingPaused: Boolean = false,
    val gridVisible: Boolean = false,
    val levelVisible: Boolean = false,
    val levelDegrees: Float = 0f,
    val mirrorFrontCamera: Boolean = true,
    val faceGuidance: FaceGuidance = FaceGuidance.Disabled,
    val faceGuidanceEnabled: Boolean = true,
    val sceneDescription: String? = null,
    val savedMediaCount: Int = 0,
    val lastSavedItem: MediaItem? = null,
    val burstModeActive: Boolean = false,
    val burstCount: Int = 0,
    val failure: CameraFailure? = null,
    val statusAnnouncement: String? = null,
    val permissionState: CameraPermissionState = CameraPermissionState.Unknown,
    val isLowStorage: Boolean = false,
    val reviewItem: MediaItem? = null,
    val showAdvancedPanel: Boolean = false,
    val supportsPhotoDuringVideo: Boolean = false,
    val isRecordingPausedAllowed: Boolean = true,
    val voiceCommandsEnabled: Boolean = false,
    val voiceListening: Boolean = false,
    val voiceAvailable: Boolean = false
) {
    val isFrontCamera: Boolean get() = activeLens?.facing == Facing.FRONT
    val canUseFlash: Boolean get() = activeLens?.hasFlash == true
    val canUseTorch: Boolean get() = activeLens?.hasFlash == true
    val maxZoom: Float get() = activeLens?.zoomMax ?: capabilities.maxZoom
    val minZoom: Float get() = activeLens?.zoomMin ?: capabilities.minZoom

    /** Accessible description of the preview surface for screen readers. */
    val previewDescription: String
        get() = buildString {
            append(selectedMode.speechLabel)
            append(". ")
            append(state.spoken)
            append(". ")
            if (isFrontCamera) append("Front camera. ") else append("Back camera. ")
            append("Zoom ")
            append(String.format(java.util.Locale.US, "%.1f", zoomRatio))
            append(" times. ")
            append(flashMode.spoken)
            append(". ")
            if (timer.seconds > 0) {
                append("Timer ")
                append(timer.label)
                append(". ")
            }
            when (val guidance = faceGuidance) {
                is FaceGuidance.Aligned -> append(guidance.message).append(". ")
                is FaceGuidance.Misaligned -> append(guidance.message).append(". ")
                is FaceGuidance.NoFace -> append(guidance.hint).append(". ")
                is FaceGuidance.MultipleFaces -> append("More than one face in view. ")
                else -> Unit
            }
            sceneDescription?.let { append(it).append(". ") }
        }
}

enum class CameraPermissionState {
    Unknown,
    Granted,
    Denied,
    PermanentlyDenied
}

/** Model for a single advanced control row so the UI stays data driven. */
data class AdvancedControl(
    val id: String,
    val label: String,
    val valueLabel: String,
    val available: Boolean,
    val unavailableReason: String?,
    val step: String
)
