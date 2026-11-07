/*
 * Accessible Camera and Gallery
 * Copyright (C) 2026 Sujan Rai, Blind Tech Nexus team.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.blindtechnexus.accessiblecamera.domain.usecase

import com.blindtechnexus.accessiblecamera.domain.model.GalleryFilter
import com.blindtechnexus.accessiblecamera.domain.model.GallerySection
import com.blindtechnexus.accessiblecamera.domain.model.MediaItem
import com.blindtechnexus.accessiblecamera.domain.model.SortOrder
import javax.inject.Inject

/**
 * Pure gallery query helpers: section membership, search matching and spoken result counts.
 *
 * Kept free of Android types so the behaviour that blind users rely on, such as "12 photos
 * found", is verified by JVM unit tests rather than by reading code.
 */
class GalleryQueryUseCase @Inject constructor() {

    /** True when an item belongs in the requested section. */
    fun matchesSection(item: MediaItem, section: GallerySection): Boolean = when (section) {
        GallerySection.ALL -> !item.inTrash
        GallerySection.PHOTOS -> !item.inTrash && !item.isVideo
        GallerySection.VIDEOS -> !item.inTrash && item.isVideo
        GallerySection.SCREENSHOTS -> !item.inTrash && item.isScreenshot
        GallerySection.FAVORITES -> !item.inTrash && item.isFavorite
        GallerySection.TRASH -> item.inTrash
    }

    /** Case insensitive search over the name, album and spoken description. */
    fun matchesQuery(item: MediaItem, query: String): Boolean {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return true
        return listOf(item.displayName, item.bucketName, item.spokenTitle)
            .any { field -> field.lowercase().contains(needle) }
    }

    /** Applies every active filter in [GalleryFilter] and returns the sorted result. */
    fun filter(items: List<MediaItem>, filter: GalleryFilter): List<MediaItem> {
        val matched = items.filter { item ->
            matchesSection(item, filter.section) &&
                matchesQuery(item, filter.query) &&
                matchesOptionalFilters(item, filter)
        }
        return when (filter.sortOrder) {
            SortOrder.NEWEST_FIRST -> matched.sortedByDescending { it.dateAddedMillis }
            SortOrder.OLDEST_FIRST -> matched.sortedBy { it.dateAddedMillis }
            SortOrder.NAME_ASCENDING -> matched.sortedBy { it.displayName.lowercase() }
            SortOrder.NAME_DESCENDING ->
                matched.sortedByDescending { it.displayName.lowercase() }
            SortOrder.LARGEST_FIRST -> matched.sortedByDescending { it.sizeBytes }
            SortOrder.SMALLEST_FIRST -> matched.sortedBy { it.sizeBytes }
        }
    }

    private fun matchesOptionalFilters(item: MediaItem, filter: GalleryFilter): Boolean {
        if (filter.favoritesOnly && !item.isFavorite) return false
        if (filter.albumId != null && item.bucketId != filter.albumId) return false
        if (filter.mediaType != null && item.type != filter.mediaType) return false
        if (filter.minSizeBytes != null && item.sizeBytes < filter.minSizeBytes) return false
        if (filter.maxSizeBytes != null && item.sizeBytes > filter.maxSizeBytes) return false
        if (filter.fromMillis != null && item.dateTakenMillis < filter.fromMillis) return false
        if (filter.toMillis != null && item.dateTakenMillis > filter.toMillis) return false
        return true
    }

    /** Spoken count so the screen reader hears the result of a filter change. */
    fun resultCountAnnouncement(filter: GalleryFilter, count: Int): String {
        val noun = when (filter.section) {
            GallerySection.VIDEOS -> if (count == 1) "video" else "videos"
            GallerySection.TRASH -> if (count == 1) "item in trash" else "items in trash"
            else -> if (count == 1) "photo" else "photos"
        }
        val prefix = if (filter.query.isBlank()) "" else "Search results. "
        return prefix + when (count) {
            0 -> "No $noun found"
            1 -> "1 $noun found"
            else -> "$count $noun found"
        }
    }
}
