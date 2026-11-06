/*
 * Accessible Camera and Gallery
 * Copyright (C) 2026 Sujan Rai, Blind Tech Nexus team.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.blindtechnexus.accessiblecamera.data.settings

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import com.blindtechnexus.accessiblecamera.domain.model.AppSettings
import com.blindtechnexus.accessiblecamera.domain.model.FontScaleOption
import com.blindtechnexus.accessiblecamera.domain.model.GalleryLayout
import com.blindtechnexus.accessiblecamera.domain.model.SaveLocation
import com.blindtechnexus.accessiblecamera.domain.model.SceneDescriptionInterval
import com.blindtechnexus.accessiblecamera.domain.model.ThemeMode
import com.blindtechnexus.accessiblecamera.domain.model.TimerDuration
import com.blindtechnexus.accessiblecamera.domain.model.Verbosity

/**
 * Converts between the persisted [Preferences] snapshot and [AppSettings].
 * Unknown stored enum names fall back to defaults so a downgrade never crashes.
 */
internal object SettingsMapper {

    fun toSettings(preferences: Preferences): AppSettings {
        val defaults = AppSettings.Defaults
        return AppSettings(
            hasCompletedOnboarding = preferences[SettingsKeys.ONBOARDING_COMPLETE] ?: defaults.hasCompletedOnboarding,
            voiceGuidanceEnabled = preferences[SettingsKeys.VOICE_GUIDANCE] ?: defaults.voiceGuidanceEnabled,
            speechRate = (preferences[SettingsKeys.SPEECH_RATE] ?: defaults.speechRate).coerceIn(0.5f, 2.5f),
            speechPitch = (preferences[SettingsKeys.SPEECH_PITCH] ?: defaults.speechPitch).coerceIn(0.5f, 2f),
            verbosity = enumOr(preferences[SettingsKeys.VERBOSITY], defaults.verbosity),
            hapticsEnabled = preferences[SettingsKeys.HAPTICS] ?: defaults.hapticsEnabled,
            audioCuesEnabled = preferences[SettingsKeys.AUDIO_CUES] ?: defaults.audioCuesEnabled,
            themeMode = enumOr(preferences[SettingsKeys.THEME], defaults.themeMode),
            fontScale = enumOr(preferences[SettingsKeys.FONT_SCALE], defaults.fontScale),
            galleryLayout = enumOr(preferences[SettingsKeys.GALLERY_LAYOUT], defaults.galleryLayout),
            showGridLines = preferences[SettingsKeys.SHOW_GRID] ?: defaults.showGridLines,
            showLevelIndicator = preferences[SettingsKeys.SHOW_LEVEL] ?: defaults.showLevelIndicator,
            mirrorFrontCamera = preferences[SettingsKeys.MIRROR_FRONT] ?: defaults.mirrorFrontCamera,
            saveLocation = enumOr(preferences[SettingsKeys.SAVE_LOCATION], defaults.saveLocation),
            defaultVideoQuality = preferences[SettingsKeys.DEFAULT_VIDEO_QUALITY] ?: defaults.defaultVideoQuality,
            timerDefault = enumOr(preferences[SettingsKeys.TIMER_DEFAULT], defaults.timerDefault),
            geotaggingEnabled = preferences[SettingsKeys.GEOTAGGING] ?: defaults.geotaggingEnabled,
            watermarkEnabled = preferences[SettingsKeys.WATERMARK] ?: defaults.watermarkEnabled,
            watermarkText = preferences[SettingsKeys.WATERMARK_TEXT] ?: defaults.watermarkText,
            faceGuidanceEnabled = preferences[SettingsKeys.FACE_GUIDANCE] ?: defaults.faceGuidanceEnabled,
            sceneDescriptionInterval = enumOr(preferences[SettingsKeys.SCENE_INTERVAL], defaults.sceneDescriptionInterval),
            voiceCommandsEnabled = preferences[SettingsKeys.VOICE_COMMANDS] ?: defaults.voiceCommandsEnabled,
            volumeKeyShutterEnabled = preferences[SettingsKeys.VOLUME_KEY_SHUTTER] ?: defaults.volumeKeyShutterEnabled,
            reviewAfterCapture = preferences[SettingsKeys.REVIEW_AFTER_CAPTURE] ?: defaults.reviewAfterCapture,
            orientationLocked = preferences[SettingsKeys.ORIENTATION_LOCK] ?: defaults.orientationLocked,
            keepScreenOn = preferences[SettingsKeys.KEEP_SCREEN_ON] ?: defaults.keepScreenOn,
            recordingTimerAnnounceSeconds = preferences[SettingsKeys.RECORDING_ANNOUNCE_SECONDS] ?: defaults.recordingTimerAnnounceSeconds,
            remoteDebugLogging = preferences[SettingsKeys.DEBUG_LOGGING] ?: defaults.remoteDebugLogging,
            shareLocationWithWatermark = preferences[SettingsKeys.SHARE_LOCATION_WATERMARK] ?: defaults.shareLocationWithWatermark
        )
    }
}

