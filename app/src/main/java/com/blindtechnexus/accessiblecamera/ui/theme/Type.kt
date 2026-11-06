/*
 * Accessible Camera and Gallery
 * Copyright (C) 2026 Sujan Rai, Blind Tech Nexus team.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.blindtechnexus.accessiblecamera.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

/**
 * Typography tuned for large text and long labels.
 *
 * Line heights are generous so that scaled text does not clip, and no style uses
 * letter spacing that harms readability. Font sizes start slightly larger than the
 * Material defaults because the primary audience benefits from bigger type.
 */
private val generousLineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None
)

private fun style(
    size: Int,
    lineHeight: Int,
    weight: FontWeight = FontWeight.Normal
) = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    lineHeightStyle = generousLineHeight
)

val AppTypography = Typography(
    displayLarge = style(52, 62, FontWeight.Bold),
    displayMedium = style(44, 54, FontWeight.Bold),
    displaySmall = style(38, 48, FontWeight.Bold),
    headlineLarge = style(34, 44, FontWeight.SemiBold),
    headlineMedium = style(30, 40, FontWeight.SemiBold),
    headlineSmall = style(26, 36, FontWeight.SemiBold),
    titleLarge = style(24, 34, FontWeight.SemiBold),
    titleMedium = style(21, 30, FontWeight.SemiBold),
    titleSmall = style(19, 28, FontWeight.Medium),
    bodyLarge = style(19, 30),
    bodyMedium = style(17, 27),
    bodySmall = style(15, 24),
    labelLarge = style(18, 26, FontWeight.SemiBold),
    labelMedium = style(16, 24, FontWeight.Medium),
    labelSmall = style(14, 22, FontWeight.Medium)
)
