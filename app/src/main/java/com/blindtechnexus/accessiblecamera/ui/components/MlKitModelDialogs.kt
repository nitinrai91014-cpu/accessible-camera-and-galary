/*
 * Accessible Camera and Gallery
 * Copyright (C) 2026 Sujan Rai, Blind Tech Nexus team.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.blindtechnexus.accessiblecamera.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.blindtechnexus.accessiblecamera.ui.theme.Dimens
import java.util.Locale

/**
 * First-run prompt shown before the on-device ML Kit models are installed.
 *
 * The wording explains that the models stay on the phone, so the dialog is safe to accept
 * even for a privacy-conscious user. Both actions are full width buttons and the dialog is
 * announced as a pane.
 */
@Composable
fun MlKitDownloadRequiredDialog(
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        onDismissRequest = onCancel,
        modifier = modifier.semantics { paneTitle = "Download Required" },
        title = {
            Text(
                text = "Download Required",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() }
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.SmallSpacing)) {
                Text(
                    text = "Face detection, scene description and barcode reading use Google ML Kit models that run entirely on this device.",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "The model bundle ships inside the app. Tapping Download copies it into the app's internal storage and verifies it. Nothing is sent anywhere and no internet connection is needed.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            AccessiblePrimaryButton(
                text = "Download",
                onClick = onDownload,
                modifier = Modifier.fillMaxWidth()
            )
        },
        dismissButton = {
            AccessibleSecondaryButton(
                text = "Cancel",
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth()
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.large
    )
}

/**
 * Determinate progress dialog for the model install. The percentage is also exposed as a
 * polite live region, so a screen reader announces progress without stealing focus from the
 * Cancel button.
 */
@Composable
fun MlKitDownloadProgressDialog(
    progress: Float,
    message: String,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clamped = progress.coerceIn(0f, 1f)
    val percent = (clamped * 100f).toInt()
    val spoken = "Installing on-device models, $percent percent. $message"
    AlertDialog(
        onDismissRequest = {
            // Progress dialogs must be cancelled explicitly so a swipe never leaves a
            // half-written model bundle behind.
        },
        modifier = modifier.semantics { paneTitle = "Installing on-device models" },
        title = {
            Text(
                text = "Installing on-device models",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() }
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) {
                        contentDescription = spoken
                        liveRegion = LiveRegionMode.Polite
                    },
                verticalArrangement = Arrangement.spacedBy(Dimens.SmallSpacing)
            ) {
                Text(
                    text = String.format(Locale.US, "%d percent", percent),
                    style = MaterialTheme.typography.titleMedium
                )
                LinearProgressIndicator(
                    progress = { clamped },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Keep this screen open. You can cancel and install later from Settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            AccessibleSecondaryButton(
                text = "Cancel",
                onClick = onCancel,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.large
    )
}
