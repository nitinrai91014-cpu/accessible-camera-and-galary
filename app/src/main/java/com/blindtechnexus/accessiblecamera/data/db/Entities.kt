/*
 * Accessible Camera and Gallery
 * Copyright (C) 2026 Sujan Rai, Blind Tech Nexus team.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.blindtechnexus.accessiblecamera.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Media metadata index used for fast gallery queries, favorites and trash. */
@Entity(
    tableName = "media_index",
    indices = [
        Index("dateTakenMillis"),
        Index("bucketId"),
        Index("isTrashed"),
        Index("isFavorite"),
        Index("mediaStoreId", unique = true)
    ]
)
data class MediaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val mediaStoreId: Long,
    val uri: String,
    val displayName: String,
    val mimeType: String,
    val mediaType: String,
    val dateTakenMillis: Long,
    val dateAddedMillis: Long,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val durationMillis: Long,
    val bucketId: String,
    val bucketName: String,
    val relativePath: String,
    val isFavorite: Boolean = false,
    val isTrashed: Boolean = false,
    val trashedAtMillis: Long? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val orientationDegrees: Int = 0,
    val hasAudio: Boolean = true,
    val lastSeenMillis: Long = System.currentTimeMillis()
)

/** Stores the original location of a trashed item so restore can put it back. */
@Entity(tableName = "trash_entry")
data class TrashEntity(
    @PrimaryKey val mediaId: Long,
    val originalUri: String,
    val trashedUri: String?,
    val trashedAtMillis: Long
)

/** Persisted capability report so the settings screen can show what was detected. */
@Entity(tableName = "capability_report")
data class CapabilityEntity(
    @PrimaryKey val id: Int = 1,
    val summary: String,
    val capturedAtMillis: Long
)

/** Recent undo action stored to support restoration after process death. */
@Entity(tableName = "undo_history")
data class UndoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val action: String,
    val payload: String,
    val createdAtMillis: Long
)

/** Editing recipe attached to a media item for non-destructive re-editing. */
@Entity(tableName = "edit_recipe")
data class EditRecipeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val mediaStoreId: Long,
    val cropLeft: Float = 0f,
    val cropTop: Float = 0f,
    val cropRight: Float = 1f,
    val cropBottom: Float = 1f,
    val rotationDegrees: Int = 0,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val exposure: Float = 0f,
    val filterName: String? = null,
    val annotationJson: String? = null,
    val textOverlaysJson: String? = null,
    val updatedAtMillis: Long = System.currentTimeMillis()
)
