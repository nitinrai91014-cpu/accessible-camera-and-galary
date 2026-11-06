/*
 * Accessible Camera and Gallery
 * Copyright (C) 2026 Sujan Rai, Blind Tech Nexus team.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.blindtechnexus.accessiblecamera.data.camera

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.util.Range
import android.util.Size
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.CameraInfo
import com.blindtechnexus.accessiblecamera.domain.model.Facing
import com.blindtechnexus.accessiblecamera.domain.model.LensInfo
import com.blindtechnexus.accessiblecamera.domain.model.LensType

/**
 * Reads Camera2 characteristics through the CameraX [CameraInfo] that owns them.
 *
 * Reading through CameraX guarantees every reported lens can actually be bound, which
 * avoids advertising physical cameras that CameraX hides behind a logical camera.
 */
class CameraCharacteristicsReader {

    fun read(cameraInfo: CameraInfo): LensInfo {
        val info = Camera2CameraInfo.from(cameraInfo)
        fun <T> characteristic(key: CameraCharacteristics.Key<T>): T? =
            runCatching { info.getCameraCharacteristic(key) }.getOrNull()

        val facing = when (characteristic(CameraCharacteristics.LENS_FACING)) {
            CameraCharacteristics.LENS_FACING_FRONT -> Facing.FRONT
            CameraCharacteristics.LENS_FACING_BACK -> Facing.BACK
            else -> Facing.EXTERNAL
        }
        val focalLengths = characteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
        val focal = focalLengths?.firstOrNull()
        val sensitivityRange = characteristic(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
        val exposureRange = characteristic(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
        val minFocusDistance = characteristic(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f
        val pixelArray = characteristic(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE) ?: Size(0, 0)
        val capabilities = characteristic(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()
        val hardwareLevel = characteristic(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL) ?: -1
        val availableOpticalStabilization = characteristic(
            CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION
        ) ?: intArrayOf()
        val availableVideoStabilization = characteristic(
            CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES
        ) ?: intArrayOf()
        // CONTROL_AWB_AVAILABLE_MODES only exists on API 33 and later. Fall back to
        // assuming auto white balance only when the newer key is unavailable.
        val availableAwbModes: IntArray = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            runCatching {
                characteristic(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES)
            }.getOrNull() ?: intArrayOf(CameraMetadata.CONTROL_AWB_MODE_AUTO)
        } else {
            intArrayOf(CameraMetadata.CONTROL_AWB_MODE_AUTO)
        }
        val availableAfModes = characteristic(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES) ?: intArrayOf()
        val availableAeModes = characteristic(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES) ?: intArrayOf()
        val maxFrameRate = characteristic(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
            ?.maxOfOrNull { it.upper } ?: 30
        val supportsHdrVideo = runCatching {
            characteristic(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
                ?.contains(CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE) == true &&
                characteristic(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES)
                    ?.contains(CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_PREVIEW_STABILIZATION) == true
        }.getOrDefault(false)
        val zoomState = cameraInfo.zoomState.value
        val zoomMin = zoomState?.minZoomRatio ?: 1f
        val zoomMax = zoomState?.maxZoomRatio ?: 1f

        return LensInfo(
            id = info.cameraId,
            type = LensType.UNKNOWN,
            facing = facing,
            zoomMin = zoomMin,
            zoomMax = zoomMax,
            focalLengthMm = focal,
            hasFlash = characteristic(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false,
            hasAutofocus = availableAfModes.any {
                it == CameraMetadata.CONTROL_AF_MODE_AUTO ||
                    it == CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE ||
                    it == CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_VIDEO
            },
            maxResolutionWidth = pixelArray.width,
            maxResolutionHeight = pixelArray.height,
            supportsRaw = capabilities.contains(CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_RAW),
            supportsManualIso = sensitivityRange != null && supportsManualExposure(availableAeModes),
            isoRange = sensitivityRange?.let { it.lower..it.upper },
            exposureTimeRangeNanos = exposureRange?.let { it.lower..it.upper },
            supportsManualFocus = minFocusDistance > 0f &&
                availableAfModes.contains(CameraMetadata.CONTROL_AF_MODE_OFF),
            supportsWhiteBalanceControl = availableAwbModes.any {
                it != CameraMetadata.CONTROL_AWB_MODE_AUTO
            },
            supportsAeLock = availableAeModes.contains(CameraMetadata.CONTROL_AE_MODE_OFF),
            supportsAfLock = availableAfModes.contains(CameraMetadata.CONTROL_AF_MODE_OFF),
            supportsAwbLock = availableAwbModes.contains(CameraMetadata.CONTROL_AWB_MODE_OFF),
            supportsOpticalStabilization = availableOpticalStabilization
                .contains(CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON),
            supportsVideoStabilization = availableVideoStabilization
                .contains(CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_ON),
            supportsHdrVideo = supportsHdrVideo,
            maxVideoFrameRate = maxFrameRate,
            hardwareLevel = describeHardwareLevel(hardwareLevel)
        )
    }

    /** Assigns ultrawide, wide and telephoto types after all lenses are known. */
    fun classify(lenses: List<LensInfo>): List<LensInfo> {
        val back = lenses.filter { it.facing == Facing.BACK }
        val front = lenses.filter { it.facing == Facing.FRONT }
        val others = lenses.filter { it.facing == Facing.EXTERNAL }
        val classifiedBack = classifyBack(back)
        val classifiedFront = front.map { it.copy(type = LensType.FRONT) }
        return (classifiedBack + classifiedFront + others).sortedWith(
            compareBy({ it.facing.ordinal }, { it.focalLengthMm ?: 0f })
        )
    }

    private fun classifyBack(back: List<LensInfo>): List<LensInfo> {
        if (back.isEmpty()) return emptyList()
        val sorted = back.sortedBy { it.focalLengthMm ?: Float.MAX_VALUE }
        if (sorted.size == 1) return sorted.map { it.copy(type = LensType.WIDE) }
        val shortest = sorted.first().focalLengthMm
        val secondShortest = sorted.getOrNull(1)?.focalLengthMm
        val longest = sorted.last().focalLengthMm
        return sorted.mapIndexed { index, lens ->
            val type = when {
                index == 0 && shortest != null && secondShortest != null && secondShortest / shortest >= 1.6f ->
                    LensType.ULTRAWIDE
                index == sorted.lastIndex && longest != null && shortest != null &&
                    longest / shortest >= 1.7f -> LensType.TELEPHOTO
                else -> LensType.WIDE
            }
            lens.copy(type = type)
        }
    }

    private fun supportsManualExposure(availableAeModes: IntArray): Boolean =
        availableAeModes.contains(CameraMetadata.CONTROL_AE_MODE_OFF)

    private fun describeHardwareLevel(level: Int): String = when (level) {
        CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "Legacy"
        CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "Limited"
        CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "Full"
        CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "Level 3"
        CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "External"
        else -> "Unknown"
    }

    /** Available auto exposure frame rate ranges for the selected camera. */
    fun availableFrameRates(cameraInfo: CameraInfo): List<Range<Int>> {
        val info = Camera2CameraInfo.from(cameraInfo)
        return runCatching {
            info.getCameraCharacteristic(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
                ?.toList() ?: emptyList()
        }.getOrDefault(emptyList())
    }
}
