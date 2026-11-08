/*
 * Accessible Camera and Gallery
 * Copyright (C) 2026 Sujan Rai, Blind Tech Nexus team.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.blindtechnexus.accessiblecamera.ui.camera

import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.outlined.Cameraswitch
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blindtechnexus.accessiblecamera.domain.model.CameraMode
import com.blindtechnexus.accessiblecamera.domain.model.CameraPermissionState
import com.blindtechnexus.accessiblecamera.domain.model.CameraState
import com.blindtechnexus.accessiblecamera.domain.model.MediaItem
import com.blindtechnexus.accessiblecamera.ui.components.AccessibleBanner
import com.blindtechnexus.accessiblecamera.ui.components.MediaThumbnail
import com.blindtechnexus.accessiblecamera.ui.components.MlKitDownloadProgressDialog
import com.blindtechnexus.accessiblecamera.ui.components.MlKitDownloadRequiredDialog
import com.blindtechnexus.accessiblecamera.ui.components.PermissionPanel
import com.blindtechnexus.accessiblecamera.ui.theme.AppColors
import com.blindtechnexus.accessiblecamera.ui.theme.Dimens

/**
 * Photo and video capture screen.
 *
 * Layout order matches the focus order: mode selector, preview, quick controls, advanced
 * controls, shutter. Everything is inside a scrollable column so large text and landscape
 * never clip. Preview keeps a fixed aspect ratio to avoid layout jumps.
 */
