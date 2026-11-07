/*
 * Accessible Camera and Gallery
 * Copyright (C) 2026 Sujan Rai, Blind Tech Nexus team.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.blindtechnexus.accessiblecamera.ui.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blindtechnexus.accessiblecamera.data.image.ImageProcessing
import com.blindtechnexus.accessiblecamera.data.media.MediaSaver
import com.blindtechnexus.accessiblecamera.data.voice.VoiceRepositoryImpl
import com.blindtechnexus.accessiblecamera.domain.model.AnnouncementPriority
import com.blindtechnexus.accessiblecamera.domain.model.MediaItem
import com.blindtechnexus.accessiblecamera.domain.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/** A single drawing stroke captured by touch or by the accessibility friendly point tools. */
data class Stroke(
    val points: List<Pair<Float, Float>>,
    val color: Int = Color.YELLOW,
    val width: Float = 12f
)

/** A text overlay placed on the image. */
data class TextOverlay(
    val text: String,
    val x: Float,
    val y: Float,
    val textSize: Float = 72f,
    val color: Int = Color.WHITE
)

/** Colour filter presets. Each preset is described in words so it is not colour-only. */
enum class FilterPreset(val label: String, val spoken: String) {
    NONE("None", "no filter"),
    MONO("Monochrome", "black and white"),
    WARM("Warm", "warmer colours"),
    COOL("Cool", "cooler colours"),
    HIGH_CONTRAST("High contrast", "stronger contrast"),
    FADED("Faded", "softer, muted colours");

    val next: FilterPreset get() = entries[(ordinal + 1) % entries.size]
}

/** Editing state for one item. Edits are non-destructive until saved as a copy. */
data class EditorUiState(
    val item: MediaItem? = null,
    val bitmap: Bitmap? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val exposure: Float = 0f,
    val rotationDegrees: Int = 0,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val cropLeft: Float = 0f,
    val cropTop: Float = 0f,
    val cropRight: Float = 1f,
    val cropBottom: Float = 1f,
    val filter: FilterPreset = FilterPreset.NONE,
    val strokes: List<Stroke> = emptyList(),
    val textOverlays: List<TextOverlay> = emptyList(),
    val drawModeActive: Boolean = false,
    val textModeActive: Boolean = false,
    val isSaving: Boolean = false,
    val saveComplete: Boolean = false,
    val savedUri: android.net.Uri? = null,
    val undoStack: List<EditSnapshot> = emptyList(),
    val showDiscardConfirmation: Boolean = false,
    val isVideo: Boolean = false,
    val videoMuted: Boolean = false,
    val videoTrimStartMs: Long = 0L,
    val videoTrimEndMs: Long = 0L
) {
    val hasEdits: Boolean
        get() = brightness != 0f || contrast != 0f || saturation != 0f || exposure != 0f ||
            rotationDegrees != 0 || flipHorizontal || flipVertical ||
            cropLeft != 0f || cropTop != 0f || cropRight != 1f || cropBottom != 1f ||
            filter != FilterPreset.NONE || strokes.isNotEmpty() || textOverlays.isNotEmpty() ||
            videoMuted || videoTrimStartMs > 0L || videoTrimEndMs > 0L
}

/** Snapshot used by undo. */
data class EditSnapshot(
    val brightness: Float,
    val contrast: Float,
    val saturation: Float,
    val exposure: Float,
    val rotationDegrees: Int,
    val flipHorizontal: Boolean,
    val flipVertical: Boolean,
    val cropLeft: Float,
    val cropTop: Float,
    val cropRight: Float,
    val cropBottom: Float,
    val filter: FilterPreset,
    val strokeCount: Int,
    val textCount: Int
)

/**
 * Editing logic.
 *
 * All pixel work is async. Adjustments are expressed as values and applied once when
 * saving, which keeps the UI responsive and makes undo trivial. Video trimming uses Media3
 * Transformer.
 */
