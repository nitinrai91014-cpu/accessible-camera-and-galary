/*
 * Accessible Camera and Gallery
 * Copyright (C) 2026 Sujan Rai, Blind Tech Nexus team.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.blindtechnexus.accessiblecamera.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Palettes chosen for WCAG AA or better contrast.
 *
 * Standard dark: background #0B0F14 with text #F2F6FB gives a contrast ratio above 15:1.
 * Standard light: background #FAFBFF with text #101418 is above 17:1.
 * High contrast variants use pure black or pure white behind yellow or dark text to reach
 * ratios above 19:1 and to remain readable under colour correction filters.
 */
object AppColors {

    // Standard dark
    val DarkBackground = Color(0xFF0B0F14)
    val DarkSurface = Color(0xFF141B23)
    val DarkSurfaceVariant = Color(0xFF1E2833)
    val DarkOnBackground = Color(0xFFF2F6FB)
    val DarkOnSurfaceVariant = Color(0xFFD3DDE8)
    val DarkPrimary = Color(0xFF8CC8FF)
    val DarkOnPrimary = Color(0xFF00243D)
    val DarkSecondary = Color(0xFFB9E6C9)
    val DarkOnSecondary = Color(0xFF04301A)
    val DarkTertiary = Color(0xFFFFD98A)
    val DarkOnTertiary = Color(0xFF2C1E00)
    val DarkError = Color(0xFFFFB4A9)
    val DarkOnError = Color(0xFF410001)
    val DarkOutline = Color(0xFF8FA0B3)

    // Standard light
    val LightBackground = Color(0xFFFAFBFF)
    val LightSurface = Color(0xFFFFFFFF)
    val LightSurfaceVariant = Color(0xFFE7EDF5)
    val LightOnBackground = Color(0xFF101418)
    val LightOnSurfaceVariant = Color(0xFF2B3542)
    val LightPrimary = Color(0xFF0B4F86)
    val LightOnPrimary = Color(0xFFFFFFFF)
    val LightSecondary = Color(0xFF155C34)
    val LightOnSecondary = Color(0xFFFFFFFF)
    val LightTertiary = Color(0xFF6B4E00)
    val LightOnTertiary = Color(0xFFFFFFFF)
    val LightError = Color(0xFF8C0F13)
    val LightOnError = Color(0xFFFFFFFF)
    val LightOutline = Color(0xFF4A5866)

    // High contrast light
    val HcLightBackground = Color(0xFFFFFFFF)
    val HcLightSurface = Color(0xFFFFFFFF)
    val HcLightSurfaceVariant = Color(0xFFEDEDED)
    val HcLightOnBackground = Color(0xFF000000)
    val HcLightPrimary = Color(0xFF00356B)
    val HcLightOnPrimary = Color(0xFFFFFFFF)
    val HcLightSecondary = Color(0xFF00401C)
    val HcLightError = Color(0xFF7A0000)
    val HcLightOutline = Color(0xFF000000)

    // High contrast dark
    val HcDarkBackground = Color(0xFF000000)
    val HcDarkSurface = Color(0xFF0A0A0A)
    val HcDarkSurfaceVariant = Color(0xFF1A1A1A)
    val HcDarkOnBackground = Color(0xFFFFFFFF)
    val HcDarkPrimary = Color(0xFFFFE24D)
    val HcDarkOnPrimary = Color(0xFF000000)
    val HcDarkSecondary = Color(0xFF6DFF9E)
    val HcDarkOnSecondary = Color(0xFF000000)
    val HcDarkError = Color(0xFFFF8A80)
    val HcDarkOnError = Color(0xFF000000)
    val HcDarkOutline = Color(0xFFFFFFFF)

    // Camera specific overlays
    val CameraScrim = Color(0xB3000000)
    val CameraChrome = Color(0xFF0B0F14)
    val CameraChromeOn = Color(0xFFF2F6FB)
    val RecordingIndicator = Color(0xFFFF5252)
    val FocusRing = Color(0xFFFFE24D)
}
