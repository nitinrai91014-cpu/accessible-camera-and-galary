/*
 * Accessible Camera and Gallery
 * Copyright (C) 2026 Sujan Rai, Blind Tech Nexus team.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.blindtechnexus.accessiblecamera.ui

import android.Manifest
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.blindtechnexus.accessiblecamera.core.util.IntentLauncher
import com.blindtechnexus.accessiblecamera.data.media.MediaPermissionEvaluator
import com.blindtechnexus.accessiblecamera.data.mlkit.MlKitModelState
import com.blindtechnexus.accessiblecamera.domain.model.AnnouncementPriority
import com.blindtechnexus.accessiblecamera.domain.model.AudioCue
import com.blindtechnexus.accessiblecamera.domain.model.HapticCue
import com.blindtechnexus.accessiblecamera.domain.model.MediaItem
import com.blindtechnexus.accessiblecamera.ui.about.AboutScreen
import com.blindtechnexus.accessiblecamera.ui.about.CapabilitiesScreen
import com.blindtechnexus.accessiblecamera.ui.about.ShortcutsScreen
import com.blindtechnexus.accessiblecamera.ui.camera.CameraScreen
import com.blindtechnexus.accessiblecamera.ui.components.AccessibleBanner
import com.blindtechnexus.accessiblecamera.ui.components.FeedbackSink
import com.blindtechnexus.accessiblecamera.ui.components.LocalFeedback
import com.blindtechnexus.accessiblecamera.ui.editor.EditorScreen
import com.blindtechnexus.accessiblecamera.ui.gallery.GalleryScreen
import com.blindtechnexus.accessiblecamera.ui.gallery.MediaViewerScreen
import com.blindtechnexus.accessiblecamera.ui.navigation.BottomTab
import com.blindtechnexus.accessiblecamera.ui.navigation.Routes
import com.blindtechnexus.accessiblecamera.ui.settings.SettingsScreen
import com.blindtechnexus.accessiblecamera.ui.settings.SettingsViewModel
import com.blindtechnexus.accessiblecamera.ui.welcome.WelcomeScreen

/**
 * Root navigation host.
 *
 * The welcome screen appears once. After that each bottom tab has its own scaffold with
 * the shared four item navigation bar, and secondary screens are pushed full screen.
 * Permission launchers live here so every screen shares one recovery flow.
 */
