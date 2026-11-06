/*
 * Accessible Camera and Gallery
 * Copyright (C) 2026 Sujan Rai, Blind Tech Nexus team.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.blindtechnexus.accessiblecamera.ui.gallery

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.blindtechnexus.accessiblecamera.core.util.Formatters
import com.blindtechnexus.accessiblecamera.core.util.TimeFormat
import com.blindtechnexus.accessiblecamera.domain.model.HapticCue
import com.blindtechnexus.accessiblecamera.domain.model.MediaItem
import com.blindtechnexus.accessiblecamera.ui.components.LocalFeedback
import com.blindtechnexus.accessiblecamera.ui.components.MediaDescriptions
import com.blindtechnexus.accessiblecamera.ui.components.MediaThumbnailFill
import com.blindtechnexus.accessiblecamera.ui.theme.Dimens
import com.blindtechnexus.accessiblecamera.ui.theme.LocalHighContrast

/**
 * One gallery grid cell.
 *
 * The cell merges its descendants so TalkBack reads one complete sentence. Selection is
 * conveyed by a check icon plus text, never by colour alone.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GalleryGridCell(
    item: MediaItem,
    index: Int,
    total: Int,
    selected: Boolean,
    onOpen: () -> Unit,
    onToggleSelection: () -> Unit,
    onLongPress: () -> Unit
) {
    val feedback = LocalFeedback.current
    val highContrast = LocalHighContrast.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .defaultMinSize(minWidth = Dimens.MinimumTouchTarget, minHeight = Dimens.MinimumTouchTarget)
            .combinedClickable(
                onClick = {
                    feedback.haptic(HapticCue.TAP)
                    onOpen()
                },
                onLongClick = {
                    feedback.haptic(HapticCue.LONG_PRESS)
                    onLongPress()
                },
                role = Role.Button
            )
            .semantics(mergeDescendants = true) {
                contentDescription = MediaDescriptions.full(item, index + 1, total)
                this.selected = selected
                stateDescription = if (selected) "Selected" else "Not selected"
                role = Role.Button
            },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = when {
            selected -> BorderStroke(4.dp, MaterialTheme.colorScheme.primary)
            highContrast -> BorderStroke(2.dp, MaterialTheme.colorScheme.outline)
            else -> null
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            MediaThumbnailFill(item = item, modifier = Modifier.fillMaxSize())
            if (item.isVideo) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = TimeFormat.clock(item.durationMillis),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
            }
            if (item.isFavorite) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = "Favorite",
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(20.dp)
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                )
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .size(28.dp)
                )
            }
        }
    }
}

/** One gallery list row with full text details, suitable for large text. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GalleryListRow(
    item: MediaItem,
    index: Int,
    total: Int,
    selected: Boolean,
    selectionMode: Boolean,
    onOpen: () -> Unit,
    onToggleSelection: () -> Unit,
    onLongPress: () -> Unit,
    onDetails: () -> Unit
) {
    val feedback = LocalFeedback.current
    val highContrast = LocalHighContrast.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = Dimens.ListRowHeight)
            .combinedClickable(
                onClick = {
                    feedback.haptic(HapticCue.TAP)
                    onOpen()
                },
                onLongClick = {
                    feedback.haptic(HapticCue.LONG_PRESS)
                    onLongPress()
                },
                role = Role.Button
            )
            .semantics(mergeDescendants = true) {
                contentDescription = MediaDescriptions.full(item, index + 1, total)
                this.selected = selected
                stateDescription = if (selected) "Selected" else "Not selected"
            },
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
        border = if (highContrast) BorderStroke(2.dp, MaterialTheme.colorScheme.outline) else null
    ) {
        Row(
            modifier = Modifier.padding(Dimens.ItemSpacing),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(Dimens.GalleryThumbnailTouchTarget)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                MediaThumbnailFill(item = item, modifier = Modifier.fillMaxSize())
                if (selected) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .size(28.dp)
                    )
                }
            }
            Spacer(Modifier.size(Dimens.ItemSpacing))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text(
                    text = item.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = Formatters.dateTime(item.dateTakenMillis),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = buildString {
                        append(item.type.label)
                        if (item.isVideo && item.durationMillis > 0) {
                            append(", ")
                            append(TimeFormat.spoken(item.durationMillis))
                        }
                        append(", ")
                        append(Formatters.size(item.sizeBytes))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!selectionMode) {
                Text(
                    text = "Details",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .defaultMinSize(minHeight = Dimens.MinimumTouchTarget)
                        .semantics { contentDescription = "Show details for ${item.displayName}" }
                        .combinedClickable(onClick = onDetails, onLongClick = null)
                        .padding(8.dp)
                )
            }
        }
    }
}
