/*
 * Accessible Camera and Gallery
 * Copyright (C) 2026 Sujan Rai, Blind Tech Nexus team.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.blindtechnexus.accessiblecamera.data.media

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.blindtechnexus.accessiblecamera.data.image.ImageProcessing
import com.blindtechnexus.accessiblecamera.data.location.LocationProvider
import com.blindtechnexus.accessiblecamera.data.watermark.WatermarkPainter
import com.blindtechnexus.accessiblecamera.domain.model.MediaItem
import com.blindtechnexus.accessiblecamera.domain.model.MediaType
import com.blindtechnexus.accessiblecamera.domain.model.SaveLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes captures to MediaStore so they appear in the system gallery, then indexes them.
 * Photos can be watermarked and geotagged according to user settings.
 */
@Singleton
class MediaSaver @Inject constructor(
    private val context: Context,
    private val indexer: MediaIndexer,
    private val watermarkPainter: WatermarkPainter
) {

    private companion object {
        const val TAG = "MediaSaver"
        const val IMAGE_PREFIX = "IMG_"
        const val VIDEO_PREFIX = "VID_"
    }

    /** Creates a temp file for the camera to write into. */
    fun createTempFile(isVideo: Boolean): File {
        val dir = File(context.cacheDir, "captures").apply { mkdirs() }
        val extension = if (isVideo) "mp4" else "jpg"
        return File(dir, "capture_${System.currentTimeMillis()}.$extension")
    }

    data class SaveOptions(
        val location: SaveLocation = SaveLocation.PICTURES,
        val geoPoint: LocationProvider.GeoPoint? = null,
        val watermarkEnabled: Boolean = false,
        val watermarkText: String = "",
        val watermarkSecondary: String? = null
    )

    /** Applies optional watermark and EXIF data, publishes to MediaStore and indexes. */
    suspend fun savePhoto(file: File, options: SaveOptions): Result<MediaItem> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (file.length() <= 0L) error("The captured file is empty")
                if (options.watermarkEnabled) {
                    applyWatermark(file, options)
                }
                options.geoPoint?.let { writeExifLocation(file, it) }
                val uri = publish(
                    file = file,
                    isVideo = false,
                    mimeType = "image/jpeg",
                    relativePath = photoRelativePath(options.location),
                    displayName = "$IMAGE_PREFIX${timestamp()}.jpg"
                ) ?: error("MediaStore did not accept the photo")
                val item = buildItem(uri, file, false, options, IMAGE_PREFIX)
                indexer.index(item)
                runCatching { file.delete() }
                item
            }.onFailure { Log.w(TAG, "Photo save failed", it) }
        }

    suspend fun saveVideo(file: File, options: SaveOptions): Result<MediaItem> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (file.length() <= 0L) error("The recorded file is empty")
                options.geoPoint?.let { writeExifLocation(file, it) }
                val uri = publish(
                    file = file,
                    isVideo = true,
                    mimeType = "video/mp4",
                    relativePath = videoRelativePath(options.location),
                    displayName = "$VIDEO_PREFIX${timestamp()}.mp4"
                ) ?: error("MediaStore did not accept the video")
                val item = buildItem(uri, file, true, options, VIDEO_PREFIX)
                indexer.index(item)
                runCatching { file.delete() }
                item
            }.onFailure { Log.w(TAG, "Video save failed", it) }
        }

    private suspend fun applyWatermark(file: File, options: SaveOptions) {
        val bitmap = ImageProcessing.decodeScaled(file, maxDimension = 4096) ?: return
        val stamped = watermarkPainter.apply(bitmap, options.watermarkText, options.watermarkSecondary)
        ImageProcessing.saveJpeg(stamped, file)
        if (stamped !== bitmap) stamped.recycle()
        bitmap.recycle()
    }

    private fun writeExifLocation(file: File, point: LocationProvider.GeoPoint) {
        runCatching {
            val exif = ExifInterface(file.absolutePath)
            exif.setLatLong(point.latitude, point.longitude)
            exif.saveAttributes()
        }.onFailure { Log.w(TAG, "Could not write location metadata", it) }
    }

    private fun publish(
        file: File,
        isVideo: Boolean,
        mimeType: String,
        relativePath: String,
        displayName: String
    ): Uri? {
        val resolver = context.contentResolver
        val collection = if (isVideo) {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        val uri = runCatching { resolver.insert(collection, values) }.getOrNull() ?: return null
        val copied = runCatching {
            resolver.openOutputStream(uri)?.use { output ->
                file.inputStream().use { input -> input.copyTo(output) }
            } != null
        }.getOrDefault(false)
        if (!copied) {
            runCatching { resolver.delete(uri, null, null) }
            return null
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val finalizeValues = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            runCatching { resolver.update(uri, finalizeValues, null, null) }
        } else {
            MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf(mimeType), null)
        }
        return uri
    }

    private fun buildItem(
        uri: Uri,
        file: File,
        isVideo: Boolean,
        options: SaveOptions,
        prefix: String
    ): MediaItem {
        val id = runCatching { android.content.ContentUris.parseId(uri) }.getOrDefault(0L)
        val now = System.currentTimeMillis()
        val dimensions = if (isVideo) 0 to 0 else quickDimensions(file)
        return MediaItem(
            id = id,
            uri = uri,
            displayName = uri.lastPathSegment ?: "$prefix${timestamp()}.jpg",
            mimeType = if (isVideo) "video/mp4" else "image/jpeg",
            type = if (isVideo) MediaType.VIDEO else MediaType.IMAGE,
            dateTakenMillis = now,
            dateAddedMillis = now,
            sizeBytes = file.length(),
            width = dimensions.first,
            height = dimensions.second,
            durationMillis = 0L,
            bucketId = if (isVideo) "Movies" else "Pictures",
            bucketName = if (isVideo) "Movies" else "Pictures",
            relativePath = if (isVideo) videoRelativePath(options.location) else photoRelativePath(options.location),
            latitude = options.geoPoint?.latitude,
            longitude = options.geoPoint?.longitude
        )
    }

    private fun quickDimensions(file: File): Pair<Int, Int> = runCatching {
        val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(file.absolutePath, options)
        options.outWidth to options.outHeight
    }.getOrDefault(0 to 0)

    private fun photoRelativePath(location: SaveLocation): String = when (location) {
        SaveLocation.PICTURES -> Environment.DIRECTORY_PICTURES + "/AccessibleCamera"
        SaveLocation.DCIM -> "DCIM/AccessibleCamera"
        SaveLocation.APP_FOLDER -> "AccessibleCamera/Photos"
    }

    private fun videoRelativePath(location: SaveLocation): String = when (location) {
        SaveLocation.PICTURES -> Environment.DIRECTORY_MOVIES + "/AccessibleCamera"
        SaveLocation.DCIM -> "DCIM/AccessibleCamera"
        SaveLocation.APP_FOLDER -> "AccessibleCamera/Videos"
    }

    private fun timestamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    /** Saves an edited bitmap as a new file, leaving the original untouched. */
    suspend fun saveEditedCopy(bitmap: android.graphics.Bitmap, displayName: String): Result<Uri> =
        withContext(Dispatchers.IO) {
            runCatching {
                val temp = createTempFile(isVideo = false)
                ImageProcessing.saveJpeg(bitmap, temp)
                val uri = publish(
                    file = temp,
                    isVideo = false,
                    mimeType = "image/jpeg",
                    relativePath = photoRelativePath(SaveLocation.PICTURES),
                    displayName = displayName
                ) ?: error("MediaStore did not accept the edited photo")
                temp.delete()
                uri
            }
        }

    /** Saves a processed video produced by Media3 transformer. */
    suspend fun saveVideoCopy(file: File, displayName: String): Result<Uri> =
        withContext(Dispatchers.IO) {
            runCatching {
                val uri = publish(
                    file = file,
                    isVideo = true,
                    mimeType = "video/mp4",
                    relativePath = videoRelativePath(SaveLocation.PICTURES),
                    displayName = displayName
                ) ?: error("MediaStore did not accept the edited video")
                uri
            }
        }
}
