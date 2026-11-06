/*
 * Accessible Camera and Gallery
 * Copyright (C) 2026 Sujan Rai, Blind Tech Nexus team.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.blindtechnexus.accessiblecamera.ui.voice

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.blindtechnexus.accessiblecamera.ui.theme.Dimens

/**
 * Explicit, user initiated voice command button. It never listens on its own and shows
 * whether recognition is currently active.
 */
@Composable
fun VoiceCommandFab(
    listening: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    ExtendedFloatingActionButton(
        onClick = onToggle,
        modifier = modifier
            .padding(Dimens.ItemSpacing)
            .semantics(mergeDescendants = true) {
                contentDescription = if (listening) {
                    "Stop listening for voice commands"
                } else {
                    "Start voice commands"
                }
                stateDescription = if (listening) "Listening" else "Not listening"
            },
        containerColor = if (listening) {
            MaterialTheme.colorScheme.tertiary
        } else {
            MaterialTheme.colorScheme.primaryContainer
        },
        contentColor = if (listening) {
            MaterialTheme.colorScheme.onTertiary
        } else {
            MaterialTheme.colorScheme.onPrimaryContainer
        }
    ) {
        Icon(
            imageVector = if (listening) Icons.Filled.Mic else Icons.Filled.MicOff,
            contentDescription = null
        )
        Text(
            text = if (listening) "Listening" else "Voice",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

/** Placeholder enabled flag helper used when speech recognition is unavailable. */
@Composable
fun voiceCommandsAvailable(state: VoiceCommandController.VoiceCommandUiState): Boolean =
    state.available && true
