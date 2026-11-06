/*
 * Accessible Camera and Gallery
 * Copyright (C) 2026 Sujan Rai, Blind Tech Nexus team.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.blindtechnexus.accessiblecamera.ui.about

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.blindtechnexus.accessiblecamera.domain.model.CameraCapabilities
import com.blindtechnexus.accessiblecamera.domain.model.VoiceCommand
import com.blindtechnexus.accessiblecamera.ui.components.AccessibleSecondaryButton
import com.blindtechnexus.accessiblecamera.ui.components.SectionHeading
import com.blindtechnexus.accessiblecamera.ui.theme.Dimens

/** About screen with credits, licence and privacy summary. */
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Dimens.ScreenPadding)
    ) {
        Text(
            text = "About",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(Modifier.height(Dimens.ItemSpacing))
        Text(
            text = "Accessible Camera and Gallery",
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = "An accessible, open-source camera and gallery app for Android.",
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(Modifier.height(Dimens.ItemSpacing))
        Text(
            text = "Version 1.0.0",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = "Developed by Sujan Rai of the Blind Tech Nexus team.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(Dimens.SectionSpacing))
        SectionHeading(text = "Privacy")
        Text(
            text = "All media and settings stay on this device. The app has no analytics, no crash reporting, and no network code for photos or gallery browsing. If you enable voice commands and your device lacks on-device speech recognition, Android may send your voice to a speech service; the app warns you before that happens.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(Dimens.SectionSpacing))
        SectionHeading(text = "Licence")
        Text(
            text = "This app is released under the Apache License, version 2.0. It uses CameraX, Media3, ML Kit, Room, DataStore, Coil, and Hilt, each under its own licence.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(Dimens.SectionSpacing))
        SectionHeading(text = "Accessibility statement")
        Text(
            text = "Every control has a label, a role and a state. Touch targets are at least 48 density independent pixels. Colour is never the only way information is conveyed. Voice guidance can be turned off independently of TalkBack, and the app avoids duplicate announcements when a screen reader is active.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(Dimens.SectionSpacing))
        AccessibleSecondaryButton(text = "Back", onClick = onBack)
        Spacer(Modifier.height(Dimens.SectionSpacing))
    }
}

/** Capability report screen explaining what this device supports and why. */
@Composable
fun CapabilitiesScreen(
    capabilities: CameraCapabilities,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Dimens.ScreenPadding)
    ) {
        Text(
            text = "Device capabilities",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(Modifier.height(Dimens.ItemSpacing))
        Text(
            text = "These results were read from the camera hardware at startup. A feature only appears as available when the device reports it.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(Dimens.ItemSpacing))
        SectionHeading(text = "Camera lenses")
        if (capabilities.lenses.isEmpty()) {
            Text(text = "No camera lenses were detected.", style = MaterialTheme.typography.bodyMedium)
        }
        capabilities.lenses.forEach { lens ->
            Text(
                text = "${lens.displayName}. ${lens.speechDescription}. Hardware level ${lens.hardwareLevel}.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
        Spacer(Modifier.height(Dimens.ItemSpacing))
        SectionHeading(text = "Available modes")
        Text(
            text = capabilities.supportedModes.joinToString(", ") { it.label },
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(Dimens.ItemSpacing))
        SectionHeading(text = "Unavailable features and reasons")
        if (capabilities.modeUnavailableReasons.isEmpty()) {
            Text(
                text = "Every mode is available on this device.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        capabilities.modeUnavailableReasons.forEach { (mode, reason) ->
            Text(
                text = "${mode.label}. $reason",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
        Spacer(Modifier.height(Dimens.ItemSpacing))
        SectionHeading(text = "Video")
        Text(
            text = buildString {
                append("Qualities: ")
                append(capabilities.videoQualities.joinToString(", ") { it.qualityLabel }.ifBlank { "none" })
                append(". Frame rates: ")
                append(capabilities.videoFrameRates.joinToString(", "))
                append(".")
            },
            style = MaterialTheme.typography.bodyMedium
        )
        SectionHeading(text = "Manual controls")
        Text(
            text = buildString {
                append("Manual exposure: ")
                append(if (capabilities.supportsManualControls) "supported" else "not supported")
                append(". RAW capture: ")
                append(if (capabilities.supportsRawCapture) "supported" else "not supported")
                append(". Optical stabilization: ")
                append(
                    if (capabilities.lenses.any { it.supportsOpticalStabilization }) {
                        "supported"
                    } else {
                        "not supported"
                    }
                )
                append(".")
            },
            style = MaterialTheme.typography.bodyMedium
        )
        SectionHeading(text = "On-device intelligence")
        Text(
            text = "Face detection: always available through ML Kit. Scene description: available. Barcode scanning: available. Speech recognition: ${if (capabilities.supportsVoiceRecognition) "available" else "not available on this device"}.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(Dimens.SectionSpacing))
        AccessibleSecondaryButton(text = "Back", onClick = onBack)
        Spacer(Modifier.height(Dimens.SectionSpacing))
    }
}

/** Voice command help and shortcut reference. */
@Composable
fun ShortcutsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Dimens.ScreenPadding)
    ) {
        Text(
            text = "Voice commands and shortcuts",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(Modifier.height(Dimens.ItemSpacing))
        Text(
            text = "Voice commands run only while you have started listening. Say stop listening, or close the app, to turn the microphone off again.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(Dimens.ItemSpacing))
        SectionHeading(text = "Supported phrases")
        VoiceCommand.entries.filter { it != VoiceCommand.UNKNOWN }.forEach { command ->
            Text(
                text = "${command.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }
        Spacer(Modifier.height(Dimens.ItemSpacing))
        SectionHeading(text = "Keyboard shortcuts")
        Text(
            text = "Space or Enter activates the focused control. The volume keys take a photo when the Photo tab is open and the volume key shutter option is on. Tab and Shift Tab move focus, and the system back gesture closes dialogs first.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(Dimens.SectionSpacing))
        AccessibleSecondaryButton(text = "Back", onClick = onBack)
        Spacer(Modifier.height(Dimens.SectionSpacing))
    }
}
