/*
 * Accessible Camera and Gallery
 * Copyright (C) 2026 Sujan Rai, Blind Tech Nexus team.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.blindtechnexus.accessiblecamera.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blindtechnexus.accessiblecamera.data.mlkit.MlKitModelProvisioner
import com.blindtechnexus.accessiblecamera.data.mlkit.MlKitModelState
import com.blindtechnexus.accessiblecamera.data.voice.VoiceRepositoryImpl
import com.blindtechnexus.accessiblecamera.domain.model.AnnouncementPriority
import com.blindtechnexus.accessiblecamera.domain.model.AppSettings
import com.blindtechnexus.accessiblecamera.domain.model.AudioCue
import com.blindtechnexus.accessiblecamera.domain.model.CameraCapabilities
import com.blindtechnexus.accessiblecamera.domain.model.HapticCue
import com.blindtechnexus.accessiblecamera.domain.model.MediaItem
import com.blindtechnexus.accessiblecamera.domain.repository.CapabilityRepository
import com.blindtechnexus.accessiblecamera.domain.repository.MediaRepository
import com.blindtechnexus.accessiblecamera.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Holds state that outlives a single screen: preferences, detected capabilities and the
 * item currently open in the viewer. Also forwards speech, tone and haptic requests so
 * screens never own an engine.
 */
@HiltViewModel
class AppStateViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val voiceRepository: VoiceRepositoryImpl,
    private val mediaRepository: MediaRepository,
    capabilityRepository: CapabilityRepository,
    mlKitModelProvisioner: MlKitModelProvisioner
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = AppSettings.Defaults
        )

    val capabilities: StateFlow<CameraCapabilities> = capabilityRepository.capabilities
        .stateIn(viewModelScope, SharingStarted.Eagerly, CameraCapabilities.Empty)

    val voiceReady: StateFlow<Boolean> = voiceRepository.isEngineReady
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** On-device ML Kit model installation state, used by the About feature guide. */
    val mlKitState: StateFlow<MlKitModelState> = mlKitModelProvisioner.state

    private val _selectedItem = MutableStateFlow<MediaItem?>(null)
    val selectedItem: StateFlow<MediaItem?> = _selectedItem.asStateFlow()

    private var lastAnnouncement: String? = null
    private var lastAnnouncementAt = 0L

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { current ->
                voiceRepository.configure(current.speechRate, current.speechPitch)
                voiceRepository.setFeatureToggles(
                    haptics = current.hapticsEnabled,
                    audioCues = current.audioCuesEnabled,
                    voiceGuidance = current.voiceGuidanceEnabled
                )
            }
        }
    }

    // region Speech and feedback

    fun announce(text: String, priority: AnnouncementPriority = AnnouncementPriority.NORMAL) {
        if (text.isBlank()) return
        val now = System.currentTimeMillis()
        if (text == lastAnnouncement && now - lastAnnouncementAt < 600L) return
        lastAnnouncement = text
        lastAnnouncementAt = now
        voiceRepository.announce(text, priority)
    }

    /** Screen level announcement used when a destination opens. */
    fun announceScreen(text: String) = announce(text, AnnouncementPriority.NORMAL)

    fun announceValue(key: String, text: String) {
        voiceRepository.announceValue(key, text)
    }

    fun vibrate(cue: HapticCue) = voiceRepository.vibrate(cue)

    fun tone(cue: AudioCue) = voiceRepository.playCue(cue)

    // endregion

    // region Selection

    fun selectItem(item: MediaItem) {
        _selectedItem.value = item
    }

    fun loadItem(mediaId: Long) {
        viewModelScope.launch {
            val item = mediaRepository.getItem(mediaId)
            if (item != null) _selectedItem.value = item
        }
    }

    /**
     * Item for the viewer route. The viewer screen calls this and falls back to its own
     * loading state while the item is fetched off the main thread.
     */
    fun findItem(mediaId: Long): MediaItem? = _selectedItem.value?.takeIf { it.id == mediaId }

    /** Suspending lookup used by the viewer when an item was not pre-selected. */
    suspend fun resolveItem(mediaId: Long): MediaItem? =
        _selectedItem.value?.takeIf { it.id == mediaId }
            ?: mediaRepository.getItem(mediaId)?.also { _selectedItem.value = it }

    // endregion

    // region Onboarding and permissions

    fun completeOnboarding() {
        viewModelScope.launch {
            settingsRepository.setOnboardingComplete(true)
            voiceRepository.announce(
                "Welcome to Accessible Camera and Gallery. Use the tabs at the bottom of the screen to move between Photo, Video, Gallery, and Settings.",
                AnnouncementPriority.HIGH
            )
        }
    }

    fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { settingsRepository.update(transform) }
    }

    fun announceCameraPermissionResult(granted: Boolean, permanentlyDenied: Boolean) {
        when {
            granted -> announce("Camera permission granted", AnnouncementPriority.HIGH)
            permanentlyDenied -> announce(
                "Camera permission is turned off for this app. Open app settings and allow camera access.",
                AnnouncementPriority.CRITICAL
            )
            else -> announce(
                "Camera permission denied. The viewfinder cannot start without it.",
                AnnouncementPriority.CRITICAL
            )
        }
    }

    /** Called after the gallery permission request finishes. */
    fun onGalleryPermissionResult() {
        announce("Gallery permissions updated", AnnouncementPriority.NORMAL)
    }

    // endregion
}
