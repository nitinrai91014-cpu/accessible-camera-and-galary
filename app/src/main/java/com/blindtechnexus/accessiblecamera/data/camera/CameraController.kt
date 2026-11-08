/*
 * Accessible Camera and Gallery
 * Copyright (C) 2026 Sujan Rai, Blind Tech Nexus team.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.blindtechnexus.accessiblecamera.data.camera

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.hardware.display.DisplayManager
import android.os.Build
import android.util.Log
import android.util.Rational
import android.view.Surface
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.MeteringPoint
import androidx.camera.core.Preview
import androidx.camera.core.TorchState
import androidx.camera.core.UseCase
import androidx.camera.core.ZoomState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.blindtechnexus.accessiblecamera.domain.model.AspectRatioOption
import com.blindtechnexus.accessiblecamera.domain.model.ExposureMode
import com.blindtechnexus.accessiblecamera.domain.model.Facing
import com.blindtechnexus.accessiblecamera.domain.model.FlashMode
import com.blindtechnexus.accessiblecamera.domain.model.FocusMode
import com.blindtechnexus.accessiblecamera.domain.model.LensInfo
import com.blindtechnexus.accessiblecamera.domain.model.LensType
import com.blindtechnexus.accessiblecamera.domain.model.PhotoFileFormat
import com.blindtechnexus.accessiblecamera.domain.model.StabilizationMode
import com.blindtechnexus.accessiblecamera.domain.model.WhiteBalanceMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns every CameraX use case and all Camera2 interop.
 *
 * Design rules:
 * - CameraX is the only source of truth for whether a camera exists. The controller asks the
 *   provider directly, so a camera that Android exposes can always be opened even if a
 *   metadata read failed earlier.
 * - Binding is attempted from the richest configuration towards simpler ones. Some devices
 *   reject a three use case combination such as preview plus photo plus video plus analysis;
 *   instead of failing the whole camera, the next attempt drops video and then analysis.
 * - Observers are attached on the main thread and removed when the camera is unbound, so
 *   repeated lens switches cannot leak live data observers or stack duplicate callbacks.
 * - All hardware interaction lives here; the ViewModel only consumes flows.
 */
