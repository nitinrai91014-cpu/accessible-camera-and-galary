/*
 * Accessible Camera and Gallery
 * Copyright (C) 2026 Sujan Rai, Blind Tech Nexus team.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.blindtechnexus.accessiblecamera.ui.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.blindtechnexus.accessiblecamera.domain.model.GalleryLayout
import com.blindtechnexus.accessiblecamera.domain.model.GallerySection
import com.blindtechnexus.accessiblecamera.domain.model.MediaItem
import com.blindtechnexus.accessiblecamera.ui.components.AccessibleBanner
import com.blindtechnexus.accessiblecamera.ui.components.AccessibleConfirmDialog
import com.blindtechnexus.accessiblecamera.ui.components.AccessibleIconButton
import com.blindtechnexus.accessiblecamera.ui.components.AccessiblePrimaryButton
import com.blindtechnexus.accessiblecamera.ui.components.AccessibleSecondaryButton
import com.blindtechnexus.accessiblecamera.ui.components.MediaThumbnailFill
import com.blindtechnexus.accessiblecamera.ui.components.PartialAccessNotice
import com.blindtechnexus.accessiblecamera.ui.components.PermissionPanel
import com.blindtechnexus.accessiblecamera.ui.theme.Dimens

/**
 * Gallery tab.
 *
 * The grid is a lazy paging grid so thousands of items scroll smoothly. Each cell is a
 * single accessibility node whose description includes type, date, dimensions, duration
 * and selection state, which keeps TalkBack navigation efficient.
 */
