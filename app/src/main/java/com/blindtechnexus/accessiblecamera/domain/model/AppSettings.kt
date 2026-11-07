/*
 * Accessible Camera and Gallery
 * Copyright (C) 2026 Sujan Rai, Blind Tech Nexus team.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.blindtechnexus.accessiblecamera.domain.model

/** How much the app speaks. */
enum class Verbosity(val label: String, val spoken: String) {
    MINIMAL("Minimal", "only essential messages"),
    STANDARD("Standard", "important state changes"),
    DETAILED("Detailed", "every value change and hint");

    val next: Verbosity get() = entries[(ordinal + 1) % entries.size]
}

enum class ThemeMode(val label: String) {
    SYSTEM("Follow system"),
    LIGHT("Light"),
    DARK("Dark"),
    HIGH_CONTRAST_LIGHT("High contrast light"),
    HIGH_CONTRAST_DARK("High contrast dark");

    val next: ThemeMode get() = entries[(ordinal + 1) % entries.size]
    val isHighContrast: Boolean get() = this == HIGH_CONTRAST_LIGHT || this == HIGH_CONTRAST_DARK
}

enum class FontScaleOption(val label: String, val scale: Float) {
    SYSTEM("Follow system", 0f),
    LARGE("Large", 1.3f),
    EXTRA_LARGE("Extra large", 1.6f),
    HUGE("Huge", 2f);

    val next: FontScaleOption get() = entries[(ordinal + 1) % entries.size]
}

enum class SaveLocation(val label: String, val relativePath: String) {
    PICTURES("Pictures and Movies folders", ""),
    DCIM("DCIM folder", "DCIM/AccessibleCamera"),
    APP_FOLDER("App media folder", "AccessibleCamera");

    val next: SaveLocation get() = entries[(ordinal + 1) % entries.size]
}

enum class SceneDescriptionInterval(val label: String, val seconds: Int) {
    OFF("Off", 0),
    FIVE_SECONDS("Every 5 seconds", 5),
    TEN_SECONDS("Every 10 seconds", 10),
    TWENTY_SECONDS("Every 20 seconds", 20),
    THIRTY_SECONDS("Every 30 seconds", 30);

    val next: SceneDescriptionInterval get() = entries[(ordinal + 1) % entries.size]
}

enum class TimerDuration(val label: String, val seconds: Int) {
    OFF("Off", 0),
    THREE_SECONDS("3 seconds", 3),
    FIVE_SECONDS("5 seconds", 5),
    TEN_SECONDS("10 seconds", 10);

    val next: TimerDuration get() = entries[(ordinal + 1) % entries.size]
}

/** All user preferences. Persisted with DataStore as a single immutable snapshot. */
data class AppSettings(
    val hasCompletedOnboarding: Boolean = false,
    val voiceGuidanceEnabled: Boolean = true,
    val speechRate: Float = 1.0f,
    val speechPitch: Float = 1.0f,
    val verbosity: Verbosity = Verbosity.STANDARD,
    val hapticsEnabled: Boolean = true,
    val audioCuesEnabled: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val fontScale: FontScaleOption = FontScaleOption.SYSTEM,
    val galleryLayout: GalleryLayout = GalleryLayout.GRID_MEDIUM,
    val showGridLines: Boolean = false,
    val showLevelIndicator: Boolean = false,
    val mirrorFrontCamera: Boolean = true,
    val saveLocation: SaveLocation = SaveLocation.PICTURES,
    val defaultVideoQuality: String = "FHD",
    val timerDefault: TimerDuration = TimerDuration.OFF,
    val geotaggingEnabled: Boolean = false,
    val watermarkEnabled: Boolean = false,
    val watermarkText: String = "Accessible Camera",
    val faceGuidanceEnabled: Boolean = true,
    val sceneDescriptionInterval: SceneDescriptionInterval = SceneDescriptionInterval.OFF,
    val voiceCommandsEnabled: Boolean = false,
    val volumeKeyShutterEnabled: Boolean = true,
    val reviewAfterCapture: Boolean = true,
    val orientationLocked: Boolean = false,
    val keepScreenOn: Boolean = true,
    val recordingTimerAnnounceSeconds: Int = 30,
    val remoteDebugLogging: Boolean = false,
    val shareLocationWithWatermark: Boolean = false
) {
    val speechRateLabel: String get() = String.format(java.util.Locale.US, "%.1f times", speechRate)
    val speechPitchLabel: String get() = String.format(java.util.Locale.US, "%.1f", speechPitch)

    companion object {
        val Defaults = AppSettings()
    }
}
