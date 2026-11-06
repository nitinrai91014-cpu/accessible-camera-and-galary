/*
 * Accessible Camera and Gallery
 * Copyright (C) 2026 Sujan Rai, Blind Tech Nexus team.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.blindtechnexus.accessiblecamera.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blindtechnexus.accessiblecamera.data.camera.CameraCapabilityDetector
import com.blindtechnexus.accessiblecamera.data.voice.OnDeviceSpeechRecognizer
import com.blindtechnexus.accessiblecamera.data.voice.VoiceRepositoryImpl
import com.blindtechnexus.accessiblecamera.domain.model.AnnouncementPriority
import com.blindtechnexus.accessiblecamera.domain.model.AppSettings
import com.blindtechnexus.accessiblecamera.domain.model.CameraCapabilities
import com.blindtechnexus.accessiblecamera.domain.model.FontScaleOption
import com.blindtechnexus.accessiblecamera.domain.model.GalleryLayout
import com.blindtechnexus.accessiblecamera.domain.model.SaveLocation
import com.blindtechnexus.accessiblecamera.domain.model.SceneDescriptionInterval
import com.blindtechnexus.accessiblecamera.domain.model.ThemeMode
import com.blindtechnexus.accessiblecamera.domain.model.TimerDuration
import com.blindtechnexus.accessiblecamera.domain.model.Verbosity
import com.blindtechnexus.accessiblecamera.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Settings state including capability notes and speech availability. */
data class SettingsUiState(
    val capabilities: CameraCapabilities = CameraCapabilities.Empty,
    val speechAvailable: Boolean = false,
    val voiceCommandsAvailable: Boolean = false,
    val onDeviceRecognition: Boolean = false,
    val message: String? = null,
    val showResetConfirmation: Boolean = false
)

