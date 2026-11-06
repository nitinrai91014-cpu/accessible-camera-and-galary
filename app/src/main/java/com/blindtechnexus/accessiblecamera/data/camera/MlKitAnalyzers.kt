/*
 * Accessible Camera and Gallery
 * Copyright (C) 2026 Sujan Rai, Blind Tech Nexus team.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.blindtechnexus.accessiblecamera.data.camera

import com.google.mlkit.vision.face.Face
import com.blindtechnexus.accessiblecamera.domain.model.FaceGuidance
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Result of evaluating one camera frame for face alignment. */
data class FaceFrameResult(
    val guidance: FaceGuidance,
    val faceLeft: Int? = null,
    val faceTop: Int? = null,
    val faceRight: Int? = null,
    val faceBottom: Int? = null,
    val frameWidth: Int = 0,
    val frameHeight: Int = 0,
    val isFrontCamera: Boolean = false
)

/**
 * A face reduced to the numbers alignment needs. Keeping ML Kit types out of the evaluator
 * makes the guidance logic unit testable on a plain JVM.
 */
data class FaceObservation(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    /** Positive turns the head to the user's right. */
    val eulerY: Float = 0f,
    /** Positive tilts the head clockwise. */
    val eulerZ: Float = 0f
) {
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/**
 * Pure, testable face alignment logic.
 *
 * The frame is divided into thirds. The face must sit inside the central third
 * horizontally, inside the central band vertically, be large enough to fill a reasonable
 * portion of the frame, and be level. Each failure produces one specific spoken hint so a
 * user can correct a single thing at a time.
 */
object FaceAlignmentEvaluator {

    data class Thresholds(
        val horizontalTolerance: Float = 0.18f,
        val verticalTolerance: Float = 0.22f,
        val minimumFaceHeightFraction: Float = 0.28f,
        val maximumFaceHeightFraction: Float = 0.92f,
        val maximumEulerY: Float = 14f,
        val maximumEulerZ: Float = 12f
    )

    fun evaluate(
        faces: List<FaceObservation>,
        frameWidth: Int,
        frameHeight: Int,
        thresholds: Thresholds = Thresholds()
    ): FaceFrameResult {
        if (frameWidth <= 0 || frameHeight <= 0) {
            return FaceFrameResult(FaceGuidance.NoFace("Camera frame is not ready."))
        }
        if (faces.isEmpty()) {
            return FaceFrameResult(
                guidance = FaceGuidance.NoFace("No face detected. Move closer and face the camera."),
                frameWidth = frameWidth,
                frameHeight = frameHeight
            )
        }
        if (faces.size > 1) {
            return FaceFrameResult(
                guidance = FaceGuidance.MultipleFaces(faces.size),
                frameWidth = frameWidth,
                frameHeight = frameHeight
            )
        }
        val face = faces.first()
        val centerX = face.centerX / frameWidth
        val centerY = face.centerY / frameHeight
        val heightFraction = face.height.toFloat() / frameHeight

        val hints = mutableListOf<String>()
        var severity = 0

        if (heightFraction < thresholds.minimumFaceHeightFraction) {
            hints += "Move closer"
            severity += 2
        } else if (heightFraction > thresholds.maximumFaceHeightFraction) {
            hints += "Move back"
            severity += 2
        }

        val horizontalOffset = centerX - 0.5f
        if (horizontalOffset > thresholds.horizontalTolerance) {
            hints += "Move your face left"
            severity += 2
        } else if (horizontalOffset < -thresholds.horizontalTolerance) {
            hints += "Move your face right"
            severity += 2
        }

        val verticalOffset = centerY - 0.5f
        if (verticalOffset > thresholds.verticalTolerance) {
            hints += "Move your face up"
            severity += 2
        } else if (verticalOffset < -thresholds.verticalTolerance) {
            hints += "Move your face down"
            severity += 2
        }

        if (face.eulerY > thresholds.maximumEulerY) {
            hints += "Turn your head to the left"
            severity += 1
        } else if (face.eulerY < -thresholds.maximumEulerY) {
            hints += "Turn your head to the right"
            severity += 1
        }
        if (face.eulerZ > thresholds.maximumEulerZ || face.eulerZ < -thresholds.maximumEulerZ) {
            hints += "Straighten your head"
            severity += 1
        }

        val guidance = if (hints.isEmpty()) {
            FaceGuidance.Aligned("Face aligned. Hold still.")
        } else {
            FaceGuidance.Misaligned(hints.joinToString(". "), severity)
        }
        return FaceFrameResult(
            guidance = guidance,
            faceLeft = face.left,
            faceTop = face.top,
            faceRight = face.right,
            faceBottom = face.bottom,
            frameWidth = frameWidth,
            frameHeight = frameHeight
        )
    }

    /** Maps an ML Kit face onto the plain observation used by [evaluate]. */
    fun observationOf(face: Face): FaceObservation {
        val bounds = face.boundingBox
        return FaceObservation(
            left = bounds.left,
            top = bounds.top,
            right = bounds.right,
            bottom = bounds.bottom,
            eulerY = face.headEulerAngleY,
            eulerZ = face.headEulerAngleZ
        )
    }
}

/** Creates and holds ML Kit detectors. They are lazily created and closed with the app. */
@Singleton
class MlKitDetectorFactory @Inject constructor() {

    private val faceDetector by lazy {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                .setMinFaceSize(0.15f)
                .enableTracking()
                .build()
        )
    }

    private val labeler by lazy {
        ImageLabeling.getClient(
            ImageLabelerOptions.Builder()
                .setConfidenceThreshold(0.6f)
                .build()
        )
    }

    private val barcodeScanner by lazy {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                .build()
        )
    }

    fun face() = faceDetector

    fun labels() = labeler

    fun barcodes() = barcodeScanner

    fun close() {
        runCatching { faceDetector.close() }
        runCatching { labeler.close() }
        runCatching { barcodeScanner.close() }
    }
}

/**
 * Converts ML Kit labels into a short, spoken, screen reader friendly sentence.
 * Colour, brightness and barcode details are intentionally included because they are the
 * details a blind user cannot otherwise infer from a preview.
 */
object SceneDescriber {

    data class Observation(
        val labels: List<Pair<String, Float>>,
        val barcodeCount: Int,
        val barcodeValues: List<String>
    )

    fun describe(observation: Observation): String {
        val labels = observation.labels
            .filter { it.second >= 0.6f }
            .take(4)
            .map { it.first.lowercase(Locale.getDefault()) }
        val labelText = when {
            labels.isEmpty() -> "No objects recognised"
            labels.size == 1 -> "Looks like ${labels.first()}"
            else -> "Looks like ${labels.dropLast(1).joinToString(", ")} and ${labels.last()}"
        }
        val barcodeText = when {
            observation.barcodeValues.isNotEmpty() ->
                ". Barcode reads ${observation.barcodeValues.first()}"
            observation.barcodeCount > 0 -> ". ${observation.barcodeCount} barcodes in view"
            else -> ""
        }
        return labelText + barcodeText
    }

    /** Input image wrapper so callers never touch ML Kit types directly. */
    fun inputImage(mediaImage: android.media.Image, rotationDegrees: Int): InputImage =
        InputImage.fromMediaImage(mediaImage, rotationDegrees)
}
