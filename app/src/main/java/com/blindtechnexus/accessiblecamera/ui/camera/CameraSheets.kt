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
import com.blindtechnexus.accessiblecamera.domain.model.MediaItem
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

/** Section header used in the advanced sheet for grouping related sliders. */
@Composable
fun SheetGroup(title: String, supporting: String? = null) {
    SectionHeading(text = title, supporting = supporting)
}