@Composable
fun CameraScreen(
    isVideoTab: Boolean,
    onOpenGallery: () -> Unit,
    onOpenViewer: (MediaItem) -> Unit,
    onRequestCameraPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    announce: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CameraViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var lastAnnouncedState by remember { mutableStateOf<String?>(null) }
    var showAdvanced by remember { mutableStateOf(false) }
    var showModes by remember { mutableStateOf(false) }
    var showFaceOptions by remember { mutableStateOf(false) }
    var showSceneOptions by remember { mutableStateOf(false) }

    LaunchedEffect(isVideoTab) { viewModel.alignModeWithTab(isVideoTab) }

    // The preview is released when the tab is left, so returning must bind again.
    LifecycleResumeEffect(Unit) {
        viewModel.onScreenResumed()
        onPauseOrDispose { }
    }

    LaunchedEffect(previewView, state.permissionState, state.activeLens?.id) {
        val view = previewView ?: return@LaunchedEffect
        if (state.permissionState == CameraPermissionState.Granted) {
            viewModel.bindCamera(lifecycleOwner, view)
        }
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.unbindCamera() }
    }

    LaunchedEffect(state.statusAnnouncement) {
        val text = state.statusAnnouncement ?: return@LaunchedEffect
        announce(text)
    }

    LaunchedEffect(state.state, state.selectedMode) {
        val key = "${state.state}-${state.selectedMode}"
        if (key != lastAnnouncedState) {
            lastAnnouncedState = key
            if (state.state == CameraState.READY && state.selectedMode != CameraMode.PHOTO) {
                announce("${state.selectedMode.speechLabel} ready")
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AppColors.CameraChrome)
    ) {
        when (state.permissionState) {
            CameraPermissionState.Granted -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .systemBarsPadding()
                ) {
                    CameraTopBar(
                        state = state,
                        isVideoTab = isVideoTab,
                        onSwitchCamera = viewModel::switchCamera,
                        onCycleFlash = viewModel::cycleFlash,
                        onToggleTorch = viewModel::toggleTorch,
                        onToggleGrid = viewModel::toggleGrid,
                        onToggleLevel = viewModel::toggleLevel,
                        onOpenModes = { showModes = true },
                        onOpenAdvanced = { showAdvanced = true },
                        onOpenGallery = onOpenGallery,
                        lastThumbnail = state.lastSavedItem
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        CameraPreview(
                            state = state,
                            onPreviewReady = { previewView = it },
                            onTapToFocus = { x, y ->
                                val view = previewView
                                if (view != null) {
                                    viewModel.focusAt(
                                        x * view.width,
                                        y * view.height
                                    )
                                }
                            }
                        )
                        if (state.isLowStorage) {
                            AccessibleBanner(
                                text = "Storage is low. Photos may fail to save.",
                                isError = true,
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(Dimens.ItemSpacing)
                            )
                        }
                        state.failure?.let { failure ->
                            CameraFailurePanel(
                                message = failure.spokenMessage,
                                retryable = failure.isRetryable,
                                onRetry = viewModel::retryBinding,
                                onDismiss = viewModel::dismissFailure,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(Dimens.ItemSpacing)
                            )
                        }
                    }

                    CameraBottomBar(
                        state = state,
                        isVideoTab = isVideoTab,
                        onShutter = viewModel::onShutterPressed,
                        onToggleRecording = viewModel::toggleRecording,
                        onPauseRecording = {
                            if (state.recordingPaused) {
                                viewModel.resumeRecording()
                            } else {
                                viewModel.pauseRecording()
                            }
                        },
                        onZoomIn = viewModel::zoomIn,
                        onZoomOut = viewModel::zoomOut,
                        onResetZoom = viewModel::resetZoom,
                        onCycleTimer = viewModel::cycleTimer,
                        onToggleAudio = {
                            viewModel.toggleAudio()
                        },
                        onOpenAdvanced = { showAdvanced = true },
                        onOpenModes = { showModes = true },
                        onSelectMode = viewModel::selectMode,
                        onToggleVoiceCommands = { viewModel.toggleVoiceCommands() },
                        onToggleTorch = viewModel::setTorch,
                        onOpenFaceOptions = { showFaceOptions = true },
                        onOpenSceneOptions = { showSceneOptions = true },
                        onDescribeNow = viewModel::describeSceneNow,
                        onScanBarcode = viewModel::scanBarcodeNow
                    )
                }
            }
            CameraPermissionState.Unknown -> Unit
            CameraPermissionState.Denied -> PermissionPanel(
                title = "Camera permission needed",
                message = "The camera is used only to show the viewfinder and to take photos or record video. Nothing is uploaded.",
                primaryLabel = "Allow camera access",
                onPrimary = onRequestCameraPermission,
                secondaryLabel = "Open app settings",
                onSecondary = onOpenAppSettings,
                announce = announce,
                modifier = Modifier.fillMaxSize()
            )
            CameraPermissionState.PermanentlyDenied -> PermissionPanel(
                title = "Camera permission is turned off",
                message = "Camera access was permanently denied. Open app settings, choose Permissions, and allow Camera.",
                primaryLabel = "Open app settings",
                onPrimary = onOpenAppSettings,
                announce = announce,
                modifier = Modifier.fillMaxSize()
            )
        }

        state.reviewItem?.let { item ->
            CaptureReviewSheet(
                item = item,
                onDismiss = viewModel::closeReview,
                onOpen = {
                    viewModel.closeReview()
                    onOpenViewer(item)
                },
                onDelete = {
                    viewModel.closeReview()
                    viewModel.moveNewestCaptureToTrash(item)
                }
            )
        }
    }

    if (showModes) {
        ModeSheet(
            state = state,
            onSelect = {
                viewModel.selectMode(it)
                showModes = false
            },
            onDismiss = { showModes = false },
            announce = announce
        )
    }

    if (showAdvanced) {
        AdvancedControlsSheet(
            state = state,
            viewModel = viewModel,
            onDismiss = { showAdvanced = false },
            announce = announce
        )
    }

    if (showFaceOptions) {
        FaceDetectionSheet(
            state = state,
            onSelect = {
                viewModel.setFaceDetectionInterval(it)
                showFaceOptions = false
            },
            onInstallModels = {
                showFaceOptions = false
                viewModel.showMlKitPrompt()
            },
            onDismiss = { showFaceOptions = false },
            announce = announce
        )
    }

    if (showSceneOptions) {
        SceneDescriptionSheet(
            state = state,
            onSelect = {
                viewModel.setSceneDescriptionInterval(it)
                showSceneOptions = false
            },
            onDescribeNow = {
                viewModel.describeSceneNow()
                showSceneOptions = false
            },
            onInstallModels = {
                showSceneOptions = false
                viewModel.showMlKitPrompt()
            },
            onDismiss = { showSceneOptions = false },
            announce = announce
        )
    }

    if (state.showMlKitDownloadPrompt && !state.mlKitInstalling) {
        MlKitDownloadRequiredDialog(
            onDownload = viewModel::installMlKitModels,
            onCancel = viewModel::dismissMlKitPrompt
        )
    }

    if (state.mlKitInstalling) {
        MlKitDownloadProgressDialog(
            progress = state.mlKitProgress,
            message = state.mlKitInstallMessage.ifBlank { "Installing on-device models" },
            onCancel = viewModel::cancelMlKitInstall
        )
    }

    state.mlKitFailure?.let { reason ->
        AccessibleBanner(
            text = "The on-device models could not be installed. $reason",
            isError = true,
            actionLabel = "Try again",
            onAction = viewModel::showMlKitPrompt,
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.ItemSpacing)
        )
    }
}

