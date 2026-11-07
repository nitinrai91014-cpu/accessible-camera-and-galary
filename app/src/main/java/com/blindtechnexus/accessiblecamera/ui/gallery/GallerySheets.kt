/*
 * Accessible Camera and Gallery
 * Copyright (C) 2026 Sujan Rai, Blind Tech Nexus team.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.blindtechnexus.accessiblecamera.ui.gallery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.blindtechnexus.accessiblecamera.core.util.Formatters
import com.blindtechnexus.accessiblecamera.domain.model.Album
import com.blindtechnexus.accessiblecamera.domain.model.GallerySection
import com.blindtechnexus.accessiblecamera.domain.model.MediaDetails
import com.blindtechnexus.accessiblecamera.domain.model.MediaType
import com.blindtechnexus.accessiblecamera.domain.model.SortOrder
import com.blindtechnexus.accessiblecamera.ui.components.AccessibleBottomSheet
import com.blindtechnexus.accessiblecamera.ui.components.AccessibleChoiceRow
import com.blindtechnexus.accessiblecamera.ui.components.AccessiblePrimaryButton
import com.blindtechnexus.accessiblecamera.ui.components.AccessibleSecondaryButton
import com.blindtechnexus.accessiblecamera.ui.components.AccessibleSwitchRow
import com.blindtechnexus.accessiblecamera.ui.components.MediaThumbnail
import com.blindtechnexus.accessiblecamera.ui.components.SectionHeading
import com.blindtechnexus.accessiblecamera.ui.theme.Dimens

/** Search, section, type, sort and favorites filters. */
@Composable
fun FilterSheet(
    state: GalleryUiState,
    onDismiss: () -> Unit,
    onSection: (GallerySection) -> Unit,
    onSort: (SortOrder) -> Unit,
    onType: (MediaType?) -> Unit,
    onFavoritesOnly: (Boolean) -> Unit,
    onClear: () -> Unit,
    announce: (String) -> Unit
) {
    var query by remember { mutableStateOf(state.filter.query) }
    var fromDate by remember { mutableStateOf<Long?>(state.filter.fromMillis) }
    var toDate by remember { mutableStateOf<Long?>(state.filter.toMillis) }
    var minSizeMb by remember { mutableStateOf<Float>((state.filter.minSizeBytes ?: 0L) / 1_048_576f) }

    AccessibleBottomSheet(
        title = "Search and filters",
        supporting = "Filters apply to the whole gallery. The current selection is described below.",
        onDismissRequest = onDismiss,
        announceOnOpen = announce
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search by name or album") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Search field" }
        )
        AccessiblePrimaryButton(
            text = "Apply search",
            onClick = {
                onSection(GallerySection.ALL)
                onDismiss()
            }
        )

        SectionHeading(text = "Sections")
        GallerySection.entries.forEach { section ->
            AccessibleChoiceRow(
                label = section.label,
                selected = state.filter.section == section,
                supporting = if (section == GallerySection.TRASH) {
                    "${state.trashedCount} items"
                } else {
                    null
                },
                onSelect = { onSection(section) }
            )
        }

        SectionHeading(text = "Media type")
        AccessibleChoiceRow(
            label = "Photos and videos",
            selected = state.filter.mediaType == null,
            onSelect = { onType(null) }
        )
        AccessibleChoiceRow(
            label = "Photos only",
            selected = state.filter.mediaType == MediaType.IMAGE,
            onSelect = { onType(MediaType.IMAGE) }
        )
        AccessibleChoiceRow(
            label = "Videos only",
            selected = state.filter.mediaType == MediaType.VIDEO,
            onSelect = { onType(MediaType.VIDEO) }
        )

        SectionHeading(text = "Sort order")
        SortOrder.entries.forEach { order ->
            AccessibleChoiceRow(
                label = order.label,
                selected = state.filter.sortOrder == order,
                onSelect = { onSort(order) }
            )
        }

        AccessibleSwitchRow(
            label = "Favorites only",
            checked = state.filter.favoritesOnly || state.filter.section == GallerySection.FAVORITES,
            onCheckedChange = onFavoritesOnly,
            description = "Show only items you marked as favorites."
        )

        SectionHeading(text = "Size and date", supporting = "Leave at zero to ignore.")
        Text(
            text = "Minimum size ${minSizeMb.toInt()} megabytes",
            style = MaterialTheme.typography.bodyMedium
        )
        var minSizeMbState by remember { mutableStateOf(minSizeMb) }
        com.blindtechnexus.accessiblecamera.ui.components.AccessibleSliderRow(
            label = "Minimum size",
            value = minSizeMbState,
            valueRange = 0f..200f,
            valueLabel = { "${it.toInt()} megabytes" },
            onValueChange = {
                minSizeMbState = it
                minSizeMb = it
            }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.SmallSpacing)
        ) {
            AccessibleSecondaryButton(
                text = "Last 7 days",
                onClick = {
                    fromDate = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
                    toDate = System.currentTimeMillis()
                    announce("Filtering the last 7 days")
                },
                modifier = Modifier.weight(1f)
            )
            AccessibleSecondaryButton(
                text = "Last 30 days",
                onClick = {
                    fromDate = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
                    toDate = System.currentTimeMillis()
                    announce("Filtering the last 30 days")
                },
                modifier = Modifier.weight(1f)
            )
        }
        if (fromDate != null) {
            Text(
                text = "Date filter from ${Formatters.date(fromDate!!)}",
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(Modifier.height(Dimens.ItemSpacing))
        AccessiblePrimaryButton(text = "Show results", onClick = onDismiss)
        AccessibleSecondaryButton(
            text = "Clear all filters",
            onClick = {
                query = ""
                fromDate = null
                toDate = null
                minSizeMbState = 0f
                onClear()
            }
        )
    }
}