@Composable
fun GalleryScreen(
    onOpenItem: (MediaItem, Int) -> Unit,
    onRequestMediaPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    announce: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GalleryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val items = viewModel.items.collectAsLazyPagingItems()
    val context = LocalContext.current
    val gridState = rememberLazyGridState()
    var pendingDeleteItem by remember { mutableStateOf<MediaItem?>(null) }

    LaunchedEffect(state.pendingUndo) {
        state.pendingUndo?.let { undo ->
            announce("${undo.message}. Undo is available.")
        }
    }

    if (state.permissionNeeded) {
        PermissionPanel(
            title = "Gallery access needed",
            message = "The app needs permission to show the photos and videos on this device. It does not upload anything.",
            primaryLabel = "Allow gallery access",
            onPrimary = onRequestMediaPermission,
            secondaryLabel = "Open app settings",
            onSecondary = onOpenSettings,
            announce = announce,
            modifier = modifier.fillMaxSize()
        )
        return
    }

    Column(modifier = modifier.fillMaxSize()) {
        GalleryHeader(
            state = state,
            onCycleSort = viewModel::cycleSort,
            onToggleLayout = viewModel::cycleLayout,
            onOpenFilters = { viewModel.toggleFiltersSheet(true) },
            onOpenAlbums = { viewModel.toggleAlbumsSheet(true) },
            onOpenTrash = viewModel::openTrash,
            onSearch = viewModel::setQuery,
            onSubmitSearch = viewModel::submitQuery,
            onSelectAll = {
                val visible = (0 until items.itemCount).mapNotNull { items[it] }
                viewModel.selectAll(visible)
            }
        )

        if (state.hasPartialAccess) {
            PartialAccessNotice(
                onChooseMore = onRequestMediaPermission,
                modifier = Modifier.padding(horizontal = Dimens.ItemSpacing, vertical = 4.dp)
            )
        }

        if (state.selectionMode) {
            SelectionActionBar(
                state = state,
                onShare = { viewModel.share(context) },
                onDelete = { viewModel.requestDelete(permanent = state.inTrash) },
                onFavorite = { viewModel.toggleFavorite(viewModel.selectedItems()) },
                onClear = viewModel::clearSelection,
                onDetails = {
                    viewModel.selectedItems().firstOrNull()?.let { viewModel.loadDetails(it) }
                }
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            when {
                items.loadState.refresh is androidx.paging.LoadState.Loading && items.itemCount == 0 -> {
                    LoadingState()
                }
                items.itemCount == 0 -> {
                    EmptyState(
                        section = state.filter.section,
                        onOpenFilters = { viewModel.toggleFiltersSheet(true) },
                        onClearFilters = viewModel::clearFilters,
                        onOpenTrash = viewModel::openTrash
                    )
                }
                else -> {
                    val columns = when (state.layout) {
                        GalleryLayout.LIST -> 1
                        else -> state.layout.columns
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        state = gridState,
                        contentPadding = PaddingValues(Dimens.ItemSpacing),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.SmallSpacing),
                        verticalArrangement = Arrangement.spacedBy(Dimens.SmallSpacing),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(
                            count = items.itemCount,
                            key = { index -> items.peek(index)?.id ?: index },
                            span = { index ->
                                if (state.layout == GalleryLayout.LIST) GridItemSpan(maxLineSpan) else GridItemSpan(1)
                            }
                        ) { index ->
                            val item = items[index]
                            if (item == null) {
                                ShimmerCell()
                            } else if (state.layout == GalleryLayout.LIST) {
                                GalleryListRow(
                                    item = item,
                                    index = index,
                                    total = items.itemCount,
                                    selected = state.selection.contains(item.id),
                                    selectionMode = state.selectionMode,
                                    onOpen = {
                                        if (state.selectionMode) {
                                            viewModel.toggleSelection(item)
                                        } else {
                                            onOpenItem(item, index)
                                        }
                                    },
                                    onToggleSelection = { viewModel.toggleSelection(item) },
                                    onLongPress = { viewModel.toggleSelection(item) },
                                    onDetails = { viewModel.loadDetails(item) }
                                )
                            } else {
                                GalleryGridCell(
                                    item = item,
                                    index = index,
                                    total = items.itemCount,
                                    selected = state.selection.contains(item.id),
                                    onOpen = {
                                        if (state.selectionMode) {
                                            viewModel.toggleSelection(item)
                                        } else {
                                            onOpenItem(item, index)
                                        }
                                    },
                                    onToggleSelection = { viewModel.toggleSelection(item) },
                                    onLongPress = { viewModel.toggleSelection(item) }
                                )
                            }
                        }
                    }
                }
            }
        }

        state.pendingUndo?.let { undo ->
            UndoBar(
                message = undo.message,
                onUndo = viewModel::undoDelete,
                onDismiss = viewModel::dismissUndo
            )
        }
    }

    if (state.showFilters) {
        FilterSheet(
            state = state,
            onDismiss = { viewModel.toggleFiltersSheet(false) },
            onSection = viewModel::setSection,
            onSort = viewModel::setSort,
            onType = viewModel::setMediaTypeFilter,
            onFavoritesOnly = viewModel::setFavoritesOnly,
            onClear = viewModel::clearFilters,
            announce = announce
        )
    }

    if (state.showAlbums) {
        AlbumSheet(
            state = state,
            onDismiss = { viewModel.toggleAlbumsSheet(false) },
            onSelectAlbum = {
                viewModel.setAlbum(it)
                viewModel.toggleAlbumsSheet(false)
            },
            onShowTrash = {
                viewModel.toggleAlbumsSheet(false)
                viewModel.openTrash()
            },
            onEmptyTrash = {
                viewModel.toggleAlbumsSheet(false)
                viewModel.emptyTrash()
            },
            announce = announce
        )
    }

    state.details?.let { details ->
        MediaDetailsSheet(
            details = details,
            onDismiss = viewModel::closeDetails,
            onDeleteTrashed = {
                viewModel.restoreFromTrash(listOf(details.item))
                viewModel.closeDetails()
            },
            onDelete = {
                pendingDeleteItem = details.item
                viewModel.closeDetails()
            }
        )
    }

    if (state.showDeleteConfirmation) {
        val count = state.pendingDeleteCount.coerceAtLeast(1)
        val noun = if (count == 1) "item" else "items"
        AccessibleConfirmDialog(
            title = if (state.permanentDelete) {
                "Delete Selected Items Permanently"
            } else {
                "Move to recently deleted?"
            },
            message = if (state.permanentDelete) {
                "Delete $count $noun permanently? This cannot be undone."
            } else {
                "This moves $count $noun to recently deleted. You can restore them later."
            },
            confirmLabel = if (state.permanentDelete) "Delete" else "Move to trash",
            dismissLabel = "Cancel",
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::cancelDelete,
            destructive = true,
            supporting = if (state.permanentDelete) {
                "Cannot be undone. Confirm only if you want these items gone for good."
            } else {
                "You can undo a move to trash immediately after it happens."
            }
        )
    }

    pendingDeleteItem?.let { item ->
        val permanent = item.inTrash
        AccessibleConfirmDialog(
            title = if (permanent) "Delete Selected Items Permanently" else "Delete this item?",
            message = if (permanent) {
                "Delete ${item.displayName} permanently? This cannot be undone."
            } else {
                "Move ${item.displayName} to recently deleted?"
            },
            confirmLabel = if (permanent) "Delete" else "Move to trash",
            supporting = if (permanent) "Cannot be undone." else "You can restore it from the trash.",
            onConfirm = {
                if (permanent) {
                    viewModel.deletePermanently(listOf(item))
                } else {
                    viewModel.toggleSelection(item)
                    viewModel.requestDelete(permanent = false)
                }
                pendingDeleteItem = null
            },
            onDismiss = { pendingDeleteItem = null }
        )
    }
}

