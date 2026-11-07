/*
 * Accessible Camera and Gallery
 * Copyright (C) 2026 Sujan Rai, Blind Tech Nexus team.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.blindtechnexus.accessiblecamera.data.media

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Central place for storage permission rules across Android versions.
 * Android 13 and later split image and video access; Android 14 adds partial access.
 */
object MediaPermissionEvaluator {

    /** Permissions to request for gallery access on this OS version. */
    fun galleryPermissions(): Array<String> = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
        )
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO
        )
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    fun hasImageReadAccess(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return isGranted(context, Manifest.permission.READ_MEDIA_IMAGES) ||
                isGranted(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return isGranted(context, Manifest.permission.READ_MEDIA_IMAGES)
        }
        return isGranted(context, Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    fun hasVideoReadAccess(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return isGranted(context, Manifest.permission.READ_MEDIA_VIDEO) ||
                isGranted(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return isGranted(context, Manifest.permission.READ_MEDIA_VIDEO)
        }
        return isGranted(context, Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    /** Android 14 partial access, meaning only user selected photos are visible. */
    fun hasPartialImageAccess(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            isGranted(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) &&
            !isGranted(context, Manifest.permission.READ_MEDIA_IMAGES)

    fun hasFullImageAccess(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return isGranted(context, Manifest.permission.READ_MEDIA_IMAGES)
        }
        return hasImageReadAccess(context)
    }

    fun isGranted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
