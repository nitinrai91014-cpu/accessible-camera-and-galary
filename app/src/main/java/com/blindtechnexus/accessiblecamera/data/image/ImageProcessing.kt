/*
 * Accessible Camera and Gallery
 * Copyright (C) 2026 Sujan Rai, Blind Tech Nexus team.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.blindtechnexus.accessiblecamera.data.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Bitmap helpers for editing. All work happens on the default dispatcher so the main
 * thread never decodes pixels.
 */
object ImageProcessing {

    /** Decodes a file with a maximum dimension to protect memory on large photos. */
    suspend fun decodeScaled(file: File, maxDimension: Int = 2048): Bitmap? =
        withContext(Dispatchers.IO) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, bounds)
                var sample = 1
                while (bounds.outWidth / sample > maxDimension || bounds.outHeight / sample > maxDimension) {
                    sample *= 2
                }
                val options = BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                BitmapFactory.decodeFile(file.absolutePath, options)
            }.getOrNull()
        }

    /** Reads EXIF orientation and returns the rotation needed to show it upright. */
    suspend fun readOrientationDegrees(file: File): Int = withContext(Dispatchers.IO) {
        runCatching {
            val exif = ExifInterface(file.absolutePath)
            when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        }.getOrDefault(0)
    }

    suspend fun rotate(bitmap: Bitmap, degrees: Int): Bitmap = withContext(Dispatchers.Default) {
        if (degrees % 360 == 0) return@withContext bitmap
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    suspend fun flip(bitmap: Bitmap, horizontal: Boolean, vertical: Boolean): Bitmap =
        withContext(Dispatchers.Default) {
            val sx = if (horizontal) -1f else 1f
            val sy = if (vertical) -1f else 1f
            val matrix = Matrix().apply { postScale(sx, sy) }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }

    suspend fun crop(bitmap: Bitmap, left: Float, top: Float, right: Float, bottom: Float): Bitmap =
        withContext(Dispatchers.Default) {
            val x = (left * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
            val y = (top * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
            val w = ((right - left) * bitmap.width).toInt().coerceIn(1, bitmap.width - x)
            val h = ((bottom - top) * bitmap.height).toInt().coerceIn(1, bitmap.height - y)
            Bitmap.createBitmap(bitmap, x, y, w, h)
        }

    /** Saves a bitmap as JPEG and returns true on success. */
    suspend fun saveJpeg(bitmap: Bitmap, file: File, quality: Int = 92): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                file.outputStream().use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
                }
                true
            }.getOrDefault(false)
        }

    /** Reads only the pixel dimensions of a file. */
    suspend fun dimensions(file: File): Pair<Int, Int> = withContext(Dispatchers.IO) {
        runCatching {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, options)
            options.outWidth to options.outHeight
        }.getOrDefault(0 to 0)
    }
}
