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
import androidx.compose.ui.unit.dp
import com.blindtechnexus.accessiblecamera.domain.model.CameraMode
import com.blindtechnexus.accessiblecamera.domain.model.CameraUiState
import com.blindtechnexus.accessiblecamera.domain.model.ExposureMode
import com.blindtechnexus.accessiblecamera.domain.model.FocusMode
import com.blindtechnexus.accessiblecamera.domain.model.PhotoFileFormat
import com.blindtechnexus.accessiblecamera.domain.model.WhiteBalanceMode
import com.blindtechnexus.accessiblecamera.ui.components.AccessibleBottomSheet
import com.blindtechnexus.accessiblecamera.ui.components.AccessibleChoiceRow
import com.blindtechnexus.accessiblecamera.ui.components.AccessibleCycleRow
import com.blindtechnexus.accessiblecamera.ui.components.AccessibleSecondaryButton
import com.blindtechnexus.accessiblecamera.ui.components.AccessibleSliderRow
import com.blindtechnexus.accessiblecamera.ui.components.AccessibleSwitchRow
import com.blindtechnexus.accessiblecamera.ui.components.SectionHeading
import com.blindtechnexus.accessiblecamera.ui.theme.Dimens

/**
 * Progressive disclosure for advanced camera settings.
 *
 * Every row derives availability from the capability registry and states a reason when a
 * control cannot be used. Values are spoken on change through the shared feedback sink.
 */