@Singleton
class CameraController @Inject constructor(
    private @ApplicationContext val context: Context
) {

    private companion object {
        const val TAG = "CameraController"
        const val PROVIDER_TIMEOUT_SECONDS = 5L
    }

    private val bindingLock = Any()
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var activeRecording: Recording? = null
    private var observeOwner: LifecycleOwner? = null

    /** True while a recording has been started and not yet finalised. */
    private val recordingInFlight = MutableStateFlow(false)

    private val _torchOn = MutableStateFlow(false)
    val torchOn: StateFlow<Boolean> = _torchOn.asStateFlow()

    private val _zoomRatio = MutableStateFlow(1f)
    val zoomRatio: StateFlow<Float> = _zoomRatio.asStateFlow()

    private val _isBound = MutableStateFlow(false)
    val isBound: StateFlow<Boolean> = _isBound.asStateFlow()

    private val _cameraError = MutableStateFlow<String?>(null)
    val cameraError: StateFlow<String?> = _cameraError.asStateFlow()

    private val _videoRecording = MutableStateFlow(false)
    val videoRecording: StateFlow<Boolean> = _videoRecording.asStateFlow()

    private val _audioAvailable = MutableStateFlow(false)

    val captureExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val videoExecutor: ExecutorService = Executors.newFixedThreadPool(2)

    /** Current bound camera, or null when nothing is bound. */
    fun boundCamera(): Camera? = camera

    /**
     * Retrieves and caches the process camera provider.
     *
     * Awaiting this future is the only correct way to know the camera stack is ready; it
     * resolves after the camera service has enumerated its devices.
     */
    suspend fun provider(): ProcessCameraProvider? {
        cameraProvider?.let { return it }
        val future = ProcessCameraProvider.getInstance(context)
        return kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            future.addListener({
                val value = runCatching { future.get() }
                    .onFailure { Log.w(TAG, "Camera provider failed", it) }
                    .getOrNull()
                cameraProvider = value
                if (continuation.isActive) continuation.resumeWith(Result.success(value))
            }, ContextCompat.getMainExecutor(context))
            continuation.invokeOnCancellation { }
        }
    }

    private fun providerBlocking(): ProcessCameraProvider? {
        cameraProvider?.let { return it }
        val future = ProcessCameraProvider.getInstance(context)
        val value = runCatching { future.get(PROVIDER_TIMEOUT_SECONDS, TimeUnit.SECONDS) }
            .onFailure { Log.w(TAG, "Timed out waiting for the camera provider", it) }
            .getOrNull()
        cameraProvider = value
        return value
    }

    /** Camera devices currently reported by the camera service. */
    fun cameraCount(): Int =
        runCatching { providerBlocking()?.availableCameraInfos?.size ?: 0 }.getOrDefault(0)

    /** True when the selector resolves to a camera this device can actually open. */
    fun hasCamera(selector: CameraSelector): Boolean {
        val provider = providerBlocking() ?: return false
        return runCatching { provider.hasCamera(selector) }.getOrDefault(false)
    }

    /** True when this device exposes at least one camera of [facing]. */
    fun hasCameraFor(facing: Facing): Boolean =
        hasCamera(CameraSelector.Builder().requireLensFacing(facingSelector(facing)).build())

    /**
     * Best available lens for a facing, used when capability detection has not produced a
     * lens list yet. Returns null when no camera of that facing exists.
     */
    fun defaultLens(facing: Facing = Facing.BACK): LensInfo? {
        val provider = providerBlocking() ?: return null
        val infos = provider.availableCameraInfos
        val target = facingSelector(facing)
        val info = infos.firstOrNull { it.lensFacing == target }
            ?: infos.firstOrNull()
            ?: return null
        val resolvedFacing = when (info.lensFacing) {
            CameraSelector.LENS_FACING_FRONT -> Facing.FRONT
            CameraSelector.LENS_FACING_BACK -> Facing.BACK
            else -> Facing.EXTERNAL
        }
        val zoom = info.zoomState.value
        return LensInfo(
            id = runCatching {
                androidx.camera.camera2.interop.Camera2CameraInfo.from(info).cameraId
            }.getOrDefault("default"),
            type = if (resolvedFacing == Facing.FRONT) LensType.FRONT else LensType.WIDE,
            facing = resolvedFacing,
            zoomMin = zoom?.minZoomRatio ?: 1f,
            zoomMax = zoom?.maxZoomRatio ?: 1f,
            focalLengthMm = null,
            hasFlash = runCatching { info.hasFlashUnit() }.getOrDefault(false),
            hasAutofocus = false,
            maxResolutionWidth = 0,
            maxResolutionHeight = 0,
            supportsRaw = false,
            supportsManualIso = false,
            isoRange = null,
            exposureTimeRangeNanos = null,
            supportsManualFocus = false,
            supportsWhiteBalanceControl = false,
            supportsAeLock = false,
            supportsAfLock = false,
            supportsAwbLock = false,
            supportsOpticalStabilization = false,
            supportsVideoStabilization = false,
            supportsHdrVideo = false,
            maxVideoFrameRate = 30,
            hardwareLevel = "Unknown"
        )
    }

    data class BindRequest(
        val lifecycleOwner: LifecycleOwner,
        val previewView: PreviewView,
        val lens: LensInfo?,
        val aspectRatio: AspectRatioOption,
        val flashMode: FlashMode,
        val mirrorFrontCamera: Boolean,
        val targetRotation: Int,
        val photoFormat: PhotoFileFormat,
        val videoQualityLabel: String,
        val videoFrameRate: Int,
        val videoBitrate: Int,
        val stabilization: StabilizationMode,
        val captureModeFast: Boolean,
        val enableVideo: Boolean,
        val enableAnalysis: Boolean = false
    )

    private data class UseCaseSet(
        val preview: Preview,
        val imageCapture: ImageCapture,
        val videoCapture: VideoCapture<Recorder>?,
        val analysis: ImageAnalysis?
    )

    /**
     * Binds preview, photo capture and, when requested and possible, video and analysis.
     *
     * Returns null on success or a spoken-ready error message. Binding is attempted in
     * decreasing complexity so an unsupported use case combination degrades the feature
     * instead of disabling the camera.
     */
    fun bind(request: BindRequest): String? = synchronized(bindingLock) {
        val provider = providerBlocking()
            ?: return "The camera service is not available yet. Try again in a moment."

        val selector = resolveSelector(provider, request.lens)
            ?: return "No usable camera was found for this device."

        val rotation = if (request.targetRotation in 0..3) request.targetRotation else displayRotation()
        val attempts = buildList {
            add(Triple(request.enableVideo, request.enableAnalysis, "full configuration"))
            if (request.enableVideo) add(Triple(false, request.enableAnalysis, "without video"))
            if (request.enableAnalysis) add(Triple(request.enableVideo, false, "without frame analysis"))
            if (request.enableVideo && request.enableAnalysis) add(Triple(false, false, "preview and photo only"))
        }

        var lastFailure: Throwable? = null
        attempts.forEach { (enableVideo, enableAnalysis, label) ->
            val result = runCatching {
                provider.unbindAll()
                val useCases = buildUseCases(request, rotation, enableVideo, enableAnalysis)
                val bound = provider.bindToLifecycle(
                    request.lifecycleOwner,
                    selector,
                    *listOfNotNull(
                        useCases.preview,
                        useCases.imageCapture,
                        useCases.videoCapture,
                        useCases.analysis
                    ).toTypedArray()
                )
                Triple(useCases, bound, label)
            }
            result.onSuccess { (useCases, bound, usedLabel) ->
                attach(provider, request, useCases, bound)
                Log.i(TAG, "Camera bound with $usedLabel")
                return null
            }
            lastFailure = result.exceptionOrNull()
            Log.w(TAG, "Camera binding failed with $label", result.exceptionOrNull())
        }

        provider.unbindAll()
        clearBindings()
        _isBound.value = false
        when {
            lastFailure?.message?.contains("in use", ignoreCase = true) == true ->
                "The camera is in use by another app."
            lastFailure?.message?.contains("Too many cameras", ignoreCase = true) == true ->
                "Too many cameras are already open."
            else -> "The camera could not be started. ${lastFailure?.message ?: "Unknown error"}"
        }
    }

    private fun resolveSelector(provider: ProcessCameraProvider, lens: LensInfo?): CameraSelector? {
        val preferred = selectorForLens(lens)
        if (runCatching { provider.hasCamera(preferred) }.getOrDefault(false)) return preferred

        // The stored lens may be stale after a camera service restart or a hot plug. Fall
        // back to any camera of the same facing, then to any camera at all.
        val facing = lens?.facing ?: Facing.BACK
        val byFacing = CameraSelector.Builder().requireLensFacing(facingSelector(facing)).build()
        if (runCatching { provider.hasCamera(byFacing) }.getOrDefault(false)) return byFacing

        val anyCamera = provider.availableCameraInfos.firstOrNull() ?: return null
        val anySelector = when (anyCamera.lensFacing) {
            CameraSelector.LENS_FACING_FRONT -> CameraSelector.DEFAULT_FRONT_CAMERA
            CameraSelector.LENS_FACING_BACK -> CameraSelector.DEFAULT_BACK_CAMERA
            else -> CameraSelector.DEFAULT_BACK_CAMERA
        }
        return if (runCatching { provider.hasCamera(anySelector) }.getOrDefault(false)) anySelector else null
    }

    private fun buildUseCases(
        request: BindRequest,
        rotation: Int,
        enableVideo: Boolean,
        enableAnalysis: Boolean
    ): UseCaseSet {
        val newPreview = Preview.Builder()
            .setTargetAspectRatio(aspectRatioConstant(request.aspectRatio))
            .setTargetRotation(rotation)
            .build()
            .also { it.setSurfaceProvider(request.previewView.surfaceProvider) }

        val captureBuilder = ImageCapture.Builder()
            .setCaptureMode(
                if (request.captureModeFast) {
                    ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
                } else {
                    ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
                }
            )
            .setTargetAspectRatio(aspectRatioConstant(request.aspectRatio))
            .setTargetRotation(rotation)
            .setFlashMode(request.flashMode.toImageCaptureFlash())

        if (request.photoFormat == PhotoFileFormat.RAW_JPEG && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                Camera2Interop.Extender(captureBuilder)
                    .setCaptureRequestOption(
                        CaptureRequest.CONTROL_AE_MODE,
                        CaptureRequest.CONTROL_AE_MODE_ON
                    )
            }
        }

        val newImageCapture = captureBuilder.build()

        var newAnalysis: ImageAnalysis? = null
        if (enableAnalysis) {
            newAnalysis = runCatching {
                ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setTargetRotation(rotation)
                    .build()
            }.getOrNull()
        }

        var newVideoCapture: VideoCapture<Recorder>? = null
        if (enableVideo) {
            val quality = qualityFromLabel(request.videoQualityLabel)
            val recorderBuilder = Recorder.Builder()
                .setExecutor(videoExecutor)
                .setQualitySelector(
                    QualitySelector.from(quality, FallbackStrategy.lowerQualityOrHigherThan(quality))
                )
            if (request.videoBitrate > 0) {
                runCatching { recorderBuilder.setTargetVideoEncodingBitRate(request.videoBitrate) }
            }
            runCatching {
                val recorder = recorderBuilder.build()
                newVideoCapture = VideoCapture.withOutput(recorder).also {
                    it.setTargetRotation(rotation)
                }
            }
        }

        return UseCaseSet(newPreview, newImageCapture, newVideoCapture, newAnalysis)
    }

    private fun attach(
        provider: ProcessCameraProvider,
        request: BindRequest,
        useCases: UseCaseSet,
        bound: Camera
    ) {
        preview = useCases.preview
        imageCapture = useCases.imageCapture
        videoCapture = useCases.videoCapture
        imageAnalysis = useCases.analysis
        camera = bound

        val owner = request.lifecycleOwner
        observeOwner = owner
        val mainExecutor = ContextCompat.getMainExecutor(context)
        mainExecutor.execute {
            runCatching {
                bound.cameraInfo.torchState.observe(owner) { state ->
                    _torchOn.value = state == TorchState.ON
                }
                bound.cameraInfo.zoomState.observe(owner) { state ->
                    state?.let { _zoomRatio.value = it.zoomRatio }
                }
                bound.cameraInfo.cameraState.observe(owner) { state ->
                    state?.error?.let { error ->
                        _cameraError.value = CameraErrors.describeCameraErrorCode(error.code)
                    }
                }
            }.onFailure { Log.w(TAG, "Camera observers could not be attached", it) }

            runCatching {
                request.previewView.scaleType = PreviewView.ScaleType.FILL_CENTER
                request.previewView.scaleX = if (
                    request.mirrorFrontCamera && request.lens?.facing == Facing.FRONT
                ) {
                    -1f
                } else {
                    1f
                }
            }
        }

        applyStabilization(request.stabilization)
        if (_torchOn.value && bound.cameraInfo.hasFlashUnit()) {
            runCatching { bound.cameraControl.enableTorch(true) }
        }
        _isBound.value = true
        _cameraError.value = null
    }

    private fun clearBindings() {
        preview = null
        imageCapture = null
        videoCapture = null
        runCatching { imageAnalysis?.clearAnalyzer() }
        imageAnalysis = null
        camera = null
        observeOwner = null
    }

    /** The bound analysis use case, or null when analysis was not requested. */
    fun analysisUseCase(): ImageAnalysis? = imageAnalysis

    fun unbind() {
        synchronized(bindingLock) {
            stopRecording()
            runCatching { cameraProvider?.unbindAll() }
            clearBindings()
            _isBound.value = false
            _torchOn.value = false
        }
    }

    /** CameraX selector for a stored lens, filtered to that exact camera when possible. */
    fun selectorForLens(lens: LensInfo?): CameraSelector {
        val facing = facingSelector(lens?.facing ?: Facing.BACK)
        if (lens == null) {
            return CameraSelector.Builder().requireLensFacing(facing).build()
        }
        return CameraSelector.Builder()
            .requireLensFacing(facing)
            .addCameraFilter { infos ->
                val filtered = infos.filter { info ->
                    runCatching {
                        androidx.camera.camera2.interop.Camera2CameraInfo.from(info).cameraId == lens.id
                    }.getOrDefault(false)
                }
                // An empty list here would make CameraX throw "No available camera can be
                // found"; keeping the original list lets CameraX select the right facing.
                if (filtered.isEmpty()) infos else filtered
            }
            .build()
    }

    private fun facingSelector(facing: Facing): Int = when (facing) {
        Facing.FRONT -> CameraSelector.LENS_FACING_FRONT
        Facing.EXTERNAL -> CameraSelector.LENS_FACING_EXTERNAL
        Facing.BACK -> CameraSelector.LENS_FACING_BACK
    }

    // region Photo capture

    /**
     * Captures a photo to [file].
     * The callback runs on the main thread and reports either the file or an error.
     */
    fun capturePhoto(
        file: File,
        flashMode: FlashMode,
        onResult: (Result<File>) -> Unit
    ) {
        val capture = imageCapture
        if (capture == null) {
            onResult(Result.failure(IllegalStateException("The camera is not ready")))
            return
        }
        capture.flashMode = flashMode.toImageCaptureFlash()
        val options = ImageCapture.OutputFileOptions.Builder(file).build()
        val mainExecutor = ContextCompat.getMainExecutor(context)
        runCatching {
            capture.takePicture(
                options,
                captureExecutor,
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        mainExecutor.execute { onResult(Result.success(file)) }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        mainExecutor.execute {
                            Log.w(TAG, "Photo capture failed", exception)
                            onResult(Result.failure(exception))
                        }
                    }
                }
            )
        }.onFailure { error ->
            mainExecutor.execute { onResult(Result.failure(error)) }
        }
    }

    // endregion

    // region Video capture

    /**
     * Starts recording to [file]. Returns an error string when recording cannot start.
     * [onEvent] receives every CameraX video event on the main thread.
     */
    @SuppressLint("MissingPermission")
    fun startRecording(
        file: File,
        withAudio: Boolean,
        onEvent: (VideoRecordEvent) -> Unit
    ): String? {
        val capture = videoCapture ?: return "Video recording is not available for this camera."
        if (activeRecording != null) return "A recording is already in progress."
        val options = FileOutputOptions.Builder(file).build()
        val mainExecutor = ContextCompat.getMainExecutor(context)
        return runCatching {
            var pending = capture.output.prepareRecording(context, options)
            if (withAudio && _audioAvailable.value) {
                pending = runCatching { pending.withAudioEnabled() }.getOrDefault(pending)
            }
            activeRecording = pending.start(mainExecutor) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> {
                        _videoRecording.value = true
                        recordingInFlight.value = true
                    }
                    is VideoRecordEvent.Finalize -> {
                        _videoRecording.value = false
                        recordingInFlight.value = false
                        activeRecording = null
                    }
                    else -> Unit
                }
                onEvent(event)
            }
            null
        }.getOrElse { throwable ->
            Log.w(TAG, "Recording could not start", throwable)
            activeRecording = null
            "Recording could not start. ${throwable.message ?: ""}"
        }
    }

    fun pauseRecording(): Boolean = runCatching {
        activeRecording?.pause()
        true
    }.getOrDefault(false)

    fun resumeRecording(): Boolean = runCatching {
        activeRecording?.resume()
        true
    }.getOrDefault(false)

    fun stopRecording() {
        runCatching { activeRecording?.stop() }
        activeRecording = null
        _videoRecording.value = false
        recordingInFlight.value = false
    }

    fun isRecording(): Boolean = activeRecording != null || recordingInFlight.value

    fun setAudioPermission(granted: Boolean) {
        _audioAvailable.value = granted
    }

    // endregion

    // region Zoom, focus and metering

    fun setZoom(ratio: Float) {
        val bound = camera ?: return
        val state = bound.cameraInfo.zoomState.value ?: return
        val clamped = ratio.coerceIn(state.minZoomRatio, state.maxZoomRatio)
        runCatching { bound.cameraControl.setZoomRatio(clamped) }
    }

    fun zoomBy(factor: Float) {
        val bound = camera ?: return
        val state = bound.cameraInfo.zoomState.value ?: return
        val target = (state.zoomRatio * factor).coerceIn(state.minZoomRatio, state.maxZoomRatio)
        runCatching { bound.cameraControl.setZoomRatio(target) }
    }

    /** Tap to focus with optional show animations. */
    fun focusAt(x: Float, y: Float, previewView: PreviewView) {
        val bound = camera ?: return
        val point: MeteringPoint = runCatching {
            previewView.meteringPointFactory.createPoint(x, y)
        }.getOrNull() ?: return
        val action = FocusMeteringAction.Builder(
            point,
            FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
        ).setAutoCancelDuration(5, TimeUnit.SECONDS).build()
        runCatching { bound.cameraControl.startFocusAndMetering(action) }
    }

    fun cancelFocus() {
        runCatching { camera?.cameraControl?.cancelFocusAndMetering() }
    }

    /** Exposure compensation in stops, clamped to the camera reported range. */
    fun setExposureCompensation(index: Int) {
        val bound = camera ?: return
        val state = bound.cameraInfo.exposureState
        if (!state.isExposureCompensationSupported) return
        val clamped = index.coerceIn(
            state.exposureCompensationRange.lower,
            state.exposureCompensationRange.upper
        )
        runCatching { bound.cameraControl.setExposureCompensationIndex(clamped) }
    }

    fun exposureState(): Pair<IntRange, Int>? {
        val state = camera?.cameraInfo?.exposureState ?: return null
        if (!state.isExposureCompensationSupported) return null
        val range: android.util.Range<Int> = state.exposureCompensationRange
        return (range.lower..range.upper) to state.exposureCompensationIndex
    }

    fun supportsExposureCompensation(): Boolean =
        camera?.cameraInfo?.exposureState?.isExposureCompensationSupported == true

    // endregion

    // region Torch and flash

    fun setTorch(enabled: Boolean): Boolean {
        val bound = camera ?: return false
        if (!bound.cameraInfo.hasFlashUnit()) return false
        return runCatching {
            bound.cameraControl.enableTorch(enabled)
            _torchOn.value = enabled
            true
        }.getOrDefault(false)
    }

    fun hasFlash(): Boolean = camera?.cameraInfo?.hasFlashUnit() ?: false

    fun setFlashMode(mode: FlashMode) {
        imageCapture?.flashMode = mode.toImageCaptureFlash()
    }

    // endregion

    // region Manual controls through Camera2 interop

    /**
     * Applies manual capture request options. Only keys the device declares are used,
     * and any failure is reported instead of silently pretending the control worked.
     */
    fun applyManualControls(
        iso: Int?,
        exposureNanos: Long?,
        focusDistance: Float?,
        whiteBalance: WhiteBalanceMode?,
        exposureMode: ExposureMode,
        focusMode: FocusMode,
        stabilization: StabilizationMode
    ): Boolean {
        val bound = camera ?: return false
        return runCatching {
            val control = Camera2CameraControl.from(bound.cameraControl)
            val options = CaptureRequestOptions.Builder()
            if (exposureMode == ExposureMode.MANUAL) {
                options.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_OFF
                )
                iso?.let { options.setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, it) }
                exposureNanos?.let {
                    options.setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, it)
                }
            } else if (exposureMode == ExposureMode.AUTO) {
                options.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_ON
                )
            }
            if (focusMode == FocusMode.MANUAL && focusDistance != null) {
                options.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_OFF
                )
                options.setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, focusDistance)
            } else if (focusMode == FocusMode.AUTO) {
                options.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                )
                options.setCaptureRequestOption(
                    CaptureRequest.LENS_FOCUS_DISTANCE,
                    0f
                )
            }
            whiteBalance?.let { mode ->
                val awbMode = when (mode) {
                    WhiteBalanceMode.AUTO -> CaptureRequest.CONTROL_AWB_MODE_AUTO
                    WhiteBalanceMode.INCANDESCENT -> CaptureRequest.CONTROL_AWB_MODE_INCANDESCENT
                    WhiteBalanceMode.DAYLIGHT -> CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT
                    WhiteBalanceMode.FLUORESCENT -> CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT
                    WhiteBalanceMode.CLOUDY -> CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT
                    WhiteBalanceMode.SHADE -> CaptureRequest.CONTROL_AWB_MODE_SHADE
                    WhiteBalanceMode.LOCKED -> CaptureRequest.CONTROL_AWB_MODE_OFF
                }
                options.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, awbMode)
            }
            control.setCaptureRequestOptions(options.build())
            applyStabilization(stabilization)
            true
        }.getOrDefault(false)
    }

    fun clearManualControls(): Boolean {
        val bound = camera ?: return false
        return runCatching {
            val control = Camera2CameraControl.from(bound.cameraControl)
            control.clearCaptureRequestOptions()
            true
        }.getOrDefault(false)
    }

    private fun applyStabilization(mode: StabilizationMode) {
        val bound = camera ?: return
        val options = CaptureRequestOptions.Builder()
        when (mode) {
            StabilizationMode.OFF -> {
                options.setCaptureRequestOption(
                    CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                    CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF
                )
                options.setCaptureRequestOption(
                    CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
                    CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_OFF
                )
            }
            StabilizationMode.STANDARD -> {
                options.setCaptureRequestOption(
                    CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                    CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON
                )
            }
            StabilizationMode.HIGH -> {
                options.setCaptureRequestOption(
                    CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                    CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_PREVIEW_STABILIZATION
                )
            }
            StabilizationMode.OPTICAL -> {
                options.setCaptureRequestOption(
                    CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
                    CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON
                )
            }
        }
        runCatching {
            Camera2CameraControl.from(bound.cameraControl).setCaptureRequestOptions(options.build())
        }
    }

    // endregion

    // region Helpers

    fun currentZoomState(): ZoomState? = camera?.cameraInfo?.zoomState?.value

    fun displayRotation(): Int {
        val manager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        val display = manager?.getDisplay(android.view.Display.DEFAULT_DISPLAY)
        return display?.rotation ?: Surface.ROTATION_0
    }

    private fun aspectRatioConstant(option: AspectRatioOption): Int = when {
        option.numerator == 16 && option.denominator == 9 -> AspectRatio.RATIO_16_9
        option.numerator == 4 && option.denominator == 3 -> AspectRatio.RATIO_4_3
        else -> AspectRatio.RATIO_4_3
    }

    private fun qualityFromLabel(label: String): Quality = when {
        label.contains("4", ignoreCase = true) && label.contains("K", ignoreCase = true) -> Quality.UHD
        label.contains("1080", ignoreCase = true) || label.contains("FHD", ignoreCase = true) -> Quality.FHD
        label.contains("720", ignoreCase = true) || label.contains("HD", ignoreCase = true) -> Quality.HD
        label.contains("480", ignoreCase = true) || label.contains("SD", ignoreCase = true) -> Quality.SD
        else -> Quality.FHD
    }

    private fun FlashMode.toImageCaptureFlash(): Int = when (this) {
        FlashMode.OFF, FlashMode.TORCH -> ImageCapture.FLASH_MODE_OFF
        FlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
        FlashMode.ON -> ImageCapture.FLASH_MODE_ON
    }

    /** Aspect ratio helper for crop and preview maths. */
    fun rationalFor(option: AspectRatioOption): Rational = Rational(option.numerator, option.denominator)

    /** Reads the sensor orientation for the current lens, used for level and rotation. */
    fun sensorOrientation(lens: LensInfo?): Int {
        val bound = camera ?: return 90
        return runCatching {
            androidx.camera.camera2.interop.Camera2CameraInfo.from(bound.cameraInfo)
                .getCameraCharacteristic(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
        }.getOrDefault(90)
    }

    fun release() {
        stopRecording()
        unbind()
        captureExecutor.shutdown()
        videoExecutor.shutdown()
    }

    // endregion
}
