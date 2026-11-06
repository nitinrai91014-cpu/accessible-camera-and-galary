/*
 * Accessible Camera and Gallery
 * Copyright (C) 2026 Sujan Rai, Blind Tech Nexus team.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.blindtechnexus.accessiblecamera.data.db

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Queries for the media index. */
@Dao
interface MediaDao {

    @Query(
        """
        SELECT * FROM media_index
        WHERE isTrashed = 0
          AND (:albumId IS NULL OR bucketId = :albumId)
          AND (:type IS NULL OR mediaType = :type)
          AND (:query = '' OR displayName LIKE '%' || :query || '%' OR bucketName LIKE '%' || :query || '%')
          AND (:favoritesOnly = 0 OR isFavorite = 1)
          AND (:fromMillis IS NULL OR dateTakenMillis >= :fromMillis)
          AND (:toMillis IS NULL OR dateTakenMillis <= :toMillis)
          AND (:minSize IS NULL OR sizeBytes >= :minSize)
          AND (:maxSize IS NULL OR sizeBytes <= :maxSize)
        ORDER BY
          CASE WHEN :sort = 'NEWEST_FIRST' THEN dateTakenMillis END DESC,
          CASE WHEN :sort = 'OLDEST_FIRST' THEN dateTakenMillis END ASC,
          CASE WHEN :sort = 'NAME_ASCENDING' THEN displayName END ASC,
          CASE WHEN :sort = 'NAME_DESCENDING' THEN displayName END DESC,
          CASE WHEN :sort = 'SMALLEST_FIRST' THEN sizeBytes END ASC,
          CASE WHEN :sort = 'LARGEST_FIRST' THEN sizeBytes END DESC
        """
    )
    fun pagedAll(
        albumId: String?,
        type: String?,
        query: String,
        favoritesOnly: Int,
        fromMillis: Long?,
        toMillis: Long?,
        minSize: Long?,
        maxSize: Long?,
        sort: String
    ): PagingSource<Int, MediaEntity>

    @Query(
        """
        SELECT * FROM media_index
        WHERE isTrashed = 0 AND bucketName LIKE :bucketPattern
        ORDER BY dateTakenMillis DESC
        """
    )
    fun pagedScreenshots(bucketPattern: String): PagingSource<Int, MediaEntity>

    @Query(
        """
        SELECT * FROM media_index WHERE isTrashed = 1
        ORDER BY trashedAtMillis DESC
        """
    )
    fun pagedTrash(): PagingSource<Int, MediaEntity>

    @Query(
        """
        SELECT * FROM media_index WHERE isTrashed = 0
        ORDER BY dateTakenMillis DESC LIMIT :limit
        """
    )
    suspend fun recent(limit: Int): List<MediaEntity>

    @Query("SELECT * FROM media_index WHERE id = :id")
    suspend fun byId(id: Long): MediaEntity?

    @Query("SELECT * FROM media_index WHERE mediaStoreId = :mediaStoreId LIMIT 1")
    suspend fun byMediaStoreId(mediaStoreId: Long): MediaEntity?

    @Query("SELECT * FROM media_index WHERE uri = :uri LIMIT 1")
    suspend fun byUri(uri: String): MediaEntity?

    @Query("SELECT * FROM media_index WHERE isTrashed = 0 ORDER BY dateTakenMillis DESC LIMIT 1")
    suspend fun latest(): MediaEntity?

    @Query(
        """
        SELECT bucketId AS bucketId, bucketName AS name, COUNT(*) AS itemCount,
               MIN(uri) AS coverUri, MAX(dateTakenMillis) AS newestMillis
        FROM media_index WHERE isTrashed = 0
        GROUP BY bucketId, bucketName
        ORDER BY newestMillis DESC
        """
    )
    fun observeAlbums(): Flow<List<AlbumRow>>

