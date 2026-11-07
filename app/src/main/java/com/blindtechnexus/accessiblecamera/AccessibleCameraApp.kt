/*
 * Accessible Camera and Gallery
 * Copyright (C) 2026 Sujan Rai, Blind Tech Nexus team.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.blindtechnexus.accessiblecamera

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.blindtechnexus.accessiblecamera.data.camera.CameraCapabilityDetector
import com.blindtechnexus.accessiblecamera.data.voice.OnDeviceSpeechRecognizer
import com.blindtechnexus.accessiblecamera.data.voice.VoiceRepositoryImpl
import com.blindtechnexus.accessiblecamera.domain.repository.SettingsRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Application entry point.
 *
 * Startup work is deliberately small: initialise speech, load settings, and start
 * capability detection in the background so the first frame of the welcome screen appears
 * immediately.
 */
@HiltAndroidApp
class AccessibleCameraApp : Application(), Configuration.Provider {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var voiceRepository: VoiceRepositoryImpl

    @Inject
    lateinit var capabilityDetector: CameraCapabilityDetector

    @Inject
    lateinit var speechRecognizer: OnDeviceSpeechRecognizer

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        voiceRepository.initialize()
        appScope.launch {
            runCatching {
                val settings = settingsRepository.settings.first()
                voiceRepository.configure(settings.speechRate, settings.speechPitch)
                voiceRepository.setFeatureToggles(
                    haptics = settings.hapticsEnabled,
                    audioCues = settings.audioCuesEnabled,
                    voiceGuidance = settings.voiceGuidanceEnabled
                )
            }.onFailure { Log.w("AccessibleCameraApp", "Initial settings load failed", it) }
            runCatching { capabilityDetector.refresh() }
                .onFailure { Log.w("AccessibleCameraApp", "Capability detection failed", it) }
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        speechRecognizer.destroy()
        voiceRepository.shutdown()
    }
}
