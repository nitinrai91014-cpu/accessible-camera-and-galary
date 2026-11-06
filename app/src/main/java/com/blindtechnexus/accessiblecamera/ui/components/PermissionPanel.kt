/*
 * Accessible Camera and Gallery
 * Copyright (C) 2026 Sujan Rai, Blind Tech Nexus team.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.blindtechnexus.accessiblecamera.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.blindtechnexus.accessiblecamera.ui.theme.Dimens

/**
 * Full screen permission explanation with a single primary action.
 * Used when a permission is missing or permanently denied.
 */
@Composable
fun PermissionPanel(
    title: String,
    message: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    announce: (String) -> Unit = {}
) {
    LaunchedEffect(title) { announce("$title. $message") }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(Dimens.ScreenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics {
                heading()
                liveRegion = LiveRegionMode.Polite
            }
        )
        Spacer(Modifier.height(Dimens.ItemSpacing))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(Dimens.SectionSpacing))
        AccessiblePrimaryButton(text = primaryLabel, onClick = onPrimary)
        if (secondaryLabel != null && onSecondary != null) {
            Spacer(Modifier.height(Dimens.ItemSpacing))
            AccessibleSecondaryButton(
                text = secondaryLabel,
                onClick = onSecondary,
                icon = Icons.Filled.Settings
            )
        }
    }
}

/** Small inline notice about partial media access on Android 14. */
@Composable
fun PartialAccessNotice(
    onChooseMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    AccessibleBanner(
        text = "You have allowed access to selected photos only. Some items in your gallery are hidden.",
        modifier = modifier,
        actionLabel = "Choose more photos",
        onAction = onChooseMore
    )
}
