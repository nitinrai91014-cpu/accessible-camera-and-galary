/*
 * Accessible Camera and Gallery
 * Copyright (C) 2026 Sujan Rai, Blind Tech Nexus team.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.blindtechnexus.accessiblecamera

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blindtechnexus.accessiblecamera.data.voice.OnDeviceSpeechRecognizer
import com.blindtechnexus.accessiblecamera.ui.AccessibleCameraNavHost
import com.blindtechnexus.accessiblecamera.ui.AppStateViewModel
import com.blindtechnexus.accessiblecamera.ui.theme.AccessibleCameraTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Single activity host.
 *
 * Configuration changes are declared in the manifest so the camera session survives
 * rotation without a full recreate. The theme reads preferences from the app view model so
 * theme and font scale changes apply immediately.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var speechRecognizer: OnDeviceSpeechRecognizer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val appViewModel: AppStateViewModel = hiltViewModel()
            val settings by appViewModel.settings.collectAsStateWithLifecycle()

            LaunchedEffect(settings.keepScreenOn) {
                if (settings.keepScreenOn) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            AccessibleCameraTheme(
                themeMode = settings.themeMode,
                fontScaleMultiplier = settings.fontScale.scale
            ) {
                AppContent(appViewModel = appViewModel)
            }
        }
        handleIntent(intent)
    }

    @Composable
    private fun AppContent(appViewModel: AppStateViewModel) {
        val settings by appViewModel.settings.collectAsStateWithLifecycle()
        val announcements = remember { appViewModel }
        AccessibleCameraNavHost(
            appViewModel = announcements
        )
        // Settings are observed above so the theme reacts to changes; referencing the value
        // keeps the composition tied to the flow.
        @Suppress("UNUSED_EXPRESSION")
        settings
    }

    /**
     * Handles media capture intents so other apps can use this camera, and the launch
     * shortcut extra that opens straight to a tab.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val action = intent?.action ?: return
        val message = when (action) {
            "android.media.action.IMAGE_CAPTURE" -> "Photo capture requested"
            "android.media.action.VIDEO_CAPTURE" -> "Video capture requested"
            "android.intent.action.VIEW" -> "Media open requested"
            else -> null
        }
        message?.let { android.util.Log.i("MainActivity", it) }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            speechRecognizer.destroy()
        }
    }
}
