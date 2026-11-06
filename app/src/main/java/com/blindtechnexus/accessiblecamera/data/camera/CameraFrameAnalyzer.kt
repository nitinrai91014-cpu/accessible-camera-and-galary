/*
 * Accessible Camera and Gallery
 * Copyright (C) 2026 Sujan Rai, Blind Tech Nexus team.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.blindtechnexus.accessiblecamera.data.camera

import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.blindtechnexus.accessiblecamera.domain.model.FaceGuidance
import com.google.mlkit.vision.barcode.common.Barcode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ImageAnalysis analyzer that runs ML Kit face detection continuously and ML Kit scene
 * description on a slower schedule.
 *
 * Frame processing is throttled and results are de-duplicated so the app does not spam a
 * screen reader with identical guidance every frame.
 */
@Singleton
class CameraFrameAnalyzer @Inject constructor(
    private val detectors: MlKitDetectorFactory
) : ImageAnalysis.Analyzer {

    private companion object {
        const val TAG = "CameraFrameAnalyzer"
        const val FACE_INTERVAL_MS = 200L
        const val SCENE_INTERVAL_MS = 4_000L
    }

    private val busy = AtomicBoolean(false)
    private val lastFaceRun = AtomicLong(0L)
    private val lastSceneRun = AtomicLong(0L)
    private val lastSpokenGuidance = AtomicLong(0L)

    private val _faceGuidance = MutableStateFlow<FaceGuidance>(FaceGuidance.Disabled)
    val faceGuidance: StateFlow<FaceGuidance> = _faceGuidance.asStateFlow()

    private val _faceResult = MutableStateFlow<FaceFrameResult?>(null)
    val faceResult: StateFlow<FaceFrameResult?> = _faceResult.asStateFlow()

    private val _sceneDescription = MutableStateFlow<String?>(null)
    val sceneDescription: StateFlow<String?> = _sceneDescription.asStateFlow()

    @Volatile
    var faceDetectionEnabled: Boolean = true

    @Volatile
    var sceneDescriptionEnabled: Boolean = false

    @Volatile
    var isFrontCamera: Boolean = false

    /** Guidance text should be spoken at most this often. */
    @Volatile
    var guidanceRepeatMillis: Long = 2_500L

    /** Called on the analyzer thread when new guidance should be spoken. */
    @Volatile
    var onGuidance: ((String) -> Unit)? = null

    /** Called when a new scene description is ready. */
    @Volatile
    var onScene: ((String) -> Unit)? = null

    fun reset() {
        _faceGuidance.value = if (faceDetectionEnabled) FaceGuidance.NoFace("Looking for a face") else FaceGuidance.Disabled
        _faceResult.value = null
        _sceneDescription.value = null
    }

    override fun analyze(imageProxy: ImageProxy) {
        val now = System.currentTimeMillis()
        val wantsFace = faceDetectionEnabled && now - lastFaceRun.get() >= FACE_INTERVAL_MS
        val wantsScene = sceneDescriptionEnabled && now - lastSceneRun.get() >= SCENE_INTERVAL_MS
        if (!wantsFace && !wantsScene) {
            imageProxy.close()
            return
        }
        if (!busy.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }
        val mediaImage = runCatching { imageProxy.image }.getOrNull()
        if (mediaImage == null) {
            busy.set(false)
            imageProxy.close()
            return
        }
        val rotation = imageProxy.imageInfo.rotationDegrees
        val width = imageProxy.width
        val height = imageProxy.height
        val input = SceneDescriber.inputImage(mediaImage, rotation)
        val runScene = wantsScene
        val runFace = wantsFace
        if (runFace) lastFaceRun.set(now)
        if (runScene) lastSceneRun.set(now)

        val faceTask = if (runFace) {
            runCatching { detectors.face().process(input) }.getOrNull()
        } else null
        val labelTask = if (runScene) {
            runCatching { detectors.labels().process(input) }.getOrNull()
        } else null
        val barcodeTask = if (runScene) {
            runCatching { detectors.barcodes().process(input) }.getOrNull()
        } else null

        if (faceTask == null && labelTask == null && barcodeTask == null) {
            busy.set(false)
            imageProxy.close()
            return
        }

        val remaining = java.util.concurrent.atomic.AtomicInteger(
            listOfNotNull(faceTask, labelTask, barcodeTask).size
        )
        val finish = {
            if (remaining.decrementAndGet() == 0) {
                runCatching { imageProxy.close() }
                busy.set(false)
            }
        }

        faceTask?.addOnSuccessListener { faces ->
            val result = FaceAlignmentEvaluator.evaluate(faces, width, height)
            _faceResult.value = result
            _faceGuidance.value = result.guidance
            maybeAnnounce(result)
        }?.addOnFailureListener { error ->
            Log.w(TAG, "Face detection failed", error)
            _faceGuidance.value = FaceGuidance.NoFace("Face detection is unavailable")
        }?.addOnCompleteListener { finish() }

        if (labelTask != null || barcodeTask != null) {
            val labels = mutableListOf<Pair<String, Float>>()
            val barcodes = mutableListOf<Barcode>()
            val sceneCounter = java.util.concurrent.atomic.AtomicInteger(
                (if (labelTask != null) 1 else 0) + (if (barcodeTask != null) 1 else 0)
            )
            val finishScene = {
                if (sceneCounter.decrementAndGet() == 0) {
                    val observation = SceneDescriber.Observation(
                        labels = labels.toList(),
                        barcodeCount = barcodes.size,
                        barcodeValues = barcodes.mapNotNull { it.rawValue }
                    )
                    val description = SceneDescriber.describe(observation)
                    _sceneDescription.value = description
                    onScene?.invoke(description)
                }
            }
            labelTask?.addOnSuccessListener { result ->
                labels += result.map { it.text to it.confidence }
            }?.addOnCompleteListener { finishScene() }
            barcodeTask?.addOnSuccessListener { result ->
                barcodes += result
            }?.addOnCompleteListener { finishScene() }
        }

        finish()
    }

    private fun maybeAnnounce(result: FaceFrameResult) {
        val text = when (val guidance = result.guidance) {
            is FaceGuidance.Misaligned -> guidance.message
            is FaceGuidance.Aligned -> guidance.message
            is FaceGuidance.NoFace -> guidance.hint
            is FaceGuidance.MultipleFaces -> "More than one face in view. Only one person should be in frame."
            else -> null
        } ?: return
        val now = System.currentTimeMillis()
        if (now - lastSpokenGuidance.get() < guidanceRepeatMillis) return
        lastSpokenGuidance.set(now)
        onGuidance?.invoke(text)
    }
}
