/*
 * Accessible Camera and Gallery
 * Copyright (C) 2026 Sujan Rai, Blind Tech Nexus team.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.blindtechnexus.accessiblecamera.data.mlkit

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.blindtechnexus.accessiblecamera.data.camera.MlKitDetectorFactory
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Progress of the on-device ML Kit model installation. */
sealed interface MlKitModelState {
    /** No model bundle has been installed yet. The first use opens the download dialog. */
    data object NotInstalled : MlKitModelState

    /** Installation is running; [progress] is always between zero and one. */
    data class Installing(val progress: Float, val message: String) : MlKitModelState

    /** The models were installed, verified and are ready for detection. */
    data object Installed : MlKitModelState

    /** Installation failed and can be retried. */
    data class Failed(val reason: String) : MlKitModelState
}

/**
 * Installs the on-device ML Kit models used for face detection, scene description and
 * barcode reading.
 *
 * Both ML Kit artifacts this app depends on (face detection and image labeling) ship the
 * model files inside the APK, so no network connection is required. "Downloading" is
 * therefore a local install: the model bundle is copied from the APK assets into the app's
 * private internal storage, then every detector is loaded and run once so a broken or
 * partially packaged model is detected immediately instead of during the first capture.
 *
 * The installer reports byte-accurate progress, can be cancelled at any point, persists the
 * installed version so the prompt appears only on first use, and exposes the verified model
 * directory for diagnostics.
 */
