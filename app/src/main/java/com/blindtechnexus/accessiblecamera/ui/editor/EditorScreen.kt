/*
 * Accessible Camera and Gallery
 * Copyright (C) 2026 Sujan Rai, Blind Tech Nexus team.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.blindtechnexus.accessiblecamera.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blindtechnexus.accessiblecamera.domain.model.MediaItem
import com.blindtechnexus.accessiblecamera.ui.components.AccessibleBanner
import com.blindtechnexus.accessiblecamera.ui.components.AccessibleConfirmDialog
import com.blindtechnexus.accessiblecamera.ui.components.AccessibleCycleRow
import com.blindtechnexus.accessiblecamera.ui.components.AccessiblePrimaryButton
import com.blindtechnexus.accessiblecamera.ui.components.AccessibleSecondaryButton
import com.blindtechnexus.accessiblecamera.ui.components.AccessibleSliderRow
import com.blindtechnexus.accessiblecamera.ui.components.AccessibleSwitchRow
import com.blindtechnexus.accessiblecamera.ui.components.SectionHeading
import com.blindtechnexus.accessiblecamera.ui.theme.Dimens

/**
 * Editor screen.
 *
 * Adjustments are described with numbers and words so the result can be understood without
 * looking. Drawing works with touch for sighted users, and the "add circle" action gives a
 * deterministic annotation for users who cannot see the canvas.
 */
