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
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.MeteringPoint
import androidx.camera.core.Preview
import androidx.camera.core.TorchState
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
import com.blindtechnexus.accessiblecamera.domain.model.FlashMode
import com.blindtechnexus.accessiblecamera.domain.model.FocusMode
import com.blindtechnexus.accessiblecamera.domain.model.LensInfo
import com.blindtechnexus.accessiblecamera.domain.model.PhotoFileFormat
import com.blindtechnexus.accessiblecamera.domain.model.StabilizationMode
import com.blindtechnexus.accessiblecamera.domain.model.WhiteBalanceMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns CameraX use cases and Camera2 interop.
 *
 * All hardware interaction lives here so the ViewModel only consumes flows. Bindings are
 * guarded by a lock because CameraX binding is not re-entrant, and every call is main
 * thread safe.
 */
@Singleton
class CameraController @Inject constructor(
    private val context: Context
) {

    private companion object {
        const val TAG = "CameraController"
    }

    private val bindingLock = Any()
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var imageAnalysis: androidx.camera.core.ImageAnalysis? = null
    private var activeRecording: Recording? = null

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

    /** Current bound camera info if any. */
    fun boundCamera(): Camera? = camera

    /** Retrieves and caches the process camera provider. */
    suspend fun provider(): ProcessCameraProvider? {
        cameraProvider?.let { return it }
        val future = ProcessCameraProvider.getInstance(context)
        return kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            future.addListener({
                val value = runCatching { future.get() }.getOrNull()
                cameraProvider = value
                if (continuation.isActive) continuation.resume(value)
            }, ContextCompat.getMainExecutor(context))
        }
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

    /**
     * Binds preview, photo capture and optionally video capture.
     * Returns null on success or a spoken-ready error message.
     */
    fun bind(request: BindRequest): String? = synchronized(bindingLock) {
        val provider = cameraProvider ?: run {
            val future = ProcessCameraProvider.getInstance(context)
            val value = runCatching { future.get(2, java.util.concurrent.TimeUnit.SECONDS) }.getOrNull()
            cameraProvider = value
            value
        } ?: return "The camera provider is not ready yet."

        return runCatching {
            provider.unbindAll()
            val selector = selectorForLens(request.lens)

            val previewBuilder = Preview.Builder()
                .setTargetAspectRatio(aspectRatioConstant(request.aspectRatio))
                .setTargetRotation(request.targetRotation)
            val newPreview = previewBuilder.build().also {
                it.setSurfaceProvider(request.previewView.surfaceProvider)
            }

            val captureBuilder = ImageCapture.Builder()
                .setCaptureMode(
                    if (request.captureModeFast) {
                        ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
                    } else {
                        ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
                    }
                )
                .setTargetAspectRatio(aspectRatioConstant(request.aspectRatio))
                .setTargetRotation(request.targetRotation)
                .setFlashMode(request.flashMode.toImageCaptureFlash())

            if (request.photoFormat == PhotoFileFormat.RAW_JPEG &&
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            ) {
                runCatching {
                    Camera2Interop.Extender(captureBuilder)
                        .setCaptureRequestOption(
                            CaptureRequest.CONTROL_AE_MODE,
                            CaptureRequest.CONTROL_AE_MODE_ON
                        )
                }
            }

            val newImageCapture = captureBuilder.build()

            val useCases = mutableListOf<androidx.camera.core.UseCase>(newPreview, newImageCapture)

            var newAnalysis: androidx.camera.core.ImageAnalysis? = null
            if (request.enableAnalysis) {
                val analysisBuilder = androidx.camera.core.ImageAnalysis.Builder()
                    .setBackpressureStrategy(androidx.camera.core.ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setTargetRotation(request.targetRotation)
                newAnalysis = runCatching { analysisBuilder.build() }.getOrNull()
                newAnalysis?.let { useCases += it }
            }
            var newVideoCapture: VideoCapture<Recorder>? = null
            if (request.enableVideo) {
                val quality = qualityFromLabel(request.videoQualityLabel)
                val recorderBuilder = Recorder.Builder()
                    .setExecutor(videoExecutor)
                    .setQualitySelector(
                        QualitySelector.from(quality, FallbackStrategy.lowerQualityOrHigherThan(quality))
                    )
                if (request.videoBitrate > 0) {
                    runCatching { recorderBuilder.setTargetVideoEncodingBitRate(request.videoBitrate) }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    runCatching { recorderBuilder.setAspectRatio(aspectRatioConstant(request.aspectRatio)) }
                }
                val recorder = recorderBuilder.build()
                newVideoCapture = VideoCapture.withOutput(recorder)
                newVideoCapture.setTargetRotation(request.targetRotation)
                useCases += newVideoCapture
            }

            val boundCamera = provider.bindToLifecycle(request.lifecycleOwner, selector, *useCases.toTypedArray())

            preview = newPreview
            imageCapture = newImageCapture
            imageAnalysis = newAnalysis
            videoCapture = newVideoCapture
            camera = boundCamera

            boundCamera.cameraInfo.torchState.observe(request.lifecycleOwner) { state ->
                _torchOn.value = state == TorchState.ON
            }
            boundCamera.cameraInfo.zoomState.observe(request.lifecycleOwner) { state ->
                state?.let { _zoomRatio.value = it.zoomRatio }
            }
            boundCamera.cameraInfo.cameraState.observe(request.lifecycleOwner) { state ->
                state?.error?.let { error ->
                    _cameraError.value = CameraErrors.describeCameraErrorCode(error.code)
                }
            }

            applyStabilization(request.stabilization)
            runCatching { request.previewView.scaleType = PreviewView.ScaleType.FILL_CENTER }
            if (request.mirrorFrontCamera && request.lens?.facing == com.blindtechnexus.accessiblecamera.domain.model.Facing.FRONT) {
                runCatching { request.previewView.scaleX = -1f }
            } else {
                runCatching { request.previewView.scaleX = 1f }
            }
            _isBound.value = true
            _cameraError.value = null
            null
        }.getOrElse { throwable ->
            Log.w(TAG, "Camera binding failed", throwable)
            _isBound.value = false
            when {
                throwable.message?.contains("in use", ignoreCase = true) == true ->
                    "The camera is in use by another app."
                throwable.message?.contains("Too many cameras", ignoreCase = true) == true ->
                    "Too many cameras are already open."
                else -> "The camera could not be started. ${throwable.message ?: "Unknown error"}"
            }
        }
    }

    /** The bound analysis use case, or null when analysis was not requested. */
    fun analysisUseCase(): androidx.camera.core.ImageAnalysis? = imageAnalysis

    fun unbind() {
        synchronized(bindingLock) {
            runCatching { cameraProvider?.unbindAll() }
            preview = null
            imageCapture = null
            videoCapture = null
            runCatching { imageAnalysis?.clearAnalyzer() }
            imageAnalysis = null
            camera = null
            _isBound.value = false
            _torchOn.value = false
        }
    }

    fun selectorForLens(lens: LensInfo?): CameraSelector {
        val facing = when (lens?.facing) {
            com.blindtechnexus.accessiblecamera.domain.model.Facing.FRONT -> CameraSelector.LENS_FACING_FRONT
            com.blindtechnexus.accessiblecamera.domain.model.Facing.EXTERNAL -> CameraSelector.LENS_FACING_EXTERNAL
            else -> CameraSelector.LENS_FACING_BACK
        }
        val base = CameraSelector.Builder().requireLensFacing(facing).build()
        if (lens == null) return base
        return CameraSelector.Builder()
            .requireLensFacing(facing)
            .addCameraFilter { infos ->
                val filtered = infos.filter { info ->
                    runCatching {
                        androidx.camera.camera2.interop.Camera2CameraInfo.from(info).cameraId == lens.id
                    }.getOrDefault(false)
                }
                if (filtered.isEmpty()) infos else filtered
            }
            .build()
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
        capture.takePicture(
            options,
            captureExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    mainExecutor.execute {
                        outputFileResults.savedUri?.let { uri ->
                            runCatching { context.contentResolver.openOutputStream(uri)?.close() }
                        }
                        onResult(Result.success(file))
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    mainExecutor.execute {
                        Log.w(TAG, "Photo capture failed", exception)
                        onResult(Result.failure(exception))
                    }
                }
            }
        )
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
            val pending = capture.output.prepareRecording(context, options)
            if (withAudio && _audioAvailable.value) {
                runCatching { pending.withAudioEnabled() }
            }
            activeRecording = pending.start(mainExecutor) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> {
                        _videoRecording.value = true
                    }
                    is VideoRecordEvent.Finalize -> {
                        _videoRecording.value = false
                        activeRecording = null
                    }
                }
                onEvent(event)
            }
            null
        }.getOrElse { throwable ->
            Log.w(TAG, "Recording could not start", throwable)
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
    }

    fun isRecording(): Boolean = activeRecording != null

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
        ).setAutoCancelDuration(5, java.util.concurrent.TimeUnit.SECONDS).build()
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
