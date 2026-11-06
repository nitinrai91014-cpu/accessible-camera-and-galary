/*
 * Accessible Camera and Gallery
 * Copyright (C) 2026 Sujan Rai, Blind Tech Nexus team.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.blindtechnexus.accessiblecamera.domain.model

import android.net.Uri

enum class MediaType(val label: String, val mimePrefix: String) {
    IMAGE("Photo", "image/"),
    VIDEO("Video", "video/"),
    UNKNOWN("File", "application/");

    companion object {
        fun fromMime(mime: String?): MediaType = when {
            mime == null -> UNKNOWN
            mime.startsWith("image/") -> IMAGE
            mime.startsWith("video/") -> VIDEO
            else -> UNKNOWN
        }
    }
}

/**
 * A single photo or video known to the app. The fields mirror what MediaStore and the
 * Room index provide so list rows and the viewer can be rendered without extra queries.
 */
data class MediaItem(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val mimeType: String,
    val type: MediaType,
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
    val latitude: Double? = null,
    val longitude: Double? = null,
    val orientationDegrees: Int = 0,
    val hasAudio: Boolean = true
) {
    val isVideo: Boolean get() = type == MediaType.VIDEO

    val spokenTitle: String
        get() = if (isVideo) "Video, $bucketName" else "Photo, $bucketName"

    val aspectRatio: Float
        get() = if (width > 0 && height > 0) width.toFloat() / height.toFloat() else 1f
}

/** Detailed metadata shown on the item details screen. */
data class MediaDetails(
    val item: MediaItem,
    val path: String,
    val dateTakenLabel: String,
    val dateAddedLabel: String,
    val sizeLabel: String,
    val resolutionLabel: String,
    val durationLabel: String?,
    val albumLabel: String,
    val mimeType: String,
    val origin: String,
    val exifSummary: Map<String, String> = emptyMap()
)

enum class GallerySection(val label: String, val spoken: String) {
    ALL("All", "all photos and videos"),
    FAVORITES("Favorites", "favorites"),
    SCREENSHOTS("Screenshots", "screenshots"),
    VIDEOS("Videos", "videos"),
    PHOTOS("Photos", "photos"),
    TRASH("Recently deleted", "recently deleted items")
}

enum class SortOrder(val label: String, val spoken: String) {
    NEWEST_FIRST("Newest first", "newest first"),
    OLDEST_FIRST("Oldest first", "oldest first"),
    NAME_ASCENDING("Name A to Z", "name from A to Z"),
    NAME_DESCENDING("Name Z to A", "name from Z to A"),
    SMALLEST_FIRST("Smallest first", "smallest file first"),
    LARGEST_FIRST("Largest first", "largest file first");

    val next: SortOrder
        get() = entries[(ordinal + 1) % entries.size]
}

enum class GalleryLayout(val label: String, val columns: Int) {
    GRID_SMALL("Small grid", 4),
    GRID_MEDIUM("Medium grid", 3),
    GRID_LARGE("Large grid", 2),
    LIST("List", 1);

    val next: GalleryLayout
        get() = entries[(ordinal + 1) % entries.size]
}

data class Album(
    val bucketId: String,
    val name: String,
    val itemCount: Int,
    val coverUri: Uri?,
    val newestMillis: Long
)

data class GalleryFilter(
    val section: GallerySection = GallerySection.ALL,
    val albumId: String? = null,
    val query: String = "",
    val mediaType: MediaType? = null,
    val minSizeBytes: Long? = null,
    val maxSizeBytes: Long? = null,
    val fromMillis: Long? = null,
    val toMillis: Long? = null,
    val favoritesOnly: Boolean = false,
    val sortOrder: SortOrder = SortOrder.NEWEST_FIRST
) {
    val hasActiveFilters: Boolean
        get() = query.isNotBlank() || mediaType != null || minSizeBytes != null ||
            maxSizeBytes != null || fromMillis != null || toMillis != null || favoritesOnly

    fun describe(): String = buildString {
        append(section.spoken)
        if (albumId != null) append(", album filtered")
        if (query.isNotBlank()) {
            append(", search for ")
            append(query)
        }
        mediaType?.let { append(", only ").append(it.label.lowercase()) }
        if (favoritesOnly) append(", favorites only")
        append(", sorted by ")
        append(sortOrder.spoken)
    }
}

/** A recent deletion that can be undone while the snackbar is visible. */
data class PendingUndo(
    val items: List<MediaItem>,
    val message: String,
    val actionLabel: String = "Undo",
    val token: Long = System.currentTimeMillis()
)
