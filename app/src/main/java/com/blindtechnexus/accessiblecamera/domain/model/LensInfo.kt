/*
 * Accessible Camera and Gallery
 * Copyright (C) 2026 Sujan Rai, Blind Tech Nexus team.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.blindtechnexus.accessiblecamera.domain.model

/** Type of optical lens, derived from the camera identifier and focal length. */
enum class LensType(val label: String, val spoken: String) {
    ULTRAWIDE("Ultrawide", "ultrawide lens"),
    WIDE("Wide", "wide lens"),
    TELEPHOTO("Telephoto", "telephoto lens"),
    FRONT("Front", "front camera"),
    UNKNOWN("Camera", "camera")
}

/**
 * A physical camera on the device. Each lens maps to one CameraX camera selector and
 * carries the zoom range and manual control support detected at runtime.
 */
data class LensInfo(
    val id: String,
    val type: LensType,
    val facing: Facing,
    val zoomMin: Float,
    val zoomMax: Float,
    val focalLengthMm: Float?,
    val hasFlash: Boolean,
    val hasAutofocus: Boolean,
    val maxResolutionWidth: Int,
    val maxResolutionHeight: Int,
    val supportsRaw: Boolean,
    val supportsManualIso: Boolean,
    val isoRange: IntRange?,
    val exposureTimeRangeNanos: LongRange?,
    val supportsManualFocus: Boolean,
    val supportsWhiteBalanceControl: Boolean,
    val supportsAeLock: Boolean,
    val supportsAfLock: Boolean,
    val supportsAwbLock: Boolean,
    val supportsOpticalStabilization: Boolean,
    val supportsVideoStabilization: Boolean,
    val supportsHdrVideo: Boolean,
    val maxVideoFrameRate: Int,
    val hardwareLevel: String
) {
    val displayName: String
        get() = buildString {
            append(type.label)
            if (focalLengthMm != null) {
                append(" ")
                append(String.format(java.util.Locale.US, "%.1f", focalLengthMm))
                append(" millimetre")
            }
        }

    val speechDescription: String
        get() = buildString {
            append(type.spoken)
            append(", zoom from ")
            append(String.format(java.util.Locale.US, "%.1f", zoomMin))
            append(" to ")
            append(String.format(java.util.Locale.US, "%.1f", zoomMax))
            append(" times")
        }
}

enum class Facing(val label: String) {
    BACK("Back"),
    FRONT("Front"),
    EXTERNAL("External")
}
