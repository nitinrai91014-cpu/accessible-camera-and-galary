/*
 * Accessible Camera and Gallery
 * Copyright (C) 2026 Sujan Rai, Blind Tech Nexus team.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.blindtechnexus.accessiblecamera.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Shared sizes. Every interactive element is at least 48 dp as required by the
 * accessibility acceptance criteria; primary camera controls are much larger.
 */
object Dimens {
    val MinimumTouchTarget = 48.dp
    val PrimaryAction = 96.dp
    val SecondaryAction = 64.dp
    val IconSize = 32.dp
    val LargeIcon = 44.dp
    val ScreenPadding = 20.dp
    val SectionSpacing = 24.dp
    val ItemSpacing = 12.dp
    val SmallSpacing = 8.dp
    val ThumbnailSize = 56.dp
    val GalleryThumbnailTouchTarget = 96.dp
    val FocusBorder = 3.dp
    val ListRowHeight = 72.dp
}