    @Query("SELECT * FROM media_index WHERE isTrashed = 1 ORDER BY trashedAtMillis DESC")
    fun observeTrash(): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media_index WHERE isFavorite = 1 AND isTrashed = 0 ORDER BY dateTakenMillis DESC")
    fun observeFavorites(): Flow<List<MediaEntity>>

    @Query("SELECT COUNT(*) FROM media_index WHERE isTrashed = 0")
    fun observeCount(): Flow<Int>

    @Query("SELECT mediaStoreId FROM media_index WHERE isTrashed = 0")
    suspend fun allVisibleMediaStoreIds(): List<Long>

    @Query("SELECT mediaStoreId FROM media_index")
    suspend fun allIndexedMediaStoreIds(): List<Long>

    @Upsert
    suspend fun upsert(entity: MediaEntity): Long

    @Upsert
    suspend fun upsertAll(entities: List<MediaEntity>)

    @Query("UPDATE media_index SET isFavorite = :favorite WHERE id IN (:ids)")
    suspend fun setFavorite(ids: List<Long>, favorite: Boolean)

    @Query("UPDATE media_index SET isTrashed = :trashed, trashedAtMillis = :trashedAt WHERE id IN (:ids)")
    suspend fun setTrashed(ids: List<Long>, trashed: Boolean, trashedAt: Long?)

    @Query("UPDATE media_index SET bucketName = :albumName WHERE id IN (:ids)")
    suspend fun moveToAlbum(ids: List<Long>, albumName: String)

    @Query("DELETE FROM media_index WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM media_index WHERE mediaStoreId = :mediaStoreId")
    suspend fun deleteByMediaStoreId(mediaStoreId: Long)

    @Query("SELECT * FROM media_index WHERE isTrashed = 1 AND trashedAtMillis < :cutoff")
    suspend fun trashOlderThan(cutoff: Long): List<MediaEntity>

    @Transaction
    suspend fun replaceIfNewer(entity: MediaEntity) {
        val existing = byMediaStoreId(entity.mediaStoreId)
        if (existing == null) {
            upsert(entity)
        } else {
            upsert(
                entity.copy(
                    id = existing.id,
                    isFavorite = existing.isFavorite || entity.isFavorite,
                    isTrashed = existing.isTrashed,
                    trashedAtMillis = existing.trashedAtMillis,
                    lastSeenMillis = System.currentTimeMillis()
                )
            )
        }
    }
}

/** Projection returned by album aggregation. */
data class AlbumRow(
    val bucketId: String?,
    val name: String?,
    val itemCount: Int,
    val coverUri: String?,
    val newestMillis: Long
)

@Dao
interface TrashDao {
    @Upsert
    suspend fun upsert(entity: TrashEntity)

    @Query("SELECT * FROM trash_entry WHERE mediaId = :mediaId")
    suspend fun byId(mediaId: Long): TrashEntity?

    @Query("DELETE FROM trash_entry WHERE mediaId IN (:ids)")
    suspend fun delete(ids: List<Long>)
}

@Dao
interface CapabilityDao {
    @Upsert
    suspend fun upsert(entity: CapabilityEntity)

    @Query("SELECT * FROM capability_report WHERE id = 1")
    suspend fun get(): CapabilityEntity?
}

@Dao
interface UndoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: UndoEntity): Long

    @Query("SELECT * FROM undo_history ORDER BY createdAtMillis DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<UndoEntity>

    @Query("DELETE FROM undo_history WHERE createdAtMillis < :cutoff")
    suspend fun purgeOlderThan(cutoff: Long)

    @Query("DELETE FROM undo_history")
    suspend fun clear()
}

@Dao
interface EditRecipeDao {
    @Upsert
    suspend fun upsert(entity: EditRecipeEntity)

    @Query("SELECT * FROM edit_recipe WHERE mediaStoreId = :mediaStoreId")
    suspend fun byMediaId(mediaStoreId: Long): EditRecipeEntity?

    @Query("DELETE FROM edit_recipe WHERE mediaStoreId = :mediaStoreId")
    suspend fun delete(mediaStoreId: Long)
}