@Composable
private fun GalleryHeader(
    state: GalleryUiState,
    onCycleSort: () -> Unit,
    onToggleLayout: () -> Unit,
    onOpenFilters: () -> Unit,
    onOpenAlbums: () -> Unit,
    onOpenTrash: () -> Unit,
    onSearch: (String) -> Unit,
    onSubmitSearch: () -> Unit,
    onSelectAll: () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = Dimens.ItemSpacing, vertical = Dimens.SmallSpacing)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = when (state.filter.section) {
                    GallerySection.TRASH -> "Recently deleted"
                    GallerySection.FAVORITES -> "Favorites"
                    GallerySection.SCREENSHOTS -> "Screenshots"
                    GallerySection.VIDEOS -> "Videos"
                    GallerySection.PHOTOS -> "Photos"
                    GallerySection.ALL -> if (state.filter.albumId != null) "Album" else "All media"
                },
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() }
            )
            AccessibleIconButton(
                icon = Icons.Filled.Search,
                contentDescription = "Search",
                onClick = onOpenFilters
            )
            AccessibleIconButton(
                icon = Icons.Filled.Sort,
                contentDescription = "Sort, currently ${state.filter.sortOrder.label}",
                onClick = onCycleSort
            )
            AccessibleIconButton(
                icon = Icons.Filled.GridView,
                contentDescription = "Change layout, currently ${state.layout.label}",
                onClick = onToggleLayout
            )
            AccessibleIconButton(
                icon = Icons.Filled.FilterList,
                contentDescription = "Filters and albums",
                onClick = onOpenFilters
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SmallSpacing)
        ) {
            AccessibleSecondaryButton(
                text = "Albums",
                onClick = onOpenAlbums,
                modifier = Modifier.weight(1f)
            )
            AccessibleSecondaryButton(
                text = "Filter",
                onClick = onOpenFilters,
                modifier = Modifier.weight(1f)
            )
            // The trash is one tap away instead of hiding behind the filter sheet.
            AccessibleSecondaryButton(
                text = if (state.trashedCount > 0) {
                    "Trash, ${state.trashedCount}"
                } else {
                    "Trash"
                },
                onClick = onOpenTrash,
                supportingText = if (state.trashedCount > 0) {
                    "${state.trashedCount} items in recently deleted"
                } else {
                    "Recently deleted is empty"
                },
                modifier = Modifier.weight(1f)
            )
            if (!state.selectionMode) {
                AccessibleSecondaryButton(
                    text = "Select all",
                    onClick = onSelectAll,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "${state.filter.describe()}. ${state.selectedCount} selected. ${state.trashedCount} in recently deleted.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )
    }
}

@Composable
private fun SelectionActionBar(
    state: GalleryUiState,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onFavorite: () -> Unit,
    onClear: () -> Unit,
    onDetails: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = Dimens.ItemSpacing, vertical = Dimens.SmallSpacing)
    ) {
        Text(
            text = "${state.selectedCount} items selected",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }
        )
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.SmallSpacing)
        ) {
            AccessibleIconButton(
                icon = Icons.Filled.Share,
                contentDescription = "Share selected items",
                onClick = onShare
            )
            AccessibleIconButton(
                icon = Icons.Filled.Star,
                contentDescription = "Toggle favorite",
                onClick = onFavorite
            )
            AccessibleIconButton(
                icon = Icons.Filled.FilterList,
                contentDescription = "Details",
                onClick = onDetails
            )
            AccessibleIconButton(
                icon = Icons.Filled.Delete,
                contentDescription = if (state.filter.section == GallerySection.TRASH) {
                    "Delete permanently"
                } else {
                    "Move to recently deleted"
                },
                onClick = onDelete
            )
            AccessibleIconButton(
                icon = Icons.Filled.Restore,
                contentDescription = "Clear selection",
                onClick = onClear
            )
        }
    }
}

