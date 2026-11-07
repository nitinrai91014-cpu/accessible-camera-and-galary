/*
 * Accessible Camera and Gallery
 * Copyright (C) 2026 Sujan Rai, Blind Tech Nexus team.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.blindtechnexus.accessiblecamera.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.ui.graphics.vector.ImageVector

/** Top level bottom bar destinations. Kept small on purpose: four tabs only. */
enum class BottomTab(
    val route: String,
    val label: String,
    val spokenDescription: String,
    val icon: ImageVector
) {
    PHOTO(
        route = "photo",
        label = "Photo",
        spokenDescription = "Photo tab, take photos",
        icon = Icons.Outlined.PhotoCamera
    ),
    VIDEO(
        route = "video",
        label = "Video",
        spokenDescription = "Video tab, record video",
        icon = Icons.Outlined.Videocam
    ),
    GALLERY(
        route = "gallery",
        label = "Gallery",
        spokenDescription = "Gallery tab, browse your photos and videos",
        icon = Icons.Outlined.PhotoLibrary
    ),
    SETTINGS(
        route = "settings",
        label = "Settings",
        spokenDescription = "Settings tab, change app options",
        icon = Icons.Outlined.Settings
    );

    companion object {
        fun fromRoute(route: String?): BottomTab =
            entries.firstOrNull { it.route == route } ?: PHOTO
    }
}

/** Secondary destinations pushed on top of a tab. */
object Routes {
    const val WELCOME = "welcome"
    const val VIEWER = "viewer/{mediaId}?index={index}&trash={trash}"
    const val EDITOR = "editor/{mediaId}"
    const val ABOUT = "about"
    const val CAPABILITIES = "capabilities"
    const val SHORTCUTS = "shortcuts"

    fun viewer(mediaId: Long, index: Int, fromTrash: Boolean = false): String =
        "viewer/$mediaId?index=$index&trash=$fromTrash"

    fun editor(mediaId: Long): String = "editor/$mediaId"
}
