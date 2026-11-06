/*
 * Accessible Camera and Gallery
 * Copyright (C) 2026 Sujan Rai, Blind Tech Nexus team.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.blindtechnexus.accessiblecamera.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blindtechnexus.accessiblecamera.domain.model.FontScaleOption
import com.blindtechnexus.accessiblecamera.domain.model.SaveLocation
import com.blindtechnexus.accessiblecamera.domain.model.SceneDescriptionInterval
import com.blindtechnexus.accessiblecamera.domain.model.ThemeMode
import com.blindtechnexus.accessiblecamera.domain.model.TimerDuration
import com.blindtechnexus.accessiblecamera.domain.model.Verbosity
import com.blindtechnexus.accessiblecamera.ui.components.AccessibleBanner
import com.blindtechnexus.accessiblecamera.ui.components.AccessibleChoiceRow
import com.blindtechnexus.accessiblecamera.ui.components.AccessibleConfirmDialog
import com.blindtechnexus.accessiblecamera.ui.components.AccessibleCycleRow
import com.blindtechnexus.accessiblecamera.ui.components.AccessiblePrimaryButton
import com.blindtechnexus.accessiblecamera.ui.components.AccessibleSecondaryButton
import com.blindtechnexus.accessiblecamera.ui.components.AccessibleSliderRow
import com.blindtechnexus.accessiblecamera.ui.components.AccessibleSwitchRow
import com.blindtechnexus.accessiblecamera.ui.components.SectionHeading
import com.blindtechnexus.accessiblecamera.ui.theme.Dimens

/**
 * Settings tab.
 *
 * Grouped into short, clearly headed sections. Each row is a single accessible control that
 * speaks its new value when changed. Destructive actions require confirmation.
 */
