/*
 * Accessible Camera and Gallery
 * Copyright (C) 2026 Sujan Rai, Blind Tech Nexus team.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.blindtechnexus.accessiblecamera.domain.model

/**
 * The capability registry. Detected once at startup and refreshed when the camera
 * configuration changes. Every screen reads availability from here; nothing in the UI
 * guesses whether a feature exists.
 */
data class CameraCapabilities(
    val hasAnyCamera: Boolean = false,
    val lenses: List<LensInfo> = emptyList(),
    val supportedModes: Set<CameraMode> = setOf(CameraMode.PHOTO),
    val modeUnavailableReasons: Map<CameraMode, String> = emptyMap(),
    val supportedAspectRatios: List<AspectRatioOption> = emptyList(),
    val photoResolutions: List<ResolutionOption> = emptyList(),
    val videoQualities: List<VideoQualityOption> = emptyList(),
    val videoFrameRates: List<Int> = emptyList(),
    val videoCodecs: List<VideoCodecOption> = emptyList(),
    val supportsAudioRecording: Boolean = false,
    val supportsBurst: Boolean = true,
    val supportsPhotoDuringVideo: Boolean = true,
    val supportsLocationTagging: Boolean = false,
    val supportsWatermark: Boolean = true,
    val supportsSceneDescription: Boolean = false,
    val supportsFaceDetection: Boolean = false,
    val supportsBarcodeScanning: Boolean = false,
    val supportsVoiceRecognition: Boolean = false,
    val supportsVolumeKeyShutter: Boolean = true,
    val maxZoom: Float = 1f,
    val minZoom: Float = 1f,
    val notes: List<String> = emptyList()
) {
    val hasFlash: Boolean get() = lenses.any { it.hasFlash && it.facing == Facing.BACK }
    val hasFrontCamera: Boolean get() = lenses.any { it.facing == Facing.FRONT }
    val hasBackCamera: Boolean get() = lenses.any { it.facing == Facing.BACK }
    val hasUltrawide: Boolean get() = lenses.any { it.type == LensType.ULTRAWIDE }
    val hasTelephoto: Boolean get() = lenses.any { it.type == LensType.TELEPHOTO }
    val supportsManualControls: Boolean get() = lenses.any { it.supportsManualIso || it.supportsManualFocus }
    val supportsRawCapture: Boolean get() = lenses.any { it.supportsRaw }

    fun isModeAvailable(mode: CameraMode): Boolean = supportedModes.contains(mode)

    /** Spoken explanation used both for disabled controls and capability dialogs. */
    fun reasonFor(mode: CameraMode): String =
        modeUnavailableReasons[mode] ?: defaultReason(mode)

    private fun defaultReason(mode: CameraMode): String = when (mode) {
        CameraMode.PORTRAIT -> "This device does not offer a portrait or bokeh camera extension."
        CameraMode.NIGHT -> "This device does not offer a night mode camera extension."
        CameraMode.HDR -> "This device does not offer a high dynamic range camera extension."
        CameraMode.PANORAMA -> "Panorama needs a camera with a still capture use case."
        CameraMode.PRO -> "This device does not report manual ISO or shutter speed control."
        CameraMode.SLOW_MOTION -> "This device cannot record at a high enough frame rate for slow motion."
        CameraMode.TIME_LAPSE -> "Time lapse needs photo capture support."
        CameraMode.VIDEO -> "This device cannot record video through CameraX."
        CameraMode.PHOTO -> "Photo capture is not available."
    }

    companion object {
        val Empty = CameraCapabilities(
            hasAnyCamera = false,
            supportedModes = emptySet(),
            modeUnavailableReasons = CameraMode.entries.associateWith {
                "No camera is available on this device."
            }
        )
    }
}

data class AspectRatioOption(val numerator: Int, val denominator: Int, val label: String) {
    val ratio: Float get() = numerator.toFloat() / denominator.toFloat()
    val speech: String get() = "$numerator to $denominator"
}

data class ResolutionOption(
    val width: Int,
    val height: Int,
    val aspectRatioLabel: String
) {
    val label: String get() = "$width by $height"
    val megapixelLabel: String
        get() {
            val mp = (width.toLong() * height.toLong()).toDouble() / 1_000_000.0
            return String.format(java.util.Locale.US, "%.1f megapixels", mp)
        }
}

data class VideoQualityOption(
    val qualityLabel: String,
    val width: Int,
    val height: Int,
    val speech: String
) {
    val displayLabel: String get() = if (width > 0) "$qualityLabel, $width by $height" else qualityLabel
}

data class VideoCodecOption(val mimeType: String, val label: String)
