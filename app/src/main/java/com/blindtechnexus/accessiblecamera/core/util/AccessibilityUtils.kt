/*
 * Accessible Camera and Gallery
 * Copyright (C) 2026 Sujan Rai, Blind Tech Nexus team.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.blindtechnexus.accessiblecamera.core.util

import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** Helpers to avoid duplicate announcements and to reason about active assistive tech. */
object AccessibilityUtils {

    fun isTouchExplorationEnabled(context: Context): Boolean {
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            ?: return false
        return manager.isEnabled && manager.isTouchExplorationEnabled
    }

    fun isAnyAccessibilityServiceEnabled(context: Context): Boolean {
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            ?: return false
        return manager.isEnabled && manager.getEnabledAccessibilityServiceList(
            android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        ).isNotEmpty()
    }

    /** True when a screen reader is likely announcing focus changes already. */
    fun isScreenReaderActive(context: Context): Boolean = isTouchExplorationEnabled(context)
}

@Composable
fun rememberScreenReaderActive(): Boolean {
    val context = LocalContext.current
    return remember(context) { AccessibilityUtils.isScreenReaderActive(context) }
}
