/*
 * Accessible Camera and Gallery
 * Copyright (C) 2026 Sujan Rai, Blind Tech Nexus team.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.blindtechnexus.accessiblecamera.ui.components

import com.blindtechnexus.accessiblecamera.core.util.Formatters
import com.blindtechnexus.accessiblecamera.core.util.TimeFormat
import com.blindtechnexus.accessiblecamera.domain.model.MediaItem

/**
 * Builds the spoken description of a gallery item. Written once and reused by the grid,
 * the list, the viewer and the details sheet so announcements stay consistent.
 */
object MediaDescriptions {

    fun short(item: MediaItem): String = buildString {
        append(item.type.label)
        append(". ")
        append(Formatters.dateTime(item.dateTakenMillis))
        if (item.isVideo && item.durationMillis > 0) {
            append(". Duration ")
            append(TimeFormat.spoken(item.durationMillis))
        }
        if (item.isFavorite) append(". Favorite")
    }

    fun full(item: MediaItem, index: Int? = null, total: Int? = null): String = buildString {
        if (index != null && total != null) {
            append("Item $index of $total. ")
        }
        append(item.type.label)
        append(", ")
        append(item.bucketName)
        append(". Taken ")
        append(Formatters.dateTime(item.dateTakenMillis))
        append(". ")
        if (item.width > 0 && item.height > 0) {
            append(Formatters.dimensions(item.width, item.height))
            append(". ")
        }
        if (item.isVideo && item.durationMillis > 0) {
            append("Duration ")
            append(TimeFormat.spoken(item.durationMillis))
            append(". ")
        }
        append("Size ")
        append(Formatters.size(item.sizeBytes))
        append(". ")
        if (item.isFavorite) append("Favorite. ")
        if (item.isTrashed) append("In recently deleted. ")
    }

    fun selectionState(selected: Boolean, selectedCount: Int): String = when {
        selectedCount == 0 -> if (selected) "Selected" else "Not selected"
        else -> if (selected) "Selected, $selectedCount items selected" else "Not selected, $selectedCount items selected"
    }

    fun shareMimeType(items: List<MediaItem>): String {
        if (items.isEmpty()) return "image/*"
        val types = items.map { it.type }.distinct()
        return if (types.size == 1) {
            if (types.first() == com.blindtechnexus.accessiblecamera.domain.model.MediaType.VIDEO) {
                "video/*"
            } else {
                "image/*"
            }
        } else {
            "*/*"
        }
    }
}