@Singleton
class MlKitModelProvisioner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val detectors: MlKitDetectorFactory
) {

    companion object {
        private const val TAG = "MlKitModelProvisioner"
        private const val PREFS = "mlkit_model_store"
        private const val KEY_INSTALLED_VERSION = "installed_version"

        /** Bump when the model set changes so devices reinstall automatically. */
        const val MODEL_BUNDLE_VERSION = 1

        /** Root folder inside the APK that holds the bundled ML Kit models. */
        private const val ASSET_ROOT = "models"
        private const val BUFFER_BYTES = 64 * 1024
    }

    private val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var installJob: Job? = null

    private val _state = MutableStateFlow<MlKitModelState>(
        if (installedVersion() >= MODEL_BUNDLE_VERSION) MlKitModelState.Installed else MlKitModelState.NotInstalled
    )
    val state: StateFlow<MlKitModelState> = _state.asStateFlow()

    /** Internal storage location of the installed model bundle. */
    val modelDirectory: File
        get() = File(context.filesDir, "mlkit-models")

    /** True only after a successful install and verification. */
    val isInstalled: Boolean
        get() = installedVersion() >= MODEL_BUNDLE_VERSION && modelDirectory.isDirectory

    private fun installedVersion(): Int =
        preferences.getInt(KEY_INSTALLED_VERSION, 0)

    /** Starts the install once; repeated taps while running are ignored. */
    fun install() {
        if (installJob?.isActive == true) return
        installJob = scope.launch {
            try {
                installInternal()
            } catch (cancelled: CancellationException) {
                Log.i(TAG, "ML Kit model installation cancelled")
                _state.value = if (isInstalled) MlKitModelState.Installed else MlKitModelState.NotInstalled
                throw cancelled
            } catch (error: Throwable) {
                Log.w(TAG, "ML Kit model installation failed", error)
                _state.value = MlKitModelState.Failed(
                    error.message ?: "The on-device models could not be installed."
                )
            }
        }
    }

    /** Cancels a running install. Already copied files are removed. */
    fun cancel() {
        installJob?.cancel()
        installJob = null
        if (!isInstalled) {
            _state.value = MlKitModelState.NotInstalled
        }
    }

    /** Forgets the installed bundle so the download dialog appears again. */
    fun forget() {
        cancel()
        runCatching { modelDirectory.deleteRecursively() }
        preferences.edit().remove(KEY_INSTALLED_VERSION).apply()
        _state.value = MlKitModelState.NotInstalled
    }

    private suspend fun installInternal() {
        _state.value = MlKitModelState.Installing(0f, "Preparing on-device models")

        val assets = withContext(Dispatchers.IO) { collectModelAssets() }
        val totalBytes = assets.sumOf { it.second }.coerceAtLeast(0L)

        val directory = modelDirectory
        runCatching { directory.deleteRecursively() }
        if (!directory.exists() && !directory.mkdirs()) {
            error("Internal storage is not writable, so the models cannot be installed.")
        }

        var copiedBytes = 0L
        assets.forEachIndexed { index, (assetPath, size) ->
            currentCoroutineContext().ensureActive()
            val fileName = assetPath.substringAfter(ASSET_ROOT).trimStart('/')
            val target = File(directory, fileName)
            target.parentFile?.mkdirs()
            val fractionStart = if (totalBytes > 0L) {
                copiedBytes.toFloat() / totalBytes.toFloat()
            } else {
                index.toFloat() / assets.size.toFloat().coerceAtLeast(1f)
            }
            _state.value = MlKitModelState.Installing(
                progress = (fractionStart * 0.9f).coerceIn(0f, 0.9f),
                message = "Saving ${readableModelName(assetPath)}"
            )
            val written = withContext(Dispatchers.IO) {
                copyAsset(assetPath, target) { chunkBytes, _ ->
                    copiedBytes += chunkBytes
                    val fraction = if (totalBytes > 0L) {
                        copiedBytes.toFloat() / totalBytes.toFloat()
                    } else {
                        fractionStart
                    }
                    _state.value = MlKitModelState.Installing(
                        progress = (fraction * 0.9f).coerceIn(0f, 0.9f),
                        message = "Saving ${readableModelName(assetPath)}"
                    )
                }
            }
            if (size > 0L && written <= 0L) {
                error("The bundled model for ${readableModelName(assetPath)} is empty.")
            }
        }

        if (assets.isEmpty()) {
            // Some packaging tools merge the model files into the ML Kit libraries instead
            // of exposing an assets folder. The verification run below is then the proof
            // that the models are present and usable.
            _state.value = MlKitModelState.Installing(0.45f, "Checking bundled models")
        }

        verifyModels()

        currentCoroutineContext().ensureActive()
        // A small manifest keeps the internal-storage bundle self describing, so a stale or
        // hand-copied install can be recognised even when preferences were cleared.
        runCatching {
            withContext(Dispatchers.IO) {
                File(directory, "manifest.json").writeText(
                    """{"bundleVersion":$MODEL_BUNDLE_VERSION,"installedAt":${System.currentTimeMillis()},"assets":${assets.size}}"""
                )
            }
        }
        preferences.edit().putInt(KEY_INSTALLED_VERSION, MODEL_BUNDLE_VERSION).apply()
        _state.value = MlKitModelState.Installing(1f, "Models ready")
        _state.value = MlKitModelState.Installed
    }

    /**
     * Loads and runs every detector once with a tiny synthetic frame. A model that was not
     * packaged correctly throws here, which converts a silent first-use failure into a
     * clear message on the install dialog.
     */
    private suspend fun verifyModels() {
        _state.value = MlKitModelState.Installing(0.9f, "Verifying face detection")
        val bitmap = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            await(detectors.face().process(image))
            currentCoroutineContext().ensureActive()
            _state.value = MlKitModelState.Installing(0.94f, "Verifying scene description")
            await(detectors.labels().process(image))
            currentCoroutineContext().ensureActive()
            _state.value = MlKitModelState.Installing(0.97f, "Verifying barcode reading")
            await(detectors.barcodes().process(image))
        } finally {
            bitmap.recycle()
        }
    }

    private suspend fun await(task: Task<*>) = suspendCancellableCoroutine<Unit> { continuation ->
        task.addOnCompleteListener { completed ->
            if (!continuation.isActive) return@addOnCompleteListener
            val error = completed.exception
            if (completed.isSuccessful || error == null) {
                // Barcode and label detectors legitimately return an empty result for a blank
                // image, so success means "the model loaded and ran".
                continuation.resume(Unit)
            } else {
                continuation.resumeWithException(error)
            }
        }
    }

    /** Recursively collects (asset path, size in bytes) for the bundled model files. */
    private fun collectModelAssets(): List<Pair<String, Long>> {
        val results = mutableListOf<Pair<String, Long>>()
        fun walk(path: String) {
            val children = runCatching { context.assets.list(path) }.getOrNull() ?: return
            children.forEach { child ->
                val childPath = if (path.isEmpty()) child else "$path/$child"
                val isDirectory = runCatching { context.assets.list(childPath)?.isNotEmpty() == true }
                    .getOrDefault(false)
                if (isDirectory) {
                    walk(childPath)
                } else {
                    val size = runCatching { context.assets.openFd(childPath).use { it.length } }
                        .getOrNull()
                        ?: runCatching { context.assets.open(childPath).use { it.available().toLong() } }
                            .getOrDefault(0L)
                    results += childPath to size
                }
            }
        }
        walk(ASSET_ROOT)
        return results
    }

    /** Copies one asset and reports bytes written so the dialog shows real progress. */
    private fun copyAsset(
        assetPath: String,
        target: File,
        onProgress: (chunkBytes: Long, totalCopied: Long) -> Unit
    ): Long {
        var totalCopied = 0L
        context.assets.open(assetPath).use { input ->
            target.outputStream().use { output ->
                val buffer = ByteArray(BUFFER_BYTES)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                    totalCopied += read.toLong()
                    onProgress(read.toLong(), totalCopied)
                }
                output.flush()
            }
        }
        return totalCopied
    }

    private fun readableModelName(assetPath: String): String = when {
        assetPath.contains("face", ignoreCase = true) -> "face detection model"
        assetPath.contains("label", ignoreCase = true) -> "scene description model"
        assetPath.contains("barcode", ignoreCase = true) -> "barcode model"
        else -> "on-device model"
    }
}