/** Top row: lens and gallery on the left, quick toggles on the right. */
@Composable
private fun CameraTopBar(
    state: com.blindtechnexus.accessiblecamera.domain.model.CameraUiState,
    isVideoTab: Boolean,
    onSwitchCamera: () -> Unit,
    onCycleFlash: () -> Unit,
    onToggleTorch: () -> Unit,
    onToggleGrid: () -> Unit,
    onToggleLevel: () -> Unit,
    onOpenModes: () -> Unit,
    onOpenAdvanced: () -> Unit,
    onOpenGallery: () -> Unit,
    lastThumbnail: MediaItem?
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.ItemSpacing, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CameraSquareButton(
                label = state.selectedMode.label,
                icon = Icons.Filled.Tune,
                onClick = onOpenModes,
                contentDescription = "Mode, ${state.selectedMode.label}. Activate to choose a mode",
                size = Dimens.MinimumTouchTarget
            )
            Spacer(Modifier.weight(1f))
            if (state.capabilities.supportsBurst && !isVideoTab) {
                CameraSquareButton(
                    label = "Burst",
                    icon = Icons.Filled.Speed,
                    onClick = onOpenAdvanced,
                    contentDescription = "Burst capture options",
                    size = Dimens.MinimumTouchTarget
                )
            }
            // The label always names the camera the button switches to, so it can never be
            // mistaken for the current lens.
            CameraSquareButton(
                label = state.cameraSwitchShortLabel,
                icon = Icons.Outlined.Cameraswitch,
                onClick = onSwitchCamera,
                enabled = state.capabilities.let { it.hasFrontCamera && it.hasBackCamera } ||
                    state.availableLenses.size > 1,
                contentDescription = state.cameraSwitchLabel,
                size = Dimens.MinimumTouchTarget
            )
            CameraSquareButton(
                label = "Flash",
                icon = if (state.flashMode == com.blindtechnexus.accessiblecamera.domain.model.FlashMode.OFF) {
                    Icons.Filled.FlashOff
                } else {
                    Icons.Filled.FlashOn
                },
                onClick = onCycleFlash,
                enabled = state.canUseFlash,
                valueText = state.flashMode.label,
                contentDescription = "Flash, ${state.flashMode.label}",
                size = Dimens.MinimumTouchTarget
            )
            CameraSquareButton(
                label = "Gallery",
                icon = Icons.Outlined.PhotoLibrary,
                onClick = onOpenGallery,
                contentDescription = "Open gallery",
                size = Dimens.MinimumTouchTarget
            )
        }
        if (lastThumbnail != null) {
            Row(
                modifier = Modifier.padding(horizontal = Dimens.ItemSpacing),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MediaThumbnail(
                    item = lastThumbnail,
                    size = 44.dp,
                    modifier = Modifier.semantics {
                        contentDescription = "Last captured item. ${lastThumbnail.displayName}"
                    }
                )
                Text(
                    text = if (state.state == CameraState.READY) state.previewDescription else state.state.spoken,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White,
                    modifier = Modifier
                        .semantics { liveRegion = LiveRegionMode.Polite }
                )
            }
        } else {
            Text(
                text = state.previewDescription,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White,
                modifier = Modifier
                    .padding(horizontal = Dimens.ItemSpacing)
                    .semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
    }
}

