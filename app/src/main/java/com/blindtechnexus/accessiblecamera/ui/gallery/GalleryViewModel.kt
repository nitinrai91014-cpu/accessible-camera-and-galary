/*
 * Accessible Camera and Gallery
 * Copyright (C) 2026 Sujan Rai, Blind Tech Nexus team.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.blindtechnexus.accessiblecamera.ui.gallery

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.blindtechnexus.accessiblecamera.core.util.Formatters
import com.blindtechnexus.accessiblecamera.data.media.MediaPermissionEvaluator
import com.blindtechnexus.accessiblecamera.data.voice.VoiceRepositoryImpl
import com.blindtechnexus.accessiblecamera.domain.model.Album
import com.blindtechnexus.accessiblecamera.domain.model.AnnouncementPriority
import com.blindtechnexus.accessiblecamera.domain.model.AudioCue
import com.blindtechnexus.accessiblecamera.domain.model.GalleryFilter
import com.blindtechnexus.accessiblecamera.domain.model.GalleryLayout
import com.blindtechnexus.accessiblecamera.domain.model.GallerySection
import com.blindtechnexus.accessiblecamera.domain.model.HapticCue
import com.blindtechnexus.accessiblecamera.domain.model.MediaDetails
import com.blindtechnexus.accessiblecamera.domain.model.MediaItem
import com.blindtechnexus.accessiblecamera.domain.model.MediaType
import com.blindtechnexus.accessiblecamera.domain.model.PendingUndo
import com.blindtechnexus.accessiblecamera.domain.model.SortOrder
import com.blindtechnexus.accessiblecamera.domain.repository.MediaRepository
import com.blindtechnexus.accessiblecamera.domain.repository.SettingsRepository
import com.blindtechnexus.accessiblecamera.domain.usecase.TrashUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** UI state for the gallery tab. */
data class GalleryUiState(
    val filter: GalleryFilter = GalleryFilter(),
    val layout: GalleryLayout = GalleryLayout.GRID_MEDIUM,
    val albums: List<Album> = emptyList(),
    val selection: Set<Long> = emptySet(),
    val selectionMode: Boolean = false,
    val trashedCount: Int = 0,
    val isLoading: Boolean = false,
    val message: String? = null,
    val pendingUndo: PendingUndo? = null,
    val details: MediaDetails? = null,
    val hasFullAccess: Boolean = true,
    val hasPartialAccess: Boolean = false,
    val permissionNeeded: Boolean = false,
    val showFilters: Boolean = false,
    val showAlbums: Boolean = false,
    val showDeleteConfirmation: Boolean = false,
    val permanentDelete: Boolean = false,
    /** Exact items the open confirmation dialog will act on. */
    val pendingDeleteItems: List<MediaItem> = emptyList(),
    val slideshowActive: Boolean = false
) {
    val selectedCount: Int get() = selection.size

    /** Number of items the confirmation dialog will delete. */
    val pendingDeleteCount: Int
        get() = pendingDeleteItems.size.takeIf { it > 0 } ?: selectedCount

    val inTrash: Boolean get() = filter.section == GallerySection.TRASH
}

/**
 * Gallery logic: paging, search, filters, sorting, selection, trash and undo.
 * All destructive operations go through [MediaRepository] which keeps MediaStore and the
 * Room index consistent.
 */
