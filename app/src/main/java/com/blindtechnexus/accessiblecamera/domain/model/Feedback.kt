/*
 * Accessible Camera and Gallery
 * Copyright (C) 2026 Sujan Rai, Blind Tech Nexus team.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.blindtechnexus.accessiblecamera.domain.model

/** Short non-speech sounds generated locally, so no asset files are required. */
enum class AudioCue(val description: String) {
    SHUTTER("Camera shutter"),
    RECORDING_START("Recording start tone"),
    RECORDING_STOP("Recording stop tone"),
    FOCUS_LOCK("Focus lock tone"),
    SUCCESS("Success tone"),
    ERROR("Error tone"),
    TICK("Timer tick"),
    ITEM_SELECTED("Selection tone"),
    TRASH("Delete tone")
}

/** Haptic patterns mapped to meaningful actions. */
enum class HapticCue(val description: String) {
    TAP("Light tap"),
    CAPTURE("Capture confirm"),
    RECORDING_START("Recording pulse"),
    RECORDING_STOP("Recording stop pulse"),
    FOCUS_LOCK("Focus lock"),
    ERROR("Error buzz"),
    SUCCESS("Success pulse"),
    LONG_PRESS("Long press"),
    SELECTION("Selection tick")
}
