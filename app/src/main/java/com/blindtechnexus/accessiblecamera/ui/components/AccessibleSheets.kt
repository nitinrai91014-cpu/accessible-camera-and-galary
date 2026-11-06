/*
 * Accessible Camera and Gallery
 * Copyright (C) 2026 Sujan Rai, Blind Tech Nexus team.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.blindtechnexus.accessiblecamera.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.blindtechnexus.accessiblecamera.ui.theme.Dimens

/**
 * Bottom sheet used for progressive disclosure.
 *
 * The sheet is labelled with a pane title so TalkBack announces it on open, and the title
 * is a heading so users can jump to it. Content is scrollable so large text never clips.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccessibleBottomSheet(
    title: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    announceOnOpen: (String) -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    LaunchedEffect(title) { announceOnOpen("$title sheet opened") }
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        modifier = modifier.semantics { paneTitle = title },
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = Dimens.ScreenPadding)
                .padding(bottom = Dimens.SectionSpacing)
                .semantics { paneTitle = title },
            verticalArrangement = Arrangement.spacedBy(Dimens.ItemSpacing)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics {
                    heading()
                    contentDescription = title
                }
            )
            if (supporting != null) {
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(Dimens.SmallSpacing))
            content()
        }
    }
}

/**
 * Confirmation dialog for destructive actions. The destructive button is never the
 * default focus and the cancel button is always present.
 */
@Composable
fun AccessibleConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    dismissLabel: String = "Cancel",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = true,
    supporting: String? = null
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.semantics { paneTitle = title },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() }
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.SmallSpacing)) {
                Text(text = message, style = MaterialTheme.typography.bodyLarge)
                if (supporting != null) {
                    Text(
                        text = supporting,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            AccessiblePrimaryButton(
                text = confirmLabel,
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth()
            )
        },
        dismissButton = {
            AccessibleSecondaryButton(
                text = dismissLabel,
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.large
    )
}
