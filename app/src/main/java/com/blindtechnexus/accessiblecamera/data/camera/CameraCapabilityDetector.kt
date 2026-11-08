/*
 * Accessible Camera and Gallery
 * Copyright (C) 2026 Sujan Rai, Blind Tech Nexus team.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.blindtechnexus.accessiblecamera.data.camera

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.DynamicRange
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import com.blindtechnexus.accessiblecamera.data.voice.OnDeviceSpeechRecognizer
import com.blindtechnexus.accessiblecamera.domain.model.AspectRatioOption
import com.blindtechnexus.accessiblecamera.domain.model.CameraCapabilities
import com.blindtechnexus.accessiblecamera.domain.model.CameraMode
import com.blindtechnexus.accessiblecamera.domain.model.Facing
import com.blindtechnexus.accessiblecamera.domain.model.LensInfo
import com.blindtechnexus.accessiblecamera.domain.model.VideoCodecOption
import com.blindtechnexus.accessiblecamera.domain.model.VideoQualityOption
import com.blindtechnexus.accessiblecamera.domain.repository.CapabilityRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Detects what this device can actually do and stores the result in the capability
 * registry. The UI never guesses: unsupported features are hidden or disabled with the
 * reason recorded here.
 */
@Singleton
class CameraCapabilityDetector @Inject constructor(
    private @ApplicationContext val context: Context
) : CapabilityRepository {

    private companion object {
        const val TAG = "CameraCapability"

        /** CameraX error codes surfaced as spoken failures. */
        fun describeCameraError(code: Int): String = when (code) {
            CameraState.ERROR_CAMERA_IN_USE -> "The camera is in use by another app"
            CameraState.ERROR_MAX_CAMERAS_IN_USE -> "Too many cameras are already open"
            CameraState.ERROR_OTHER_RECOVERABLE_ERROR -> "The camera reported a recoverable error"
            CameraState.ERROR_STREAM_CONFIG -> "The camera could not use this configuration"
            CameraState.ERROR_CAMERA_DISABLED -> "The camera is disabled by device policy"
            CameraState.ERROR_CAMERA_FATAL_ERROR -> "The camera reported a fatal error"
            CameraState.ERROR_DO_NOT_DISTURB_MODE_ENABLED -> "The camera is unavailable while do not disturb is on"
            else -> "The camera reported an unknown error"
        }
    }

    private val reader = CameraCharacteristicsReader()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _capabilities = MutableStateFlow(CameraCapabilities.Empty)
    override val capabilities: StateFlow<CameraCapabilities> = _capabilities.asStateFlow()

    @Volatile
    private var provider: ProcessCameraProvider? = null

    override suspend fun refresh(): CameraCapabilities = withContext(Dispatchers.Default) {
        val detected = runCatching { detect() }
            .onFailure { Log.w(TAG, "Capability detection failed", it) }
            .getOrElse { CameraCapabilities.Empty }
        _capabilities.value = detected
        detected
    }

    fun detectInBackground() {
        scope.launch { refresh() }
    }

    private suspend fun detect(): CameraCapabilities {
        val cameraProvider = obtainProvider() ?: return CameraCapabilities.Empty.copy(
            notes = listOf("Camera provider unavailable")
        )
        val infos = cameraProvider.availableCameraInfos
        if (infos.isEmpty()) return CameraCapabilities.Empty.copy(
            notes = listOf("Android reported no camera devices")
        )

        // Reading full Camera2 characteristics can fail on a few vendor implementations,
        // especially for physical sub-cameras. A camera that CameraX can see must never be
        // reported as missing, so every failure falls back to the information CameraX itself
        // exposes for the lens.
        val rawLenses = infos.mapNotNull { info ->
            runCatching { reader.read(info) }.getOrElse { error ->
                Log.w(TAG, "Camera2 characteristics unavailable, using CameraX fallback", error)
                runCatching { fallbackLens(info) }.getOrNull()
            }
        }
        if (rawLenses.isEmpty()) {
            // Even if no lens metadata can be read, CameraX can still bind these cameras.
            // Report them with conservative defaults instead of claiming there is no camera.
            val safeLenses = infos.mapNotNull { info -> runCatching { fallbackLens(info) }.getOrNull() }
            if (safeLenses.isEmpty()) return CameraCapabilities.Empty.copy(
                notes = listOf("Camera devices were found but could not be identified")
            )
            return capabilitiesFor(infos, safeLenses, cameraProvider)
        }

        val lenses = runCatching { reader.classify(rawLenses) }.getOrDefault(rawLenses)
        return capabilitiesFor(infos, lenses, cameraProvider)
    }

    /**
     * Minimal lens description built only from what CameraX reports, used when the deeper
     * Camera2 interop read fails.
     */
    private fun fallbackLens(info: androidx.camera.core.CameraInfo): LensInfo {
        val facing = when (info.lensFacing) {
            CameraSelector.LENS_FACING_FRONT -> Facing.FRONT
            CameraSelector.LENS_FACING_BACK -> Facing.BACK
            else -> Facing.EXTERNAL
        }
        val zoom = info.zoomState.value
        return LensInfo(
            id = runCatching {
                androidx.camera.camera2.interop.Camera2CameraInfo.from(info).cameraId
            }.getOrDefault("camera-${info.hashCode()}"),
            type = if (facing == Facing.FRONT) {
                com.blindtechnexus.accessiblecamera.domain.model.LensType.FRONT
            } else {
                com.blindtechnexus.accessiblecamera.domain.model.LensType.WIDE
            },
            facing = facing,
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

    /** Builds the capability registry from an already resolved list of lenses. */
    private suspend fun capabilitiesFor(
        infos: List<androidx.camera.core.CameraInfo>,
        lenses: List<LensInfo>,
        cameraProvider: ProcessCameraProvider
    ): CameraCapabilities {
        val backLenses = lenses.filter { it.facing == Facing.BACK }
        val frontLenses = lenses.filter { it.facing == Facing.FRONT }
        val primaryBack = backLenses.firstOrNull { it.type != com.blindtechnexus.accessiblecamera.domain.model.LensType.ULTRAWIDE }
            ?: backLenses.firstOrNull()

        val notes = mutableListOf<String>()
        val reasons = mutableMapOf<CameraMode, String>()

        // Photo is always available when a camera exists.
        val supportedModes = mutableSetOf(CameraMode.PHOTO, CameraMode.PRO)
        if (primaryBack == null || !(primaryBack.supportsManualIso || primaryBack.supportsManualFocus)) {
            supportedModes.remove(CameraMode.PRO)
            reasons[CameraMode.PRO] =
                "This device does not report manual ISO, shutter speed, or focus control."
        }

        // CameraX extensions are optional and vendor dependent.
        val extensionsManager = obtainExtensionsManager(cameraProvider)
        val backSelector = primaryBack?.let { selectorFor(it) } ?: CameraSelector.DEFAULT_BACK_CAMERA
        val frontSelector = frontLenses.firstOrNull()?.let { selectorFor(it) } ?: CameraSelector.DEFAULT_FRONT_CAMERA

        suspend fun extensionAvailable(mode: Int): Boolean {
            val manager = extensionsManager ?: return false
            val backAvailable = runCatching { manager.isExtensionAvailable(backSelector, mode) }.getOrDefault(false)
            if (backAvailable) return true
            return runCatching { manager.isExtensionAvailable(frontSelector, mode) }.getOrDefault(false)
        }

        if (extensionAvailable(ExtensionMode.BOKEH)) {
            supportedModes += CameraMode.PORTRAIT
        } else {
            reasons[CameraMode.PORTRAIT] =
                "This device has no portrait or bokeh camera extension, so background blur is not available."
        }
        if (extensionAvailable(ExtensionMode.NIGHT)) {
            supportedModes += CameraMode.NIGHT
        } else {
            reasons[CameraMode.NIGHT] =
                "This device has no night mode camera extension, so low light enhancement is not available."
        }
        if (extensionAvailable(ExtensionMode.HDR)) {
            supportedModes += CameraMode.HDR
        } else {
            reasons[CameraMode.HDR] =
                "This device has no high dynamic range camera extension."
        }

        // Video capability, qualities and frame rates.
        val primaryInfo = primaryBack?.let { lens ->
            infos.firstOrNull { info ->
                runCatching {
                    androidx.camera.camera2.interop.Camera2CameraInfo.from(info).cameraId == lens.id
                }.getOrDefault(false)
            }
        }
        val videoCapabilities: androidx.camera.video.VideoCapabilities? = if (primaryInfo != null) {
            runCatching { Recorder.getVideoCapabilities(primaryInfo) }.getOrNull()
        } else null

        val qualities: Set<Quality> = if (videoCapabilities != null) {
            val supported = runCatching {
                videoCapabilities.getSupportedQualities(DynamicRange.SDR)
            }.getOrNull()
            supported?.toSet() ?: emptySet()
        } else {
            emptySet()
        }

        if (qualities.isNotEmpty()) {
            supportedModes += CameraMode.VIDEO
        } else {
            reasons[CameraMode.VIDEO] = "This device cannot record video through CameraX."
        }

        val qualityOptions = listOf(
            Quality.UHD to "Ultra high definition 4K",
            Quality.FHD to "Full high definition 1080p",
            Quality.HD to "High definition 720p",
            Quality.SD to "Standard definition 480p"
        ).filter { (quality, _) -> qualities.contains(quality) }
            .map { (quality, label) ->
                VideoQualityOption(
                    qualityLabel = label,
                    width = qualityWidth(quality),
                    height = qualityHeight(quality),
                    speech = label
                )
            }

        val maxFrameRate = lenses.maxOfOrNull { it.maxVideoFrameRate } ?: 30
        // Only advertise frame rates the device actually declares, and always include the
        // safe default so the recorder can bind on unusual hardware.
        val frameRates: List<Int> = listOf(24, 30, 60, 120)
            .filter { fps -> fps == 30 || fps <= maxFrameRate }
            .ifEmpty { listOf(30) }
        if (supportedModes.contains(CameraMode.VIDEO) && maxFrameRate >= 60) {
            supportedModes += CameraMode.SLOW_MOTION
        } else {
            reasons[CameraMode.SLOW_MOTION] =
                "Slow motion needs a camera that records at 60 frames per second or faster. This device reports $maxFrameRate frames per second."
        }

        if (primaryInfo != null) {
            supportedModes += CameraMode.TIME_LAPSE
        } else {
            reasons[CameraMode.TIME_LAPSE] = "Time lapse needs a camera with photo capture support."
        }

        // Panorama needs repeated still capture, which every bound camera provides.
        if (primaryInfo != null) {
            supportedModes += CameraMode.PANORAMA
        } else {
            reasons[CameraMode.PANORAMA] = "Panorama needs a camera with still capture support."
        }

        val aspectRatios = listOf(
            AspectRatioOption(4, 3, "4:3"),
            AspectRatioOption(16, 9, "16:9"),
            AspectRatioOption(1, 1, "1:1")
        )

        val photoResolutions = backLenses.filter { it.maxResolutionWidth > 0 }
            .map { lens ->
                com.blindtechnexus.accessiblecamera.domain.model.ResolutionOption(
                    width = lens.maxResolutionWidth,
                    height = lens.maxResolutionHeight,
                    aspectRatioLabel = if (lens.maxResolutionWidth > lens.maxResolutionHeight) "Landscape" else "Portrait"
                )
            }

        val audioFeature = context.packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)
        val locationFeature = context.packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS) ||
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION)

        val codecs = buildList {
            add(VideoCodecOption("video/avc", "H.264, most compatible"))
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                add(VideoCodecOption("video/hevc", "H.265, smaller files"))
            }
        }

        val voiceAvailable = OnDeviceSpeechRecognizer.isSpeechRecognitionAvailable(context)

        notes += "Detected ${lenses.size} camera lenses."
        notes += "Hardware level: ${primaryBack?.hardwareLevel ?: "Unknown"}."

        return CameraCapabilities(
            // CameraX can bind any of these lenses, so the device definitely has a camera.
            hasAnyCamera = lenses.isNotEmpty() && infos.isNotEmpty(),
            lenses = lenses,
            supportedModes = supportedModes,
            modeUnavailableReasons = reasons,
            supportedAspectRatios = aspectRatios,
            photoResolutions = photoResolutions,
            videoQualities = qualityOptions,
            videoFrameRates = frameRates.ifEmpty { listOf(30) },
            videoCodecs = codecs,
            supportsAudioRecording = audioFeature,
            supportsBurst = primaryBack != null,
            supportsPhotoDuringVideo = primaryBack != null,
            supportsLocationTagging = locationFeature,
            supportsWatermark = true,
            supportsSceneDescription = true,
            supportsFaceDetection = true,
            supportsBarcodeScanning = true,
            supportsVoiceRecognition = voiceAvailable,
            maxZoom = lenses.maxOfOrNull { it.zoomMax } ?: 1f,
            minZoom = lenses.minOfOrNull { it.zoomMin } ?: 1f,
            notes = notes
        )
    }

    private suspend fun obtainProvider(): ProcessCameraProvider? {
        provider?.let { return it }
        return suspendCancellableCoroutine { continuation ->
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                val value = runCatching { future.get() }.getOrNull()
                provider = value
                if (continuation.isActive) continuation.resume(value) { }
            }, androidx.core.content.ContextCompat.getMainExecutor(context))
            continuation.invokeOnCancellation { }
        }
    }

    private suspend fun obtainExtensionsManager(cameraProvider: ProcessCameraProvider): ExtensionsManager? =
        suspendCancellableCoroutine { continuation ->
            val future = ExtensionsManager.getInstanceAsync(context, cameraProvider)
            future.addListener({
                val manager = runCatching { future.get() }.getOrNull()
                if (continuation.isActive) continuation.resume(manager) { }
            }, androidx.core.content.ContextCompat.getMainExecutor(context))
            continuation.invokeOnCancellation { }
        }

    fun selectorFor(lens: LensInfo?): CameraSelector {
        val facing = lens?.facing ?: Facing.BACK
        val base = CameraSelector.Builder()
            .requireLensFacing(
                if (facing == Facing.FRONT) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
            )
            .build()
        if (lens == null) return base
        return CameraSelector.Builder()
            .addCameraFilter { infos ->
                infos.filter { info ->
                    runCatching {
                        androidx.camera.camera2.interop.Camera2CameraInfo.from(info).cameraId == lens.id
                    }.getOrDefault(false)
                }
            }
            .requireLensFacing(
                if (facing == Facing.FRONT) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
            )
            .build()
    }

    private fun qualityRank(quality: Quality): Int = when (quality) {
        Quality.UHD -> 4
        Quality.FHD -> 3
        Quality.HD -> 2
        Quality.SD -> 1
        else -> 0
    }

    private fun qualityWidth(quality: Quality): Int = when (quality) {
        Quality.UHD -> 3840
        Quality.FHD -> 1920
        Quality.HD -> 1280
        Quality.SD -> 720
        else -> 0
    }

    private fun qualityHeight(quality: Quality): Int = when (quality) {
        Quality.UHD -> 2160
        Quality.FHD -> 1080
        Quality.HD -> 720
        Quality.SD -> 480
        else -> 0
    }

    /** Default named quality from settings. */
    fun qualityFromName(name: String): Quality = when (name.uppercase()) {
        "UHD", "4K" -> Quality.UHD
        "FHD", "1080P" -> Quality.FHD
        "HD", "720P" -> Quality.HD
        "SD", "480P" -> Quality.SD
        else -> Quality.FHD
    }

    /** Quality selector that degrades gracefully when a device lacks a resolution. */
    fun qualitySelector(quality: Quality): QualitySelector =
        QualitySelector.from(
            quality,
            androidx.camera.video.FallbackStrategy.higherQualityOrLowerThan(quality)
        )

    fun dynamicRangeFor(lens: LensInfo?): DynamicRange =
        if (lens?.supportsHdrVideo == true) DynamicRange.HLG_10_BIT else DynamicRange.SDR

    fun cameraStateDescription(state: CameraState.Type): String = when (state) {
        CameraState.Type.OPENING -> "Camera opening"
        CameraState.Type.OPEN -> "Camera open"
        CameraState.Type.CLOSING -> "Camera closing"
        CameraState.Type.CLOSED -> "Camera closed"
        else -> "Camera state unknown"
    }

}
