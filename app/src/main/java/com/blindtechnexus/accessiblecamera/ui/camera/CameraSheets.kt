/*
 * Accessible Camera and Gallery
 * Copyright (C) 2026 Sujan Rai, Blind Tech Nexus team.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.blindtechnexus.accessiblecamera.ui.camera

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.blindtechnexus.accessiblecamera.domain.model.CameraMode
import com.blindtechnexus.accessiblecamera.domain.model.CameraUiState
import com.blindtechnexus.accessiblecamera.domain.model.FaceDetectionInterval
import com.blindtechnexus.accessiblecamera.domain.model.MediaItem
import com.blindtechnexus.accessiblecamera.domain.model.SceneDescriptionInterval
import com.blindtechnexus.accessiblecamera.ui.components.AccessibleBottomSheet
import com.blindtechnexus.accessiblecamera.ui.components.AccessibleChoiceRow
import com.blindtechnexus.accessiblecamera.ui.components.AccessiblePrimaryButton
import com.blindtechnexus.accessiblecamera.ui.components.AccessibleSecondaryButton
import com.blindtechnexus.accessiblecamera.ui.components.MediaThumbnail
import com.blindtechnexus.accessiblecamera.ui.components.SectionHeading
import com.blindtechnexus.accessiblecamera.ui.theme.Dimens

/**
 * Mode chooser. Shows every mode the app knows about, marks unsupported modes as
 * unavailable and speaks the reason rather than silently hiding them, so the user learns
 * why a feature is missing.
 */
@Composable
fun ModeSheet(
    state: CameraUiState,
    onSelect: (CameraMode) -> Unit,
    onDismiss: () -> Unit,
    announce: (String) -> Unit
) {
    AccessibleBottomSheet(
        title = "Capture modes",
        supporting = "Some modes need specific camera hardware. Unsupported modes explain why.",
        onDismissRequest = onDismiss,
        announceOnOpen = announce
    ) {
        CameraMode.entries.forEach { mode ->
            val available = state.capabilities.isModeAvailable(mode)
            AccessibleChoiceRow(
                label = mode.label,
                selected = state.selectedMode == mode,
                supporting = if (available) {
                    mode.description
                } else {
                    "${mode.description} ${state.capabilities.reasonFor(mode)}"
                },
                onSelect = {
                    if (available) {
                        onSelect(mode)
                    } else {
                        announce(state.capabilities.reasonFor(mode))
                    }
                }
            )
        }
        Spacer(Modifier.height(Dimens.ItemSpacing))
        AccessibleSecondaryButton(text = "Close", onClick = onDismiss)
    }
}

/** Review overlay shown after capture when review is enabled. */
@Composable
fun CaptureReviewSheet(
    item: MediaItem,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    AccessibleBottomSheet(
        title = "Saved",
        supporting = if (item.isVideo) {
            "Video saved to ${item.bucketName}"
        } else {
            "Photo saved to ${item.bucketName}"
        },
        onDismissRequest = onDismiss
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.ItemSpacing)
        ) {
            MediaThumbnail(item = item, size = 120.dp)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .semantics {
                        contentDescription = com.blindtechnexus.accessiblecamera.ui.components.MediaDescriptions.full(item)
                    }
            ) {
                Text(
                    text = com.blindtechnexus.accessiblecamera.ui.components.MediaDescriptions.short(item),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = com.blindtechnexus.accessiblecamera.core.util.Formatters.size(item.sizeBytes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        AccessiblePrimaryButton(text = "Open in viewer", onClick = onOpen)
        AccessibleSecondaryButton(text = "Keep and continue shooting", onClick = onDismiss)
        AccessibleSecondaryButton(
            text = "Delete this capture",
            onClick = onDelete,
            supportingText = "Deletes the item you just captured. A confirmation is required."
        )
    }
}

/**
 * Face detection cadence picker.
 *
 * A face keeps moving, so a fixed continuous scan is wasteful and noisy. Every option here
 * is a real interval that the analyzer honours, and Off removes the detection entirely.
 */
@Composable
fun FaceDetectionSheet(
    state: CameraUiState,
    onSelect: (FaceDetectionInterval) -> Unit,
    onInstallModels: () -> Unit,
    onDismiss: () -> Unit,
    announce: (String) -> Unit
) {
    AccessibleBottomSheet(
        title = "Face detection",
        supporting = "Uses Google ML Kit on this device to check how a face is aligned. Choose how often it runs.",
        onDismissRequest = onDismiss,
        announceOnOpen = announce
    ) {
        if (!state.mlKitModelsReady) {
            Text(
                text = "The on-device face detection model is not installed yet. Face detection stays off until you install it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            AccessiblePrimaryButton(
                text = "Install on-device models",
                onClick = onInstallModels,
                supportingText = "Opens the Download Required dialog."
            )
            Spacer(Modifier.height(Dimens.SmallSpacing))
        }
        FaceDetectionInterval.entries.forEach { interval ->
            AccessibleChoiceRow(
                label = interval.label,
                selected = state.faceDetectionInterval == interval,
                supporting = interval.description,
                onSelect = { onSelect(interval) }
            )
        }
        Spacer(Modifier.height(Dimens.ItemSpacing))
        AccessibleSecondaryButton(text = "Close", onClick = onDismiss)
    }
}

/**
 * Describe Scene cadence picker, with an explicit Describe now action.
 */
@Composable
fun SceneDescriptionSheet(
    state: CameraUiState,
    onSelect: (SceneDescriptionInterval) -> Unit,
    onDescribeNow: () -> Unit,
    onInstallModels: () -> Unit,
    onDismiss: () -> Unit,
    announce: (String) -> Unit
) {
    AccessibleBottomSheet(
        title = "Describe scene",
        supporting = "Names what the camera sees, using the on-device ML Kit image labeling model. Descriptions are spoken and shown over the preview.",
        onDismissRequest = onDismiss,
        announceOnOpen = announce
    ) {
        state.sceneDescription?.let { description ->
            Text(
                text = "Last description: $description",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(Dimens.SmallSpacing))
        }
        if (!state.mlKitModelsReady) {
            Text(
                text = "The on-device scene description model is not installed yet. Descriptions stay off until you install it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            AccessiblePrimaryButton(
                text = "Install on-device models",
                onClick = onInstallModels,
                supportingText = "Opens the Download Required dialog."
            )
            Spacer(Modifier.height(Dimens.SmallSpacing))
        } else {
            AccessiblePrimaryButton(
                text = "Describe now",
                onClick = onDescribeNow,
                supportingText = "Takes one description immediately instead of waiting for the interval."
            )
            Spacer(Modifier.height(Dimens.SmallSpacing))
        }
        SceneDescriptionInterval.entries.forEach { interval ->
            AccessibleChoiceRow(
                label = interval.label,
                selected = state.sceneDescriptionInterval == interval,
                supporting = interval.description,
                onSelect = { onSelect(interval) }
            )
        }
        Spacer(Modifier.height(Dimens.ItemSpacing))
        AccessibleSecondaryButton(text = "Close", onClick = onDismiss)
    }
}

/** Section header used in the advanced sheet for grouping related sliders. */
@Composable
fun SheetGroup(title: String, supporting: String? = null) {
    SectionHeading(text = title, supporting = supporting)
}