@HiltViewModel
class GalleryViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaRepository: MediaRepository,
    private val settingsRepository: SettingsRepository,
    private val voice: VoiceRepositoryImpl,
    private val trashUseCase: TrashUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(GalleryUiState())
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    private val filterFlow = MutableStateFlow(GalleryFilter())

    val items = filterFlow
        .let { flow ->
            mediaRepository.pagedMedia(flow)
        }
        .cachedIn(viewModelScope)

    private val selectionItems = mutableMapOf<Long, MediaItem>()

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _uiState.update { it.copy(layout = settings.galleryLayout) }
            }
        }
        viewModelScope.launch {
            mediaRepository.observeAlbums().collect { albums ->
                _uiState.update { it.copy(albums = albums) }
            }
        }
        viewModelScope.launch {
            mediaRepository.observeTrashed().collect { trashed ->
                _uiState.update { it.copy(trashedCount = trashed.size) }
            }
        }
        refreshPermissions()
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            mediaRepository.rescan()
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun refreshPermissions() {
        val full = MediaPermissionEvaluator.hasFullImageAccess(context)
        val partial = MediaPermissionEvaluator.hasPartialImageAccess(context)
        _uiState.update {
            it.copy(
                hasFullAccess = full,
                hasPartialAccess = partial,
                permissionNeeded = !full && !partial
            )
        }
    }

    // region Filtering

    fun setSection(section: GallerySection) {
        updateFilter { it.copy(section = section, albumId = null) }
        voice.announce("Showing ${section.spoken}", AnnouncementPriority.NORMAL)
    }

    fun setAlbum(album: Album?) {
        updateFilter { it.copy(albumId = album?.bucketId) }
        voice.announce(
            if (album == null) "Showing all albums" else "Showing album ${album.name}",
            AnnouncementPriority.NORMAL
        )
    }

    fun setQuery(query: String) {
        updateFilter { it.copy(query = query) }
    }

    fun submitQuery() {
        val filter = _uiState.value.filter
        voice.announce(
            if (filter.query.isBlank()) {
                "Search cleared"
            } else {
                "Searching for ${filter.query}"
            },
            AnnouncementPriority.NORMAL
        )
    }

    fun cycleSort() {
        val next = _uiState.value.filter.sortOrder.next
        updateFilter { it.copy(sortOrder = next) }
        voice.announce("Sorted ${next.spoken}", AnnouncementPriority.NORMAL)
    }

    fun setSort(order: SortOrder) {
        updateFilter { it.copy(sortOrder = order) }
        voice.announce("Sorted ${order.spoken}", AnnouncementPriority.NORMAL)
    }

    fun setMediaTypeFilter(type: MediaType?) {
        updateFilter { it.copy(mediaType = type) }
        voice.announce(
            when (type) {
                MediaType.IMAGE -> "Showing photos only"
                MediaType.VIDEO -> "Showing videos only"
                else -> "Showing photos and videos"
            },
            AnnouncementPriority.NORMAL
        )
    }

    fun setFavoritesOnly(enabled: Boolean) {
        updateFilter { it.copy(favoritesOnly = enabled) }
        voice.announce(if (enabled) "Favorites only" else "All items", AnnouncementPriority.NORMAL)
    }

    fun setDateRange(fromMillis: Long?, toMillis: Long?) {
        updateFilter { it.copy(fromMillis = fromMillis, toMillis = toMillis) }
        voice.announce("Date filter applied", AnnouncementPriority.NORMAL)
    }

    fun setSizeRange(minBytes: Long?, maxBytes: Long?) {
        updateFilter { it.copy(minSizeBytes = minBytes, maxSizeBytes = maxBytes) }
        voice.announce("Size filter applied", AnnouncementPriority.NORMAL)
    }

    fun clearFilters() {
        updateFilter { GalleryFilter(sortOrder = it.sortOrder) }
        voice.announce("Filters cleared", AnnouncementPriority.NORMAL)
    }

    private fun updateFilter(transform: (GalleryFilter) -> GalleryFilter) {
        _uiState.update { state ->
            val updated = transform(state.filter)
            filterFlow.value = updated
            state.copy(filter = updated)
        }
    }

    fun setLayout(layout: GalleryLayout) {
        _uiState.update { it.copy(layout = layout) }
        viewModelScope.launch { settingsRepository.update { it.copy(galleryLayout = layout) } }
        voice.announce("${layout.label} view", AnnouncementPriority.NORMAL)
    }

    fun cycleLayout() {
        setLayout(_uiState.value.layout.next)
    }

    fun toggleFiltersSheet(show: Boolean) {
        _uiState.update { it.copy(showFilters = show) }
    }

    fun toggleAlbumsSheet(show: Boolean) {
        _uiState.update { it.copy(showAlbums = show) }
    }

    fun toggleSlideshow(active: Boolean) {
        _uiState.update { it.copy(slideshowActive = active) }
        voice.announce(if (active) "Slideshow started" else "Slideshow stopped", AnnouncementPriority.NORMAL)
    }

    // endregion

    // region Selection

    fun toggleSelection(item: MediaItem) {
        _uiState.update { state ->
            val selected = state.selection.contains(item.id)
            val newSelection = if (selected) {
                state.selection - item.id
            } else {
                state.selection + item.id
            }
            if (selected) selectionItems.remove(item.id) else selectionItems[item.id] = item
            state.copy(
                selection = newSelection,
                selectionMode = newSelection.isNotEmpty()
            )
        }
        voice.vibrate(HapticCue.SELECTION)
        voice.playCue(AudioCue.ITEM_SELECTED)
        val selectedNow = _uiState.value.selection.contains(item.id)
        voice.announce(
            "${if (selectedNow) "Selected" else "Deselected"}. ${_uiState.value.selectedCount} items selected",
            AnnouncementPriority.NORMAL
        )
    }

    fun selectAll(visible: List<MediaItem>) {
        val ids = visible.map { it.id }.toSet()
        visible.forEach { selectionItems[it.id] = it }
        _uiState.update { it.copy(selection = ids, selectionMode = ids.isNotEmpty()) }
        voice.announce("Selected ${ids.size} items", AnnouncementPriority.NORMAL)
    }

    fun clearSelection() {
        selectionItems.clear()
        _uiState.update { it.copy(selection = emptySet(), selectionMode = false) }
        voice.announce("Selection cleared", AnnouncementPriority.NORMAL)
    }

    fun selectedItems(): List<MediaItem> =
        _uiState.value.selection.mapNotNull { selectionItems[it] }

    // endregion

    // region Actions

    /**
     * Opens the delete confirmation.
     *
     * Inside Recently deleted a delete is always permanent, even when the caller asked for a
     * move to trash. This is what previously produced a "Move to recently deleted" dialog
     * from the Delete Permanently button. With nothing selected in trash the whole trash is
     * targeted, which makes Empty trash work from the same dialog.
     */
    fun requestDelete(permanent: Boolean = false) {
        val state = _uiState.value
        val effectivePermanent = permanent || state.inTrash
        viewModelScope.launch {
            val items = when {
                selectedItems().isNotEmpty() -> selectedItems()
                state.details?.item != null -> listOf(state.details.item)
                effectivePermanent -> runCatching { mediaRepository.observeTrashed().first() }
                    .getOrDefault(emptyList())
                else -> emptyList()
            }
            if (items.isEmpty()) {
                voice.announce(
                    if (effectivePermanent) {
                        "Recently deleted is empty. There is nothing to delete permanently."
                    } else {
                        "Select at least one item first."
                    },
                    AnnouncementPriority.HIGH
                )
                return@launch
            }
            _uiState.update {
                it.copy(
                    showDeleteConfirmation = true,
                    permanentDelete = effectivePermanent,
                    pendingDeleteItems = items
                )
            }
            val spoken = if (effectivePermanent) {
                trashUseCase.confirmationFor(items).message
            } else {
                trashUseCase.trashSummary(items).removePrefix("Moved")
                    .replaceFirstChar { it.uppercase() }
            }
            voice.announce(spoken, AnnouncementPriority.HIGH)
        }
    }

    fun cancelDelete() {
        _uiState.update {
            it.copy(showDeleteConfirmation = false, pendingDeleteItems = emptyList())
        }
        voice.announce("Deletion cancelled", AnnouncementPriority.NORMAL)
    }

    fun confirmDelete() {
        val state = _uiState.value
        val items = state.pendingDeleteItems.ifEmpty {
            selectedItems().ifEmpty { state.details?.item?.let { listOf(it) } ?: emptyList() }
        }
        val wasInTrash = state.inTrash
        _uiState.update {
            it.copy(showDeleteConfirmation = false, pendingDeleteItems = emptyList())
        }
        if (items.isEmpty()) return
        viewModelScope.launch {
            if (state.permanentDelete) {
                val message = trashUseCase.deletePermanently(items)
                voice.playCue(AudioCue.TRASH)
                voice.vibrate(HapticCue.SUCCESS)
                voice.announce(message, AnnouncementPriority.CRITICAL)
                _uiState.update { current ->
                    current.copy(
                        details = null,
                        pendingDeleteItems = emptyList(),
                        message = message
                    )
                }
            } else {
                val message = trashUseCase.moveToTrash(items)
                voice.playCue(AudioCue.TRASH)
                voice.vibrate(HapticCue.SUCCESS)
                voice.announce(message, AnnouncementPriority.HIGH)
                _uiState.update {
                    it.copy(
                        pendingUndo = PendingUndo(items = items, message = message),
                        details = null,
                        pendingDeleteItems = emptyList()
                    )
                }
            }
            clearSelection()
            if (wasInTrash) {
                // Stay in Recently deleted so the empty state is visible immediately.
                setSection(GallerySection.TRASH)
            }
        }
    }

    fun undoDelete() {
        val pending = _uiState.value.pendingUndo ?: return
        viewModelScope.launch {
            mediaRepository.restore(pending.items)
            _uiState.update { it.copy(pendingUndo = null) }
            voice.announce("Restored ${pending.items.size} items", AnnouncementPriority.HIGH)
        }
    }

    fun dismissUndo() {
        _uiState.update { it.copy(pendingUndo = null) }
    }

    fun restoreFromTrash(items: List<MediaItem>) {
        viewModelScope.launch {
            mediaRepository.restore(items)
            voice.announce("Restored ${items.size} items", AnnouncementPriority.HIGH)
        }
    }

    /** Opens Recently deleted directly, without going through the filter sheet. */
    fun openTrash() {
        setSection(GallerySection.TRASH)
        val count = _uiState.value.trashedCount
        voice.announce(
            if (count == 0) {
                "Recently deleted is empty."
            } else {
                "Recently deleted, $count ${if (count == 1) "item" else "items"}. " +
                    "Activate Delete permanently to remove them, or Restore to keep them."
            },
            AnnouncementPriority.HIGH
        )
    }

    /** Empty trash always goes through the permanent delete confirmation. */
    fun emptyTrash() {
        if (_uiState.value.trashedCount == 0) {
            voice.announce("Recently deleted is already empty", AnnouncementPriority.NORMAL)
            return
        }
        requestDelete(permanent = true)
    }

    fun deletePermanently(items: List<MediaItem>) {
        viewModelScope.launch {
            mediaRepository.deletePermanently(items)
            voice.announce("${items.size} items permanently deleted", AnnouncementPriority.CRITICAL)
        }
    }

    fun moveToTrash(items: List<MediaItem>) {
        viewModelScope.launch {
            mediaRepository.deleteToTrash(items)
            voice.announce("${items.size} items moved to recently deleted", AnnouncementPriority.HIGH)
            _uiState.update {
                it.copy(pendingUndo = PendingUndo(items = items, message = "${items.size} items moved to recently deleted"))
            }
        }
    }

    fun toggleFavorite(items: List<MediaItem>) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            val makeFavorite = items.any { !it.isFavorite }
            mediaRepository.setFavorite(items, makeFavorite)
            voice.announce(
                if (makeFavorite) "Added to favorites" else "Removed from favorites",
                AnnouncementPriority.NORMAL
            )
        }
    }

    fun moveToAlbum(albumName: String) {
        val items = selectedItems()
        if (items.isEmpty()) {
            voice.announce("Select items first", AnnouncementPriority.NORMAL)
            return
        }
        viewModelScope.launch {
            val moved = mediaRepository.moveToAlbum(items, albumName)
            voice.announce("Moved $moved items to $albumName", AnnouncementPriority.HIGH)
        }
    }

    fun share(context: Context) {
        val items = selectedItems()
        if (items.isEmpty()) return
        if (items.size == 1) {
            com.blindtechnexus.accessiblecamera.core.util.IntentLauncher.shareUri(
                context,
                items.first().uri,
                items.first().mimeType
            )
        } else {
            com.blindtechnexus.accessiblecamera.core.util.IntentLauncher.shareUris(
                context,
                items.map { it.uri },
                com.blindtechnexus.accessiblecamera.ui.components.MediaDescriptions.shareMimeType(items)
            )
        }
        voice.announce("Opening share options", AnnouncementPriority.NORMAL)
    }

    fun loadDetails(item: MediaItem) {
        viewModelScope.launch {
            val details = mediaRepository.details(item)
            _uiState.update { it.copy(details = details) }
            voice.announce("Details for ${item.displayName}", AnnouncementPriority.NORMAL)
        }
    }

    fun closeDetails() {
        _uiState.update { it.copy(details = null) }
    }

    fun shareUri(context: Context, uri: Uri, mime: String) {
        com.blindtechnexus.accessiblecamera.core.util.IntentLauncher.shareUri(context, uri, mime)
    }

    fun describeFilter() {
        voice.announce(_uiState.value.filter.describe(), AnnouncementPriority.NORMAL)
    }

    suspend fun recentForViewer(limit: Int = 200): List<MediaItem> =
        mediaRepository.recentItems(limit)

    fun describeItem(item: MediaItem, index: Int, total: Int) {
        voice.announce(
            com.blindtechnexus.accessiblecamera.ui.components.MediaDescriptions.full(item, index, total),
            AnnouncementPriority.NORMAL
        )
    }

    fun itemCountLabel(): String {
        val layout = _uiState.value.layout
        return "${layout.label}. ${_uiState.value.selectedCount} selected"
    }

    fun sizeSummary(items: List<MediaItem>): String =
        Formatters.size(items.sumOf { it.sizeBytes })

    // endregion
}
