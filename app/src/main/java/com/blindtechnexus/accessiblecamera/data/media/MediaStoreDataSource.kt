/*
 * Accessible Camera and Gallery
 * Copyright (C) 2026 Sujan Rai, Blind Tech Nexus team.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.blindtechnexus.accessiblecamera.data.media

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import com.blindtechnexus.accessiblecamera.domain.model.MediaItem
import com.blindtechnexus.accessiblecamera.domain.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads media metadata straight from MediaStore using scoped storage APIs.
 * Query results are mapped into plain [MediaItem] values so Room and the UI never touch
 * a cursor.
 */
@Singleton
class MediaStoreDataSource @Inject constructor(
    private @ApplicationContext val context: Context
) {

    private val projection = buildList {
        add(MediaStore.MediaColumns._ID)
        add(MediaStore.MediaColumns.DISPLAY_NAME)
        add(MediaStore.MediaColumns.MIME_TYPE)
        add(MediaStore.MediaColumns.DATE_TAKEN)
        add(MediaStore.MediaColumns.DATE_ADDED)
        add(MediaStore.MediaColumns.SIZE)
        add(MediaStore.MediaColumns.WIDTH)
        add(MediaStore.MediaColumns.HEIGHT)
        add(MediaStore.MediaColumns.BUCKET_ID)
        add(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
        add(MediaStore.MediaColumns.RELATIVE_PATH)
        add(MediaStore.MediaColumns.DURATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            add(MediaStore.MediaColumns.IS_TRASHED)
        }
    }.toTypedArray()

    /** Reads all images and videos, newest first. */
    suspend fun queryImagesAndVideos(limit: Int = 5000, offset: Int = 0): List<MediaItem> =
        withContext(Dispatchers.IO) {
            val items = mutableListOf<MediaItem>()
            items += queryCollection(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, MediaType.IMAGE, limit, offset)
            items += queryCollection(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, MediaType.VIDEO, limit, offset)
            items.sortedByDescending { it.dateTakenMillis }
        }

    /** Items removed by other apps or by the user in the system gallery. */
    suspend fun queryTrashed(): List<MediaItem> = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return@withContext emptyList()
        val items = mutableListOf<MediaItem>()
        items += queryCollection(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            MediaType.IMAGE,
            limit = 1000,
            offset = 0,
            includeTrashedOnly = true
        )
        items += queryCollection(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            MediaType.VIDEO,
            limit = 1000,
            offset = 0,
            includeTrashedOnly = true
        )
        items
    }

    private fun queryCollection(
        collection: Uri,
        type: MediaType,
        limit: Int,
        offset: Int,
        includeTrashedOnly: Boolean = false
    ): List<MediaItem> {
        val results = mutableListOf<MediaItem>()
        val selection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            "${MediaStore.MediaColumns.IS_TRASHED} = ?"
        } else null
        val selectionArgs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            arrayOf(if (includeTrashedOnly) "1" else "0")
        } else null
        val sortOrder = "${MediaStore.MediaColumns.DATE_TAKEN} DESC"
        val bundle = Bundle().apply {
            putInt(android.content.ContentResolver.QUERY_ARG_LIMIT, limit)
            putInt(android.content.ContentResolver.QUERY_ARG_OFFSET, offset)
            putString(android.content.ContentResolver.QUERY_ARG_SQL_SORT_ORDER, sortOrder)
        }
        val cursor = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.contentResolver.query(collection, projection, bundle, null)
            } else {
                context.contentResolver.query(collection, projection, selection, selectionArgs, sortOrder)
            }
        }.getOrNull() ?: return emptyList()

        cursor.use { c ->
            val idColumn = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameColumn = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val mimeColumn = c.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            val dateTakenColumn = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN)
            val dateAddedColumn = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val sizeColumn = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val widthColumn = c.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
            val heightColumn = c.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
            val bucketIdColumn = c.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_ID)
            val bucketNameColumn = c.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
            val pathColumn = c.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
            val durationColumn = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DURATION)

            while (c.moveToNext()) {
                val id = c.getLong(idColumn)
                val uri = ContentUris.withAppendedId(collection, id)
                val dateTaken = c.getLong(dateTakenColumn).takeIf { it > 0 }
                    ?: (c.getLong(dateAddedColumn) * 1000L)
                results += MediaItem(
                    id = id,
                    uri = uri,
                    displayName = c.getString(nameColumn) ?: "Untitled",
                    mimeType = c.getString(mimeColumn) ?: type.mimePrefix + "*",
                    type = type,
                    dateTakenMillis = dateTaken,
                    dateAddedMillis = c.getLong(dateAddedColumn) * 1000L,
                    sizeBytes = c.getLong(sizeColumn),
                    width = c.getInt(widthColumn),
                    height = c.getInt(heightColumn),
                    durationMillis = if (type == MediaType.VIDEO) c.getLong(durationColumn) else 0L,
                    bucketId = c.getString(bucketIdColumn) ?: "",
                    bucketName = c.getString(bucketNameColumn) ?: "Unknown album",
                    relativePath = c.getString(pathColumn) ?: ""
                )
            }
        }
        return results
    }

    /**
     * Sends a media item to the system trash. On Android 11 and later the item can be
     * restored by the app or by the system gallery.
     *
     * @return true when the item was trashed.
     */
    suspend fun trash(item: MediaItem): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val values = android.content.ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_TRASHED, 1)
                }
                context.contentResolver.update(item.uri, values, null, null) > 0
            } else {
                false
            }
        }.getOrDefault(false)
    }

    /** Restores an item from the system trash on Android 11 and later. */
    suspend fun restore(item: MediaItem): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val values = android.content.ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_TRASHED, 0)
                }
                context.contentResolver.update(item.uri, values, null, null) > 0
            } else {
                false
            }
        }.getOrDefault(false)
    }

    /** Permanently deletes a single item. */
    suspend fun deletePermanently(item: MediaItem): Boolean = withContext(Dispatchers.IO) {
        runCatching { context.contentResolver.delete(item.uri, null, null) > 0 }.getOrDefault(false)
    }

    /** Deletes several items, returning how many were removed. */
    suspend fun deletePermanently(items: List<MediaItem>): Int = withContext(Dispatchers.IO) {
        var deleted = 0
        for (item in items) {
            if (deletePermanently(item)) deleted++
        }
        deleted
    }

    /** Asks MediaStore to rescan a specific app-owned file. */
    suspend fun scanFile(uri: Uri) = withContext(Dispatchers.IO) {
        runCatching {
            val intent = android.content.Intent(android.content.Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uri)
            context.sendBroadcast(intent)
        }
    }

    /** True when the app may read images through MediaStore. */
    fun canReadImages(): Boolean =
        MediaPermissionEvaluator.hasImageReadAccess(context)

    /** True when the app may read video through MediaStore. */
    fun canReadVideo(): Boolean =
        MediaPermissionEvaluator.hasVideoReadAccess(context)
}
