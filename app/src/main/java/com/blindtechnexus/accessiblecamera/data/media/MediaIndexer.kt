/*
 * Accessible Camera and Gallery
 * Copyright (C) 2026 Sujan Rai, Blind Tech Nexus team.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.blindtechnexus.accessiblecamera.data.media

import android.util.Log
import com.blindtechnexus.accessiblecamera.data.db.MediaDao
import com.blindtechnexus.accessiblecamera.data.db.MediaEntity
import com.blindtechnexus.accessiblecamera.domain.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the Room index in sync with MediaStore. Running the sync on the IO dispatcher
 * keeps the main thread free even with tens of thousands of items.
 */
@Singleton
class MediaIndexer @Inject constructor(
    private val mediaDao: MediaDao,
    private val mediaStore: MediaStoreDataSource
) {

    private val tag = "MediaIndexer"

    /** Full reconciliation pass. Errors are logged and never crash the UI. */
    suspend fun syncAll(): Int = withContext(Dispatchers.IO) {
        runCatching {
            val items = mediaStore.queryImagesAndVideos(limit = 10_000, offset = 0)
            if (items.isEmpty()) return@runCatching 0
            val existingIds = mediaDao.allIndexedMediaStoreIds().toHashSet()
            val entities = items.map { it.toEntity() }
            mediaDao.upsertAll(entities)
            val currentIds = items.map { it.id }.toSet()
            val stale = existingIds.filterNot { currentIds.contains(it) }
            for (mediaStoreId in stale) {
                mediaDao.deleteByMediaStoreId(mediaStoreId)
            }
            items.size
        }.onFailure { Log.w(tag, "Media index sync failed", it) }.getOrDefault(0)
    }

    /** Indexes one freshly captured item so it appears in the gallery immediately. */
    suspend fun index(item: MediaItem): Long = withContext(Dispatchers.IO) {
        mediaDao.upsert(item.toEntity())
    }
}

/** Converts a MediaStore item into its Room row. */
fun MediaItem.toEntity(): MediaEntity = MediaEntity(
    mediaStoreId = id,
    uri = uri.toString(),
    displayName = displayName,
    mimeType = mimeType,
    mediaType = type.name,
    dateTakenMillis = dateTakenMillis,
    dateAddedMillis = dateAddedMillis,
    sizeBytes = sizeBytes,
    width = width,
    height = height,
    durationMillis = durationMillis,
    bucketId = bucketId,
    bucketName = bucketName,
    relativePath = relativePath,
    isFavorite = isFavorite,
    isTrashed = isTrashed,
    latitude = latitude,
    longitude = longitude,
    orientationDegrees = orientationDegrees,
    hasAudio = hasAudio
)

/** Converts a Room row back into a MediaItem for the UI. */
fun MediaEntity.toItem(): MediaItem = MediaItem(
    id = id,
    uri = android.net.Uri.parse(uri),
    displayName = displayName,
    mimeType = mimeType,
    type = runCatching { com.blindtechnexus.accessiblecamera.domain.model.MediaType.valueOf(mediaType) }
        .getOrDefault(com.blindtechnexus.accessiblecamera.domain.model.MediaType.UNKNOWN),
    dateTakenMillis = dateTakenMillis,
    dateAddedMillis = dateAddedMillis,
    sizeBytes = sizeBytes,
    width = width,
    height = height,
    durationMillis = durationMillis,
    bucketId = bucketId,
    bucketName = bucketName,
    relativePath = relativePath,
    isFavorite = isFavorite,
    isTrashed = isTrashed,
    latitude = latitude,
    longitude = longitude,
    orientationDegrees = orientationDegrees,
    hasAudio = hasAudio
)