@Composable
fun SettingsScreen(
    onOpenCapabilities: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenShortcuts: () -> Unit,
    onRequestLocationPermission: () -> Unit,
    onRequestAudioPermission: () -> Unit,
    announce: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var watermarkText by remember(settings.watermarkText) { mutableStateOf(settings.watermarkText) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Dimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.SmallSpacing)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            text = "Accessible Camera and Gallery. An accessible, open-source camera and gallery app for Android.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (!state.speechAvailable) {
            AccessibleBanner(
                text = "No text to speech engine was found. Voice guidance will stay silent until a speech engine is installed.",
                isError = true
            )
        }

        SectionHeading(text = "Voice guidance", supporting = "Independent of TalkBack. Turn either one off at any time.")
        AccessibleSwitchRow(
            label = "Voice guidance",
            checked = settings.voiceGuidanceEnabled,
            onCheckedChange = viewModel::voiceGuidanceEnabled,
            description = "Speaks state changes, values, errors and confirmations."
        )
        AccessibleSliderRow(
            label = "Speech rate",
            value = settings.speechRate,
            valueRange = 0.5f..2.5f,
            valueLabel = { String.format(java.util.Locale.US, "%.1f times", it) },
            enabled = settings.voiceGuidanceEnabled,
            description = "How fast the voice speaks.",
            onValueChange = viewModel::speechRate,
            onChangeFinished = viewModel::speechRate
        )
        AccessibleSliderRow(
            label = "Speech pitch",
            value = settings.speechPitch,
            valueRange = 0.5f..2f,
            valueLabel = { String.format(java.util.Locale.US, "%.1f", it) },
            enabled = settings.voiceGuidanceEnabled,
            onValueChange = viewModel::speechPitch,
            onChangeFinished = viewModel::speechPitch
        )
        AccessibleCycleRow(
            label = "Verbosity",
            value = settings.verbosity.label,
            onActivate = viewModel::cycleVerbosity,
            description = when (settings.verbosity) {
                Verbosity.MINIMAL -> "Speaks ${settings.verbosity.spoken}."
                Verbosity.STANDARD -> "Speaks ${settings.verbosity.spoken}."
                Verbosity.DETAILED -> "Speaks ${settings.verbosity.spoken}."
            }
        )
        AccessibleSecondaryButton(text = "Test voice guidance", onClick = viewModel::testVoice)

        SectionHeading(text = "Feedback")
        AccessibleSwitchRow(
            label = "Haptics",
            checked = settings.hapticsEnabled,
            onCheckedChange = viewModel::haptics,
            description = "Vibrations for capture, focus and errors."
        )
        AccessibleSwitchRow(
            label = "Audio cues",
            checked = settings.audioCuesEnabled,
            onCheckedChange = viewModel::audioCues,
            description = "Short tones for capture and recording."
        )

        SectionHeading(text = "Appearance")
        AccessibleCycleRow(
            label = "Theme and contrast",
            value = settings.themeMode.label,
            onActivate = viewModel::cycleTheme,
            description = "High contrast themes exceed WCAG AA contrast."
        )
        AccessibleChoiceRow(
            label = "High contrast dark",
            selected = settings.themeMode == ThemeMode.HIGH_CONTRAST_DARK,
            supporting = "Pure black background with yellow accents.",
            onSelect = { viewModel.setTheme(ThemeMode.HIGH_CONTRAST_DARK) }
        )
        AccessibleChoiceRow(
            label = "High contrast light",
            selected = settings.themeMode == ThemeMode.HIGH_CONTRAST_LIGHT,
            supporting = "Pure white background with black text.",
            onSelect = { viewModel.setTheme(ThemeMode.HIGH_CONTRAST_LIGHT) }
        )
        AccessibleCycleRow(
            label = "Text size",
            value = settings.fontScale.label,
            onActivate = viewModel::cycleFontScale,
            description = "Adds to the system font scale rather than replacing it."
        )
        if (settings.fontScale == FontScaleOption.SYSTEM) {
            Text(
                text = "Text size follows the system setting. Change it in Android accessibility settings for the largest sizes.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SectionHeading(text = "Gallery")
        AccessibleCycleRow(
            label = "Default layout",
            value = settings.galleryLayout.label,
            onActivate = viewModel::cycleGalleryLayout
        )

        SectionHeading(text = "Camera")
        AccessibleSwitchRow(
            label = "Grid lines",
            checked = settings.showGridLines,
            onCheckedChange = viewModel::gridLines
        )
        AccessibleSwitchRow(
            label = "Level indicator",
            checked = settings.showLevelIndicator,
            onCheckedChange = viewModel::levelIndicator
        )
        AccessibleSwitchRow(
            label = "Mirror front camera preview",
            checked = settings.mirrorFrontCamera,
            onCheckedChange = viewModel::mirrorFront,
            description = "Mirrors the preview only. Saved photos are not mirrored."
        )
        AccessibleSwitchRow(
            label = "Face alignment guidance",
            checked = settings.faceGuidanceEnabled,
            onCheckedChange = viewModel::faceGuidance,
            description = "Uses on-device face detection to help position a face."
        )
        AccessibleCycleRow(
            label = "Scene description interval",
            value = settings.sceneDescriptionInterval.label,
            onActivate = viewModel::cycleSceneInterval,
            description = "Describes what the camera sees, on device, at this interval."
        )
        AccessibleSwitchRow(
            label = "Review after capture",
            checked = settings.reviewAfterCapture,
            onCheckedChange = viewModel::reviewAfterCapture
        )
        AccessibleSwitchRow(
            label = "Volume key shutter",
            checked = settings.volumeKeyShutterEnabled,
            onCheckedChange = viewModel::volumeKeyShutter
        )
        AccessibleSwitchRow(
            label = "Lock orientation",
            checked = settings.orientationLocked,
            onCheckedChange = viewModel::orientationLock,
            description = "Prevents the interface from rotating."
        )
        AccessibleSwitchRow(
            label = "Keep screen on",
            checked = settings.keepScreenOn,
            onCheckedChange = viewModel::keepScreenOn,
            description = "The screen stays awake while the camera is open."
        )
        AccessibleCycleRow(
            label = "Default timer",
            value = settings.timerDefault.label,
            onActivate = viewModel::cycleTimer
        )
        AccessibleCycleRow(
            label = "Default video resolution",
            value = settings.defaultVideoQuality,
            onActivate = viewModel::cycleDefaultQuality,
            enabled = state.capabilities.videoQualities.isNotEmpty(),
            unavailableReason = "Video recording is not available on this device."
        )
        Text(
            text = "Recording time is announced every ${settings.recordingTimerAnnounceSeconds} seconds.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.SmallSpacing)
        ) {
            AccessibleSecondaryButton(
                text = "Announce every 10 seconds",
                onClick = { viewModel.recordingAnnounceInterval(10) },
                modifier = Modifier.weight(1f)
            )
            AccessibleSecondaryButton(
                text = "Every 30 seconds",
                onClick = { viewModel.recordingAnnounceInterval(30) },
                modifier = Modifier.weight(1f)
            )
            AccessibleSecondaryButton(
                text = "Every 60 seconds",
                onClick = { viewModel.recordingAnnounceInterval(60) },
                modifier = Modifier.weight(1f)
            )
        }

        SectionHeading(text = "Saving")
        AccessibleCycleRow(
            label = "Save location",
            value = settings.saveLocation.label,
            onActivate = viewModel::cycleSaveLocation
        )
        SaveLocation.entries.forEach { location ->
            AccessibleChoiceRow(
                label = location.label,
                selected = settings.saveLocation == location,
                supporting = "Files are placed in ${location.relativePath.ifBlank { "standard Pictures and Movies folders" }}",
                onSelect = { viewModel.setSaveLocation(location) }
            )
        }
        AccessibleSwitchRow(
            label = "Location tagging",
            checked = settings.geotaggingEnabled,
            onCheckedChange = { enabled ->
                if (enabled) {
                    onRequestLocationPermission()
                }
                viewModel.geotagging(enabled)
            },
            description = "Off by default. Stores GPS coordinates in each photo."
        )
        AccessibleSwitchRow(
            label = "Watermark",
            checked = settings.watermarkEnabled,
            onCheckedChange = viewModel::watermark,
            description = "Adds a high contrast text label to saved photos."
        )
        OutlinedTextField(
            value = watermarkText,
            onValueChange = {
                watermarkText = it
                viewModel.watermarkText(it)
            },
            label = { Text("Watermark text") },
            singleLine = true,
            enabled = settings.watermarkEnabled,
            modifier = Modifier.fillMaxWidth()
        )
        AccessibleSwitchRow(
            label = "Include location in watermark",
            checked = settings.shareLocationWithWatermark,
            onCheckedChange = viewModel::shareLocationWithWatermark,
            enabled = settings.watermarkEnabled && settings.geotaggingEnabled,
            unavailableReason = "Requires both watermark and location tagging."
        )

        SectionHeading(text = "Voice commands")
        AccessibleSwitchRow(
            label = "Enable voice commands",
            checked = settings.voiceCommandsEnabled,
            onCheckedChange = { enabled ->
                if (enabled && !OnDeviceSpeechRecognizerAvailable(state)) {
                    announce("Speech recognition is not available on this device.")
                } else {
                    viewModel.voiceCommands(enabled)
                }
            },
            enabled = state.speechAvailable,
            unavailableReason = "Speech recognition is not available on this device.",
            description = "The microphone opens only when you start listening, never in the background."
        )
        if (state.speechAvailable) {
            Text(
                text = if (state.onDeviceRecognition) {
                    "On-device recognition is supported. Audio can stay on the phone."
                } else {
                    "This device does not report on-device recognition. The app requests offline recognition when possible."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (settings.voiceCommandsEnabled) {
            AccessibleSecondaryButton(
                text = "Allow microphone for voice commands",
                onClick = onRequestAudioPermission,
                supportingText = "Speech recognition needs microphone access."
            )
        }

        SectionHeading(text = "Device and privacy")
        AccessibleSecondaryButton(
            text = "View device capabilities",
            onClick = onOpenCapabilities,
            supportingText = "See which camera features this device supports and why others are unavailable."
        )
        AccessibleSecondaryButton(text = "Voice command help", onClick = onOpenShortcuts)
        AccessibleSecondaryButton(text = "About this app", onClick = onOpenAbout)
        AccessibleSecondaryButton(
            text = "Describe device capabilities aloud",
            onClick = viewModel::describeCapabilities
        )
        Text(
            text = "This app stores everything on your device. There is no analytics and no network use. The only optional network use is the system speech recogniser if you enable voice commands and your device lacks on-device recognition.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        SectionHeading(text = "Reset")
        AccessibleSecondaryButton(
            text = "Reset all settings",
            onClick = viewModel::requestReset,
            supportingText = "Restores every option to its default. Your photos are never deleted."
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = Dimens.SectionSpacing))
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Accessible Camera and Gallery, version 1.0.0",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Developed by Sujan Rai of the Blind Tech Nexus team.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Open source under the Apache License 2.0.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(Dimens.SectionSpacing))
    }

    if (state.showResetConfirmation) {
        AccessibleConfirmDialog(
            title = "Reset all settings?",
            message = "Every preference returns to its default value. Photos and videos are not affected.",
            confirmLabel = "Reset settings",
            onConfirm = viewModel::confirmReset,
            onDismiss = viewModel::cancelReset
        )
    }
}

private fun OnDeviceSpeechRecognizerAvailable(state: SettingsUiState): Boolean = state.speechAvailable
