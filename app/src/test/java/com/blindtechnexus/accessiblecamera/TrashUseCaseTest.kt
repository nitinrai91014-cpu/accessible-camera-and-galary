/*
 * Accessible Camera and Gallery
 * Copyright (C) 2026 Sujan Rai, Blind Tech Nexus team.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.blindtechnexus.accessiblecamera

import android.net.Uri
import com.blindtechnexus.accessiblecamera.domain.model.MediaItem
import com.blindtechnexus.accessiblecamera.domain.model.MediaType
import com.blindtechnexus.accessiblecamera.domain.repository.MediaRepository
import com.blindtechnexus.accessiblecamera.domain.usecase.TrashUseCase
import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Trash rules: confirmation is always required before a permanent delete. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TrashUseCaseTest {

    private class FakeMediaRepository : MediaRepository {
        var trashed: List<MediaItem> = emptyList()
        var restored: List<MediaItem> = emptyList()
        var deleted: List<MediaItem> = emptyList()

        override fun pagedMedia(filter: Flow<com.blindtechnexus.accessiblecamera.domain.model.GalleryFilter>): Flow<PagingData<MediaItem>> = emptyFlow()
        override fun observeAlbums() = emptyFlow<List<com.blindtechnexus.accessiblecamera.domain.model.Album>>()
        override fun observeTrashed() = emptyFlow<List<MediaItem>>()
        override fun observeFavorites() = emptyFlow<List<MediaItem>>()
        override suspend fun recentItems(limit: Int) = emptyList<MediaItem>()
        override suspend fun getItem(id: Long): MediaItem? = null
        override suspend fun details(item: MediaItem) = throw UnsupportedOperationException()
        override suspend fun latestThumbnail(): MediaItem? = null
        override suspend fun deleteToTrash(items: List<MediaItem>) { trashed = items }
        override suspend fun restore(items: List<MediaItem>) { restored = items }
        override suspend fun deletePermanently(items: List<MediaItem>) { deleted = items }
        override suspend fun setFavorite(items: List<MediaItem>, favorite: Boolean) = Unit
        override suspend fun moveToAlbum(items: List<MediaItem>, albumName: String) = 0
        override suspend fun rescan() = Unit
        override suspend fun persistableUri(uri: Uri): Uri? = null
        override suspend fun importCapture(uri: Uri, type: String): MediaItem? = null
        override fun observeIndexCount(): Flow<Int> = emptyFlow()
    }

    private fun item(id: Long, video: Boolean = false) = MediaItem(
        id = id,
        uri = Uri.parse("content://media/$id"),
        displayName = "file$id",
        mimeType = if (video) "video/mp4" else "image/jpeg",
        type = if (video) MediaType.VIDEO else MediaType.IMAGE,
        dateTakenMillis = 0L,
        dateAddedMillis = 0L,
        sizeBytes = 10L,
        width = 10,
        height = 10,
        durationMillis = 0L,
        bucketId = "b",
        bucketName = "Camera",
        relativePath = "DCIM/Camera/"
    )

    @Test
    fun `single photo confirmation names the item type`() {
        val confirmation = TrashUseCase(FakeMediaRepository()).confirmationFor(listOf(item(1)))
        assertTrue(confirmation.required)
        assertTrue(confirmation.message.contains("photo"))
        assertTrue(confirmation.message.contains("cannot be undone"))
    }

    @Test
    fun `single video confirmation says video`() {
        val confirmation = TrashUseCase(FakeMediaRepository()).confirmationFor(listOf(item(1, video = true)))
        assertTrue(confirmation.message.contains("video"))
    }

    @Test
    fun `multi selection confirmation counts items`() {
        val confirmation = TrashUseCase(FakeMediaRepository()).confirmationFor(listOf(item(1), item(2), item(3)))
        assertTrue(confirmation.message.contains("3 items"))
        assertEquals("Delete 3 items", confirmation.confirmLabel)
    }

    @Test
    fun `empty selection needs no confirmation`() {
        assertFalse(TrashUseCase(FakeMediaRepository()).confirmationFor(emptyList()).required)
    }

    @Test
    fun `move to trash reports how many items moved`() = runTest {
        val repository = FakeMediaRepository()
        val useCase = TrashUseCase(repository)
        val message = useCase.moveToTrash(listOf(item(1), item(2)))
        assertEquals(listOf(1L, 2L), repository.trashed.map { it.id })
        assertTrue(message.contains("2 items"))
        assertTrue(message.contains("undo"))
    }

    @Test
    fun `restore and permanent delete call the repository`() = runTest {
        val repository = FakeMediaRepository()
        val useCase = TrashUseCase(repository)
        useCase.restore(listOf(item(1)))
        useCase.deletePermanently(listOf(item(2)))
        assertEquals(listOf(1L), repository.restored.map { it.id })
        assertEquals(listOf(2L), repository.deleted.map { it.id })
    }
}
