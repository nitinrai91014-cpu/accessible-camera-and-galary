/*
 * Accessible Camera and Gallery
 * Copyright (C) 2026 Sujan Rai, Blind Tech Nexus team.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.blindtechnexus.accessiblecamera.domain.model

/**
 * Every capture mode the app can present. A mode is only shown as enabled when the
 * capability registry proves the device can support it.
 */
enum class CameraMode(
    val label: String,
    val description: String,
    val isVideo: Boolean = false,
    val requiresExtension: Boolean = false,
    val isProOnly: Boolean = false
) {
    PHOTO(
        label = "Photo",
        description = "Take a single still photo."
    ),
    PORTRAIT(
        label = "Portrait",
        description = "Blur the background behind the subject.",
        requiresExtension = true
    ),
    NIGHT(
        label = "Night",
        description = "Brighten photos in low light.",
        requiresExtension = true
    ),
    HDR(
        label = "High dynamic range",
        description = "Keep detail in bright and dark areas.",
        requiresExtension = true
    ),
    PANORAMA(
        label = "Panorama",
        description = "Sweep across a scene to build one wide photo."
    ),
    PRO(
        label = "Pro or manual",
        description = "Control ISO, shutter speed, focus, and white balance directly.",
        isProOnly = true
    ),
    VIDEO(
        label = "Video",
        description = "Record video with optional sound.",
        isVideo = true
    ),
    SLOW_MOTION(
        label = "Slow motion",
        description = "Record fast movement and play it back slowly.",
        isVideo = true
    ),
    TIME_LAPSE(
        label = "Time lapse",
        description = "Capture a scene at intervals and play it back quickly.",
        isVideo = true
    );

    val speechLabel: String
        get() = when (this) {
            PHOTO -> "Photo mode"
            PORTRAIT -> "Portrait mode"
            NIGHT -> "Night mode"
            HDR -> "High dynamic range mode"
            PANORAMA -> "Panorama mode"
            PRO -> "Pro manual mode"
            VIDEO -> "Video mode"
            SLOW_MOTION -> "Slow motion mode"
            TIME_LAPSE -> "Time lapse mode"
        }
}
