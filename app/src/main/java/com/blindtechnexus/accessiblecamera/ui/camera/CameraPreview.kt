/*
 * Accessible Camera and Gallery
 * Copyright (C) 2026 Sujan Rai, Blind Tech Nexus team.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.blindtechnexus.accessiblecamera.ui.camera

import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.blindtechnexus.accessiblecamera.domain.model.CameraUiState
import com.blindtechnexus.accessiblecamera.domain.model.FaceGuidance
import com.blindtechnexus.accessiblecamera.ui.theme.AppColors
import com.blindtechnexus.accessiblecamera.ui.theme.Dimens

/**
 * The camera viewfinder.
 *
 * The Compose layer exposes a single accessibility node describing the current mode,
 * state, zoom, flash and face guidance. Tapping the preview focuses at that point and
 * announces the action, so tap to focus is discoverable without vision.
 */
@Composable
fun CameraPreview(
    state: CameraUiState,
    onPreviewReady: (PreviewView) -> Unit,
    onTapToFocus: (Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentOnReady by rememberUpdatedState(onPreviewReady)
    val currentOnTap by rememberUpdatedState(onTapToFocus)

    Box(
        modifier = modifier
            .fillMaxSize()
            .semantics(mergeDescendants = true) {
                contentDescription = state.previewDescription
            }
    ) {
        AndroidView(
            factory = { context ->
                PreviewView(context).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    contentDescription = "Camera viewfinder. Double tap to focus at the centre."
                    currentOnReady(this)
                }
            },
            update = { view -> currentOnReady(view) },
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val width = size.width.toFloat().coerceAtLeast(1f)
                        val height = size.height.toFloat().coerceAtLeast(1f)
                        currentOnTap(
                            (offset.x / width).coerceIn(0f, 1f),
                            (offset.y / height).coerceIn(0f, 1f)
                        )
                    }
                }
        )

        if (state.gridVisible) {
            GridOverlay()
        }
        if (state.levelVisible) {
            LevelOverlay(degrees = state.levelDegrees)
        }
        FaceGuidanceBadge(
            guidance = state.faceGuidance,
            enabled = state.faceGuidanceEnabled && state.mlKitModelsReady,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = Dimens.SectionSpacing)
        )
        // The newest scene description stays visible so a sighted helper can read it too.
        state.sceneDescription?.let { description ->
            StatusPill(
                text = "Scene: $description",
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = Dimens.SectionSpacing + 44.dp)
            )
        }
        state.lastBarcode?.let { barcode ->
            StatusPill(
                text = barcode,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = Dimens.SectionSpacing + 88.dp)
            )
        }
        if (state.recordingPaused) {
            StatusPill(
                text = "Recording paused",
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = Dimens.ScreenPadding)
            )
        }
        if (state.countdownSecondsRemaining != null) {
            StatusPill(
                text = "Timer ${state.countdownSecondsRemaining}",
                modifier = Modifier
                    .align(Alignment.Center)
            )
        }
    }
}

/** Thin thirds grid. Solid high contrast lines rather than colour coding. */
@Composable
private fun GridOverlay() {
    val strokePx = with(LocalDensity.current) { 2.dp.toPx() }
    Canvas(modifier = Modifier.fillMaxSize()) {
        val color = Color.White.copy(alpha = 0.8f)
        val thirdWidth = size.width / 3f
        val thirdHeight = size.height / 3f
        for (i in 1..2) {
            drawLine(
                color = color,
                start = Offset(thirdWidth * i, 0f),
                end = Offset(thirdWidth * i, size.height),
                strokeWidth = strokePx
            )
            drawLine(
                color = color,
                start = Offset(0f, thirdHeight * i),
                end = Offset(size.width, thirdHeight * i),
                strokeWidth = strokePx
            )
        }
    }
}

/** Horizontal level guide; the numeric angle is announced separately. */
@Composable
private fun LevelOverlay(degrees: Float) {
    val strokePx = with(LocalDensity.current) { 4.dp.toPx() }
    val markerPx = with(LocalDensity.current) { 18.dp.toPx() }
    Canvas(modifier = Modifier.fillMaxSize()) {
        val centerY = size.height / 2f
        val centerX = size.width / 2f
        val length = size.width * 0.42f
        val offset = (degrees / 45f).coerceIn(-1f, 1f) * (size.height * 0.18f)
        drawLine(
            color = AppColors.FocusRing,
            start = Offset(centerX - length / 2f, centerY + offset),
            end = Offset(centerX + length / 2f, centerY + offset),
            strokeWidth = strokePx
        )
        drawLine(
            color = Color.White,
            start = Offset(centerX, centerY - markerPx),
            end = Offset(centerX, centerY + markerPx),
            strokeWidth = strokePx / 2f
        )
    }
}

@Composable
private fun FaceGuidanceBadge(
    guidance: FaceGuidance,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    if (!enabled) return
    val label = when (guidance) {
        is FaceGuidance.Aligned -> guidance.message
        is FaceGuidance.Misaligned -> guidance.message
        is FaceGuidance.NoFace -> guidance.hint
        is FaceGuidance.MultipleFaces -> "More than one face in view"
        FaceGuidance.Unavailable -> "Face detection is not available"
        FaceGuidance.Disabled -> return
    }
    StatusPill(text = label, modifier = modifier)
}

@Composable
private fun StatusPill(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.semantics { contentDescription = text },
        shape = MaterialTheme.shapes.extraLarge,
        color = AppColors.CameraScrim,
        contentColor = Color.White
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}
