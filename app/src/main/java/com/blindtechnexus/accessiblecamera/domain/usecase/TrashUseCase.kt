/*
 * Accessible Camera and Gallery
 * Copyright (C) 2026 Sujan Rai, Blind Tech Nexus team.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.blindtechnexus.accessiblecamera.domain.usecase

import com.blindtechnexus.accessiblecamera.domain.model.MediaItem
import com.blindtechnexus.accessiblecamera.domain.repository.MediaRepository
import javax.inject.Inject

/**
 * Trash, restore and permanent delete rules.
 *
 * Deleting always goes through the in app trash first, and permanently deleting a non empty
 * selection always requires a confirmation. The confirmation wording is produced here so the
 * gallery and the media viewer cannot drift apart.
 */
class TrashUseCase @Inject constructor(
    private val mediaRepository: MediaRepository
) {

    data class Confirmation(val required: Boolean, val message: String, val confirmLabel: String)

    /** Number of items, spelled out so the message reads well in speech. */
    fun confirmationFor(items: List<MediaItem>): Confirmation {
        val count = items.size
        if (count == 0) {
            return Confirmation(false, "Nothing is selected", "Delete")
        }
        val noun = if (count == 1) "item" else "items"
        val message = if (count == 1) {
            "Permanently delete this ${if (items.first().isVideo) "video" else "photo"}? This cannot be undone."
        } else {
            "Permanently delete $count $noun? This cannot be undone."
        }
        return Confirmation(
            required = true,
            message = message,
            confirmLabel = if (count == 1) "Delete permanently" else "Delete $count $noun"
        )
    }

    /** Spoken summary after a move to trash, used for the undo snackbar and speech. */
    fun trashSummary(items: List<MediaItem>): String = when (items.size) {
        0 -> "Nothing moved"
        1 -> "Moved 1 item to trash. You can undo this."
        else -> "Moved ${items.size} items to trash. You can undo this."
    }

    suspend fun moveToTrash(items: List<MediaItem>): String {
        mediaRepository.deleteToTrash(items)
        return trashSummary(items)
    }

    suspend fun restore(items: List<MediaItem>): String {
        mediaRepository.restore(items)
        return when (items.size) {
            0 -> "Nothing restored"
            1 -> "Restored 1 item"
            else -> "Restored ${items.size} items"
        }
    }

    suspend fun deletePermanently(items: List<MediaItem>): String {
        mediaRepository.deletePermanently(items)
        return when (items.size) {
            0 -> "Nothing deleted"
            1 -> "Deleted 1 item permanently"
            else -> "Deleted ${items.size} items permanently"
        }
    }
}