@Composable
fun AccessibleCameraNavHost(
    appViewModel: AppStateViewModel,
    modifier: Modifier = Modifier
) {
    val settings by appViewModel.settings.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val context = LocalContext.current

    val feedback = remember(appViewModel) {
        object : FeedbackSink {
            override fun haptic(cue: HapticCue) = appViewModel.vibrate(cue)
            override fun tone(cue: AudioCue) = appViewModel.tone(cue)
            override fun announce(text: String) =
                appViewModel.announce(text, AnnouncementPriority.NORMAL)

            override fun announceValue(key: String, text: String) =
                appViewModel.announceValue(key, text)
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val activity = context as? Activity
        val permanentlyDenied = !granted && activity != null &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
        appViewModel.announceCameraPermissionResult(granted, permanentlyDenied)
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        appViewModel.announce(
            if (granted) {
                "Microphone allowed"
            } else {
                "Microphone not allowed. Video will be recorded without sound."
            },
            AnnouncementPriority.HIGH
        )
    }

    val galleryPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results.values.any { it }
        appViewModel.announce(
            if (granted) {
                "Gallery access allowed"
            } else {
                "Gallery access denied. Open app settings to allow it."
            },
            AnnouncementPriority.HIGH
        )
        appViewModel.onGalleryPermissionResult()
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results.values.any { it }
        appViewModel.announce(
            if (granted) {
                "Location permission allowed. Location is stored only when tagging is on."
            } else {
                "Location permission denied. Photos will not include a location."
            },
            AnnouncementPriority.HIGH
        )
    }

    val requestCameraPermission: () -> Unit = {
        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }
    val requestAudioPermission: () -> Unit = {
        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }
    val requestGalleryPermission: () -> Unit = {
        galleryPermissionLauncher.launch(MediaPermissionEvaluator.galleryPermissions())
    }
    val requestLocationPermission: () -> Unit = {
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        )
    }

    CompositionLocalProvider(LocalFeedback provides feedback) {
        NavHost(
            navController = navController,
            startDestination = if (settings.hasCompletedOnboarding) BottomTab.PHOTO.route else Routes.WELCOME,
            modifier = modifier.fillMaxSize()
        ) {
            composable(Routes.WELCOME) {
                WelcomeScreen(
                    onStart = {
                        appViewModel.completeOnboarding()
                        navController.navigate(BottomTab.PHOTO.route) {
                            popUpTo(Routes.WELCOME) { inclusive = true }
                        }
                    },
                    announce = appViewModel::announceScreen
                )
            }

            composable(BottomTab.PHOTO.route) {
                TabScaffold(
                    navController = navController,
                    currentTab = BottomTab.PHOTO,
                    announce = appViewModel::announceScreen,
                    showBottomBar = true
                ) {
                    CameraScreen(
                        isVideoTab = false,
                        onOpenGallery = { navController.navigate(BottomTab.GALLERY.route) },
                        onOpenViewer = { item -> navController.navigate(Routes.viewer(item.id, 0)) },
                        onRequestCameraPermission = requestCameraPermission,
                        onOpenAppSettings = { IntentLauncher.openAppSettings(context) },
                        announce = appViewModel::announceScreen
                    )
                }
            }

            composable(BottomTab.VIDEO.route) {
                TabScaffold(
                    navController = navController,
                    currentTab = BottomTab.VIDEO,
                    announce = appViewModel::announceScreen,
                    showBottomBar = true
                ) {
                    CameraScreen(
                        isVideoTab = true,
                        onOpenGallery = { navController.navigate(BottomTab.GALLERY.route) },
                        onOpenViewer = { item -> navController.navigate(Routes.viewer(item.id, 0)) },
                        onRequestCameraPermission = requestCameraPermission,
                        onOpenAppSettings = { IntentLauncher.openAppSettings(context) },
                        announce = appViewModel::announceScreen
                    )
                }
            }

            composable(BottomTab.GALLERY.route) {
                TabScaffold(
                    navController = navController,
                    currentTab = BottomTab.GALLERY,
                    announce = appViewModel::announceScreen,
                    showBottomBar = true
                ) {
                    GalleryScreen(
                        onOpenItem = { item: MediaItem, index: Int ->
                            appViewModel.selectItem(item)
                            // Carry the trash flag so the viewer offers Delete permanently.
                            navController.navigate(Routes.viewer(item.id, index, item.inTrash))
                        },
                        onRequestMediaPermission = requestGalleryPermission,
                        onOpenSettings = { IntentLauncher.openAppSettings(context) },
                        announce = appViewModel::announceScreen
                    )
                }
            }

            composable(BottomTab.SETTINGS.route) {
                TabScaffold(
                    navController = navController,
                    currentTab = BottomTab.SETTINGS,
                    announce = appViewModel::announceScreen,
                    showBottomBar = true
                ) {
                    val settingsViewModel: SettingsViewModel = hiltViewModel()
                    SettingsScreen(
                        onOpenCapabilities = { navController.navigate(Routes.CAPABILITIES) },
                        onOpenAbout = { navController.navigate(Routes.ABOUT) },
                        onOpenShortcuts = { navController.navigate(Routes.SHORTCUTS) },
                        onRequestLocationPermission = requestLocationPermission,
                        onRequestAudioPermission = requestAudioPermission,
                        announce = appViewModel::announceScreen,
                        viewModel = settingsViewModel
                    )
                }
            }

            composable(
                route = Routes.VIEWER,
                arguments = listOf(
                    navArgument("mediaId") { type = NavType.LongType },
                    navArgument("index") { type = NavType.IntType; defaultValue = 0 },
                    navArgument("trash") { type = NavType.BoolType; defaultValue = false }
                )
            ) { entry ->
                val mediaId = entry.arguments?.getLong("mediaId") ?: 0L
                val index = entry.arguments?.getInt("index") ?: 0
                val trash = entry.arguments?.getBoolean("trash") ?: false
                ViewerRoute(
                    mediaId = mediaId,
                    initialIndex = index,
                    fromTrash = trash,
                    appViewModel = appViewModel,
                    onBack = { navController.popBackStack() },
                    onEdit = { mediaItem -> navController.navigate(Routes.editor(mediaItem.id)) }
                )
            }

            composable(
                route = Routes.EDITOR,
                arguments = listOf(navArgument("mediaId") { type = NavType.LongType })
            ) { entry ->
                val mediaId = entry.arguments?.getLong("mediaId") ?: 0L
                EditorScreen(
                    mediaId = mediaId,
                    onBack = { navController.popBackStack() },
                    onSaved = {
                        appViewModel.announce("Edited copy saved", AnnouncementPriority.HIGH)
                        navController.popBackStack()
                    },
                    announce = appViewModel::announceScreen
                )
            }

            composable(Routes.ABOUT) {
                val capabilities by appViewModel.capabilities.collectAsStateWithLifecycle()
                val modelState by appViewModel.mlKitState.collectAsStateWithLifecycle()
                AboutScreen(
                    capabilities = capabilities,
                    mlKitModelsReady = modelState is MlKitModelState.Installed,
                    mlKitModelsInstalling = modelState is MlKitModelState.Installing,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.CAPABILITIES) {
                val capabilities by appViewModel.capabilities.collectAsStateWithLifecycle()
                CapabilitiesScreen(
                    capabilities = capabilities,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.SHORTCUTS) {
                ShortcutsScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

/**
 * Loads the requested media item and shows the viewer, or a clear message when the item
 * has disappeared between the gallery list and the viewer.
 */
@Composable
private fun ViewerRoute(
    mediaId: Long,
    initialIndex: Int,
    fromTrash: Boolean,
    appViewModel: AppStateViewModel,
    onBack: () -> Unit,
    onEdit: (MediaItem) -> Unit
) {
    val cached = appViewModel.findItem(mediaId)
    var item by remember(mediaId) { mutableStateOf(cached) }
    var failed by remember(mediaId) { mutableStateOf(false) }

    LaunchedEffect(mediaId, cached) {
        if (item == null) {
            val resolved = appViewModel.resolveItem(mediaId)
            if (resolved == null) {
                failed = true
            } else {
                item = resolved
            }
        }
    }

    when {
        item != null -> MediaViewerScreen(
            item = item!!,
            initialIndex = initialIndex,
            fromTrash = fromTrash,
            onBack = onBack,
            onEdit = onEdit,
            announce = appViewModel::announceScreen
        )
        failed -> Box(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            AccessibleBanner(
                text = "This item is no longer available. It may have been deleted or moved.",
                isError = true,
                actionLabel = "Go back",
                onAction = onBack
            )
        }
        else -> Box(modifier = Modifier.fillMaxSize()) {
            LaunchedEffect(Unit) {
                appViewModel.announce("Opening item", AnnouncementPriority.NORMAL)
            }
        }
    }
}

/** Shared per-tab scaffold with the accessible bottom navigation bar. */
@Composable
private fun TabScaffold(
    navController: androidx.navigation.NavHostController,
    currentTab: BottomTab,
    announce: (String) -> Unit,
    showBottomBar: Boolean,
    content: @Composable () -> Unit
) {
    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                BottomNavigationBar(
                    currentTab = currentTab,
                    onSelect = { tab ->
                        if (tab != currentTab) {
                            navController.navigate(tab.route) {
                                popUpTo(BottomTab.PHOTO.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                            announce(tab.spokenDescription)
                        }
                    }
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = padding.calculateBottomPadding())
        ) {
            content()
        }
    }
}
