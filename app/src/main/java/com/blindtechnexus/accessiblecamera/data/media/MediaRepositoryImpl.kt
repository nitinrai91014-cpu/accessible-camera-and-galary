/*
 * Accessible Camera and Gallery
 * Copyright (C) 2026 Sujan Rai, Blind Tech Nexus team.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.blindtechnexus.accessiblecamera.data.media

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.map
import com.blindtechnexus.accessiblecamera.data.db.MediaDao
import com.blindtechnexus.accessiblecamera.data.db.MediaEntity
import com.blindtechnexus.accessiblecamera.data.db.TrashDao
import com.blindtechnexus.accessiblecamera.data.db.TrashEntity
import com.blindtechnexus.accessiblecamera.core.util.Formatters
import com.blindtechnexus.accessiblecamera.core.util.TimeFormat
import com.blindtechnexus.accessiblecamera.domain.model.Album
import com.blindtechnexus.accessiblecamera.domain.model.GalleryFilter
import com.blindtechnexus.accessiblecamera.domain.model.GallerySection
import com.blindtechnexus.accessiblecamera.domain.model.MediaDetails
import com.blindtechnexus.accessiblecamera.domain.model.MediaItem
import com.blindtechnexus.accessiblecamera.domain.model.MediaType
import com.blindtechnexus.accessiblecamera.domain.repository.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Media repository combining MediaStore (the source of truth for file data) with the
 * Room index (fast queries, favorites, app trash and undo).
 */
