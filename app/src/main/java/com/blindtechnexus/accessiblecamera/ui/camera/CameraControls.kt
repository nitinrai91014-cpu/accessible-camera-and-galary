/*
 * Accessible Camera and Gallery
 * Copyright (C) 2026 Sujan Rai, Blind Tech Nexus team.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.blindtechnexus.accessiblecamera.ui.camera

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.blindtechnexus.accessiblecamera.domain.model.CameraMode
import com.blindtechnexus.accessiblecamera.domain.model.CameraState
import com.blindtechnexus.accessiblecamera.domain.model.HapticCue
import com.blindtechnexus.accessiblecamera.ui.components.LocalFeedback
import com.blindtechnexus.accessiblecamera.ui.theme.AppColors
import com.blindtechnexus.accessiblecamera.ui.theme.Dimens
import com.blindtechnexus.accessiblecamera.ui.theme.LocalHighContrast

/**
 * The main capture button. 96 dp across, labelled in text below, and it changes label and
 * spoken state for photo, recording, paused and counting down.
 */
@Composable
fun ShutterButton(
    state: CameraState,
    isVideoMode: Boolean,
    countdownActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val feedback = LocalFeedback.current
    val highContrast = LocalHighContrast.current
    val scale by animateFloatAsState(
        targetValue = if (state == CameraState.CAPTURING) 0.92f else 1f,
        label = "shutterScale"
    )
    val label = when {
        countdownActive -> "Cancel timer"
        state == CameraState.RECORDING -> "Stop recording"
        state == CameraState.PAUSED -> "Stop recording"
        state == CameraState.CAPTURING -> "Taking photo"
        state == CameraState.SAVING -> "Saving"
        isVideoMode -> "Start recording"
        else -> "Take photo"
    }
    val description = buildString {
        append(label)
        append(". ")
        append(state.spoken)
    }
    val interactive = enabled && (state.canCapturePhoto || state.canStopRecording || countdownActive ||
        state == CameraState.RECORDING || state == CameraState.PAUSED)

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Surface(
            onClick = {
                if (interactive) {
                    feedback.haptic(if (isVideoMode) HapticCue.LONG_PRESS else HapticCue.TAP)
                    onClick()
                }
            },
            enabled = interactive,
            modifier = Modifier
                .size(Dimens.PrimaryAction)
                .scale(scale)
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                    contentDescription = description
                    stateDescription = state.spoken
                    if (!interactive) disabled()
                },
            shape = CircleShape,
            color = when {
                !interactive -> MaterialTheme.colorScheme.surfaceVariant
                state == CameraState.RECORDING || state == CameraState.PAUSED -> AppColors.RecordingIndicator
                isVideoMode -> AppColors.RecordingIndicator
                else -> Color.White
            },
            contentColor = if (isVideoMode || state == CameraState.RECORDING) Color.White else Color.Black,
            border = BorderStroke(
                width = if (highContrast) 5.dp else 3.dp,
                color = if (interactive) Color.White else MaterialTheme.colorScheme.outline
            )
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = when {
                        state == CameraState.RECORDING -> Icons.Filled.Stop
                        state == CameraState.PAUSED -> Icons.Filled.Stop
                        isVideoMode -> Icons.Filled.FiberManualRecord
                        else -> Icons.Outlined.PhotoCamera
                    },
                    contentDescription = null,
                    modifier = Modifier.size(48.dp)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            textAlign = TextAlign.Center
        )
    }
}

/** Pause or resume control shown only while recording. */
@Composable
fun RecordingPauseButton(
    paused: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val label = if (paused) "Resume recording" else "Pause recording"
    CameraSquareButton(
        label = label,
        icon = if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
        onClick = onClick,
        modifier = modifier,
        contentDescription = label
    )
}

/** Square camera control with visible text so no meaning relies on an icon alone. */
@Composable
fun CameraSquareButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean? = null,
    valueText: String? = null,
    contentDescription: String? = null,
    size: androidx.compose.ui.unit.Dp = Dimens.SecondaryAction
) {
    val feedback = LocalFeedback.current
    val highContrast = LocalHighContrast.current
    val stateText = when {
        !enabled -> "Not available"
        selected == true -> "On"
        selected == false -> "Off"
        valueText != null -> valueText
        else -> null
    }
    val combined = listOfNotNull(label, stateText).joinToString(". ")
    Surface(
        onClick = {
            if (enabled) {
                feedback.haptic(HapticCue.TAP)
                onClick()
            }
        },
        enabled = enabled,
        modifier = modifier
            .defaultMinSize(minWidth = size, minHeight = size)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                this.contentDescription = contentDescription ?: combined
                stateText?.let { stateDescription = it }
                if (!enabled) disabled()
            },
        shape = MaterialTheme.shapes.medium,
        color = when {
            !enabled -> AppColors.CameraChrome.copy(alpha = 0.6f)
            selected == true -> MaterialTheme.colorScheme.primary
            else -> AppColors.CameraChrome
        },
        contentColor = when {
            !enabled -> Color.White.copy(alpha = 0.6f)
            selected == true -> MaterialTheme.colorScheme.onPrimary
            else -> Color.White
        },
        border = BorderStroke(
            width = if (highContrast) 3.dp else 1.5.dp,
            color = Color.White.copy(alpha = if (enabled) 0.85f else 0.4f)
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(Dimens.IconSize))
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
            if (stateText != null) {
                Text(
                    text = stateText,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )
            }
        }
    }
}

