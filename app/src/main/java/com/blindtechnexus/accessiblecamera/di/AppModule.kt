/*
 * Accessible Camera and Gallery
 * Copyright (C) 2026 Sujan Rai, Blind Tech Nexus team.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.blindtechnexus.accessiblecamera.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.blindtechnexus.accessiblecamera.data.camera.CameraController
import com.blindtechnexus.accessiblecamera.data.camera.CameraFrameAnalyzer
import com.blindtechnexus.accessiblecamera.data.camera.MlKitDetectorFactory
import com.blindtechnexus.accessiblecamera.data.db.AppDatabase
import com.blindtechnexus.accessiblecamera.data.db.CapabilityDao
import com.blindtechnexus.accessiblecamera.data.db.EditRecipeDao
import com.blindtechnexus.accessiblecamera.data.db.MediaDao
import com.blindtechnexus.accessiblecamera.data.db.TrashDao
import com.blindtechnexus.accessiblecamera.data.db.UndoDao
import com.blindtechnexus.accessiblecamera.data.location.LocationProvider
import com.blindtechnexus.accessiblecamera.data.media.MediaIndexer
import com.blindtechnexus.accessiblecamera.data.media.MediaSaver
import com.blindtechnexus.accessiblecamera.data.media.MediaStoreDataSource
import com.blindtechnexus.accessiblecamera.data.voice.OnDeviceSpeechRecognizer
import com.blindtechnexus.accessiblecamera.data.voice.TextToSpeechEngine
import com.blindtechnexus.accessiblecamera.data.voice.VoiceRepositoryImpl
import com.blindtechnexus.accessiblecamera.data.watermark.WatermarkPainter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

/** Application wide singletons. */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun providesApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun providesDataStore(
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        scope = scope,
        produceFile = { context.preferencesDataStoreFile("accessible_camera_settings") }
    )

    @Provides
    @Singleton
    fun providesDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun providesMediaDao(database: AppDatabase): MediaDao = database.mediaDao()

    @Provides
    fun providesTrashDao(database: AppDatabase): TrashDao = database.trashDao()

    @Provides
    fun providesCapabilityDao(database: AppDatabase): CapabilityDao = database.capabilityDao()

    @Provides
    fun providesUndoDao(database: AppDatabase): UndoDao = database.undoDao()

    @Provides
    fun providesEditRecipeDao(database: AppDatabase): EditRecipeDao = database.editRecipeDao()

    @Provides
    @Singleton
    fun providesMediaStoreDataSource(@ApplicationContext context: Context): MediaStoreDataSource =
        MediaStoreDataSource(context)

    @Provides
    @Singleton
    fun providesMediaIndexer(
        mediaDao: MediaDao,
        mediaStoreDataSource: MediaStoreDataSource
    ): MediaIndexer = MediaIndexer(mediaDao, mediaStoreDataSource)

    @Provides
    @Singleton
    fun providesLocationProvider(@ApplicationContext context: Context): LocationProvider =
        LocationProvider(context)

    @Provides
    @Singleton
    fun providesTextToSpeechEngine(@ApplicationContext context: Context): TextToSpeechEngine =
        TextToSpeechEngine(context)

    @Provides
    @Singleton
    fun providesVoiceRepository(
        @ApplicationContext context: Context,
        engine: TextToSpeechEngine
    ): VoiceRepositoryImpl = VoiceRepositoryImpl(context, engine)

    @Provides
    @Singleton
    fun providesCameraController(@ApplicationContext context: Context): CameraController =
        CameraController(context)

    @Provides
    @Singleton
    fun providesMlKitFactory(): MlKitDetectorFactory = MlKitDetectorFactory()

    @Provides
    @Singleton
    fun providesFrameAnalyzer(factory: MlKitDetectorFactory): CameraFrameAnalyzer =
        CameraFrameAnalyzer(factory)

    @Provides
    @Singleton
    fun providesMediaSaver(
        @ApplicationContext context: Context,
        indexer: MediaIndexer,
        watermarkPainter: WatermarkPainter
    ): MediaSaver = MediaSaver(context, indexer, watermarkPainter)

    @Provides
    @Singleton
    fun providesWatermarkPainter(): WatermarkPainter = WatermarkPainter()

    @Provides
    @Singleton
    fun providesSpeechRecognizer(@ApplicationContext context: Context): OnDeviceSpeechRecognizer =
        OnDeviceSpeechRecognizer(context)
}