@Composable
fun EditorScreen(
    mediaId: Long,
    onBack: () -> Unit,
    onSaved: (android.net.Uri) -> Unit,
    announce: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EditorViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var textInput by remember { mutableStateOf("") }
    val liveStrokes = remember { mutableStateListOf<Stroke>() }

    LaunchedEffect(mediaId) { viewModel.load(mediaId) }
    LaunchedEffect(state.error) {
        state.error?.let { announce(it) }
    }
    LaunchedEffect(state.saveComplete) {
        if (state.saveComplete && state.savedUri != null) {
            onSaved(state.savedUri!!)
        }
    }
    LaunchedEffect(state.strokes.size) {
        liveStrokes.clear()
        liveStrokes.addAll(state.strokes)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Dimens.ScreenPadding)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SmallSpacing)
        ) {
            AccessibleSecondaryButton(
                text = "Back",
                onClick = {
                    if (state.hasEdits) viewModel.requestDiscard() else onBack()
                },
                modifier = Modifier.weight(1f)
            )
            AccessiblePrimaryButton(
                text = if (state.isSaving) "Saving" else "Save copy",
                onClick = viewModel::saveCopy,
                enabled = !state.isSaving && !state.isLoading,
                modifier = Modifier.weight(1f)
            )
        }

        Text(
            text = "Edit ${state.item?.displayName ?: "item"}",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier
                .padding(vertical = Dimens.SmallSpacing)
                .semantics { heading() }
        )

        if (state.isLoading) {
            Text(text = "Loading editor", style = MaterialTheme.typography.bodyLarge)
            return@Column
        }

        state.error?.let { error ->
            AccessibleBanner(text = error, isError = true)
            return@Column
        }

        if (state.isVideo) {
            VideoEditSection(state = state, viewModel = viewModel)
            return@Column
        }

        val bitmap = state.bitmap
        if (bitmap == null) {
            AccessibleBanner(text = "No preview available for this photo.", isError = true)
            return@Column
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat().coerceAtLeast(1f))
                .background(Color.Black)
                .pointerInput(state.drawModeActive) {
                    if (!state.drawModeActive) return@pointerInput
                    detectDragGestures(
                        onDragStart = { offset ->
                            liveStrokes.add(
                                Stroke(
                                    points = listOf(
                                        offset.x / size.width to offset.y / size.height
                                    ),
                                    width = 16f
                                )
                            )
                        },
                        onDrag = { change, _ ->
                            val last = liveStrokes.lastOrNull() ?: return@detectDragGestures
                            val updated = last.copy(
                                points = last.points + (
                                    change.position.x / size.width to change.position.y / size.height
                                    )
                            )
                            liveStrokes[liveStrokes.lastIndex] = updated
                        },
                        onDragEnd = {
                            val last = liveStrokes.lastOrNull() ?: return@detectDragGestures
                            viewModel.addFreehandStroke(last)
                        }
                    )
                }
                .semantics {
                    contentDescription = buildString {
                        append("Photo preview. ${bitmap.width} by ${bitmap.height} pixels. ")
                        append("Filter ${state.filter.spoken}. ")
                        if (state.drawModeActive) append("Drawing mode active. ")
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawImage(bitmap.asImageBitmap())
                liveStrokes.forEach { stroke ->
                    if (stroke.points.size > 1) {
                        val path = androidx.compose.ui.graphics.Path().apply {
                            val first = stroke.points.first()
                            moveTo(first.first * size.width, first.second * size.height)
                            stroke.points.drop(1).forEach { point ->
                                lineTo(point.first * size.width, point.second * size.height)
                            }
                        }
                        drawPath(
                            path = path,
                            color = Color.Yellow,
                            style = DrawStroke(width = 6f)
                        )
                    }
                }
                state.textOverlays.forEach { overlay ->
                    // Text overlays are rendered into the saved bitmap; the preview shows a
                    // marker so the user knows where text will land.
                    val x = (overlay.x / bitmap.width) * size.width
                    val y = (overlay.y / bitmap.height) * size.height
                    drawCircle(color = Color.White, radius = 8f, center = Offset(x, y))
                }
            }
        }
        Text(
            text = if (state.drawModeActive) {
                "Drawing mode. Drag on the preview, or use Add circle for a precise shape."
            } else {
                "Preview. Turn on drawing mode to draw."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        SectionHeading(text = "Adjustments")
        AccessibleSliderRow(
            label = "Brightness",
            value = state.brightness,
            valueRange = -1f..1f,
            valueLabel = { percent(it) },
            onValueChange = viewModel::setBrightness
        )
        AccessibleSliderRow(
            label = "Contrast",
            value = state.contrast,
            valueRange = -1f..1f,
            valueLabel = { percent(it) },
            onValueChange = viewModel::setContrast
        )
        AccessibleSliderRow(
            label = "Saturation",
            value = state.saturation,
            valueRange = -1f..1f,
            valueLabel = { percent(it) },
            onValueChange = viewModel::setSaturation
        )
        AccessibleSliderRow(
            label = "Exposure",
            value = state.exposure,
            valueRange = -1f..1f,
            valueLabel = { percent(it) },
            onValueChange = viewModel::setExposure
        )

        SectionHeading(text = "Transform")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.SmallSpacing)
        ) {
            AccessibleSecondaryButton(
                text = "Rotate 90 degrees",
                onClick = viewModel::rotate,
                modifier = Modifier.weight(1f)
            )
            AccessibleSecondaryButton(
                text = "Flip horizontal",
                onClick = viewModel::flipHorizontal,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.SmallSpacing)
        ) {
            AccessibleSecondaryButton(
                text = "Flip vertical",
                onClick = viewModel::flipVertical,
                modifier = Modifier.weight(1f)
            )
            AccessibleSecondaryButton(
                text = "Undo",
                onClick = viewModel::undo,
                modifier = Modifier.weight(1f)
            )
        }

        SectionHeading(text = "Crop")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.SmallSpacing)
        ) {
            AccessibleSecondaryButton(
                text = "Square",
                onClick = { viewModel.cropPreset("Square") },
                modifier = Modifier.weight(1f)
            )
            AccessibleSecondaryButton(
                text = "Centre half",
                onClick = { viewModel.cropPreset("Centre half") },
                modifier = Modifier.weight(1f)
            )
            AccessibleSecondaryButton(
                text = "Reset",
                onClick = { viewModel.cropPreset("Reset") },
                modifier = Modifier.weight(1f)
            )
        }
        Text(
            text = "Crop from ${(state.cropLeft * 100).toInt()} percent to ${(state.cropRight * 100).toInt()} percent.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        SectionHeading(text = "Filter")
        AccessibleCycleRow(
            label = "Filter",
            value = state.filter.label,
            onActivate = viewModel::cycleFilter,
            description = "Currently ${state.filter.spoken}."
        )

        SectionHeading(text = "Annotate")
        AccessibleSwitchRow(
            label = "Drawing mode",
            checked = state.drawModeActive,
            onCheckedChange = { viewModel.toggleDrawMode() },
            description = "Drag on the preview. Also available as Add circle below."
        )
        AccessibleSecondaryButton(
            text = "Add circle annotation",
            onClick = viewModel::addAccessibleStroke,
            supportingText = "Adds a circle in the centre of the photo without needing to see it."
        )
        OutlinedTextField(
            value = textInput,
            onValueChange = { textInput = it },
            label = { Text("Text to add") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        AccessiblePrimaryButton(
            text = "Add text overlay",
            onClick = {
                viewModel.addText(textInput)
                textInput = ""
            }
        )
        AccessibleSecondaryButton(
            text = "Clear all annotations",
            onClick = viewModel::clearAnnotations
        )

        Spacer(Modifier.height(Dimens.ItemSpacing))
        AccessibleSecondaryButton(
            text = "Describe current edits aloud",
            onClick = viewModel::describeEdits
        )
        Spacer(Modifier.height(Dimens.SectionSpacing))
    }

    if (state.showDiscardConfirmation) {
        AccessibleConfirmDialog(
            title = "Discard edits?",
            message = "Your adjustments have not been saved as a copy. The original file is unchanged either way.",
            confirmLabel = "Discard and go back",
            dismissLabel = "Keep editing",
            onConfirm = onBack,
            onDismiss = viewModel::cancelDiscard,
            destructive = true
        )
    }
}

@Composable
private fun VideoEditSection(state: EditorUiState, viewModel: EditorViewModel) {
    val duration = state.item?.durationMillis ?: 0L
    SectionHeading(
        text = "Video editing",
        supporting = "Trimming and muting export a new copy. The original video is never modified."
    )
    Text(
        text = "Duration ${com.blindtechnexus.accessiblecamera.core.util.TimeFormat.spoken(duration)}",
        style = MaterialTheme.typography.bodyLarge
    )
    AccessibleSliderRow(
        label = "Trim start",
        value = state.videoTrimStartMs.toFloat(),
        valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
        valueLabel = { com.blindtechnexus.accessiblecamera.core.util.TimeFormat.clock(it.toLong()) },
        onValueChange = { viewModel.setTrim(it.toLong(), state.videoTrimEndMs) }
    )
    AccessibleSliderRow(
        label = "Trim end",
        value = state.videoTrimEndMs.toFloat(),
        valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
        valueLabel = { com.blindtechnexus.accessiblecamera.core.util.TimeFormat.clock(it.toLong()) },
        onValueChange = { viewModel.setTrim(state.videoTrimStartMs, it.toLong()) }
    )
    AccessibleSwitchRow(
        label = "Mute video",
        checked = state.videoMuted,
        onCheckedChange = { viewModel.toggleVideoMute() },
        description = "Removes the audio track from the exported copy."
    )
    AccessibleSecondaryButton(text = "Reset trim", onClick = { viewModel.setTrim(0L, duration) })
    if (state.isSaving) {
        Text(
            text = "Exporting video. Please keep the app open.",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

private fun percent(value: Float): String =
    String.format(java.util.Locale.US, "%d percent", (value * 100).toInt())
