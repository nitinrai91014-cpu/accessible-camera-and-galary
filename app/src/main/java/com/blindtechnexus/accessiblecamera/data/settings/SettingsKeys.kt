/*
 * Accessible Camera and Gallery
 * Copyright (C) 2026 Sujan Rai, Blind Tech Nexus team.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.blindtechnexus.accessiblecamera.data.settings

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

/** Preference keys for the single settings DataStore file. */
internal object SettingsKeys {
    val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
    val VOICE_GUIDANCE = booleanPreferencesKey("voice_guidance_enabled")
    val SPEECH_RATE = floatPreferencesKey("speech_rate")
    val SPEECH_PITCH = floatPreferencesKey("speech_pitch")
    val VERBOSITY = stringPreferencesKey("verbosity")
    val HAPTICS = booleanPreferencesKey("haptics_enabled")
    val AUDIO_CUES = booleanPreferencesKey("audio_cues_enabled")
    val THEME = stringPreferencesKey("theme_mode")
    val FONT_SCALE = stringPreferencesKey("font_scale")
    val GALLERY_LAYOUT = stringPreferencesKey("gallery_layout")
    val SHOW_GRID = booleanPreferencesKey("show_grid_lines")
    val SHOW_LEVEL = booleanPreferencesKey("show_level_indicator")
    val MIRROR_FRONT = booleanPreferencesKey("mirror_front_camera")
    val SAVE_LOCATION = stringPreferencesKey("save_location")
    val DEFAULT_VIDEO_QUALITY = stringPreferencesKey("default_video_quality")
    val TIMER_DEFAULT = stringPreferencesKey("timer_default")
    val GEOTAGGING = booleanPreferencesKey("geotagging_enabled")
    val WATERMARK = booleanPreferencesKey("watermark_enabled")
    val WATERMARK_TEXT = stringPreferencesKey("watermark_text")
    /** Legacy boolean, still written so a downgrade keeps working. */
    val FACE_GUIDANCE = booleanPreferencesKey("face_guidance_enabled")
    val FACE_INTERVAL = stringPreferencesKey("face_detection_interval")
    val SCENE_INTERVAL = stringPreferencesKey("scene_description_interval")
    val BARCODE_SCANNING = booleanPreferencesKey("barcode_scanning_enabled")
    val SHUTTER_SOUND = booleanPreferencesKey("shutter_sound_enabled")
    val VOICE_COMMANDS = booleanPreferencesKey("voice_commands_enabled")
    val VOLUME_KEY_SHUTTER = booleanPreferencesKey("volume_key_shutter")
    val REVIEW_AFTER_CAPTURE = booleanPreferencesKey("review_after_capture")
    val ORIENTATION_LOCK = booleanPreferencesKey("orientation_locked")
    val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
    val RECORDING_ANNOUNCE_SECONDS = intPreferencesKey("recording_announce_seconds")
    val DEBUG_LOGGING = booleanPreferencesKey("remote_debug_logging")
    val SHARE_LOCATION_WATERMARK = booleanPreferencesKey("share_location_watermark")
}
