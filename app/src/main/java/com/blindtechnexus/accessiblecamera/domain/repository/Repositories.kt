/*
 * Accessible Camera and Gallery
 * Copyright (C) 2026 Sujan Rai, Blind Tech Nexus team.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.blindtechnexus.accessiblecamera.domain.repository

import android.net.Uri
import androidx.paging.PagingData
import com.blindtechnexus.accessiblecamera.domain.model.Album
import com.blindtechnexus.accessiblecamera.domain.model.AppSettings
import com.blindtechnexus.accessiblecamera.domain.model.CameraCapabilities
import com.blindtechnexus.accessiblecamera.domain.model.GalleryFilter
import com.blindtechnexus.accessiblecamera.domain.model.MediaDetails
import com.blindtechnexus.accessiblecamera.domain.model.MediaItem
import kotlinx.coroutines.flow.Flow

/** Reads and writes user preferences. */
interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun current(): AppSettings
    suspend fun update(transform: (AppSettings) -> AppSettings)
    suspend fun resetAll()
    suspend fun setOnboardingComplete(complete: Boolean)
}

/** Provides the detected device capabilities. */
interface CapabilityRepository {
    val capabilities: Flow<CameraCapabilities>
    suspend fun refresh(): CameraCapabilities
}

/** Media access backed by MediaStore plus the Room index. */
interface MediaRepository {
    fun pagedMedia(filter: Flow<GalleryFilter>): Flow<PagingData<MediaItem>>
    fun observeAlbums(): Flow<List<Album>>
    fun observeTrashed(): Flow<List<MediaItem>>
    fun observeFavorites(): Flow<List<MediaItem>>
    suspend fun recentItems(limit: Int): List<MediaItem>
    suspend fun getItem(id: Long): MediaItem?
    suspend fun details(item: MediaItem): MediaDetails
    suspend fun latestThumbnail(): MediaItem?
    suspend fun deleteToTrash(items: List<MediaItem>)
    suspend fun restore(items: List<MediaItem>)
    suspend fun deletePermanently(items: List<MediaItem>)
    suspend fun setFavorite(items: List<MediaItem>, favorite: Boolean)
    suspend fun moveToAlbum(items: List<MediaItem>, albumName: String): Int
    suspend fun rescan()
    suspend fun persistableUri(uri: Uri): Uri?
    suspend fun importCapture(uri: Uri, type: String): MediaItem?
    fun observeIndexCount(): Flow<Int>
}

/** Renders text and haptic feedback. */
interface VoiceRepository {
    val isSpeaking: Flow<Boolean>
    fun announce(text: String, priority: com.blindtechnexus.accessiblecamera.domain.model.AnnouncementPriority)
    fun stop()
    fun setRate(rate: Float)
    fun setPitch(pitch: Float)
    fun speakCue(cue: com.blindtechnexus.accessiblecamera.domain.model.AudioCue)
    fun playCue(cue: com.blindtechnexus.accessiblecamera.domain.model.AudioCue)
    fun vibrate(cue: com.blindtechnexus.accessiblecamera.domain.model.HapticCue)
}
