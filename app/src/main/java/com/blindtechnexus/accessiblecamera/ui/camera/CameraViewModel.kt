/*
 * Accessible Camera and Gallery
 * Copyright (C) 2026 Sujan Rai, Blind Tech Nexus team.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.blindtechnexus.accessiblecamera.ui.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.core.content.ContextCompat
import com.blindtechnexus.accessiblecamera.core.util.Formatters
import com.blindtechnexus.accessiblecamera.core.util.StorageInfo
import com.blindtechnexus.accessiblecamera.core.util.SystemSoundPlayer
import com.blindtechnexus.accessiblecamera.data.camera.CameraController
import com.blindtechnexus.accessiblecamera.data.camera.CameraErrors
import com.blindtechnexus.accessiblecamera.data.camera.CameraFrameAnalyzer
import com.blindtechnexus.accessiblecamera.data.location.LocationProvider
import com.blindtechnexus.accessiblecamera.data.media.MediaSaver
import com.blindtechnexus.accessiblecamera.data.mlkit.MlKitModelProvisioner
import com.blindtechnexus.accessiblecamera.data.mlkit.MlKitModelState
import com.blindtechnexus.accessiblecamera.data.voice.VoiceRepositoryImpl
import com.blindtechnexus.accessiblecamera.domain.model.VoiceCommand
import com.blindtechnexus.accessiblecamera.ui.voice.VoiceCommandController
import com.blindtechnexus.accessiblecamera.di.ApplicationScope
import com.blindtechnexus.accessiblecamera.domain.model.AnnouncementPriority
import com.blindtechnexus.accessiblecamera.domain.model.AppSettings
import com.blindtechnexus.accessiblecamera.domain.model.AspectRatioOption
import com.blindtechnexus.accessiblecamera.domain.model.AudioCue
import com.blindtechnexus.accessiblecamera.domain.model.CameraMode
import com.blindtechnexus.accessiblecamera.domain.model.CameraPermissionState
import com.blindtechnexus.accessiblecamera.domain.model.CameraState
import com.blindtechnexus.accessiblecamera.domain.model.CameraUiState
import com.blindtechnexus.accessiblecamera.domain.model.ExposureMode
import com.blindtechnexus.accessiblecamera.domain.model.FaceDetectionInterval
import com.blindtechnexus.accessiblecamera.domain.model.FaceGuidance
import com.blindtechnexus.accessiblecamera.domain.model.Facing
import com.blindtechnexus.accessiblecamera.domain.model.FailureCode
import com.blindtechnexus.accessiblecamera.domain.model.FlashMode
import com.blindtechnexus.accessiblecamera.domain.model.FocusMode
import com.blindtechnexus.accessiblecamera.domain.model.HapticCue
import com.blindtechnexus.accessiblecamera.domain.model.LensInfo
import com.blindtechnexus.accessiblecamera.domain.model.MeteringMode
import com.blindtechnexus.accessiblecamera.domain.model.PhotoFileFormat
import com.blindtechnexus.accessiblecamera.domain.model.SceneDescriptionInterval
import com.blindtechnexus.accessiblecamera.domain.model.StabilizationMode
import com.blindtechnexus.accessiblecamera.domain.model.TimerDuration
import com.blindtechnexus.accessiblecamera.domain.model.VideoQualityOption
import com.blindtechnexus.accessiblecamera.domain.model.WhiteBalanceMode
import com.blindtechnexus.accessiblecamera.domain.repository.CapabilityRepository
import com.blindtechnexus.accessiblecamera.domain.repository.MediaRepository
import com.blindtechnexus.accessiblecamera.domain.repository.SettingsRepository
import com.blindtechnexus.accessiblecamera.domain.usecase.CaptureReadinessUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

/**
 * The single source of truth for camera screens.
 *
 * Responsibilities:
 * - Own the [CameraState] machine: idle, initializing, ready, focusing, capturing,
 *   recording, paused, saving, error.
 * - Prevent concurrent captures with an atomic guard plus the state value.
 * - Coordinate countdown timers, bursts, recording timers and announcements.
 * - Delegate all hardware work to [CameraController].
 */