/**
 * Writes a full [AppSettings] snapshot. Kept top level so repositories can import it.
 */
fun MutablePreferences.writeAll(settings: AppSettings) {
    this[SettingsKeys.ONBOARDING_COMPLETE] = settings.hasCompletedOnboarding
    this[SettingsKeys.VOICE_GUIDANCE] = settings.voiceGuidanceEnabled
    this[SettingsKeys.SPEECH_RATE] = settings.speechRate
    this[SettingsKeys.SPEECH_PITCH] = settings.speechPitch
    this[SettingsKeys.VERBOSITY] = settings.verbosity.name
    this[SettingsKeys.HAPTICS] = settings.hapticsEnabled
    this[SettingsKeys.AUDIO_CUES] = settings.audioCuesEnabled
    this[SettingsKeys.THEME] = settings.themeMode.name
    this[SettingsKeys.FONT_SCALE] = settings.fontScale.name
    this[SettingsKeys.GALLERY_LAYOUT] = settings.galleryLayout.name
    this[SettingsKeys.SHOW_GRID] = settings.showGridLines
    this[SettingsKeys.SHOW_LEVEL] = settings.showLevelIndicator
    this[SettingsKeys.MIRROR_FRONT] = settings.mirrorFrontCamera
    this[SettingsKeys.SAVE_LOCATION] = settings.saveLocation.name
    this[SettingsKeys.DEFAULT_VIDEO_QUALITY] = settings.defaultVideoQuality
    this[SettingsKeys.TIMER_DEFAULT] = settings.timerDefault.name
    this[SettingsKeys.GEOTAGGING] = settings.geotaggingEnabled
    this[SettingsKeys.WATERMARK] = settings.watermarkEnabled
    this[SettingsKeys.WATERMARK_TEXT] = settings.watermarkText
    this[SettingsKeys.FACE_GUIDANCE] = settings.faceGuidanceEnabled
    this[SettingsKeys.SCENE_INTERVAL] = settings.sceneDescriptionInterval.name
    this[SettingsKeys.VOICE_COMMANDS] = settings.voiceCommandsEnabled
    this[SettingsKeys.VOLUME_KEY_SHUTTER] = settings.volumeKeyShutterEnabled
    this[SettingsKeys.REVIEW_AFTER_CAPTURE] = settings.reviewAfterCapture
    this[SettingsKeys.ORIENTATION_LOCK] = settings.orientationLocked
    this[SettingsKeys.KEEP_SCREEN_ON] = settings.keepScreenOn
    this[SettingsKeys.RECORDING_ANNOUNCE_SECONDS] = settings.recordingTimerAnnounceSeconds
    this[SettingsKeys.DEBUG_LOGGING] = settings.remoteDebugLogging
    this[SettingsKeys.SHARE_LOCATION_WATERMARK] = settings.shareLocationWithWatermark
}

private inline fun <reified T : Enum<T>> enumOr(name: String?, fallback: T): T =
    if (name == null) fallback else runCatching { enumValueOf<T>(name) }.getOrDefault(fallback)
