/*
 * Accessible Camera and Gallery
 * Copyright (C) 2026 Sujan Rai, Blind Tech Nexus team.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.blindtechnexus.accessiblecamera.di

import com.blindtechnexus.accessiblecamera.data.camera.CameraCapabilityDetector
import com.blindtechnexus.accessiblecamera.data.media.MediaRepositoryImpl
import com.blindtechnexus.accessiblecamera.data.settings.SettingsRepositoryImpl
import com.blindtechnexus.accessiblecamera.data.voice.VoiceRepositoryImpl
import com.blindtechnexus.accessiblecamera.domain.repository.CapabilityRepository
import com.blindtechnexus.accessiblecamera.domain.repository.MediaRepository
import com.blindtechnexus.accessiblecamera.domain.repository.SettingsRepository
import com.blindtechnexus.accessiblecamera.domain.repository.VoiceRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds repository interfaces to their implementations. */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindCapabilityRepository(impl: CameraCapabilityDetector): CapabilityRepository

    @Binds
    @Singleton
    abstract fun bindMediaRepository(impl: MediaRepositoryImpl): MediaRepository

    @Binds
    @Singleton
    abstract fun bindVoiceRepository(impl: VoiceRepositoryImpl): VoiceRepository
}