@HiltViewModel
class EditorViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaRepository: MediaRepository,
    private val mediaSaver: MediaSaver,
    private val voice: VoiceRepositoryImpl
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private var sourceFile: File? = null

    fun load(mediaId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val item = mediaRepository.getItem(mediaId)
            if (item == null) {
                _uiState.update {
                    it.copy(isLoading = false, error = "This item could not be found")
                }
                voice.announce("This item could not be found", AnnouncementPriority.CRITICAL)
                return@launch
            }
            if (item.isVideo) {
                _uiState.update {
                    it.copy(
                        item = item,
                        isVideo = true,
                        isLoading = false,
                        videoTrimEndMs = item.durationMillis
                    )
                }
                voice.announce(
                    "Editing video. Duration ${com.blindtechnexus.accessiblecamera.core.util.TimeFormat.spoken(item.durationMillis)}.",
                    AnnouncementPriority.HIGH
                )
                return@launch
            }
            val file = withContext(Dispatchers.IO) {
                runCatching {
                    val temp = File(context.cacheDir, "edit_${item.id}.jpg")
                    context.contentResolver.openInputStream(item.uri)?.use { input ->
                        temp.outputStream().use { output -> input.copyTo(output) }
                    }
                    temp
                }.getOrNull()
            }
            if (file == null) {
                _uiState.update {
                    it.copy(isLoading = false, error = "The photo could not be opened for editing")
                }
                voice.announce("The photo could not be opened for editing", AnnouncementPriority.CRITICAL)
                return@launch
            }
            sourceFile = file
            val bitmap = ImageProcessing.decodeScaled(file, maxDimension = 2048)
            _uiState.update {
                it.copy(
                    item = item,
                    bitmap = bitmap,
                    isLoading = false,
                    error = if (bitmap == null) "The photo could not be decoded" else null
                )
            }
            if (bitmap != null) {
                voice.announce(
                    "Editing photo. ${bitmap.width} by ${bitmap.height} pixels.",
                    AnnouncementPriority.HIGH
                )
            }
        }
    }

    private fun pushUndo() {
        val state = _uiState.value
        _uiState.update {
            it.copy(
                undoStack = (it.undoStack + EditSnapshot(
                    brightness = state.brightness,
                    contrast = state.contrast,
                    saturation = state.saturation,
                    exposure = state.exposure,
                    rotationDegrees = state.rotationDegrees,
                    flipHorizontal = state.flipHorizontal,
                    flipVertical = state.flipVertical,
                    cropLeft = state.cropLeft,
                    cropTop = state.cropTop,
                    cropRight = state.cropRight,
                    cropBottom = state.cropBottom,
                    filter = state.filter,
                    strokeCount = state.strokes.size,
                    textCount = state.textOverlays.size
                )).takeLast(20)
            )
        }
    }

    fun undo() {
        val state = _uiState.value
        val snapshot = state.undoStack.lastOrNull()
        if (snapshot == null) {
            voice.announce("Nothing to undo", AnnouncementPriority.NORMAL)
            return
        }
        _uiState.update {
            it.copy(
                brightness = snapshot.brightness,
                contrast = snapshot.contrast,
                saturation = snapshot.saturation,
                exposure = snapshot.exposure,
                rotationDegrees = snapshot.rotationDegrees,
                flipHorizontal = snapshot.flipHorizontal,
                flipVertical = snapshot.flipVertical,
                cropLeft = snapshot.cropLeft,
                cropTop = snapshot.cropTop,
                cropRight = snapshot.cropRight,
                cropBottom = snapshot.cropBottom,
                filter = snapshot.filter,
                strokes = it.strokes.take(snapshot.strokeCount),
                textOverlays = it.textOverlays.take(snapshot.textCount),
                undoStack = it.undoStack.dropLast(1)
            )
        }
        voice.announce("Undone", AnnouncementPriority.NORMAL)
    }

    fun setBrightness(value: Float) = adjust("Brightness") { it.copy(brightness = value) }

    fun setContrast(value: Float) = adjust("Contrast") { it.copy(contrast = value) }

    fun setSaturation(value: Float) = adjust("Saturation") { it.copy(saturation = value) }

    fun setExposure(value: Float) = adjust("Exposure") { it.copy(exposure = value) }

    private fun adjust(label: String, transform: (EditorUiState) -> EditorUiState) {
        _uiState.update(transform)
        voice.announceValue(label, "$label ${percentLabel(_uiState.value)}")
    }

    private fun percentLabel(state: EditorUiState): String =
        String.format(java.util.Locale.US, "%.0f percent", state.brightness * 100)

    fun rotate() {
        pushUndo()
        val next = (_uiState.value.rotationDegrees + 90) % 360
        _uiState.update { it.copy(rotationDegrees = next) }
        voice.announce("Rotated to $next degrees", AnnouncementPriority.NORMAL)
    }

    fun flipHorizontal() {
        pushUndo()
        _uiState.update { it.copy(flipHorizontal = !it.flipHorizontal) }
        voice.announce(
            if (_uiState.value.flipHorizontal) "Flipped horizontally" else "Horizontal flip removed",
            AnnouncementPriority.NORMAL
        )
    }

    fun flipVertical() {
        pushUndo()
        _uiState.update { it.copy(flipVertical = !it.flipVertical) }
        voice.announce(
            if (_uiState.value.flipVertical) "Flipped vertically" else "Vertical flip removed",
            AnnouncementPriority.NORMAL
        )
    }

    fun setCrop(left: Float, top: Float, right: Float, bottom: Float) {
        _uiState.update {
            it.copy(
                cropLeft = left.coerceIn(0f, 0.9f),
                cropTop = top.coerceIn(0f, 0.9f),
                cropRight = right.coerceIn(0.1f, 1f),
                cropBottom = bottom.coerceIn(0.1f, 1f)
            )
        }
        voice.announce("Crop applied", AnnouncementPriority.NORMAL)
    }

    fun cropPreset(name: String) {
        pushUndo()
        when (name) {
            "Square" -> setCrop(0.1f, 0.1f, 0.9f, 0.9f)
            "Centre half" -> setCrop(0.25f, 0.25f, 0.75f, 0.75f)
            "Reset" -> setCrop(0f, 0f, 1f, 1f)
            else -> setCrop(0f, 0f, 1f, 1f)
        }
        voice.announce("Crop $name applied", AnnouncementPriority.NORMAL)
    }

    fun cycleFilter() {
        pushUndo()
        val next = _uiState.value.filter.next
        _uiState.update { it.copy(filter = next) }
        voice.announce("Filter ${next.spoken}", AnnouncementPriority.NORMAL)
    }

    fun setFilter(filter: FilterPreset) {
        pushUndo()
        _uiState.update { it.copy(filter = filter) }
        voice.announce("Filter ${filter.spoken}", AnnouncementPriority.NORMAL)
    }

    fun toggleDrawMode() {
        val next = !_uiState.value.drawModeActive
        _uiState.update { it.copy(drawModeActive = next, textModeActive = false) }
        voice.announce(
            if (next) "Drawing mode on. Drag to draw, or use the add stroke button." else "Drawing mode off",
            AnnouncementPriority.NORMAL
        )
    }

    fun toggleTextMode() {
        val next = !_uiState.value.textModeActive
        _uiState.update { it.copy(textModeActive = next, drawModeActive = false) }
        voice.announce(if (next) "Text mode on" else "Text mode off", AnnouncementPriority.NORMAL)
    }

    /**
     * Adds a stroke at the centre with a small, deterministic shape.
     * This exists because freehand drawing excludes people who cannot see the canvas.
     */
    fun addAccessibleStroke() {
        pushUndo()
        val bitmap = _uiState.value.bitmap ?: return
        val cx = bitmap.width / 2f
        val cy = bitmap.height / 2f
        val radius = minOf(bitmap.width, bitmap.height) * 0.12f
        val points = (0..24).map { index ->
            val angle = Math.toRadians(index * (360.0 / 24))
            (cx + radius * kotlin.math.cos(angle)).toFloat() to (cy + radius * kotlin.math.sin(angle)).toFloat()
        }
        _uiState.update { it.copy(strokes = it.strokes + Stroke(points = points)) }
        voice.announce("Circle annotation added at the centre", AnnouncementPriority.NORMAL)
    }

    /** Adds a freehand stroke drawn on the preview. Points are normalised 0 to 1. */
    fun addFreehandStroke(stroke: Stroke) {
        if (stroke.points.size < 2) return
        pushUndo()
        val bitmap = _uiState.value.bitmap ?: return
        val scaled = stroke.copy(
            points = stroke.points.map { (x, y) -> x * bitmap.width to y * bitmap.height },
            width = 16f
        )
        _uiState.update { it.copy(strokes = it.strokes + scaled) }
        voice.announce("Stroke added", AnnouncementPriority.NORMAL)
    }

    fun clearAnnotations() {
        pushUndo()
        _uiState.update { it.copy(strokes = emptyList(), textOverlays = emptyList()) }
        voice.announce("All annotations removed", AnnouncementPriority.NORMAL)
    }

    fun addText(text: String) {
        if (text.isBlank()) return
        pushUndo()
        val bitmap = _uiState.value.bitmap ?: return
        _uiState.update {
            it.copy(
                textOverlays = it.textOverlays + TextOverlay(
                    text = text,
                    x = bitmap.width * 0.1f,
                    y = bitmap.height * 0.85f,
                    textSize = (bitmap.width * 0.06f).coerceIn(28f, 160f)
                )
            )
        }
        voice.announce("Text added: $text", AnnouncementPriority.NORMAL)
    }

    fun toggleVideoMute() {
        val next = !_uiState.value.videoMuted
        _uiState.update { it.copy(videoMuted = next) }
        voice.announce(if (next) "Video will be muted" else "Video audio kept", AnnouncementPriority.NORMAL)
    }

    fun setTrim(startMs: Long, endMs: Long) {
        val duration = _uiState.value.item?.durationMillis ?: 0L
        _uiState.update {
            it.copy(
                videoTrimStartMs = startMs.coerceIn(0, duration),
                videoTrimEndMs = endMs.coerceIn(0, duration)
            )
        }
        voice.announce(
            "Trim from ${com.blindtechnexus.accessiblecamera.core.util.TimeFormat.spoken(startMs)} " +
                "to ${com.blindtechnexus.accessiblecamera.core.util.TimeFormat.spoken(endMs)}",
            AnnouncementPriority.NORMAL
        )
    }

    /** Applies every adjustment and saves a new copy next to the original. */
    fun saveCopy() {
        val state = _uiState.value
        if (state.isVideo) {
            saveVideoCopy(state)
            return
        }
        val bitmap = state.bitmap ?: return
        if (state.isSaving) return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val rendered = withContext(Dispatchers.Default) { render(bitmap, state) }
            val name = "EDIT_${System.currentTimeMillis()}.jpg"
            val result = mediaSaver.saveEditedCopy(rendered, name)
            result.onSuccess { uri ->
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        saveComplete = true,
                        savedUri = uri
                    )
                }
                voice.announce("Edited copy saved. The original is unchanged.", AnnouncementPriority.HIGH)
            }.onFailure { error ->
                _uiState.update { it.copy(isSaving = false, error = error.message) }
                voice.announce(
                    "The edited copy could not be saved. ${error.message ?: ""}",
                    AnnouncementPriority.CRITICAL
                )
            }
        }
    }

    private fun saveVideoCopy(state: EditorUiState) {
        val item = state.item ?: return
        if (state.isSaving) return
        _uiState.update { it.copy(isSaving = true) }
        voice.announce("Preparing the video edit. This can take a moment.", AnnouncementPriority.HIGH)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val output = File(context.cacheDir, "video_edit_${System.currentTimeMillis()}.mp4")
                    val mediaItemBuilder = androidx.media3.common.MediaItem.Builder()
                        .setUri(item.uri)
                    if (state.videoTrimEndMs > state.videoTrimStartMs) {
                        mediaItemBuilder.setClippingConfiguration(
                            androidx.media3.common.MediaItem.ClippingConfiguration.Builder()
                                .setStartPositionMs(state.videoTrimStartMs)
                                .setEndPositionMs(state.videoTrimEndMs)
                                .build()
                        )
                    }
                    val edited = androidx.media3.transformer.EditedMediaItem.Builder(
                        mediaItemBuilder.build()
                    ).apply {
                        if (state.videoMuted) setRemoveAudio(true)
                    }.build()
                    val transformer = androidx.media3.transformer.Transformer.Builder(context).build()
                    kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
                        transformer.addListener(
                            object : androidx.media3.transformer.Transformer.Listener {
                                override fun onCompleted(
                                    composition: androidx.media3.transformer.Composition,
                                    exportResult: androidx.media3.transformer.ExportResult
                                ) {
                                    if (continuation.isActive) {
                                        continuation.resumeWith(Result.success(Unit))
                                    }
                                }

                                override fun onError(
                                    composition: androidx.media3.transformer.Composition,
                                    exportResult: androidx.media3.transformer.ExportResult,
                                    exportException: androidx.media3.transformer.ExportException
                                ) {
                                    if (continuation.isActive) {
                                        continuation.resumeWith(Result.failure(exportException))
                                    }
                                }
                            }
                        )
                        transformer.start(edited, output.absolutePath)
                    }
                    output
                }
            }
            result.onSuccess { file ->
                val saved = mediaSaver.saveVideoCopy(file, "EDIT_${System.currentTimeMillis()}.mp4")
                saved.onSuccess { uri ->
                    _uiState.update { it.copy(isSaving = false, saveComplete = true, savedUri = uri) }
                    voice.announce("Edited video saved as a new copy.", AnnouncementPriority.HIGH)
                }.onFailure { error ->
                    _uiState.update { it.copy(isSaving = false, error = error.message) }
                    voice.announce("The edited video could not be saved.", AnnouncementPriority.CRITICAL)
                }
            }.onFailure { error ->
                _uiState.update { it.copy(isSaving = false, error = error.message) }
                voice.announce(
                    "Video editing failed. ${error.message ?: ""}",
                    AnnouncementPriority.CRITICAL
                )
            }
        }
    }

    /** Renders a new bitmap with all requested adjustments in one pass. */
    private fun render(source: Bitmap, state: EditorUiState): Bitmap {
        var working = source
        if (state.cropLeft > 0f || state.cropTop > 0f || state.cropRight < 1f || state.cropBottom < 1f) {
            val x = (state.cropLeft * working.width).toInt().coerceIn(0, working.width - 1)
            val y = (state.cropTop * working.height).toInt().coerceIn(0, working.height - 1)
            val w = ((state.cropRight - state.cropLeft) * working.width).toInt().coerceIn(1, working.width - x)
            val h = ((state.cropBottom - state.cropTop) * working.height).toInt().coerceIn(1, working.height - y)
            working = Bitmap.createBitmap(working, x, y, w, h)
        }
        if (state.rotationDegrees != 0 || state.flipHorizontal || state.flipVertical) {
            val matrix = Matrix().apply {
                postRotate(state.rotationDegrees.toFloat())
                postScale(
                    if (state.flipHorizontal) -1f else 1f,
                    if (state.flipVertical) -1f else 1f
                )
            }
            working = Bitmap.createBitmap(working, 0, 0, working.width, working.height, matrix, true)
        }
        val output = Bitmap.createBitmap(working.width, working.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val colorMatrix = buildColorMatrix(state)
        val filterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = android.graphics.ColorMatrixColorFilter(colorMatrix)
        }
        canvas.drawBitmap(working, 0f, 0f, filterPaint)

        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        state.strokes.forEach { stroke ->
            paint.color = stroke.color
            paint.strokeWidth = stroke.width * (output.width / 1000f).coerceAtLeast(1f)
            if (stroke.points.size > 1) {
                val path = Path()
                path.moveTo(stroke.points.first().first, stroke.points.first().second)
                stroke.points.drop(1).forEach { point -> path.lineTo(point.first, point.second) }
                canvas.drawPath(path, paint)
            }
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.DEFAULT_BOLD
        }
        state.textOverlays.forEach { overlay ->
            textPaint.color = overlay.color
            textPaint.textSize = overlay.textSize
            canvas.drawText(overlay.text, overlay.x, overlay.y, textPaint)
        }
        return output
    }

    private fun buildColorMatrix(state: EditorUiState): android.graphics.ColorMatrix {
        val brightness = state.brightness * 255f + state.exposure * 128f
        val contrast = 1f + state.contrast
        val saturation = 1f + state.saturation
        val matrix = android.graphics.ColorMatrix()
        matrix.setSaturation(saturation)
        val contrastMatrix = android.graphics.ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, brightness,
                0f, contrast, 0f, 0f, brightness,
                0f, 0f, contrast, 0f, brightness,
                0f, 0f, 0f, 1f, 0f
            )
        )
        matrix.postConcat(contrastMatrix)
        when (state.filter) {
            FilterPreset.NONE -> Unit
            FilterPreset.MONO -> {
                val mono = android.graphics.ColorMatrix()
                mono.setSaturation(0f)
                matrix.postConcat(mono)
            }
            FilterPreset.WARM -> matrix.postConcat(
                android.graphics.ColorMatrix(
                    floatArrayOf(
                        1.1f, 0f, 0f, 0f, 12f,
                        0f, 1.02f, 0f, 0f, 4f,
                        0f, 0f, 0.92f, 0f, 0f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
            FilterPreset.COOL -> matrix.postConcat(
                android.graphics.ColorMatrix(
                    floatArrayOf(
                        0.92f, 0f, 0f, 0f, 0f,
                        0f, 1.0f, 0f, 0f, 0f,
                        0f, 0f, 1.12f, 0f, 10f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
            FilterPreset.HIGH_CONTRAST -> matrix.postConcat(
                android.graphics.ColorMatrix(
                    floatArrayOf(
                        1.35f, 0f, 0f, 0f, -30f,
                        0f, 1.35f, 0f, 0f, -30f,
                        0f, 0f, 1.35f, 0f, -30f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
            FilterPreset.FADED -> {
                matrix.postConcat(
                    android.graphics.ColorMatrix(
                        floatArrayOf(
                            0.9f, 0f, 0f, 0f, 22f,
                            0f, 0.9f, 0f, 0f, 22f,
                            0f, 0f, 0.9f, 0f, 22f,
                            0f, 0f, 0f, 1f, 0f
                        )
                    )
                )
            }
        }
        return matrix
    }

    fun describeEdits() {
        val state = _uiState.value
        val parts = buildList {
            if (state.cropLeft > 0f || state.cropRight < 1f) add("cropped")
            if (state.rotationDegrees != 0) add("rotated ${state.rotationDegrees} degrees")
            if (state.flipHorizontal) add("flipped horizontally")
            if (state.flipVertical) add("flipped vertically")
            if (state.brightness != 0f) add("brightness ${state.brightness}")
            if (state.contrast != 0f) add("contrast ${state.contrast}")
            if (state.saturation != 0f) add("saturation ${state.saturation}")
            if (state.exposure != 0f) add("exposure ${state.exposure}")
            if (state.filter != FilterPreset.NONE) add("filter ${state.filter.spoken}")
            if (state.strokes.isNotEmpty()) add("${state.strokes.size} drawings")
            if (state.textOverlays.isNotEmpty()) add("${state.textOverlays.size} text overlays")
            if (state.videoMuted) add("muted")
        }
        voice.announce(
            if (parts.isEmpty()) "No edits yet" else "Current edits: ${parts.joinToString(", ")}",
            AnnouncementPriority.HIGH
        )
    }

    fun requestDiscard() {
        _uiState.update { it.copy(showDiscardConfirmation = true) }
    }

    fun cancelDiscard() {
        _uiState.update { it.copy(showDiscardConfirmation = false) }
    }
}