@HiltViewModel
class CameraViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cameraController: CameraController,
    private val frameAnalyzer: CameraFrameAnalyzer,
    private val capabilityRepository: CapabilityRepository,
    private val mediaRepository: MediaRepository,
    private val mediaSaver: MediaSaver,
    private val settingsRepository: SettingsRepository,
    private val locationProvider: LocationProvider,
    private val voice: VoiceRepositoryImpl,
    private val voiceCommands: VoiceCommandController,
    private val captureReadiness: CaptureReadinessUseCase,
    private val mlKitModels: MlKitModelProvisioner,
    private val systemSounds: SystemSoundPlayer,
    @ApplicationScope private val appScope: CoroutineScope
) : ViewModel() {

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    /** Voice command availability and listening state, for the explicit listen button. */
    val voiceCommandState: StateFlow<VoiceCommandController.VoiceCommandUiState> =
        voiceCommands.state

    private fun mirrorVoiceState() {
        val voiceState = voiceCommands.state.value
        _uiState.update {
            it.copy(
                voiceAvailable = voiceState.available,
                voiceListening = voiceState.listening
            )
        }
    }

    private val captureInFlight = AtomicBoolean(false)
    private var settings: AppSettings = AppSettings.Defaults
    private var countdownJob: Job? = null
    private var recordingTimerJob: Job? = null
    private var burstJob: Job? = null
    private var bindJob: Job? = null
    private var mlKitPromptDismissed = false

    /** Guards the one-time "models installed" announcement, including the first state emission. */
    private var mlKitReadyAnnounced = mlKitModels.isInstalled
    private var recordingStartMillis = 0L
    private var pausedDuration = 0L
    private var pauseStartMillis = 0L
    private var lastAnnouncedSecond = 0L
    private var lastFaceAnnouncement = 0L
    private var boundLifecycleOwner: LifecycleOwner? = null
    private var previewView: PreviewView? = null
    private var pendingCaptureFile: File? = null
    private var lastGuidanceText: String? = null

    init {
        voiceCommands.attachHandler { command -> handleVoiceCommand(command) }
        viewModelScope.launch {
            voiceCommands.state.collect { mirrorVoiceState() }
        }
        viewModelScope.launch {
            settingsRepository.settings.collect { current ->
                settings = current
                _uiState.update { state ->
                    state.copy(
                        timer = if (state.countdownSecondsRemaining == null) current.timerDefault else state.timer,
                        gridVisible = current.showGridLines,
                        levelVisible = current.showLevelIndicator,
                        mirrorFrontCamera = current.mirrorFrontCamera,
                        faceDetectionInterval = current.faceDetectionInterval,
                        sceneDescriptionInterval = current.sceneDescriptionInterval,
                        barcodeScanningEnabled = current.barcodeScanningEnabled,
                        shutterSoundEnabled = current.shutterSoundEnabled,
                        faceGuidance = when {
                            !current.faceDetectionInterval.enabled -> FaceGuidance.Disabled
                            state.faceGuidance is FaceGuidance.Disabled ->
                                FaceGuidance.NoFace("Looking for a face")
                            else -> state.faceGuidance
                        }
                    )
                }
                voice.configure(current.speechRate, current.speechPitch)
                voice.setFeatureToggles(
                    haptics = current.hapticsEnabled,
                    audioCues = current.audioCuesEnabled,
                    voiceGuidance = current.voiceGuidanceEnabled
                )
                _uiState.update { it.copy(voiceCommandsEnabled = current.voiceCommandsEnabled) }
                applyAnalysisSettings(current)
                maybeOfferMlKitInstall()
            }
        }
        viewModelScope.launch {
            mlKitModels.state.collect { modelState ->
                _uiState.update { current ->
                    when (modelState) {
                        is MlKitModelState.Installed -> current.copy(
                            mlKitModelsReady = true,
                            mlKitInstalling = false,
                            mlKitProgress = 1f,
                            mlKitInstallMessage = "Models ready",
                            mlKitFailure = null,
                            showMlKitDownloadPrompt = false
                        )
                        is MlKitModelState.NotInstalled -> current.copy(
                            mlKitModelsReady = false,
                            mlKitInstalling = false,
                            mlKitProgress = 0f,
                            mlKitFailure = null
                        )
                        is MlKitModelState.Installing -> current.copy(
                            mlKitInstalling = true,
                            mlKitProgress = modelState.progress,
                            mlKitInstallMessage = modelState.message,
                            mlKitFailure = null,
                            showMlKitDownloadPrompt = false
                        )
                        is MlKitModelState.Failed -> current.copy(
                            mlKitInstalling = false,
                            mlKitFailure = modelState.reason,
                            showMlKitDownloadPrompt = false
                        )
                    }
                }
                if (modelState is MlKitModelState.Installed && !mlKitReadyAnnounced) {
                    mlKitReadyAnnounced = true
                    voice.announce("On-device models installed. Face detection and scene description are ready.", AnnouncementPriority.HIGH)
                } else if (modelState is MlKitModelState.Failed) {
                    voice.announce(
                        "The on-device models could not be installed. ${modelState.reason}",
                        AnnouncementPriority.CRITICAL
                    )
                }
                maybeOfferMlKitInstall()
            }
        }
        viewModelScope.launch {
            capabilityRepository.capabilities.collect { capabilities ->
                _uiState.update { state ->
                    val lenses = if (capabilities.hasAnyCamera) capabilities.lenses else emptyList()
                    val active = state.activeLens?.let { current ->
                        lenses.firstOrNull { it.id == current.id }
                    } ?: lenses.firstOrNull { it.facing == Facing.BACK } ?: lenses.firstOrNull()
                    val mode = if (capabilities.isModeAvailable(state.selectedMode)) {
                        state.selectedMode
                    } else {
                        CameraMode.PHOTO
                    }
                    state.copy(
                        capabilities = capabilities,
                        availableLenses = lenses,
                        activeLens = active,
                        selectedMode = mode,
                        isoRange = active?.isoRange ?: 100..3200,
                        shutterRangeNanos = active?.exposureTimeRangeNanos ?: 1_000_000L..100_000_000L,
                        manualIso = active?.isoRange?.first ?: 100,
                        manualShutterNanos = active?.exposureTimeRangeNanos?.first ?: 1_000_000L,
                        supportsPhotoDuringVideo = capabilities.supportsPhotoDuringVideo,
                        videoQuality = capabilities.videoQualities.firstOrNull { it.qualityLabel.startsWith("Full") }
                            ?: capabilities.videoQualities.firstOrNull(),
                        videoCodec = capabilities.videoCodecs.firstOrNull(),
                        videoFrameRate = capabilities.videoFrameRates.firstOrNull { it == 30 }
                            ?: capabilities.videoFrameRates.firstOrNull() ?: 30,
                        flashMode = if (active?.hasFlash == true) state.flashMode else FlashMode.OFF,
                        faceGuidance = if (capabilities.supportsFaceDetection && state.faceGuidanceEnabled) {
                            FaceGuidance.NoFace("Point the camera at a face")
                        } else {
                            FaceGuidance.Unavailable
                        }
                    )
                }
            }
        }
        viewModelScope.launch {
            frameAnalyzer.faceGuidance.collect { guidance ->
                _uiState.update { it.copy(faceGuidance = guidance) }
                announceGuidance(guidance)
            }
        }
        viewModelScope.launch {
            frameAnalyzer.sceneDescription.collect { description ->
                if (description != null) {
                    _uiState.update { it.copy(sceneDescription = description) }
                    voice.announce(description, AnnouncementPriority.LOW)
                }
            }
        }
        viewModelScope.launch {
            cameraController.torchOn.collect { on ->
                _uiState.update { it.copy(torchEnabled = on) }
            }
        }
        viewModelScope.launch {
            cameraController.zoomRatio.collect { ratio ->
                _uiState.update { it.copy(zoomRatio = ratio) }
            }
        }
        viewModelScope.launch {
            cameraController.cameraError.collect { error ->
                if (error != null) {
                    _uiState.update { it.copy(failure = CameraErrors.fromCameraState(error), state = CameraState.ERROR) }
                }
            }
        }
        refreshLatestThumbnail()
        checkPermissionState()
    }

    /** Pushes the current ML Kit and sound preferences into the analyzer and sound player. */
    private fun applyAnalysisSettings(current: AppSettings) {
        frameAnalyzer.setFaceInterval(current.faceDetectionInterval.seconds)
        frameAnalyzer.setSceneInterval(current.sceneDescriptionInterval.seconds)
        frameAnalyzer.barcodeScanningEnabled = current.barcodeScanningEnabled
        systemSounds.enabled = current.shutterSoundEnabled
    }

    /**
     * Shows the one-time Download Required dialog when an ML Kit feature is switched on but
     * the on-device models have not been installed yet.
     */
    private fun maybeOfferMlKitInstall() {
        val current = _uiState.value
        val wantsModels = current.faceDetectionInterval.enabled ||
            current.sceneDescriptionInterval.enabled ||
            current.barcodeScanningEnabled
        val shouldOffer = current.permissionState == CameraPermissionState.Granted &&
            wantsModels &&
            !current.mlKitModelsReady &&
            !current.mlKitInstalling &&
            !mlKitPromptDismissed
        _uiState.update { it.copy(showMlKitDownloadPrompt = shouldOffer) }
    }

    // region Permissions

    fun checkPermissionState() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        val hasAudio = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        cameraController.setAudioPermission(hasAudio)
        _uiState.update {
            it.copy(
                permissionState = if (granted) CameraPermissionState.Granted else CameraPermissionState.Denied,
                audioEnabled = hasAudio && it.capabilities.supportsAudioRecording
            )
        }
        if (!granted) {
            _uiState.update { it.copy(state = CameraState.IDLE) }
        }
    }

    fun onPermissionResult(granted: Boolean, permanentlyDenied: Boolean) {
        _uiState.update {
            it.copy(
                permissionState = when {
                    granted -> CameraPermissionState.Granted
                    permanentlyDenied -> CameraPermissionState.PermanentlyDenied
                    else -> CameraPermissionState.Denied
                },
                failure = when {
                    granted -> null
                    permanentlyDenied -> CameraErrors.permissionPermanentlyDenied()
                    else -> CameraErrors.permissionDenied()
                }
            )
        }
        if (granted) {
            voice.announce("Camera permission granted", AnnouncementPriority.HIGH)
            checkPermissionState()
            maybeOfferMlKitInstall()
        } else if (permanentlyDenied) {
            voice.announce(
                CameraErrors.permissionPermanentlyDenied().spokenMessage,
                AnnouncementPriority.CRITICAL
            )
        }
    }

    fun onAudioPermissionResult(granted: Boolean) {
        cameraController.setAudioPermission(granted)
        _uiState.update { it.copy(audioEnabled = granted) }
        voice.announce(
            if (granted) "Microphone allowed. Video will include sound." else "Microphone not allowed. Video will be silent.",
            AnnouncementPriority.NORMAL
        )
    }

    // endregion

    // region Binding

    /**
     * Called by the screen once the preview view and lifecycle are available.
     *
     * The previous implementation read the capability registry with `first()`, which returns
     * the empty initial value while detection is still running in the background. That made a
     * perfectly good camera look missing. Here the registry is refreshed when it has no camera
     * yet, and CameraX itself is the final authority before any error is shown.
     */
    fun bindCamera(lifecycleOwner: LifecycleOwner, view: PreviewView) {
        boundLifecycleOwner = lifecycleOwner
        previewView = view
        if (_uiState.value.permissionState != CameraPermissionState.Granted) {
            checkPermissionState()
            if (_uiState.value.permissionState != CameraPermissionState.Granted) {
                _uiState.update { it.copy(state = CameraState.IDLE) }
                return
            }
        }
        if (bindJob?.isActive == true) return
        _uiState.update { it.copy(state = CameraState.INITIALIZING, failure = null) }
        bindJob = viewModelScope.launch {
            val provider = cameraController.provider()
            if (provider == null) {
                failWith(
                    com.blindtechnexus.accessiblecamera.domain.model.CameraFailure(
                        code = FailureCode.CAMERA_UNAVAILABLE,
                        spokenMessage = "The camera could not start because the camera service is unavailable.",
                        visibleMessage = "Camera service unavailable"
                    )
                )
                return@launch
            }

            // Refresh detection only when it has not produced a camera yet. Awaiting this is
            // enough to remove the startup race without slowing down every rebind.
            var capabilities = capabilityRepository.capabilities.first()
            if (!capabilities.hasAnyCamera) {
                capabilities = capabilityRepository.refresh()
            }

            val lens = resolveActiveLens(capabilities)
            if (lens != null && lens.id != _uiState.value.activeLens?.id) {
                _uiState.update { it.copy(activeLens = lens) }
            }

            val selector = cameraController.selectorForLens(lens)
            if (!cameraController.hasCamera(selector)) {
                // One more attempt with fresh detection before telling the user the device has
                // no camera; hot plugging, a camera service restart or stale metadata can all
                // resolve here.
                capabilities = capabilityRepository.refresh()
                val retryLens = resolveActiveLens(capabilities)
                if (!cameraController.hasCamera(cameraController.selectorForLens(retryLens))) {
                    failWith(
                        com.blindtechnexus.accessiblecamera.domain.model.CameraFailure(
                            code = FailureCode.CAMERA_UNAVAILABLE,
                            spokenMessage = "Android did not report a usable camera. Close other apps that may be using the camera, then retry.",
                            visibleMessage = "No usable camera found",
                            isRetryable = true
                        )
                    )
                    return@launch
                }
            }

            val state = _uiState.value
            // Keep the previous lens list when detection produced none, so the lens picker never
            // flickers empty during a rebind.
            val lensList = capabilities.lenses.ifEmpty { state.availableLenses }
            val storage = StorageInfo.snapshot(context)
            val error = cameraController.bind(
                CameraController.BindRequest(
                    lifecycleOwner = lifecycleOwner,
                    previewView = view,
                    lens = state.activeLens,
                    aspectRatio = state.aspectRatio,
                    flashMode = state.flashMode,
                    mirrorFrontCamera = settings.mirrorFrontCamera,
                    targetRotation = runCatching { view.display.rotation }.getOrDefault(cameraController.displayRotation()),
                    photoFormat = state.photoFormat,
                    videoQualityLabel = state.videoQuality?.qualityLabel ?: "FHD",
                    videoFrameRate = state.videoFrameRate,
                    videoBitrate = state.videoBitrate,
                    stabilization = state.stabilization,
                    captureModeFast = true,
                    enableVideo = capabilities.isModeAvailable(CameraMode.VIDEO),
                    // Analysis is always bound while the camera is open. The analyzer throttles
                    // itself, and this way turning Describe Scene on later works immediately
                    // instead of needing a rebind.
                    enableAnalysis = true
                )
            )
            if (error == null) {
                cameraController.analysisUseCase()?.let { analysis ->
                    analysis.setAnalyzer(cameraController.captureExecutor, frameAnalyzer)
                }
                frameAnalyzer.isFrontCamera = _uiState.value.isFrontCamera
                frameAnalyzer.onGuidance = { text -> appScope.launch { announceFaceGuidance(text) } }
                frameAnalyzer.onBarcode = { text -> appScope.launch { announceBarcode(text) } }
                frameAnalyzer.reset()
                _uiState.update {
                    it.copy(
                        state = CameraState.READY,
                        failure = null,
                        capabilities = capabilities,
                        availableLenses = lensList,
                        isLowStorage = storage.isLow,
                        exposureRange = cameraController.exposureState()?.first ?: it.exposureRange,
                        supportsZoom = (it.activeLens?.zoomMax ?: 1f) > 1.01f,
                        torchEnabled = cameraController.torchOn.value,
                        zoomRatio = cameraController.zoomRatio.value
                    )
                }
                maybeOfferMlKitInstall()
                voice.announce(
                    buildString {
                        append(_uiState.value.selectedMode.speechLabel)
                        append(" ready. ")
                        append(if (_uiState.value.isFrontCamera) "Front camera. " else "Back camera. ")
                        append("Shutter control is at the bottom of the screen.")
                    },
                    AnnouncementPriority.NORMAL
                )
                if (storage.isLow) {
                    voice.announce(
                        "Warning. Storage is low. Free space before recording video.",
                        AnnouncementPriority.CRITICAL
                    )
                }
            } else {
                failWith(
                    com.blindtechnexus.accessiblecamera.domain.model.CameraFailure(
                        code = FailureCode.CAMERA_IN_USE,
                        spokenMessage = error,
                        visibleMessage = error
                    )
                )
            }
        }
    }

    /**
     * Picks the lens to bind: the user's current lens when it still exists, otherwise the
     * first rear lens, otherwise any lens, and finally a lens read straight from CameraX.
     */
    private fun resolveActiveLens(capabilities: com.blindtechnexus.accessiblecamera.domain.model.CameraCapabilities): LensInfo? {
        val current = _uiState.value.activeLens
        val lenses = capabilities.lenses
        val fromRegistry = current?.let { existing -> lenses.firstOrNull { it.id == existing.id } }
            ?: lenses.firstOrNull { it.facing == Facing.BACK }
            ?: lenses.firstOrNull()
        if (fromRegistry != null) return fromRegistry
        val facing = current?.facing ?: Facing.BACK
        return cameraController.defaultLens(facing) ?: cameraController.defaultLens(Facing.FRONT)
    }

    fun unbindCamera() {
        cancelCountdown()
        burstJob?.cancel()
        bindJob?.cancel()
        bindJob = null
        cameraController.unbind()
        frameAnalyzer.reset()
        _uiState.update { it.copy(state = CameraState.IDLE, torchEnabled = false) }
    }

    /** Re-binds when returning to the tab, because the preview was released on dispose. */
    fun onScreenResumed() {
        val owner = boundLifecycleOwner ?: return
        val view = previewView ?: return
        if (_uiState.value.permissionState == CameraPermissionState.Granted &&
            cameraController.isBound.value.not()
        ) {
            bindCamera(owner, view)
        }
    }

    fun retryBinding() {
        val owner = boundLifecycleOwner
        val view = previewView
        if (owner != null && view != null) {
            bindCamera(owner, view)
        } else {
            failWith(
                com.blindtechnexus.accessiblecamera.domain.model.CameraFailure(
                    code = FailureCode.UNKNOWN,
                    spokenMessage = "Move to the Photo or Video tab, then try again.",
                    visibleMessage = "Preview is not ready"
                )
            )
        }
    }

    private fun failWith(failure: com.blindtechnexus.accessiblecamera.domain.model.CameraFailure) {
        _uiState.update {
            it.copy(
                state = CameraState.ERROR,
                failure = failure
            )
        }
        voice.vibrate(HapticCue.ERROR)
        voice.playCue(AudioCue.ERROR)
        voice.announce(failure.spokenMessage, AnnouncementPriority.CRITICAL)
    }

    fun dismissFailure() {
        _uiState.update { it.copy(failure = null, state = if (cameraController.isBound.value) CameraState.READY else CameraState.IDLE) }
    }

    // endregion

    // region Modes and lenses

    /**
     * Keeps the selected mode in step with the bottom tab. Video tab always selects a video
     * mode, and the Photo tab always returns to a still mode.
     */
    fun alignModeWithTab(isVideoTab: Boolean) {
        val current = _uiState.value.selectedMode
        if (isVideoTab && !current.isVideo) {
            selectMode(CameraMode.VIDEO)
        } else if (!isVideoTab && current.isVideo) {
            selectMode(CameraMode.PHOTO)
        }
    }

    fun selectMode(mode: CameraMode) {
        val capabilities = _uiState.value.capabilities
        if (!capabilities.isModeAvailable(mode)) {
            voice.announce(capabilities.reasonFor(mode), AnnouncementPriority.HIGH)
            _uiState.update { it.copy(statusAnnouncement = capabilities.reasonFor(mode)) }
            return
        }
        if (_uiState.value.state == CameraState.RECORDING || _uiState.value.state == CameraState.PAUSED) {
            voice.announce("Stop recording before changing mode.", AnnouncementPriority.HIGH)
            return
        }
        _uiState.update { it.copy(selectedMode = mode, showAdvancedPanel = false) }
        voice.vibrate(HapticCue.SELECTION)
        voice.announce(mode.speechLabel, AnnouncementPriority.NORMAL)
        rebindIfPossible()
    }

    fun switchCamera() {
        val state = _uiState.value
        if (state.state == CameraState.RECORDING || state.state == CameraState.PAUSED) {
            voice.announce("Stop recording before switching cameras.", AnnouncementPriority.HIGH)
            return
        }
        val current = state.activeLens
        val nextFacing = if (current?.facing == Facing.FRONT) Facing.BACK else Facing.FRONT
        val lenses = state.availableLenses.ifEmpty { state.capabilities.lenses }
        val next = lenses.firstOrNull {
            it.facing == nextFacing && it.type != com.blindtechnexus.accessiblecamera.domain.model.LensType.ULTRAWIDE
        } ?: lenses.firstOrNull { it.facing == nextFacing }
            ?: cameraController.defaultLens(nextFacing)

        if (next == null || !cameraController.hasCameraFor(nextFacing)) {
            voice.announce(
                if (nextFacing == Facing.FRONT) {
                    "This device does not report a front camera."
                } else {
                    "This device does not report a rear camera."
                },
                AnnouncementPriority.HIGH
            )
            return
        }

        // Turning the torch off before rebinding avoids leaving a front camera with a stale
        // torch request and makes the next announcement accurate.
        if (_uiState.value.torchEnabled) {
            cameraController.setTorch(false)
        }
        _uiState.update { it.copy(activeLens = next, torchEnabled = false) }
        frameAnalyzer.isFrontCamera = next.facing == Facing.FRONT
        voice.vibrate(HapticCue.SELECTION)
        voice.announce(
            "Switched to ${if (next.facing == Facing.FRONT) "front camera" else "rear camera"}. " +
                "${if (next.facing == Facing.FRONT) "Switch to Rear Camera button" else "Switch to Front Camera button"} is now available.",
            AnnouncementPriority.NORMAL
        )
        rebindIfPossible()
    }

    fun selectLens(lens: LensInfo) {
        if (lens.id == _uiState.value.activeLens?.id) return
        _uiState.update { it.copy(activeLens = lens, torchEnabled = false) }
        frameAnalyzer.isFrontCamera = lens.facing == Facing.FRONT
        voice.announce("Now using ${lens.speechDescription}", AnnouncementPriority.NORMAL)
        rebindIfPossible()
    }

    private fun rebindIfPossible() {
        val owner = boundLifecycleOwner
        val view = previewView
        if (owner != null && view != null && cameraController.isBound.value) {
            bindCamera(owner, view)
        }
    }

    // endregion

    // region Photo capture

    fun onShutterPressed() {
        val state = _uiState.value
        when {
            state.state == CameraState.RECORDING || state.state == CameraState.PAUSED -> {
                capturePhotoDuringVideo()
                return
            }
            state.countdownSecondsRemaining != null -> {
                cancelCountdown()
                return
            }
        }
        val decision = captureReadiness.evaluate(
            state = state.state,
            captureInFlight = captureInFlight.get(),
            storage = StorageInfo.snapshot(context),
            hasCamera = state.capabilities.hasAnyCamera
        )
        if (!decision.allowed && decision.reason != CaptureReadinessUseCase.Reason.READY) {
            if (decision.reason != CaptureReadinessUseCase.Reason.BUSY) {
                voice.announce(decision.spokenMessage, AnnouncementPriority.HIGH)
            } else {
                voice.announce("The camera is busy. Please wait.", AnnouncementPriority.HIGH)
            }
            return
        }
        if (state.timer.seconds > 0) {
            startCountdown()
        } else {
            takePhoto()
        }
    }

    private fun startCountdown() {
        val seconds = _uiState.value.timer.seconds
        if (seconds <= 0) {
            takePhoto()
            return
        }
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            voice.announce("Timer started, $seconds seconds", AnnouncementPriority.HIGH)
            for (remaining in seconds downTo 1) {
                _uiState.update { it.copy(countdownSecondsRemaining = remaining) }
                voice.playCue(AudioCue.TICK)
                voice.vibrate(HapticCue.TAP)
                voice.announceValue("countdown", "$remaining", debounceMillis = 0)
                if (remaining > 1) delay(1000)
            }
            _uiState.update { it.copy(countdownSecondsRemaining = null) }
            takePhoto()
        }
    }

    fun cancelCountdown() {
        countdownJob?.cancel()
        countdownJob = null
        if (_uiState.value.countdownSecondsRemaining != null) {
            _uiState.update { it.copy(countdownSecondsRemaining = null) }
            voice.announce("Timer cancelled", AnnouncementPriority.HIGH)
        }
    }

    /** Starts a burst of photos. Only available when the device supports it. */
    fun startBurst() {
        val state = _uiState.value
        if (!state.capabilities.supportsBurst) {
            voice.announce("Burst capture is not available on this device.", AnnouncementPriority.HIGH)
            return
        }
        if (!state.state.canCapturePhoto) {
            voice.announce("The camera is busy.", AnnouncementPriority.HIGH)
            return
        }
        burstJob?.cancel()
        _uiState.update { it.copy(burstModeActive = true, burstCount = 0) }
        voice.announce("Burst started", AnnouncementPriority.HIGH)
        burstJob = viewModelScope.launch {
            var count = 0
            while (count < 20) {
                capturePhotoInternal { success ->
                    if (!success) {
                        _uiState.update { s -> s.copy(burstModeActive = false) }
                        return@capturePhotoInternal
                    }
                }
                count++
                _uiState.update { it.copy(burstCount = count) }
                delay(400)
            }
            stopBurst(count)
        }
    }

    fun stopBurst(count: Int = _uiState.value.burstCount) {
        burstJob?.cancel()
        burstJob = null
        _uiState.update { it.copy(burstModeActive = false) }
        voice.announce("Burst finished. $count photos saved.", AnnouncementPriority.HIGH)
    }

    private fun capturePhotoInternal(onComplete: (Boolean) -> Unit) {
        if (!captureInFlight.compareAndSet(false, true)) {
            onComplete(false)
            return
        }
        _uiState.update { it.copy(state = CameraState.CAPTURING) }
        val file = mediaSaver.createTempFile(isVideo = false)
        cameraController.capturePhoto(file, _uiState.value.flashMode) { result ->
            result.onSuccess { saved ->
                systemSounds.playShutter()
                voice.playCue(AudioCue.SHUTTER)
                voice.vibrate(HapticCue.CAPTURE)
                persistPhoto(saved, announceSaved = false) { ok ->
                    captureInFlight.set(false)
                    _uiState.update { it.copy(state = CameraState.READY) }
                    onComplete(ok)
                }
            }.onFailure { error ->
                captureInFlight.set(false)
                _uiState.update { it.copy(state = CameraState.READY) }
                failWith(CameraErrors.captureFailed(error.message))
                onComplete(false)
            }
        }
    }

    private fun takePhoto() {
        if (!captureInFlight.compareAndSet(false, true)) {
            voice.announce("Please wait, the previous photo is still saving.", AnnouncementPriority.NORMAL)
            return
        }
        val before = _uiState.value
        _uiState.update { it.copy(state = CameraState.CAPTURING, statusAnnouncement = "Taking photo") }
        val file = mediaSaver.createTempFile(isVideo = false)
        pendingCaptureFile = file
        cameraController.capturePhoto(file, before.flashMode) { result ->
            result.onSuccess { saved ->
                systemSounds.playShutter()
                voice.playCue(AudioCue.SHUTTER)
                voice.vibrate(HapticCue.CAPTURE)
                _uiState.update { it.copy(state = CameraState.SAVING) }
                persistPhoto(saved) { ok ->
                    captureInFlight.set(false)
                    _uiState.update {
                        it.copy(
                            state = CameraState.READY,
                            statusAnnouncement = null
                        )
                    }
                    if (ok && settings.reviewAfterCapture) {
                        // The screen opens the review overlay from lastSavedItem.
                    }
                }
            }.onFailure { error ->
                captureInFlight.set(false)
                pendingCaptureFile = null
                failWith(CameraErrors.captureFailed(error.message))
            }
        }
    }

    private fun persistPhoto(
        file: File,
        announceSaved: Boolean = true,
        onComplete: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            val geoPoint = if (settings.geotaggingEnabled && locationProvider.hasPermission()) {
                locationProvider.lastKnown()
            } else null
            val options = MediaSaver.SaveOptions(
                location = settings.saveLocation,
                geoPoint = geoPoint,
                watermarkEnabled = settings.watermarkEnabled,
                watermarkText = settings.watermarkText,
                watermarkSecondary = if (settings.shareLocationWithWatermark) locationProvider.describe(geoPoint) else null
            )
            val result = mediaSaver.savePhoto(file, options)
            result.onSuccess { item ->
                _uiState.update {
                    it.copy(
                        lastSavedItem = item,
                        savedMediaCount = it.savedMediaCount + 1,
                        reviewItem = if (settings.reviewAfterCapture) item else null
                    )
                }
                if (announceSaved) {
                    voice.playCue(AudioCue.SUCCESS)
                    voice.announce(
                        if (settings.verbosity == com.blindtechnexus.accessiblecamera.domain.model.Verbosity.DETAILED) {
                            "Photo saved. ${item.displayName}, ${Formatters.size(item.sizeBytes)}"
                        } else {
                            "Photo saved"
                        },
                        AnnouncementPriority.NORMAL
                    )
                }
                onComplete(true)
            }.onFailure { error ->
                failWith(CameraErrors.saveFailed(error.message))
                onComplete(false)
            }
        }
    }

    /** Deletes the capture that is currently in the review sheet, with undo available. */
    fun moveNewestCaptureToTrash(item: com.blindtechnexus.accessiblecamera.domain.model.MediaItem) {
        viewModelScope.launch {
            runCatching { mediaRepository.deleteToTrash(listOf(item)) }
                .onSuccess {
                    voice.announce(
                        "Capture moved to recently deleted. You can restore it in the gallery.",
                        AnnouncementPriority.HIGH
                    )
                }
                .onFailure {
                    voice.announce("The capture could not be deleted.", AnnouncementPriority.CRITICAL)
                }
            _uiState.update { it.copy(lastSavedItem = null, reviewItem = null) }
            refreshLatestThumbnail()
        }
    }

    fun openReview(item: com.blindtechnexus.accessiblecamera.domain.model.MediaItem) {
        _uiState.update { it.copy(reviewItem = item) }
    }

    fun closeReview() {
        _uiState.update { it.copy(reviewItem = null, lastSavedItem = null) }
    }

    /**
     * Photos during video are only offered where the device supports a still capture plus
     * video combination. CameraX exposes this through the two bound use cases; we guard it
     * with the capability flag so the button is never shown as working when it is not.
     */
    private fun capturePhotoDuringVideo() {
        val state = _uiState.value
        if (!state.supportsPhotoDuringVideo) {
            voice.announce(
                "Taking photos while recording is not available on this device.",
                AnnouncementPriority.HIGH
            )
            return
        }
        if (!captureInFlight.compareAndSet(false, true)) {
            voice.announce("Please wait for the previous photo to save.", AnnouncementPriority.NORMAL)
            return
        }
        val file = mediaSaver.createTempFile(isVideo = false)
        cameraController.capturePhoto(file, FlashMode.OFF) { result ->
            result.onSuccess { saved ->
                systemSounds.playShutter()
                voice.playCue(AudioCue.SHUTTER)
                voice.vibrate(HapticCue.CAPTURE)
                persistPhoto(saved) { ok ->
                    captureInFlight.set(false)
                    if (ok) {
                        voice.announce("Photo saved during recording", AnnouncementPriority.NORMAL)
                    }
                }
            }.onFailure { error ->
                captureInFlight.set(false)
                voice.announce(
                    "Photo during recording failed. ${error.message ?: ""}",
                    AnnouncementPriority.HIGH
                )
            }
        }
    }

    // endregion

    // region Video capture

    fun toggleRecording() {
        val state = _uiState.value
        when (state.state) {
            CameraState.RECORDING -> stopRecording()
            CameraState.PAUSED -> stopRecording()
            CameraState.READY, CameraState.FOCUSING -> startRecording()
            CameraState.CAPTURING, CameraState.SAVING -> voice.announce(
                "Please wait, the app is still saving.",
                AnnouncementPriority.HIGH
            )
            else -> voice.announce("The camera is not ready to record.", AnnouncementPriority.HIGH)
        }
    }

    private fun startRecording() {
        val state = _uiState.value
        if (!state.capabilities.isModeAvailable(CameraMode.VIDEO)) {
            voice.announce(state.capabilities.reasonFor(CameraMode.VIDEO), AnnouncementPriority.CRITICAL)
            return
        }
        val storage = StorageInfo.snapshot(context)
        if (storage.isCritical) {
            failWith(CameraErrors.lowStorage(storage.availableBytes / (1024 * 1024)))
            return
        }
        val file = mediaSaver.createTempFile(isVideo = true)
        val error = cameraController.startRecording(
            file = file,
            withAudio = state.audioEnabled
        ) { event ->
            handleVideoEvent(event, file)
        }
        if (error != null) {
            failWith(
                com.blindtechnexus.accessiblecamera.domain.model.CameraFailure(
                    code = FailureCode.UNKNOWN,
                    spokenMessage = error,
                    visibleMessage = "Recording could not start"
                )
            )
            return
        }
        _uiState.update {
            it.copy(
                state = CameraState.RECORDING,
                recordingDurationMillis = 0L,
                recordingPaused = false,
                failure = null
            )
        }
        systemSounds.playRecordingStart()
        voice.playCue(AudioCue.RECORDING_START)
        voice.vibrate(HapticCue.RECORDING_START)
        voice.announce(
            "Recording started. ${if (state.audioEnabled) "With sound." else "Silent, microphone is off."}",
            AnnouncementPriority.HIGH
        )
        startRecordingTimer()
    }

    fun pauseRecording() {
        if (_uiState.value.state != CameraState.RECORDING) return
        if (cameraController.pauseRecording()) {
            pauseStartMillis = System.currentTimeMillis()
            _uiState.update { it.copy(state = CameraState.PAUSED, recordingPaused = true) }
            voice.vibrate(HapticCue.SELECTION)
            voice.announce("Recording paused", AnnouncementPriority.HIGH)
        } else {
            voice.announce("This recorder cannot pause. Stop and start a new recording.", AnnouncementPriority.HIGH)
        }
    }

    fun resumeRecording() {
        if (_uiState.value.state != CameraState.PAUSED) return
        if (cameraController.resumeRecording()) {
            if (pauseStartMillis > 0) {
                pausedDuration += System.currentTimeMillis() - pauseStartMillis
                pauseStartMillis = 0
            }
            _uiState.update { it.copy(state = CameraState.RECORDING, recordingPaused = false) }
            voice.announce("Recording resumed", AnnouncementPriority.HIGH)
        } else {
            voice.announce("Recording could not resume.", AnnouncementPriority.HIGH)
        }
    }

    fun stopRecording() {
        if (!_uiState.value.state.canStopRecording) return
        _uiState.update { it.copy(state = CameraState.SAVING) }
        systemSounds.playRecordingStop()
        voice.playCue(AudioCue.RECORDING_STOP)
        voice.vibrate(HapticCue.RECORDING_STOP)
        cameraController.stopRecording()
        recordingTimerJob?.cancel()
        voice.announce("Recording stopped. Saving video.", AnnouncementPriority.HIGH)
    }

    private fun handleVideoEvent(event: androidx.camera.video.VideoRecordEvent, file: File) {
        when (event) {
            is androidx.camera.video.VideoRecordEvent.Start -> Unit
            is androidx.camera.video.VideoRecordEvent.Pause -> {
                _uiState.update { it.copy(state = CameraState.PAUSED, recordingPaused = true) }
            }
            is androidx.camera.video.VideoRecordEvent.Resume -> {
                _uiState.update { it.copy(state = CameraState.RECORDING, recordingPaused = false) }
            }
            is androidx.camera.video.VideoRecordEvent.Finalize -> {
                recordingTimerJob?.cancel()
                val error = event.error
                if (error != androidx.camera.video.VideoRecordEvent.Finalize.ERROR_NONE) {
                    failWith(CameraErrors.fromVideoError(error))
                }
                if (error == androidx.camera.video.VideoRecordEvent.Finalize.ERROR_NONE && file.length() > 0) {
                    saveRecordedVideo(file, event.recordingStats.recordedDurationNanos / 1_000_000L)
                } else if (file.exists() && file.length() == 0L) {
                    file.delete()
                }
                _uiState.update {
                    it.copy(
                        state = if (error == androidx.camera.video.VideoRecordEvent.Finalize.ERROR_NONE) {
                            CameraState.READY
                        } else {
                            CameraState.ERROR
                        },
                        recordingDurationMillis = 0L,
                        recordingPaused = false
                    )
                }
                pausedDuration = 0
                pauseStartMillis = 0
                recordingStartMillis = 0
            }
        }
    }

    private fun saveRecordedVideo(file: File, durationMillis: Long) {
        viewModelScope.launch {
            val geoPoint = if (settings.geotaggingEnabled && locationProvider.hasPermission()) {
                locationProvider.lastKnown()
            } else null
            val result = mediaSaver.saveVideo(
                file,
                MediaSaver.SaveOptions(
                    location = settings.saveLocation,
                    geoPoint = geoPoint,
                    watermarkEnabled = false,
                    watermarkText = settings.watermarkText
                )
            )
            result.onSuccess { item ->
                _uiState.update {
                    it.copy(
                        lastSavedItem = item,
                        savedMediaCount = it.savedMediaCount + 1,
                        reviewItem = if (settings.reviewAfterCapture) item else null
                    )
                }
                voice.playCue(AudioCue.SUCCESS)
                voice.announce(
                    "Video saved. ${com.blindtechnexus.accessiblecamera.core.util.TimeFormat.spoken(durationMillis)} long.",
                    AnnouncementPriority.HIGH
                )
            }.onFailure { error ->
                failWith(CameraErrors.saveFailed(error.message))
            }
        }
    }

    private fun startRecordingTimer() {
        recordingStartMillis = System.currentTimeMillis()
        pausedDuration = 0
        lastAnnouncedSecond = 0
        recordingTimerJob?.cancel()
        recordingTimerJob = viewModelScope.launch {
            while (true) {
                delay(500)
                val state = _uiState.value
                if (state.state != CameraState.RECORDING && state.state != CameraState.PAUSED) break
                val elapsed = System.currentTimeMillis() - recordingStartMillis - pausedDuration
                _uiState.update { it.copy(recordingDurationMillis = elapsed) }
                val seconds = elapsed / 1000
                val announceEvery = settings.recordingTimerAnnounceSeconds.coerceAtLeast(10)
                if (seconds > 0 && seconds % announceEvery == 0L && seconds != lastAnnouncedSecond) {
                    lastAnnouncedSecond = seconds
                    voice.announce(
                        "Recording ${com.blindtechnexus.accessiblecamera.core.util.TimeFormat.spoken(elapsed)}",
                        AnnouncementPriority.LOW
                    )
                }
            }
        }
    }

    fun toggleAudio() {
        val state = _uiState.value
        if (!state.capabilities.supportsAudioRecording) {
            voice.announce("This device has no microphone for video sound.", AnnouncementPriority.HIGH)
            return
        }
        val next = !state.audioEnabled
        _uiState.update { it.copy(audioEnabled = next) }
        voice.announce(if (next) "Sound on" else "Sound off", AnnouncementPriority.NORMAL)
    }

    // endregion

    // region Camera controls

    fun setFlashMode(mode: FlashMode) {
        if (mode != FlashMode.OFF && !_uiState.value.canUseFlash) {
            voice.announce("This camera has no flash.", AnnouncementPriority.HIGH)
            return
        }
        _uiState.update { it.copy(flashMode = mode) }
        cameraController.setFlashMode(mode)
        if (mode == FlashMode.TORCH) {
            cameraController.setTorch(true)
        } else if (_uiState.value.torchEnabled) {
            cameraController.setTorch(false)
        }
        voice.vibrate(HapticCue.SELECTION)
        voice.announce(mode.spoken, AnnouncementPriority.NORMAL)
    }

    fun cycleFlash() {
        val state = _uiState.value
        val options = buildList {
            add(FlashMode.OFF)
            if (state.canUseFlash) {
                add(FlashMode.AUTO)
                add(FlashMode.ON)
                add(FlashMode.TORCH)
            }
        }
        val nextIndex = (options.indexOf(state.flashMode) + 1) % options.size
        setFlashMode(options[nextIndex])
    }

    fun toggleTorch() {
        setTorch(!_uiState.value.torchEnabled)
    }

    /**
     * Turns the flashlight on or off. Used by the dedicated Flashlight switch in photo mode
     * and by the flash button when it reaches the Torch value.
     */
    fun setTorch(enabled: Boolean) {
        val state = _uiState.value
        val hasFlash = state.canUseTorch || cameraController.hasFlash()
        if (!hasFlash) {
            voice.announce("This camera has no flashlight.", AnnouncementPriority.HIGH)
            return
        }
        val applied = cameraController.setTorch(enabled)
        if (!applied) {
            voice.announce(
                "The flashlight could not be turned ${if (enabled) "on" else "off"}.",
                AnnouncementPriority.HIGH
            )
            return
        }
        _uiState.update { it.copy(torchEnabled = enabled) }
        voice.vibrate(HapticCue.SELECTION)
        voice.announce(if (enabled) "Flashlight on" else "Flashlight off", AnnouncementPriority.NORMAL)
    }

    fun cycleTimer() {
        val next = _uiState.value.timer.next
        _uiState.update { it.copy(timer = next) }
        voice.vibrate(HapticCue.SELECTION)
        voice.announce(if (next.seconds == 0) "Timer off" else "Timer ${next.label}", AnnouncementPriority.NORMAL)
    }

    fun setZoom(ratio: Float) {
        val state = _uiState.value
        val clamped = ratio.coerceIn(state.minZoom, state.maxZoom)
        _uiState.update { it.copy(zoomRatio = clamped) }
        cameraController.setZoom(clamped)
        voice.announceValue("zoom", "Zoom ${String.format(java.util.Locale.US, "%.1f", clamped)} times")
    }

    fun zoomIn() = setZoom(_uiState.value.zoomRatio * 1.25f)

    fun zoomOut() = setZoom(_uiState.value.zoomRatio / 1.25f)

    fun resetZoom() {
        setZoom(_uiState.value.minZoom.coerceAtLeast(1f))
        voice.announce("Zoom reset", AnnouncementPriority.NORMAL)
    }

    fun focusAt(x: Float, y: Float) {
        val view = previewView ?: return
        _uiState.update { it.copy(state = CameraState.FOCUSING) }
        cameraController.focusAt(x, y, view)
        voice.vibrate(HapticCue.FOCUS_LOCK)
        voice.playCue(AudioCue.FOCUS_LOCK)
        voice.announce("Focus locked at this point", AnnouncementPriority.LOW)
        viewModelScope.launch {
            delay(1200)
            if (_uiState.value.state == CameraState.FOCUSING && !captureInFlight.get()) {
                _uiState.update { it.copy(state = CameraState.READY) }
            }
        }
    }

    fun setExposureCompensation(index: Int) {
        val state = _uiState.value
        val clamped = index.coerceIn(state.exposureRange.first, state.exposureRange.last)
        _uiState.update { it.copy(exposureCompensation = clamped) }
        cameraController.setExposureCompensation(clamped)
        voice.announceValue(
            "exposure",
            "Exposure ${if (clamped == 0) "zero" else if (clamped > 0) "plus $clamped" else "minus ${-clamped}"}"
        )
    }

    fun toggleGrid() {
        val next = !_uiState.value.gridVisible
        _uiState.update { it.copy(gridVisible = next) }
        voice.announce(if (next) "Grid lines on" else "Grid lines off", AnnouncementPriority.NORMAL)
    }

    fun toggleLevel() {
        val next = !_uiState.value.levelVisible
        _uiState.update { it.copy(levelVisible = next) }
        voice.announce(if (next) "Level indicator on" else "Level indicator off", AnnouncementPriority.NORMAL)
    }

    fun updateLevel(degrees: Float) {
        _uiState.update { it.copy(levelDegrees = degrees) }
    }

    fun toggleAdvancedPanel() {
        val next = !_uiState.value.showAdvancedPanel
        _uiState.update { it.copy(showAdvancedPanel = next) }
        voice.announce(if (next) "Advanced controls opened" else "Advanced controls closed", AnnouncementPriority.NORMAL)
    }

    fun setFocusMode(mode: FocusMode) {
        _uiState.update { it.copy(focusMode = mode) }
        applyManual()
        voice.announce(mode.spoken, AnnouncementPriority.NORMAL)
    }

    fun setExposureMode(mode: ExposureMode) {
        _uiState.update { it.copy(exposureMode = mode) }
        applyManual()
        voice.announce(mode.spoken, AnnouncementPriority.NORMAL)
    }

    fun setWhiteBalance(mode: WhiteBalanceMode) {
        _uiState.update { it.copy(whiteBalanceMode = mode) }
        applyManual()
        voice.announce(mode.spoken, AnnouncementPriority.NORMAL)
    }

    fun setMeteringMode(mode: MeteringMode) {
        _uiState.update { it.copy(meteringMode = mode) }
        voice.announce(mode.spoken, AnnouncementPriority.NORMAL)
    }

    fun setStabilization(mode: StabilizationMode) {
        val lens = _uiState.value.activeLens
        if (mode == StabilizationMode.OPTICAL && lens?.supportsOpticalStabilization != true) {
            voice.announce("This camera has no optical stabilization.", AnnouncementPriority.HIGH)
            return
        }
        _uiState.update { it.copy(stabilization = mode) }
        applyManual()
        voice.announce(mode.spoken, AnnouncementPriority.NORMAL)
    }

    fun setIso(value: Int) {
        val state = _uiState.value
        if (state.activeLens?.supportsManualIso != true) {
            voice.announce("Manual ISO is not available on this camera.", AnnouncementPriority.HIGH)
            return
        }
        val clamped = value.coerceIn(state.isoRange.first, state.isoRange.last)
        _uiState.update { it.copy(manualIso = clamped, exposureMode = ExposureMode.MANUAL) }
        applyManual()
        voice.announceValue("iso", "ISO $clamped")
    }

    fun setShutterNanos(value: Long) {
        val state = _uiState.value
        val clamped = value.coerceIn(state.shutterRangeNanos.first, state.shutterRangeNanos.last)
        _uiState.update { it.copy(manualShutterNanos = clamped, exposureMode = ExposureMode.MANUAL) }
        applyManual()
        voice.announceValue(
            "shutter",
            "Shutter ${shutterLabel(clamped)}"
        )
    }

    fun setManualFocus(distance: Float) {
        val lens = _uiState.value.activeLens
        if (lens?.supportsManualFocus != true) {
            voice.announce("Manual focus is not available on this camera.", AnnouncementPriority.HIGH)
            return
        }
        _uiState.update { it.copy(manualFocusDistance = distance, focusMode = FocusMode.MANUAL) }
        applyManual()
        voice.announceValue("focus", "Focus distance $distance")
    }

    private fun applyManual() {
        val state = _uiState.value
        val applied = cameraController.applyManualControls(
            iso = if (state.exposureMode == ExposureMode.MANUAL) state.manualIso else null,
            exposureNanos = if (state.exposureMode == ExposureMode.MANUAL) state.manualShutterNanos else null,
            focusDistance = if (state.focusMode == FocusMode.MANUAL) state.manualFocusDistance else null,
            whiteBalance = state.whiteBalanceMode,
            exposureMode = state.exposureMode,
            focusMode = state.focusMode,
            stabilization = state.stabilization
        )
        if (!applied) {
            voice.announce(
                "This camera rejected the manual setting and stays in automatic mode.",
                AnnouncementPriority.HIGH
            )
            _uiState.update { it.copy(exposureMode = ExposureMode.AUTO, focusMode = FocusMode.AUTO) }
        }
    }

    fun setAspectRatio(option: AspectRatioOption) {
        _uiState.update { it.copy(aspectRatio = option) }
        voice.announce("Aspect ratio ${option.speech}", AnnouncementPriority.NORMAL)
        rebindIfPossible()
    }

    fun setPhotoFormat(format: PhotoFileFormat) {
        val capabilities = _uiState.value.capabilities
        if (format == PhotoFileFormat.RAW_JPEG && !capabilities.supportsRawCapture) {
            voice.announce("RAW capture is not supported by this camera.", AnnouncementPriority.HIGH)
            return
        }
        _uiState.update { it.copy(photoFormat = format) }
        voice.announce(format.spoken, AnnouncementPriority.NORMAL)
    }

    fun setVideoQuality(option: VideoQualityOption) {
        _uiState.update { it.copy(videoQuality = option) }
        voice.announce(option.displayLabel, AnnouncementPriority.NORMAL)
        rebindIfPossible()
    }

    fun setVideoFrameRate(fps: Int) {
        val capabilities = _uiState.value.capabilities
        if (!capabilities.videoFrameRates.contains(fps)) {
            voice.announce("$fps frames per second is not supported on this camera.", AnnouncementPriority.HIGH)
            return
        }
        _uiState.update { it.copy(videoFrameRate = fps) }
        voice.announce("$fps frames per second", AnnouncementPriority.NORMAL)
        rebindIfPossible()
    }

    fun setVideoCodec(codec: com.blindtechnexus.accessiblecamera.domain.model.VideoCodecOption) {
        _uiState.update { it.copy(videoCodec = codec) }
        voice.announce(codec.label, AnnouncementPriority.NORMAL)
    }

    fun setVideoBitrate(bitrate: Int) {
        _uiState.update { it.copy(videoBitrate = bitrate) }
        voice.announceValue("bitrate", "${bitrate / 1_000_000} megabits per second")
        rebindIfPossible()
    }

    /** Switches face guidance between off and the default three second interval. */
    fun toggleFaceGuidance() {
        val next = if (_uiState.value.faceGuidanceEnabled) {
            FaceDetectionInterval.OFF
        } else {
            FaceDetectionInterval.THREE_SECONDS
        }
        setFaceDetectionInterval(next)
    }

    /**
     * Sets how often ML Kit checks face alignment. The value is persisted and applied to the
     * analyzer immediately so the next frame uses the new cadence.
     */
    fun setFaceDetectionInterval(interval: FaceDetectionInterval) {
        _uiState.update {
            it.copy(
                faceDetectionInterval = interval,
                faceGuidance = when {
                    !interval.enabled -> FaceGuidance.Disabled
                    !_uiState.value.mlKitModelsReady -> FaceGuidance.Unavailable
                    else -> FaceGuidance.NoFace("Point the camera at a face")
                }
            )
        }
        frameAnalyzer.setFaceInterval(interval.seconds)
        frameAnalyzer.reset()
        viewModelScope.launch {
            settingsRepository.update { it.copy(faceDetectionInterval = interval) }
        }
        if (!interval.enabled) {
            voice.announce("Face detection off", AnnouncementPriority.NORMAL)
        } else {
            voice.announce("Face detection ${interval.label.lowercase()}", AnnouncementPriority.NORMAL)
            maybeOfferMlKitInstall()
        }
    }

    /** Sets how often the scene is described, including the every three second option. */
    fun setSceneDescriptionInterval(interval: SceneDescriptionInterval) {
        _uiState.update {
            it.copy(
                sceneDescriptionInterval = interval,
                sceneDescription = if (interval.enabled) it.sceneDescription else null
            )
        }
        frameAnalyzer.setSceneInterval(interval.seconds)
        viewModelScope.launch {
            settingsRepository.update { it.copy(sceneDescriptionInterval = interval) }
        }
        if (!interval.enabled) {
            voice.announce("Scene description off", AnnouncementPriority.NORMAL)
        } else {
            voice.announce("Describe scene ${interval.label.lowercase()}", AnnouncementPriority.NORMAL)
            maybeOfferMlKitInstall()
        }
    }

    fun setBarcodeScanningEnabled(enabled: Boolean) {
        frameAnalyzer.barcodeScanningEnabled = enabled
        _uiState.update { it.copy(barcodeScanningEnabled = enabled) }
        viewModelScope.launch {
            settingsRepository.update { it.copy(barcodeScanningEnabled = enabled) }
        }
        voice.announce(if (enabled) "Barcode reading on" else "Barcode reading off", AnnouncementPriority.NORMAL)
    }

    /** Runs one scene description immediately. */
    fun describeSceneNow() {
        if (!ensureMlKitReady("Scene description")) return
        frameAnalyzer.requestSceneDescription()
        frameAnalyzer.barcodeScanningEnabled = _uiState.value.barcodeScanningEnabled
        _uiState.update { it.copy(describeSceneBusy = true) }
        voice.announce("Describing the scene", AnnouncementPriority.LOW)
        viewModelScope.launch {
            delay(2_500)
            _uiState.update { it.copy(describeSceneBusy = false) }
        }
    }

    /** Runs one barcode read immediately. */
    fun scanBarcodeNow() {
        if (!ensureMlKitReady("Barcode reading")) return
        frameAnalyzer.requestBarcodeScan()
        voice.announce("Looking for a barcode", AnnouncementPriority.LOW)
    }

    private fun ensureMlKitReady(feature: String): Boolean {
        if (_uiState.value.mlKitModelsReady) return true
        _uiState.update { it.copy(showMlKitDownloadPrompt = true) }
        voice.announce(
            "$feature needs the on-device models. Download them first.",
            AnnouncementPriority.HIGH
        )
        return false
    }

    // region ML Kit model installation

    /** Starts the one-time model install from the Download Required dialog. */
    fun installMlKitModels() {
        _uiState.update { it.copy(showMlKitDownloadPrompt = false, mlKitFailure = null) }
        mlKitModels.install()
        voice.announce("Installing on-device models", AnnouncementPriority.HIGH)
    }

    fun cancelMlKitInstall() {
        // A deliberate cancel must not be followed by an immediate second prompt.
        mlKitPromptDismissed = true
        mlKitModels.cancel()
        _uiState.update {
            it.copy(mlKitInstalling = false, mlKitProgress = 0f, showMlKitDownloadPrompt = false)
        }
        voice.announce(
            "Model installation cancelled. Face detection and scene description stay off until you install them.",
            AnnouncementPriority.HIGH
        )
    }

    fun dismissMlKitPrompt() {
        mlKitPromptDismissed = true
        _uiState.update { it.copy(showMlKitDownloadPrompt = false) }
        voice.announce(
            "Download cancelled. You can install the models later from Settings.",
            AnnouncementPriority.NORMAL
        )
    }

    fun showMlKitPrompt() {
        mlKitPromptDismissed = false
        _uiState.update { it.copy(showMlKitDownloadPrompt = true, mlKitFailure = null) }
    }

    fun dismissMlKitFailure() {
        _uiState.update { it.copy(mlKitFailure = null) }
    }

    // endregion

    // endregion

    // region Announcements and helpers

    private fun announceGuidance(guidance: FaceGuidance) {
        val text = when (guidance) {
            is FaceGuidance.Aligned -> guidance.message
            is FaceGuidance.Misaligned -> guidance.message
            is FaceGuidance.NoFace -> guidance.hint
            is FaceGuidance.MultipleFaces -> "More than one face in view"
            else -> null
        } ?: return
        if (text == lastGuidanceText) return
        val now = System.currentTimeMillis()
        if (now - lastFaceAnnouncement < 1800) return
        lastFaceAnnouncement = now
        lastGuidanceText = text
        if (_uiState.value.faceGuidanceEnabled) {
            voice.announce(text, AnnouncementPriority.LOW)
        }
    }

    private fun announceFaceGuidance(text: String) {
        if (!_uiState.value.faceGuidanceEnabled) return
        voice.announce(text, AnnouncementPriority.LOW)
    }

    private fun announceBarcode(text: String) {
        _uiState.update { it.copy(lastBarcode = text) }
        voice.announce(text, AnnouncementPriority.HIGH)
    }

    fun refreshLatestThumbnail() {
        viewModelScope.launch {
            val latest = mediaRepository.latestThumbnail()
            _uiState.update { it.copy(lastSavedItem = latest ?: it.lastSavedItem) }
        }
    }

    fun shutterLabel(nanos: Long): String {
        val seconds = nanos / 1_000_000_000.0
        return when {
            seconds >= 1.0 -> String.format(java.util.Locale.US, "%.1f seconds", seconds)
            else -> "1 over ${(1.0 / seconds).toInt()} second"
        }
    }

    fun cycleAspectRatio() {
        val options = _uiState.value.capabilities.supportedAspectRatios
        if (options.isEmpty()) return
        val currentIndex = options.indexOfFirst { it.numerator == _uiState.value.aspectRatio.numerator }
        val next = options[(currentIndex + 1).coerceAtLeast(0) % options.size]
        setAspectRatio(next)
    }

    fun cycleWhiteBalance() {
        setWhiteBalance(_uiState.value.whiteBalanceMode.next)
    }

    fun cycleFocusMode() {
        setFocusMode(_uiState.value.focusMode.next)
    }

    fun cycleExposureMode() {
        setExposureMode(_uiState.value.exposureMode.next)
    }

    fun cycleMetering() {
        setMeteringMode(_uiState.value.meteringMode.next)
    }

    fun cycleStabilization() {
        setStabilization(_uiState.value.stabilization.next)
    }

    fun cycleVideoFrameRate() {
        val rates = _uiState.value.capabilities.videoFrameRates
        if (rates.isEmpty()) return
        val index = rates.indexOf(_uiState.value.videoFrameRate)
        setVideoFrameRate(rates[(index + 1) % rates.size])
    }

    fun cycleVideoQuality() {
        val options = _uiState.value.capabilities.videoQualities
        if (options.isEmpty()) return
        val index = options.indexOfFirst { it.qualityLabel == _uiState.value.videoQuality?.qualityLabel }
        setVideoQuality(options[(index + 1).coerceAtLeast(0) % options.size])
    }

    // region Voice commands

    /**
     * Starts or stops explicit listening. Returns true when listening started, so the screen
     * can request the microphone permission first.
     */
    fun toggleVoiceCommands(): Boolean {
        val current = voiceCommands.state.value
        if (current.listening) {
            voiceCommands.stopListening()
            return false
        }
        voiceCommands.refreshAvailability()
        return voiceCommands.startListening()
    }

    fun cancelVoiceCommand() = voiceCommands.cancelPending()

    /** Maps a recognised command onto the action that is meaningful right now. */
    private fun handleVoiceCommand(command: VoiceCommand): Boolean {
        val state = _uiState.value
        return when (command) {
            VoiceCommand.TAKE_PHOTO -> {
                if (state.selectedMode.isVideo) {
                    toggleRecording()
                } else {
                    onShutterPressed()
                }
                true
            }
            VoiceCommand.START_VIDEO -> {
                if (state.state == CameraState.RECORDING) {
                    false
                } else {
                    toggleRecording()
                    true
                }
            }
            VoiceCommand.STOP_VIDEO -> {
                if (state.state == CameraState.RECORDING || state.state == CameraState.PAUSED) {
                    stopRecording()
                    true
                } else {
                    false
                }
            }
            VoiceCommand.PAUSE_VIDEO -> {
                if (state.state == CameraState.RECORDING) {
                    pauseRecording()
                    true
                } else {
                    false
                }
            }
            VoiceCommand.RESUME_VIDEO -> {
                if (state.state == CameraState.PAUSED) {
                    resumeRecording()
                    true
                } else {
                    false
                }
            }
            VoiceCommand.ZOOM_IN -> {
                zoomIn()
                true
            }
            VoiceCommand.ZOOM_OUT -> {
                zoomOut()
                true
            }
            VoiceCommand.SWITCH_CAMERA -> {
                switchCamera()
                true
            }
            VoiceCommand.FLASH -> {
                cycleFlash()
                true
            }
            VoiceCommand.TIMER -> {
                cycleTimer()
                true
            }
            VoiceCommand.OPEN_GALLERY,
            VoiceCommand.OPEN_PHOTO,
            VoiceCommand.OPEN_SETTINGS,
            VoiceCommand.NEXT,
            VoiceCommand.PREVIOUS,
            VoiceCommand.BACK,
            VoiceCommand.SELECT,
            VoiceCommand.DESELECT,
            VoiceCommand.SHARE,
            VoiceCommand.PLAY_PAUSE -> false
            // Delete and confirmation are owned by the controller so a mis-recognition
            // can never destroy a file without a spoken confirmation.
            else -> false
        }
    }

    // endregion

    /** Reads back the current state for volume key and voice command handling. */
    fun speakCurrentState() {
        voice.announce(_uiState.value.previewDescription, AnnouncementPriority.HIGH)
    }

    companion object {
        /** Preferences used to gate the volume key shutter. */
        const val FEATURE = "camera"
    }

    // endregion

    override fun onCleared() {
        super.onCleared()
        countdownJob?.cancel()
        recordingTimerJob?.cancel()
        burstJob?.cancel()
        bindJob?.cancel()
        voiceCommands.detachHandler()
        voiceCommands.stopListening()
        cameraController.release()
        systemSounds.release()
    }
}