@Composable
private fun UndoBar(
    message: String,
    onUndo: () -> Unit,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(Dimens.ItemSpacing)
            .semantics(mergeDescendants = true) {
                liveRegion = LiveRegionMode.Assertive
                contentDescription = "$message. Undo available"
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SmallSpacing)
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.weight(1f)
        )
        AccessibleSecondaryButton(text = "Undo", onClick = onUndo)
        AccessibleSecondaryButton(text = "Dismiss", onClick = onDismiss)
    }
}

@Composable
private fun LoadingState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimens.ScreenPadding),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Loading your media",
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun EmptyState(
    section: GallerySection,
    onOpenFilters: () -> Unit,
    onClearFilters: () -> Unit,
    onOpenTrash: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimens.ScreenPadding)
            .semantics(mergeDescendants = true) {
                liveRegion = LiveRegionMode.Polite
            },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (section == GallerySection.TRASH) {
            // A plain, always-present text view so the empty trash is announced and readable.
            Text(
                text = "Recently deleted is empty",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(Modifier.height(Dimens.SmallSpacing))
            Text(
                text = "Items you delete appear here first. Nothing will be removed for good until you choose Delete permanently.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(Dimens.SectionSpacing))
            AccessibleSecondaryButton(text = "Back to all media", onClick = onClearFilters)
            return
        }
        Text(
            text = when (section) {
                GallerySection.FAVORITES -> "No favorites yet"
                GallerySection.PHOTOS -> "No photos to show"
                GallerySection.VIDEOS -> "No videos to show"
                GallerySection.SCREENSHOTS -> "No screenshots to show"
                else -> "No items to show"
            },
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(Modifier.height(Dimens.SmallSpacing))
        Text(
            text = "Adjust filters or take a new photo from the Photo tab.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(Dimens.SectionSpacing))
        AccessibleSecondaryButton(text = "Open filters", onClick = onOpenFilters)
        Spacer(Modifier.height(Dimens.SmallSpacing))
        AccessiblePrimaryButton(text = "Clear all filters", onClick = onClearFilters)
        if (section != GallerySection.TRASH) {
            Spacer(Modifier.height(Dimens.SmallSpacing))
            AccessibleSecondaryButton(text = "Open trash", onClick = onOpenTrash)
        }
    }
}

@Composable
private fun ShimmerCell() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    )
}