@Singleton
class MediaRepositoryImpl @Inject constructor(
    private @ApplicationContext val context: Context,
    private val mediaDao: MediaDao,
    private val trashDao: TrashDao,
    private val mediaStore: MediaStoreDataSource,
    private val indexer: MediaIndexer
) : MediaRepository {

    private val screenshotsPattern = "%Screenshot%"

    override fun pagedMedia(filter: Flow<GalleryFilter>): Flow<PagingData<MediaItem>> =
        filter.flatMapLatest { active ->
            Pager(
                config = PagingConfig(
                    pageSize = 60,
                    prefetchDistance = 30,
                    initialLoadSize = 90,
                    enablePlaceholders = false,
                    maxSize = 600
                ),
                pagingSourceFactory = { sourceFor(active) }
            ).flow.map { data -> data.map { it.toItem() } }
        }

    private fun sourceFor(filter: GalleryFilter): PagingSource<Int, MediaEntity> = when (filter.section) {
        GallerySection.TRASH -> mediaDao.pagedTrash()
        GallerySection.SCREENSHOTS -> mediaDao.pagedScreenshots(screenshotsPattern)
        else -> mediaDao.pagedAll(
            albumId = filter.albumId,
            type = when (filter.section) {
                GallerySection.VIDEOS -> MediaType.VIDEO.name
                GallerySection.PHOTOS -> MediaType.IMAGE.name
                else -> filter.mediaType?.name
            },
            query = filter.query.trim(),
            favoritesOnly = if (filter.section == GallerySection.FAVORITES || filter.favoritesOnly) 1 else 0,
            fromMillis = filter.fromMillis,
            toMillis = filter.toMillis,
            minSize = filter.minSizeBytes,
            maxSize = filter.maxSizeBytes,
            sort = filter.sortOrder.name
        )
    }

    override fun observeAlbums(): Flow<List<Album>> = mediaDao.observeAlbums().map { rows ->
        rows.mapNotNull { row ->
            if (row.bucketId == null) return@mapNotNull null
            Album(
                bucketId = row.bucketId,
                name = row.name ?: "Unknown album",
                itemCount = row.itemCount,
                coverUri = row.coverUri?.let { Uri.parse(it) },
                newestMillis = row.newestMillis
            )
        }
    }

    override fun observeTrashed(): Flow<List<MediaItem>> =
        mediaDao.observeTrash().map { list -> list.map { it.toItem() } }

    override fun observeFavorites(): Flow<List<MediaItem>> =
        mediaDao.observeFavorites().map { list -> list.map { it.toItem() } }

    override suspend fun recentItems(limit: Int): List<MediaItem> = withContext(Dispatchers.IO) {
        mediaDao.recent(limit).map { it.toItem() }
    }

    override suspend fun getItem(id: Long): MediaItem? = withContext(Dispatchers.IO) {
        mediaDao.byId(id)?.toItem()
    }

    override suspend fun details(item: MediaItem): MediaDetails = withContext(Dispatchers.IO) {
        val exif = if (item.type == MediaType.IMAGE) readExif(item.uri) else emptyMap()
        MediaDetails(
            item = item,
            path = item.relativePath.ifBlank { item.displayName },
            dateTakenLabel = Formatters.dateTime(item.dateTakenMillis),
            dateAddedLabel = Formatters.dateTime(item.dateAddedMillis),
            sizeLabel = Formatters.size(item.sizeBytes),
            resolutionLabel = Formatters.dimensions(item.width, item.height),
            durationLabel = if (item.isVideo) TimeFormat.spoken(item.durationMillis) else null,
            albumLabel = item.bucketName,
            mimeType = item.mimeType,
            origin = if (item.relativePath.contains("Screenshot", ignoreCase = true)) "Screenshot" else "Camera or download",
            exifSummary = exif
        )
    }

    private fun readExif(uri: Uri): Map<String, String> = runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val exif = ExifInterface(stream)
            buildMap {
                exif.getAttribute(ExifInterface.TAG_MAKE)?.let { put("Camera maker", it) }
                exif.getAttribute(ExifInterface.TAG_MODEL)?.let { put("Camera model", it) }
                exif.getAttribute(ExifInterface.TAG_F_NUMBER)?.let { put("Aperture", "f over $it") }
                exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)?.let { put("Shutter", "$it seconds") }
                exif.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS)?.let { put("ISO", it) }
                exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH)?.let { put("Focal length", it) }
                exif.getAttribute(ExifInterface.TAG_LENS_MODEL)?.let { put("Lens", it) }
            }
        }
    }.getOrNull() ?: emptyMap()

    override suspend fun latestThumbnail(): MediaItem? = withContext(Dispatchers.IO) {
        mediaDao.latest()?.toItem()
    }

    override suspend fun deleteToTrash(items: List<MediaItem>) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val ids = items.map { it.id }
        for (item in items) {
            val systemTrashed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                mediaStore.trash(item)
            } else {
                false
            }
            trashDao.upsert(
                TrashEntity(
                    mediaId = item.id,
                    originalUri = item.uri.toString(),
                    trashedUri = if (systemTrashed) item.uri.toString() else null,
                    trashedAtMillis = now
                )
            )
        }
        mediaDao.setTrashed(ids, true, now)
    }

    override suspend fun restore(items: List<MediaItem>) = withContext(Dispatchers.IO) {
        val ids = items.map { it.id }
        for (item in items) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                mediaStore.restore(item)
            }
        }
        trashDao.delete(ids)
        mediaDao.setTrashed(ids, false, null)
    }

    override suspend fun deletePermanently(items: List<MediaItem>) = withContext(Dispatchers.IO) {
        for (item in items) {
            val removed = mediaStore.deletePermanently(item)
            if (!removed && Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                // Without scoped storage MediaStore delete needs write permission which we do
                // not request on newer platforms. Nothing else to do.
            }
        }
        val ids = items.map { it.id }
        trashDao.delete(ids)
        mediaDao.deleteByIds(ids)
    }

    override suspend fun setFavorite(items: List<MediaItem>, favorite: Boolean) =
        withContext(Dispatchers.IO) {
            mediaDao.setFavorite(items.map { it.id }, favorite)
        }

    override suspend fun moveToAlbum(items: List<MediaItem>, albumName: String): Int =
        withContext(Dispatchers.IO) {
            var moved = 0
            for (item in items) {
                val target = resolveAlbumUri(item, albumName) ?: continue
                val values = android.content.ContentValues().apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, target)
                    }
                    put(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME, albumName)
                }
                runCatching { context.contentResolver.update(item.uri, values, null, null) }
                moved++
            }
            mediaDao.moveToAlbum(items.map { it.id }, albumName)
            moved
        }

    private fun resolveAlbumUri(item: MediaItem, albumName: String): String? {
        val base = when (item.type) {
            MediaType.IMAGE -> "Pictures"
            MediaType.VIDEO -> "Movies"
            else -> return null
        }
        val safeName = albumName.replace(Regex("[^A-Za-z0-9 _-]"), "").ifBlank { "Album" }
        return "$base/$safeName/"
    }

    override suspend fun rescan() {
        indexer.syncAll()
    }

    override suspend fun persistableUri(uri: Uri): Uri? = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            uri
        }.getOrNull()
    }

    override suspend fun importCapture(uri: Uri, type: String): MediaItem? = withContext(Dispatchers.IO) {
        runCatching {
            val collection = if (type.startsWith("video")) {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
            if (uri.authority?.contains("media") == true) {
                val id = ContentUris.parseId(uri)
                val item = mediaStore.queryImagesAndVideos(limit = 1, offset = 0)
                    .firstOrNull { it.id == id }
                if (item != null) {
                    indexer.index(item)
                    return@runCatching item
                }
            }
            val queryUri = if (collection == MediaStore.Video.Media.EXTERNAL_CONTENT_URI) {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
            context.contentResolver.query(queryUri, null, null, null, "${MediaStore.MediaColumns.DATE_ADDED} DESC")
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idIndex = cursor.getColumnIndex(MediaStore.MediaColumns._ID)
                        val id = cursor.getLong(idIndex)
                        val itemUri = ContentUris.withAppendedId(queryUri, id)
                        val nameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                        val now = System.currentTimeMillis()
                        val item = MediaItem(
                            id = id,
                            uri = itemUri,
                            displayName = cursor.getString(nameIndex) ?: "New media",
                            mimeType = if (type.startsWith("video")) "video/mp4" else "image/jpeg",
                            type = if (type.startsWith("video")) MediaType.VIDEO else MediaType.IMAGE,
                            dateTakenMillis = now,
                            dateAddedMillis = now,
                            sizeBytes = 0L,
                            width = 0,
                            height = 0,
                            durationMillis = 0L,
                            bucketId = "capture",
                            bucketName = "Camera",
                            relativePath = ""
                        )
                        indexer.index(item)
                        return@use item
                    }
                    null
                }
        }.getOrNull()
    }

    override fun observeIndexCount(): Flow<Int> = mediaDao.observeCount()
}