/**
 * Mode selector. Rendered as a horizontally scrollable row of large labelled chips.
 * Unavailable modes remain visible but disabled and announce the reason when activated
 * through a long press alternative text in the advanced sheet.
 */
@Composable
fun ModeSelectorRow(
    modes: List<CameraMode>,
    selected: CameraMode,
    availableModes: Set<CameraMode>,
    onSelect: (CameraMode) -> Unit,
    modifier: Modifier = Modifier,
    onUnavailable: (CameraMode) -> Unit
) {
    val feedback = LocalFeedback.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        modes.forEach { mode ->
            val isAvailable = availableModes.contains(mode)
            val isSelected = mode == selected
            Surface(
                onClick = {
                    if (isAvailable) {
                        feedback.haptic(HapticCue.SELECTION)
                        onSelect(mode)
                    } else {
                        onUnavailable(mode)
                    }
                },
                modifier = Modifier
                    .defaultMinSize(minWidth = Dimens.MinimumTouchTarget, minHeight = Dimens.MinimumTouchTarget)
                    .semantics(mergeDescendants = true) {
                        role = Role.Tab
                        contentDescription = if (isAvailable) {
                            "${mode.label} mode"
                        } else {
                            "${mode.label} mode, not available on this device"
                        }
                        stateDescription = if (isSelected) "Selected" else "Not selected"
                    },
                shape = MaterialTheme.shapes.extraLarge,
                color = when {
                    isSelected -> MaterialTheme.colorScheme.primary
                    isAvailable -> AppColors.CameraChrome
                    else -> AppColors.CameraChrome.copy(alpha = 0.45f)
                },
                contentColor = when {
                    isSelected -> MaterialTheme.colorScheme.onPrimary
                    isAvailable -> Color.White
                    else -> Color.White.copy(alpha = 0.55f)
                }
            ) {
                Text(
                    text = mode.label,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    maxLines = 1
                )
            }
        }
    }
}

/** Recording timer displayed in a single high contrast pill. */
@Composable
fun RecordingTimerPill(
    millis: Long,
    paused: Boolean,
    modifier: Modifier = Modifier
) {
    val text = com.blindtechnexus.accessiblecamera.core.util.TimeFormat.clock(millis)
    Surface(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = if (paused) {
                "Recording paused at $text"
            } else {
                "Recording, $text elapsed"
            }
        },
        shape = MaterialTheme.shapes.extraLarge,
        color = AppColors.CameraScrim,
        contentColor = Color.White
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .background(
                        color = if (paused) Color.White else AppColors.RecordingIndicator,
                        shape = CircleShape
                    )
                    .border(2.dp, Color.White, CircleShape)
            )
            Spacer(Modifier.width(8.dp))
            Text(text = text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Zoom stepper with explicit plus and minus buttons and a reset action. */
@Composable
fun ZoomControls(
    zoomRatio: Float,
    minZoom: Float,
    maxZoom: Float,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CameraSquareButton(
            label = "Zoom out",
            icon = Icons.Filled.Remove,
            onClick = onZoomOut,
            enabled = enabled && zoomRatio > minZoom,
            size = Dimens.MinimumTouchTarget
        )
        CameraSquareButton(
            label = String.format(java.util.Locale.US, "%.1f times", zoomRatio),
            icon = Icons.Filled.Search,
            onClick = onReset,
            enabled = enabled,
            contentDescription = "Zoom level ${String.format(java.util.Locale.US, "%.1f", zoomRatio)} times. Activate to reset zoom",
            size = Dimens.MinimumTouchTarget
        )
        CameraSquareButton(
            label = "Zoom in",
            icon = Icons.Filled.Add,
            onClick = onZoomIn,
            enabled = enabled && zoomRatio < maxZoom,
            size = Dimens.MinimumTouchTarget
        )
    }
}
