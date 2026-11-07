/*
 * Accessible Camera and Gallery
 * Copyright (C) 2026 Sujan Rai, Blind Tech Nexus team.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.blindtechnexus.accessiblecamera.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat
import com.blindtechnexus.accessiblecamera.domain.model.ThemeMode

/** True when the app should draw extra borders and avoid subtle fills. */
val LocalHighContrast = staticCompositionLocalOf { false }

/** True when text should be described as very large. */
val LocalLargeText = staticCompositionLocalOf { false }

private val StandardDarkScheme = darkColorScheme(
    primary = AppColors.DarkPrimary,
    onPrimary = AppColors.DarkOnPrimary,
    primaryContainer = AppColors.DarkSurfaceVariant,
    onPrimaryContainer = AppColors.DarkOnBackground,
    secondary = AppColors.DarkSecondary,
    onSecondary = AppColors.DarkOnSecondary,
    tertiary = AppColors.DarkTertiary,
    onTertiary = AppColors.DarkOnTertiary,
    background = AppColors.DarkBackground,
    onBackground = AppColors.DarkOnBackground,
    surface = AppColors.DarkSurface,
    onSurface = AppColors.DarkOnBackground,
    surfaceVariant = AppColors.DarkSurfaceVariant,
    onSurfaceVariant = AppColors.DarkOnSurfaceVariant,
    outline = AppColors.DarkOutline,
    outlineVariant = AppColors.DarkOutline,
    error = AppColors.DarkError,
    onError = AppColors.DarkOnError,
    errorContainer = AppColors.DarkSurfaceVariant,
    onErrorContainer = AppColors.DarkError
)

private val StandardLightScheme = lightColorScheme(
    primary = AppColors.LightPrimary,
    onPrimary = AppColors.LightOnPrimary,
    primaryContainer = AppColors.LightSurfaceVariant,
    onPrimaryContainer = AppColors.LightOnBackground,
    secondary = AppColors.LightSecondary,
    onSecondary = AppColors.LightOnSecondary,
    tertiary = AppColors.LightTertiary,
    onTertiary = AppColors.LightOnTertiary,
    background = AppColors.LightBackground,
    onBackground = AppColors.LightOnBackground,
    surface = AppColors.LightSurface,
    onSurface = AppColors.LightOnBackground,
    surfaceVariant = AppColors.LightSurfaceVariant,
    onSurfaceVariant = AppColors.LightOnSurfaceVariant,
    outline = AppColors.LightOutline,
    outlineVariant = AppColors.LightOutline,
    error = AppColors.LightError,
    onError = AppColors.LightOnError,
    errorContainer = AppColors.LightSurfaceVariant,
    onErrorContainer = AppColors.LightError
)

private val HighContrastLightScheme = lightColorScheme(
    primary = AppColors.HcLightPrimary,
    onPrimary = AppColors.HcLightOnPrimary,
    primaryContainer = AppColors.HcLightSurfaceVariant,
    onPrimaryContainer = AppColors.HcLightOnBackground,
    secondary = AppColors.HcLightSecondary,
    onSecondary = AppColors.HcLightOnPrimary,
    tertiary = AppColors.HcLightSecondary,
    onTertiary = AppColors.HcLightOnPrimary,
    background = AppColors.HcLightBackground,
    onBackground = AppColors.HcLightOnBackground,
    surface = AppColors.HcLightSurface,
    onSurface = AppColors.HcLightOnBackground,
    surfaceVariant = AppColors.HcLightSurfaceVariant,
    onSurfaceVariant = AppColors.HcLightOnBackground,
    outline = AppColors.HcLightOutline,
    outlineVariant = AppColors.HcLightOutline,
    error = AppColors.HcLightError,
    onError = AppColors.HcLightOnPrimary,
    errorContainer = AppColors.HcLightSurfaceVariant,
    onErrorContainer = AppColors.HcLightError
)

private val HighContrastDarkScheme = darkColorScheme(
    primary = AppColors.HcDarkPrimary,
    onPrimary = AppColors.HcDarkOnPrimary,
    primaryContainer = AppColors.HcDarkSurfaceVariant,
    onPrimaryContainer = AppColors.HcDarkOnBackground,
    secondary = AppColors.HcDarkSecondary,
    onSecondary = AppColors.HcDarkOnSecondary,
    tertiary = AppColors.HcDarkPrimary,
    onTertiary = AppColors.HcDarkOnPrimary,
    background = AppColors.HcDarkBackground,
    onBackground = AppColors.HcDarkOnBackground,
    surface = AppColors.HcDarkSurface,
    onSurface = AppColors.HcDarkOnBackground,
    surfaceVariant = AppColors.HcDarkSurfaceVariant,
    onSurfaceVariant = AppColors.HcDarkOnBackground,
    outline = AppColors.HcDarkOutline,
    outlineVariant = AppColors.HcDarkOutline,
    error = AppColors.HcDarkError,
    onError = AppColors.HcDarkOnError,
    errorContainer = AppColors.HcDarkSurfaceVariant,
    onErrorContainer = AppColors.HcDarkError
)

/** Chooses a colour scheme from the user setting and the system night mode. */
@Composable
fun resolveColorScheme(themeMode: ThemeMode): androidx.compose.material3.ColorScheme {
    val systemDark = isSystemInDarkTheme()
    return when (themeMode) {
        ThemeMode.SYSTEM -> if (systemDark) StandardDarkScheme else StandardLightScheme
        ThemeMode.LIGHT -> StandardLightScheme
        ThemeMode.DARK -> StandardDarkScheme
        ThemeMode.HIGH_CONTRAST_LIGHT -> HighContrastLightScheme
        ThemeMode.HIGH_CONTRAST_DARK -> HighContrastDarkScheme
    }
}

/**
 * Root theme. Applies the chosen colour scheme, the optional in-app font scale and the
 * system window decoration.
 */
@Composable
fun AccessibleCameraTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    fontScaleMultiplier: Float = 0f,
    content: @Composable () -> Unit
) {
    val colorScheme = resolveColorScheme(themeMode)
    val systemDensity = LocalDensity.current
    val effectiveDensity = if (fontScaleMultiplier > 0f) {
        Density(
            density = systemDensity.density,
            fontScale = systemDensity.fontScale * fontScaleMultiplier
        )
    } else {
        systemDensity
    }
    val highContrast = themeMode.isHighContrast
    val largeText = if (fontScaleMultiplier > 0f) {
        fontScaleMultiplier >= 1.5f
    } else {
        systemDensity.fontScale >= 1.5f
    }
    val view = LocalView.current
    val context = LocalContext.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = colorScheme.background.luminance() > 0.5f
                isAppearanceLightNavigationBars = colorScheme.background.luminance() > 0.5f
            }
        }
    }
    CompositionLocalProvider(
        LocalDensity provides effectiveDensity,
        LocalHighContrast provides highContrast,
        LocalLargeText provides largeText
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            content = content
        )
    }
}

private fun androidx.compose.ui.graphics.Color.luminance(): Float {
    fun channel(value: Float): Float =
        if (value <= 0.03928f) value / 12.92f else Math.pow(((value + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
    return 0.2126f * channel(red) + 0.7152f * channel(green) + 0.0722f * channel(blue)
}
