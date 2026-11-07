/*
 * Accessible Camera and Gallery
 * Copyright (C) 2026 Sujan Rai, Blind Tech Nexus team.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.blindtechnexus.accessiblecamera.ui

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.blindtechnexus.accessiblecamera.ui.navigation.BottomTab
import com.blindtechnexus.accessiblecamera.ui.theme.Dimens

/**
 * The four way bottom navigation bar.
 *
 * Each item is a tab with both a visible label and a spoken state, and the bar is ordered
 * Photo, Video, Gallery, Settings to match both the visual and logical reading order.
 */
@Composable
fun BottomNavigationBar(
    currentTab: BottomTab,
    onSelect: (BottomTab) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        BottomTab.entries.forEach { tab ->
            val selected = currentTab == tab
            NavigationBarItem(
                selected = selected,
                onClick = { if (!selected) onSelect(tab) },
                icon = {
                    Icon(imageVector = tab.icon, contentDescription = null)
                },
                label = {
                    Text(text = tab.label, style = MaterialTheme.typography.labelMedium)
                },
                alwaysShowLabel = true,
                modifier = Modifier
                    .defaultMinSize(minHeight = Dimens.MinimumTouchTarget)
                    .semantics(mergeDescendants = true) {
                        role = Role.Tab
                        contentDescription = tab.label
                        stateDescription = if (selected) "Selected" else "Not selected"
                    },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}