/**
 * Settings logic. Every change is persisted immediately and acknowledged by speech so a
 * screen reader user gets confirmation without leaving the row.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val capabilityDetector: CameraCapabilityDetector,
    private val voice: VoiceRepositoryImpl,
    private val speechRecognizer: OnDeviceSpeechRecognizer,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings.Defaults)

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            speechAvailable = OnDeviceSpeechRecognizer.isSpeechRecognitionAvailable(context),
            onDeviceRecognition = OnDeviceSpeechRecognizer.isOnDeviceRecognitionSupported(context)
        )
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            capabilityDetector.capabilities.collect { capabilities ->
                _uiState.value = _uiState.value.copy(capabilities = capabilities)
            }
        }
        viewModelScope.launch {
            val available = OnDeviceSpeechRecognizer.isSpeechRecognitionAvailable(context)
            _uiState.value = _uiState.value.copy(
                speechAvailable = available,
                voiceCommandsAvailable = available
            )
        }
    }

    fun voiceGuidanceEnabled(enabled: Boolean) = update("Voice guidance ${if (enabled) "on" else "off"}") {
        it.copy(voiceGuidanceEnabled = enabled)
    }

    fun speechRate(rate: Float) = update("Speech rate ${String.format(java.util.Locale.US, "%.1f", rate)}") {
        it.copy(speechRate = rate)
    }

    fun speechPitch(pitch: Float) = update("Speech pitch ${String.format(java.util.Locale.US, "%.1f", pitch)}") {
        it.copy(speechPitch = pitch)
    }

    fun cycleVerbosity() = update("Verbosity ${settings.value.verbosity.next.label}") {
        it.copy(verbosity = it.verbosity.next)
    }

    fun haptics(enabled: Boolean) = update("Haptics ${if (enabled) "on" else "off"}") {
        it.copy(hapticsEnabled = enabled)
    }

    fun audioCues(enabled: Boolean) = update("Audio cues ${if (enabled) "on" else "off"}") {
        it.copy(audioCuesEnabled = enabled)
    }

    fun cycleTheme() = update("Theme ${settings.value.themeMode.next.label}") {
        it.copy(themeMode = it.themeMode.next)
    }

    fun setTheme(theme: ThemeMode) = update("Theme ${theme.label}") { it.copy(themeMode = theme) }

    fun cycleFontScale() = update("Text size ${settings.value.fontScale.next.label}") {
        it.copy(fontScale = it.fontScale.next)
    }

    fun setFontScale(scale: FontScaleOption) = update("Text size ${scale.label}") {
        it.copy(fontScale = scale)
    }

    fun cycleGalleryLayout() = update("Gallery layout ${settings.value.galleryLayout.next.label}") {
        it.copy(galleryLayout = it.galleryLayout.next)
    }

    fun setGalleryLayout(layout: GalleryLayout) = update(layout.label) { it.copy(galleryLayout = layout) }

    fun gridLines(enabled: Boolean) = update("Grid lines ${if (enabled) "on" else "off"}") {
        it.copy(showGridLines = enabled)
    }

    fun levelIndicator(enabled: Boolean) = update("Level indicator ${if (enabled) "on" else "off"}") {
        it.copy(showLevelIndicator = enabled)
    }

    fun mirrorFront(enabled: Boolean) = update("Mirror front camera ${if (enabled) "on" else "off"}") {
        it.copy(mirrorFrontCamera = enabled)
    }

    fun cycleSaveLocation() = update("Save location ${settings.value.saveLocation.next.label}") {
        it.copy(saveLocation = it.saveLocation.next)
    }

    fun setSaveLocation(location: SaveLocation) = update(location.label) { it.copy(saveLocation = location) }

    fun cycleDefaultQuality() = update("Default quality updated") {
        val qualities = _uiState.value.capabilities.videoQualities
        if (qualities.isEmpty()) return@update it
        val index = qualities.indexOfFirst { option -> option.qualityLabel.contains(it.defaultVideoQuality, true) }
        val next = qualities[(index + 1).mod(qualities.size)]
        it.copy(defaultVideoQuality = next.qualityLabel)
    }

    fun cycleTimer() = update("Default timer ${settings.value.timerDefault.next.label}") {
        it.copy(timerDefault = it.timerDefault.next)
    }

    fun setTimer(timer: TimerDuration) = update("Default timer ${timer.label}") {
        it.copy(timerDefault = timer)
    }

    fun geotagging(enabled: Boolean) = update(
        if (enabled) {
            "Location tagging on. Photos will store where they were taken."
        } else {
            "Location tagging off"
        }
    ) { it.copy(geotaggingEnabled = enabled) }

    fun watermark(enabled: Boolean) = update("Watermark ${if (enabled) "on" else "off"}") {
        it.copy(watermarkEnabled = enabled)
    }

    fun watermarkText(text: String) = update("Watermark text updated") {
        it.copy(watermarkText = text.take(60))
    }

    fun faceGuidance(enabled: Boolean) = update("Face guidance ${if (enabled) "on" else "off"}") {
        it.copy(faceGuidanceEnabled = enabled)
    }

    fun cycleSceneInterval() = update(
        "Scene description ${settings.value.sceneDescriptionInterval.next.label}"
    ) { it.copy(sceneDescriptionInterval = it.sceneDescriptionInterval.next) }

    fun setSceneInterval(interval: SceneDescriptionInterval) =
        update("Scene description ${interval.label}") { it.copy(sceneDescriptionInterval = interval) }

    fun voiceCommands(enabled: Boolean) = update(
        if (enabled) {
            "Voice commands on. The microphone opens only when you start listening."
        } else {
            "Voice commands off"
        }
    ) { it.copy(voiceCommandsEnabled = enabled) }

    fun volumeKeyShutter(enabled: Boolean) = update("Volume key shutter ${if (enabled) "on" else "off"}") {
        it.copy(volumeKeyShutterEnabled = enabled)
    }

    fun reviewAfterCapture(enabled: Boolean) = update("Review after capture ${if (enabled) "on" else "off"}") {
        it.copy(reviewAfterCapture = enabled)
    }

    fun orientationLock(enabled: Boolean) = update("Orientation lock ${if (enabled) "on" else "off"}") {
        it.copy(orientationLocked = enabled)
    }

    fun keepScreenOn(enabled: Boolean) = update("Keep screen on ${if (enabled) "on" else "off"}") {
        it.copy(keepScreenOn = enabled)
    }

    fun recordingAnnounceInterval(seconds: Int) = update("Recording announcement every $seconds seconds") {
        it.copy(recordingTimerAnnounceSeconds = seconds.coerceIn(10, 300))
    }

    fun shareLocationWithWatermark(enabled: Boolean) =
        update("Location in watermark ${if (enabled) "on" else "off"}") {
            it.copy(shareLocationWithWatermark = enabled)
        }

    fun requestReset() {
        _uiState.value = _uiState.value.copy(showResetConfirmation = true)
        voice.announce("Confirm resetting all settings to defaults", AnnouncementPriority.HIGH)
    }

    fun cancelReset() {
        _uiState.value = _uiState.value.copy(showResetConfirmation = false)
    }

    fun confirmReset() {
        viewModelScope.launch {
            settingsRepository.resetAll()
            _uiState.value = _uiState.value.copy(showResetConfirmation = false)
            voice.announce("All settings reset to defaults", AnnouncementPriority.HIGH)
        }
    }

    fun testVoice() {
        voice.announce(
            "Voice guidance is working. This is the current speech rate.",
            AnnouncementPriority.HIGH
        )
    }

    fun describeCapabilities() {
        val capabilities = _uiState.value.capabilities
        val text = buildString {
            append("Device capabilities. ")
            append("${capabilities.lenses.size} camera lenses detected. ")
            append("Supported modes: ")
            append(capabilities.supportedModes.joinToString(", ") { it.label })
            append(". ")
            capabilities.modeUnavailableReasons.forEach { (mode, reason) ->
                append("${mode.label} unavailable. $reason ")
            }
            append("Video qualities: ")
            append(capabilities.videoQualities.joinToString(", ") { it.qualityLabel }.ifBlank { "none" })
            append(". ")
            append("Speech recognition ${if (capabilities.supportsVoiceRecognition) "available" else "not available"}. ")
        }
        _uiState.value = _uiState.value.copy(message = text)
        voice.announce(text, AnnouncementPriority.HIGH)
    }

    private fun update(announcement: String, transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            settingsRepository.update(transform)
            voice.announce(announcement, AnnouncementPriority.NORMAL)
        }
    }

}
