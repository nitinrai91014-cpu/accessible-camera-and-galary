/*
 * Accessible Camera and Gallery
 * Copyright (C) 2026 Sujan Rai, Blind Tech Nexus team.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.blindtechnexus.accessiblecamera.ui.welcome

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.blindtechnexus.accessiblecamera.ui.components.AccessiblePrimaryButton
import com.blindtechnexus.accessiblecamera.ui.components.SectionHeading
import com.blindtechnexus.accessiblecamera.ui.theme.Dimens

/**
 * First run screen. Announces its title and purpose on open, then explains every
 * permission in plain language before anything is requested.
 */
@Composable
fun WelcomeScreen(
    onStart: () -> Unit,
    announce: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(Unit) {
        announce(
            "Accessible Camera and Gallery. An accessible, open-source camera and gallery app. " +
                "Use the Start button at the bottom of the screen to continue."
        )
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(Dimens.ScreenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(Modifier.height(Dimens.SectionSpacing))
        Text(
            text = "Accessible Camera and Gallery",
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(Modifier.height(Dimens.SmallSpacing))
        Text(
            text = "An accessible, open-source camera and gallery app for Android.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(Dimens.SectionSpacing))
        Text(
            text = "Take photos, record video, and manage your media with full screen reader support and voice guidance. Every control is labelled and every action is confirmed by sound, speech, or vibration.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(Dimens.SectionSpacing))

        SectionHeading(
            text = "What this app uses",
            supporting = "Each permission is requested only when it is needed."
        )
        PermissionLine(
            title = "Camera",
            detail = "Shows the viewfinder and takes photos or records video."
        )
        PermissionLine(
            title = "Microphone",
            detail = "Only while recording video with sound turned on, or while you start voice commands."
        )
        PermissionLine(
            title = "Photos and videos",
            detail = "Lets the gallery show the media already on this device."
        )
        PermissionLine(
            title = "Location",
            detail = "Off by default. Used only if you turn on location tagging in Settings."
        )

        Spacer(Modifier.height(Dimens.SectionSpacing))
        Text(
            text = "Everything stays on your device. There is no analytics and no network access without your consent.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(Dimens.SectionSpacing))
        AccessiblePrimaryButton(
            text = "Start",
            onClick = onStart,
            supportingText = "Opens the Photo tab. You can change every option later in Settings."
        )
        Spacer(Modifier.height(Dimens.ItemSpacing))
        Text(
            text = "Developed by Sujan Rai of the Blind Tech Nexus team.",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(Dimens.SectionSpacing))
    }
}

@Composable
private fun PermissionLine(title: String, detail: String) {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .semantics(mergeDescendants = true) { }
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall
        )
        Text(
            text = detail,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