@Composable
fun AdvancedControlsSheet(
    state: CameraUiState,
    viewModel: CameraViewModel,
    onDismiss: () -> Unit,
    announce: (String) -> Unit
) {
    val lens = state.activeLens
    val supportsManualIso = lens?.supportsManualIso == true
    val supportsManualFocus = lens?.supportsManualFocus == true
    val supportsWhiteBalance = lens?.supportsWhiteBalanceControl == true
    val supportsOptical = lens?.supportsOpticalStabilization == true
    val supportsStabilization = lens?.supportsVideoStabilization == true

    AccessibleBottomSheet(
        title = "Advanced camera controls",
        supporting = "These settings override automatic exposure and focus when the camera supports it.",
        onDismissRequest = onDismiss,
        announceOnOpen = announce
    ) {
        SectionHeading(text = "Exposure and focus")
        AccessibleCycleRow(
            label = "Exposure mode",
            value = state.exposureMode.label,
            onActivate = viewModel::cycleExposureMode,
            description = if (supportsManualIso) {
                "Automatic, locked, or manual ISO and shutter speed."
            } else {
                "Manual exposure is not available on this camera, so automatic remains active."
            },
            enabled = true
        )
        AccessibleSliderRow(
            label = "Exposure compensation",
            value = state.exposureCompensation.toFloat(),
            valueRange = state.exposureRange.first.toFloat()..state.exposureRange.last.toFloat(),
            steps = (state.exposureRange.last - state.exposureRange.first - 1).coerceAtLeast(0),
            valueLabel = { value ->
                val rounded = value.toInt()
                when {
                    rounded == 0 -> "Zero"
                    rounded > 0 -> "Plus $rounded"
                    else -> "Minus ${-rounded}"
                }
            },
            enabled = state.exposureMode == ExposureMode.AUTO,
            description = if (state.exposureMode == ExposureMode.AUTO) {
                "Brighten or darken the photo."
            } else {
                "Not applied while exposure is locked or manual."
            },
            onValueChange = { viewModel.setExposureCompensation(it.toInt()) },
            onChangeFinished = { viewModel.setExposureCompensation(it.toInt()) }
        )
        if (supportsManualIso) {
            AccessibleSliderRow(
                label = "ISO",
                value = state.manualIso.toFloat(),
                valueRange = state.isoRange.first.toFloat()..state.isoRange.last.toFloat(),
                valueLabel = { "ISO ${it.toInt()}" },
                description = "Available range ${state.isoRange.first} to ${state.isoRange.last}.",
                onValueChange = { viewModel.setIso(it.toInt()) }
            )
            AccessibleSliderRow(
                label = "Shutter speed",
                value = state.manualShutterNanos.toFloat(),
                valueRange = state.shutterRangeNanos.first.toFloat()..state.shutterRangeNanos.last.toFloat(),
                valueLabel = { viewModel.shutterLabel(it.toLong()) },
                onValueChange = { viewModel.setShutterNanos(it.toLong()) }
            )
        } else {
            Text(
                text = "Manual ISO and shutter speed are not available on this camera.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        AccessibleCycleRow(
            label = "Focus mode",
            value = state.focusMode.label,
            onActivate = viewModel::cycleFocusMode,
            enabled = supportsManualFocus || state.focusMode == FocusMode.AUTO,
            unavailableReason = "Manual focus is not available on this camera.",
            description = "Continuous autofocus, tap to focus, or fully manual focus distance."
        )
        if (supportsManualFocus) {
            AccessibleSliderRow(
                label = "Focus distance",
                value = state.manualFocusDistance,
                valueRange = 0f..10f,
                valueLabel = { String.format(java.util.Locale.US, "%.1f dioptres", it) },
                enabled = state.focusMode == FocusMode.MANUAL,
                description = "Higher values focus closer to the camera.",
                onValueChange = { viewModel.setManualFocus(it) }
            )
        }
        AccessibleCycleRow(
            label = "Metering",
            value = state.meteringMode.label,
            onActivate = viewModel::cycleMetering,
            description = "How the camera measures brightness across the frame."
        )
        AccessibleCycleRow(
            label = "White balance",
            value = state.whiteBalanceMode.label,
            onActivate = viewModel::cycleWhiteBalance,
            enabled = supportsWhiteBalance,
            unavailableReason = "This camera does not report white balance control."
        )
        AccessibleCycleRow(
            label = "Stabilization",
            value = state.stabilization.label,
            onActivate = viewModel::cycleStabilization,
            enabled = supportsStabilization || supportsOptical,
            unavailableReason = "This camera does not report video stabilization."
        )

        Spacer(Modifier.height(Dimens.ItemSpacing))
        SectionHeading(text = "Capture settings")
        AccessibleCycleRow(
            label = "Aspect ratio",
            value = state.aspectRatio.speech,
            onActivate = viewModel::cycleAspectRatio,
            enabled = state.capabilities.supportedAspectRatios.size > 1
        )
        AccessibleCycleRow(
            label = "Photo format",
            value = state.photoFormat.label,
            onActivate = {
                viewModel.setPhotoFormat(
                    if (state.photoFormat == PhotoFileFormat.JPEG) {
                        PhotoFileFormat.RAW_JPEG
                    } else {
                        PhotoFileFormat.JPEG
                    }
                )
            },
            enabled = state.capabilities.supportsRawCapture,
            unavailableReason = "RAW capture is not supported by this device."
        )
        if (state.capabilities.supportsBurst) {
            AccessibleCycleRow(
                label = "Burst capture",
                value = if (state.burstModeActive) "Running, ${state.burstCount}" else "Start burst",
                onActivate = {
                    if (state.burstModeActive) {
                        viewModel.stopBurst()
                    } else {
                        viewModel.startBurst()
                    }
                },
                description = "Take up to 20 photos in a row."
            )
        }
        AccessibleSwitchRow(
            label = "Grid lines",
            checked = state.gridVisible,
            onCheckedChange = { viewModel.toggleGrid() },
            description = "Shows thirds guidelines over the preview."
        )
        AccessibleSwitchRow(
            label = "Level indicator",
            checked = state.levelVisible,
            onCheckedChange = { viewModel.toggleLevel() },
            description = "Shows a horizontal guide so photos are level."
        )
        AccessibleCycleRow(
            label = "Face detection",
            value = state.faceDetectionInterval.label,
            onActivate = {
                viewModel.setFaceDetectionInterval(state.faceDetectionInterval.next)
            },
            description = "Speaks how to align a face. Off, or check every 2, 3, 5 or 7 seconds."
        )
        AccessibleCycleRow(
            label = "Describe scene",
            value = state.sceneDescriptionInterval.label,
            onActivate = {
                viewModel.setSceneDescriptionInterval(state.sceneDescriptionInterval.next)
            },
            description = "Names what the camera sees on the chosen interval. Three seconds is a good default."
        )
        AccessibleSwitchRow(
            label = "Read barcodes in descriptions",
            checked = state.barcodeScanningEnabled,
            onCheckedChange = { viewModel.setBarcodeScanningEnabled(it) },
            description = "Adds barcode and QR values to scene descriptions."
        )

        if (state.capabilities.isModeAvailable(CameraMode.VIDEO)) {
            Spacer(Modifier.height(Dimens.ItemSpacing))
            SectionHeading(text = "Video settings")
            AccessibleCycleRow(
                label = "Video resolution",
                value = state.videoQuality?.displayLabel ?: "Not available",
                onActivate = viewModel::cycleVideoQuality,
                enabled = state.capabilities.videoQualities.size > 1
            )
            AccessibleCycleRow(
                label = "Frame rate",
                value = "${state.videoFrameRate} frames per second",
                onActivate = viewModel::cycleVideoFrameRate,
                enabled = state.capabilities.videoFrameRates.size > 1,
                unavailableReason = "Only one frame rate is supported."
            )
            AccessibleCycleRow(
                label = "Video codec",
                value = state.videoCodec?.label ?: "Default",
                onActivate = {
                    val codecs = state.capabilities.videoCodecs
                    if (codecs.isEmpty()) return@AccessibleCycleRow
                    val index = codecs.indexOfFirst { it.mimeType == state.videoCodec?.mimeType }
                    viewModel.setVideoCodec(codecs[(index + 1) % codecs.size])
                },
                enabled = state.capabilities.videoCodecs.size > 1
            )
            AccessibleSliderRow(
                label = "Video bitrate",
                value = (if (state.videoBitrate == 0) 20_000_000 else state.videoBitrate).toFloat(),
                valueRange = 4_000_000f..80_000_000f,
                valueLabel = { "${(it / 1_000_000).toInt()} megabits per second" },
                description = "Higher bitrate means larger files and more detail.",
                onValueChange = { viewModel.setVideoBitrate(it.toInt()) }
            )
            AccessibleSwitchRow(
                label = "Record audio",
                checked = state.audioEnabled,
                onCheckedChange = { viewModel.toggleAudio() },
                enabled = state.capabilities.supportsAudioRecording,
                unavailableReason = "This device has no microphone."
            )
        }

        Spacer(Modifier.height(Dimens.ItemSpacing))
        SectionHeading(text = "Lenses")
        state.availableLenses.forEach { lensInfo ->
            AccessibleChoiceRow(
                label = lensInfo.displayName,
                selected = state.activeLens?.id == lensInfo.id,
                supporting = lensInfo.speechDescription,
                onSelect = { viewModel.selectLens(lensInfo) }
            )
        }
        if (state.availableLenses.size <= 1) {
            Text(
                text = "Only one camera lens is reported by this device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(Dimens.SectionSpacing))
        AccessibleSecondaryButton(text = "Close advanced controls", onClick = onDismiss)
    }
}