/** Bottom bar with mode chips, zoom, and the shutter. */
@Composable
private fun CameraBottomBar(
    state: com.blindtechnexus.accessiblecamera.domain.model.CameraUiState,
    isVideoTab: Boolean,
    onShutter: () -> Unit,
    onToggleRecording: () -> Unit,
    onPauseRecording: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onResetZoom: () -> Unit,
    onCycleTimer: () -> Unit,
    onToggleAudio: () -> Unit,
    onOpenAdvanced: () -> Unit,
    onOpenModes: () -> Unit,
    onSelectMode: (CameraMode) -> Unit,
    onToggleVoiceCommands: () -> Unit,
    onToggleTorch: (Boolean) -> Unit,
    onOpenFaceOptions: () -> Unit,
    onOpenSceneOptions: () -> Unit,
    onDescribeNow: () -> Unit,
    onScanBarcode: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.ItemSpacing, vertical = 8.dp)
    ) {
        if (state.capabilities.supportedModes.size > 1) {
            ModeSelectorRow(
                modes = CameraMode.entries.filter { mode ->
                    state.capabilities.isModeAvailable(mode) || mode == state.selectedMode ||
                        mode in listOf(CameraMode.NIGHT, CameraMode.PORTRAIT, CameraMode.PRO)
                }.ifEmpty { listOf(CameraMode.PHOTO) },
                selected = state.selectedMode,
                availableModes = state.capabilities.supportedModes,
                onSelect = onSelectMode,
                onUnavailable = { mode ->
                    onOpenModes()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            )
        }

        if (!isVideoTab) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (state.canUseTorch) {
                    CameraFlashlightSwitch(
                        checked = state.torchEnabled,
                        onCheckedChange = onToggleTorch
                    )
                }
                if (state.capabilities.supportsFaceDetection) {
                    CameraSquareButton(
                        label = "Face",
                        icon = Icons.Outlined.Face,
                        onClick = onOpenFaceOptions,
                        selected = state.faceGuidanceEnabled,
                        valueText = state.faceDetectionInterval.label,
                        contentDescription = "Face detection options, ${state.faceDetectionInterval.label}",
                        size = Dimens.MinimumTouchTarget
                    )
                }
                CameraSquareButton(
                    label = "Describe",
                    icon = Icons.Outlined.Face,
                    onClick = onOpenSceneOptions,
                    selected = state.sceneDescriptionEnabled,
                    valueText = state.sceneDescriptionInterval.label,
                    contentDescription = "Describe scene options, ${state.sceneDescriptionInterval.label}",
                    size = Dimens.MinimumTouchTarget
                )
                if (state.capabilities.supportsBarcodeScanning) {
                    CameraSquareButton(
                        label = "Barcode",
                        icon = Icons.Filled.Search,
                        onClick = onScanBarcode,
                        contentDescription = "Scan barcode now",
                        size = Dimens.MinimumTouchTarget
                    )
                }
                CameraSquareButton(
                    label = "Read now",
                    icon = Icons.Filled.VolumeUp,
                    onClick = onDescribeNow,
                    contentDescription = "Describe scene now",
                    size = Dimens.MinimumTouchTarget
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isVideoTab) {
                CameraSquareButton(
                    label = "Sound",
                    icon = if (state.audioEnabled) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                    onClick = onToggleAudio,
                    selected = state.audioEnabled,
                    enabled = state.capabilities.supportsAudioRecording,
                    size = Dimens.MinimumTouchTarget
                )
            } else {
                CameraSquareButton(
                    label = "Timer",
                    icon = Icons.Filled.Timer,
                    onClick = onCycleTimer,
                    valueText = if (state.timer.seconds == 0) "Off" else state.timer.label,
                    size = Dimens.MinimumTouchTarget
                )
            }
            ZoomControls(
                zoomRatio = state.zoomRatio,
                minZoom = state.minZoom,
                maxZoom = state.maxZoom,
                onZoomIn = onZoomIn,
                onZoomOut = onZoomOut,
                onReset = onResetZoom,
                enabled = state.state.canCapturePhoto || state.state == CameraState.RECORDING,
                modifier = Modifier.weight(1f)
            )
            if (state.voiceCommandsEnabled) {
                CameraSquareButton(
                    label = if (state.voiceListening) "Listening" else "Voice",
                    icon = if (state.voiceListening) Icons.Filled.Mic else Icons.Filled.MicOff,
                    onClick = onToggleVoiceCommands,
                    enabled = state.voiceAvailable,
                    selected = state.voiceListening,
                    contentDescription = when {
                        !state.voiceAvailable -> "Voice commands unavailable"
                        state.voiceListening -> "Stop listening for voice commands"
                        else -> "Listen for voice commands"
                    },
                    size = Dimens.MinimumTouchTarget
                )
            }
            CameraSquareButton(
                label = "More",
                icon = Icons.Filled.Tune,
                onClick = onOpenAdvanced,
                contentDescription = "More camera controls",
                size = Dimens.MinimumTouchTarget
            )
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            if (state.state == CameraState.RECORDING || state.state == CameraState.PAUSED) {
                RecordingPauseButton(
                    paused = state.recordingPaused,
                    onClick = onPauseRecording
                )
            } else {
                Spacer(Modifier.height(Dimens.SecondaryAction))
            }
            ShutterButton(
                state = state.state,
                isVideoMode = isVideoTab || state.selectedMode.isVideo,
                countdownActive = state.countdownSecondsRemaining != null,
                onClick = {
                    if (isVideoTab || state.selectedMode.isVideo) {
                        onToggleRecording()
                    } else {
                        onShutter()
                    }
                }
            )
            if (state.state == CameraState.RECORDING || state.state == CameraState.PAUSED) {
                RecordingTimerPill(
                    millis = state.recordingDurationMillis,
                    paused = state.recordingPaused
                )
            } else {
                CameraSquareButton(
                    label = "Face",
                    icon = Icons.Outlined.Face,
                    onClick = onOpenFaceOptions,
                    contentDescription = "Face detection options, ${state.faceDetectionInterval.label}",
                    selected = state.faceGuidanceEnabled,
                    size = Dimens.MinimumTouchTarget
                )
            }
        }
        if (state.burstModeActive) {
            Text(
                text = "Burst active, ${state.burstCount} photos",
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
    }
}

/**
 * Dedicated flashlight switch for photo mode.
 *
 * It is a real toggle with a switch role, so a screen reader announces "Flashlight, switch,
 * on or off" and a switch access user can change it without hunting for the flash cycle.
 */
@Composable
private fun CameraFlashlightSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = "Flashlight"
            stateDescription = if (checked) "On" else "Off"
        },
        shape = MaterialTheme.shapes.medium,
        color = AppColors.CameraChrome,
        contentColor = Color.White,
        border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.85f))
    ) {
        Row(
            modifier = Modifier
                .toggleable(
                    value = checked,
                    role = Role.Switch,
                    onValueChange = onCheckedChange
                )
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = if (checked) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                contentDescription = null,
                modifier = Modifier.height(Dimens.IconSize)
            )
            Text(
                text = "Flashlight",
                style = MaterialTheme.typography.labelSmall
            )
            Switch(checked = checked, onCheckedChange = null)
        }
    }
}

/** Inline failure panel with an explicit retry action. */
@Composable
private fun CameraFailurePanel(
    message: String,
    retryable: Boolean,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AccessibleBanner(
        text = message,
        isError = true,
        modifier = modifier,
        actionLabel = if (retryable) "Retry camera" else null,
        onAction = if (retryable) onRetry else null
    )
}
