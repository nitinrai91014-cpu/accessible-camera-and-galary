/*
 * Accessible Camera and Gallery
 * Copyright (C) 2026 Sujan Rai, Blind Tech Nexus team.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.blindtechnexus.accessiblecamera

import android.net.Uri
import com.blindtechnexus.accessiblecamera.domain.model.GalleryFilter
import com.blindtechnexus.accessiblecamera.domain.model.GallerySection
import com.blindtechnexus.accessiblecamera.domain.model.MediaItem
import com.blindtechnexus.accessiblecamera.domain.model.MediaType
import com.blindtechnexus.accessiblecamera.domain.model.SortOrder
import com.blindtechnexus.accessiblecamera.domain.usecase.GalleryQueryUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Section membership, search and sort behaviour of the gallery. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GalleryQueryUseCaseTest {

    private val useCase = GalleryQueryUseCase()

    private fun item(
        id: Long,
        name: String = "IMG_$id.jpg",
        type: MediaType = MediaType.IMAGE,
        size: Long = 1_000L,
        added: Long = id * 1_000L,
        favorite: Boolean = false,
        trashed: Boolean = false,
        bucket: String = "Camera",
        path: String = "DCIM/Camera/"
    ) = MediaItem(
        id = id,
        uri = Uri.parse("content://media/$id"),
        displayName = name,
        mimeType = if (type == MediaType.VIDEO) "video/mp4" else "image/jpeg",
        type = type,
        dateTakenMillis = added,
        dateAddedMillis = added,
        sizeBytes = size,
        width = 4000,
        height = 3000,
        durationMillis = if (type == MediaType.VIDEO) 8_000L else 0L,
        bucketId = bucket,
        bucketName = bucket,
        relativePath = path,
        isFavorite = favorite,
        isTrashed = trashed
    )

    @Test
    fun `trash section contains only trashed items`() {
        val trashed = item(1, trashed = true)
        assertTrue(useCase.matchesSection(trashed, GallerySection.TRASH))
        assertFalse(useCase.matchesSection(trashed, GallerySection.ALL))
    }

    @Test
    fun `videos and photos are separated`() {
        val video = item(2, type = MediaType.VIDEO)
        assertTrue(useCase.matchesSection(video, GallerySection.VIDEOS))
        assertFalse(useCase.matchesSection(video, GallerySection.PHOTOS))
    }

    @Test
    fun `screenshots are detected from the path`() {
        val shot = item(3, name = "Screenshot_20260101.png", path = "Pictures/Screenshots/")
        assertTrue(useCase.matchesSection(shot, GallerySection.SCREENSHOTS))
    }

    @Test
    fun `favorites section follows the favorite flag`() {
        assertTrue(useCase.matchesSection(item(4, favorite = true), GallerySection.FAVORITES))
        assertFalse(useCase.matchesSection(item(5), GallerySection.FAVORITES))
    }

    @Test
    fun `search is case insensitive across name and album`() {
        val photo = item(6, name = "Sunset.jpg", bucket = "Holidays")
        assertTrue(useCase.matchesQuery(photo, "sunset"))
        assertTrue(useCase.matchesQuery(photo, "HOLIDAY"))
        assertFalse(useCase.matchesQuery(photo, "cat"))
        assertTrue(useCase.matchesQuery(photo, "   "))
    }

    @Test
    fun `newest first sort puts the latest item first`() {
        val items = listOf(item(1), item(2), item(3))
        val sorted = useCase.filter(items, GalleryFilter(sortOrder = SortOrder.NEWEST_FIRST))
        assertEquals(listOf(3L, 2L, 1L), sorted.map { it.id })
    }

    @Test
    fun `largest first sort orders by size`() {
        val items = listOf(item(1, size = 10), item(2, size = 500), item(3, size = 100))
        val sorted = useCase.filter(items, GalleryFilter(sortOrder = SortOrder.LARGEST_FIRST))
        assertEquals(listOf(2L, 3L, 1L), sorted.map { it.id })
    }

    @Test
    fun `filters combine section, query and size`() {
        val items = listOf(
            item(1, name = "Trip.jpg", size = 900),
            item(2, name = "Trip.mp4", type = MediaType.VIDEO, size = 900),
            item(3, name = "Other.jpg", size = 900)
        )
        val result = useCase.filter(
            items,
            GalleryFilter(section = GallerySection.PHOTOS, query = "trip", minSizeBytes = 500)
        )
        assertEquals(listOf(1L), result.map { it.id })
    }

    @Test
    fun `result count announcement is spoken naturally`() {
        val filter = GalleryFilter(section = GallerySection.PHOTOS)
        assertEquals("No photos found", useCase.resultCountAnnouncement(filter, 0))
        assertEquals("1 photo found", useCase.resultCountAnnouncement(filter, 1))
        assertEquals("12 photos found", useCase.resultCountAnnouncement(filter, 12))
        assertEquals(
            "Search results. 2 videos found",
            useCase.resultCountAnnouncement(GalleryFilter(section = GallerySection.VIDEOS, query = "a"), 2)
        )
    }
}