/** Album list including the recently deleted bucket. */
@Composable
fun AlbumSheet(
    state: GalleryUiState,
    onDismiss: () -> Unit,
    onSelectAlbum: (Album?) -> Unit,
    onEmptyTrash: () -> Unit,
    announce: (String) -> Unit
) {
    AccessibleBottomSheet(
        title = "Albums",
        supporting = "Albums come from the folders on this device.",
        onDismissRequest = onDismiss,
        announceOnOpen = announce
    ) {
        AccessibleChoiceRow(
            label = "All media",
            selected = state.filter.albumId == null,
            onSelect = { onSelectAlbum(null) }
        )
        state.albums.forEach { album ->
            Row(modifier = Modifier.fillMaxWidth()) {
                MediaThumbnail(item = null, size = 56.dp)
                Spacer(Modifier.width(Dimens.SmallSpacing))
                AccessibleChoiceRow(
                    label = album.name,
                    supporting = "${album.itemCount} items",
                    selected = state.filter.albumId == album.bucketId,
                    onSelect = { onSelectAlbum(album) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        SectionHeading(text = "Recently deleted")
        Text(
            text = "${state.trashedCount} items in recently deleted.",
            style = MaterialTheme.typography.bodyMedium
        )
        AccessibleSecondaryButton(
            text = "Show recently deleted",
            onClick = {
                onDismiss()
                announce("Opening recently deleted")
            }
        )
        AccessibleSecondaryButton(
            text = "Empty recently deleted permanently",
            onClick = onEmptyTrash,
            supportingText = "Requires confirmation. This cannot be undone."
        )
    }
}

/** Item details with size, resolution, path, album and EXIF summary. */
@Composable
fun MediaDetailsSheet(
    details: MediaDetails,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onDeleteTrashed: () -> Unit
) {
    AccessibleBottomSheet(
        title = "Item details",
        supporting = details.item.displayName,
        onDismissRequest = onDismiss
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.ItemSpacing)
        ) {
            MediaThumbnail(item = details.item, size = 96.dp)
            Column(modifier = Modifier.weight(1f)) {
                DetailRow("Type", details.item.type.label)
                DetailRow("Taken", details.dateTakenLabel)
                DetailRow("Added", details.dateAddedLabel)
            }
        }
        DetailRow("File name", details.item.displayName)
        DetailRow("Size", details.sizeLabel)
        DetailRow("Resolution", details.resolutionLabel)
        details.durationLabel?.let { DetailRow("Duration", it) }
        DetailRow("Album", details.albumLabel)
        DetailRow("Path", details.path)
        DetailRow("Format", details.mimeType)
        DetailRow("Origin", details.origin)
        if (details.item.latitude != null && details.item.longitude != null) {
            DetailRow(
                "Location",
                String.format(java.util.Locale.US, "%.4f, %.4f", details.item.latitude, details.item.longitude)
            )
        }
        if (details.exifSummary.isNotEmpty()) {
            SectionHeading(text = "Camera metadata")
            details.exifSummary.forEach { (key, value) -> DetailRow(key, value) }
        }
        Spacer(Modifier.height(Dimens.ItemSpacing))
        AccessibleSecondaryButton(text = "Close", onClick = onDismiss)
        if (details.item.isTrashed) {
            AccessiblePrimaryButton(
                text = "Restore from recently deleted",
                onClick = onDeleteTrashed
            )
            AccessibleSecondaryButton(
                text = "Delete permanently",
                onClick = onDelete,
                supportingText = "This cannot be undone and needs confirmation."
            )
        } else {
            AccessibleSecondaryButton(
                text = "Move to recently deleted",
                onClick = onDelete,
                supportingText = "You can undo this immediately afterwards."
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = "$label: $value" }
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.weight(0.4f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(0.6f)
        )
    }
}

