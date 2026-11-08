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
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.barcode.common.Barcode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs the on-device ML Kit models on the live camera stream.
 *
 * Responsibilities:
 * - Face alignment every N seconds, where N comes from the user's setting.
 * - A full scene description every N seconds, or immediately when requested.
 * - A barcode read on demand, or as part of a description.
 *
 * Two details matter for correctness:
 *
 * 1. The [ImageProxy] is closed exactly once, and only after every ML Kit task that is using
 *    its image has completed. Closing it early (or closing it while a task is still running)
 *    makes ML Kit fail every call, which is why scene descriptions previously never arrived.
 * 2. Tasks are throttled with real timestamps, so an interval of 3 seconds runs the model
 *    every three seconds instead of on every frame.
 */
@Singleton
class CameraFrameAnalyzer @Inject constructor(
    private val detectors: MlKitDetectorFactory
) : ImageAnalysis.Analyzer {

    private companion object {
        const val TAG = "CameraFrameAnalyzer"
        const val MINIMUM_INTERVAL_MS = 1_000L
    }

    private val busy = AtomicBoolean(false)
    private val lastFaceRun = AtomicLong(0L)
    private val lastSceneRun = AtomicLong(0L)
    private val lastSpokenGuidance = AtomicLong(0L)
    private val sceneRequested = AtomicBoolean(false)
    private val barcodeRequested = AtomicBoolean(false)

    private val _faceGuidance = MutableStateFlow<FaceGuidance>(FaceGuidance.Disabled)
    val faceGuidance: StateFlow<FaceGuidance> = _faceGuidance.asStateFlow()

    private val _faceResult = MutableStateFlow<FaceFrameResult?>(null)
    val faceResult: StateFlow<FaceFrameResult?> = _faceResult.asStateFlow()

    private val _sceneDescription = MutableStateFlow<String?>(null)
    val sceneDescription: StateFlow<String?> = _sceneDescription.asStateFlow()

    @Volatile
    var faceDetectionEnabled: Boolean = true

    /** How often face alignment is checked, in milliseconds. Zero or less disables it. */
    @Volatile
    var faceIntervalMillis: Long = 3_000L

    @Volatile
    var sceneDescriptionEnabled: Boolean = false

    /** How often a scene description is generated, in milliseconds. */
    @Volatile
    var sceneIntervalMillis: Long = 3_000L

    @Volatile
    var barcodeScanningEnabled: Boolean = true

    @Volatile
    var isFrontCamera: Boolean = false

    /** Guidance may be spoken at most this often even if the model runs more frequently. */
    @Volatile
    var guidanceRepeatMillis: Long = 3_000L

    /** Called on the analyzer thread when new guidance should be spoken. */
    @Volatile
    var onGuidance: ((String) -> Unit)? = null

    /** Called when a new scene description is ready. */
    @Volatile
    var onScene: ((String) -> Unit)? = null

    /** Called when a barcode result is ready; null message means nothing was found. */
    @Volatile
    var onBarcode: ((String) -> Unit)? = null

    /** Requests one description on the next available frame. */
    fun requestSceneDescription() {
        sceneRequested.set(true)
    }

    /** Requests one barcode read on the next available frame. */
    fun requestBarcodeScan() {
        barcodeRequested.set(true)
    }

    fun setFaceInterval(seconds: Int) {
        faceDetectionEnabled = seconds > 0
        faceIntervalMillis = (seconds * 1_000L).coerceAtLeast(MINIMUM_INTERVAL_MS)
        guidanceRepeatMillis = faceIntervalMillis
        if (!faceDetectionEnabled) {
            _faceResult.value = null
            _faceGuidance.value = FaceGuidance.Disabled
        }
    }

    fun setSceneInterval(seconds: Int) {
        sceneDescriptionEnabled = seconds > 0
        sceneIntervalMillis = (seconds * 1_000L).coerceAtLeast(MINIMUM_INTERVAL_MS)
        if (!sceneDescriptionEnabled) {
            sceneRequested.set(false)
        }
    }

    fun reset() {
        lastFaceRun.set(0L)
        lastSceneRun.set(0L)
        lastSpokenGuidance.set(0L)
        sceneRequested.set(false)
        barcodeRequested.set(false)
        _faceGuidance.value = if (faceDetectionEnabled) {
            FaceGuidance.NoFace("Looking for a face")
        } else {
            FaceGuidance.Disabled
        }
        _faceResult.value = null
        _sceneDescription.value = null
    }

    override fun analyze(imageProxy: ImageProxy) {
        // Never start new work while a previous frame is still being processed: ML Kit would
        // otherwise queue frames faster than the model can run.
        if (!busy.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }

        val now = System.currentTimeMillis()
        val faceDue = faceDetectionEnabled && now - lastFaceRun.get() >= faceIntervalMillis
        val sceneOnDemand = sceneRequested.getAndSet(false)
        val barcodeOnDemand = barcodeRequested.getAndSet(false)
        val sceneDue = sceneOnDemand ||
            (sceneDescriptionEnabled && now - lastSceneRun.get() >= sceneIntervalMillis)
        val barcodeDue = barcodeOnDemand || (sceneDue && barcodeScanningEnabled)

        if (!faceDue && !sceneDue && !barcodeDue) {
            busy.set(false)
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

        if (faceDue) lastFaceRun.set(now)
        if (sceneDue) lastSceneRun.set(now)

        val faceTask: Task<List<com.google.mlkit.vision.face.Face>>? = if (faceDue) {
            runCatching { detectors.face().process(input) }.getOrNull()
        } else null
        val labelTask = if (sceneDue) {
            runCatching { detectors.labels().process(input) }.getOrNull()
        } else null
        // Barcodes are only read when the setting allows it, or when the user explicitly asks
        // for a scan. Scene descriptions therefore never mention codes that are switched off.
        val barcodeTask = if (barcodeDue) {
            runCatching { detectors.barcodes().process(input) }.getOrNull()
        } else null

        val tasks = listOfNotNull(faceTask, labelTask, barcodeTask)
        if (tasks.isEmpty()) {
            busy.set(false)
            imageProxy.close()
            return
        }

        // The proxy stays open until every task has finished with its image.
        val remaining = AtomicInteger(tasks.size)
        val finishOne = {
            if (remaining.decrementAndGet() == 0) {
                runCatching { imageProxy.close() }
                busy.set(false)
            }
        }
        tasks.forEach { task ->
            task.addOnCompleteListener { finishOne() }
        }

        faceTask?.addOnSuccessListener { faces ->
            val result = FaceAlignmentEvaluator.evaluate(
                faces = faces.map { FaceAlignmentEvaluator.observationOf(it) },
                frameWidth = width,
                frameHeight = height
            )
            _faceResult.value = result
            _faceGuidance.value = result.guidance
            maybeAnnounce(result)
        }?.addOnFailureListener { error ->
            Log.w(TAG, "Face detection failed", error)
            _faceGuidance.value = FaceGuidance.NoFace("Face detection is unavailable right now")
        }

        if (labelTask != null || barcodeTask != null) {
            val labels = mutableListOf<Pair<String, Float>>()
            val barcodes = mutableListOf<Barcode>()
            val sceneTasks = listOfNotNull(labelTask, barcodeTask)
            val sceneRemaining = AtomicInteger(sceneTasks.size)
            val emitSceneDescription = sceneDue && labelTask != null
            val finishScene = {
                if (sceneRemaining.decrementAndGet() == 0) {
                    val barcodeValues = barcodes.mapNotNull { it.rawValue ?: it.displayValue }
                    if (emitSceneDescription) {
                        val observation = SceneDescriber.Observation(
                            labels = labels.toList(),
                            barcodeCount = barcodes.size,
                            barcodeValues = barcodeValues
                        )
                        val description = SceneDescriber.describe(observation)
                        _sceneDescription.value = description
                        onScene?.invoke(description)
                    }
                    if (barcodeOnDemand) {
                        onBarcode?.invoke(
                            barcodeValues.firstOrNull()?.let { "Barcode reads $it" }
                                ?: "No barcode or QR code found in view"
                        )
                    }
                }
            }
            labelTask?.addOnSuccessListener { result ->
                labels += result.map { it.text to it.confidence }
            }
            barcodeTask?.addOnSuccessListener { result ->
                barcodes += result
            }
            sceneTasks.forEach { task ->
                task.addOnCompleteListener { finishScene() }
            }
        }
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
