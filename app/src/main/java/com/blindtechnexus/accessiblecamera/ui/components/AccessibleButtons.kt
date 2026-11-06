/*
 * Accessible Camera and Gallery
 * Copyright (C) 2026 Sujan Rai, Blind Tech Nexus team.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.blindtechnexus.accessiblecamera.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.blindtechnexus.accessiblecamera.domain.model.HapticCue
import com.blindtechnexus.accessiblecamera.ui.theme.Dimens
import com.blindtechnexus.accessiblecamera.ui.theme.LocalHighContrast

/**
 * Large primary action. Always at least 64 dp tall so it is comfortable for switch access
 * and for users with limited dexterity.
 */
@Composable
fun AccessiblePrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    contentDescriptionOverride: String? = null,
    supportingText: String? = null
) {
    val feedback = LocalFeedback.current
    val highContrast = LocalHighContrast.current
    Column(modifier = modifier.fillMaxWidth()) {
        Button(
            onClick = {
                feedback.haptic(HapticCue.TAP)
                onClick()
            },
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = Dimens.SecondaryAction)
                .semantics {
                    role = Role.Button
                    contentDescriptionOverride?.let { contentDescription = it }
                    if (!enabled) disabled()
                    supportingText?.let { stateDescription = it }
                },
            enabled = enabled,
            border = if (highContrast) BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface) else null,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(Dimens.IconSize)
                )
                Spacer(Modifier.width(Dimens.SmallSpacing))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = Dimens.SmallSpacing)
            )
        }
        if (supportingText != null) {
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp, start = 4.dp)
            )
        }
    }
}

/** Secondary action, still at least 48 dp tall. */
@Composable
fun AccessibleSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    supportingText: String? = null,
    tonal: Boolean = true
) {
    val feedback = LocalFeedback.current
    val highContrast = LocalHighContrast.current
    Column(modifier = modifier) {
        OutlinedButton(
            onClick = {
                feedback.haptic(HapticCue.TAP)
                onClick()
            },
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = Dimens.MinimumTouchTarget)
                .semantics {
                    role = Role.Button
                    if (!enabled) disabled()
                    supportingText?.let { stateDescription = it }
                },
            enabled = enabled,
            border = BorderStroke(
                width = if (highContrast) 3.dp else 1.5.dp,
                color = if (enabled) {
                    MaterialTheme.colorScheme.outline
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                }
            ),
            colors = if (tonal) {
                ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            } else {
                ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            }
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(Dimens.SmallSpacing))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 6.dp)
            )
        }
        if (supportingText != null) {
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp)
            )
        }
    }
}

/**
 * Square control used for camera and gallery actions. The visible text plus icon label
 * guarantees that no meaning depends on the icon or colour alone.
 */
@Composable
fun AccessibleActionTile(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean? = null,
    valueText: String? = null,
    unavailableReason: String? = null,
    minHeight: androidx.compose.ui.unit.Dp = Dimens.SecondaryAction
) {
    val feedback = LocalFeedback.current
    val highContrast = LocalHighContrast.current
    val containerColor = when {
        !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        selected == true -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        selected == true -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }
    val stateText = when {
        !enabled -> unavailableReason ?: "Not available on this device"
        selected == true -> "On"
        selected == false -> "Off"
        valueText != null -> valueText
        else -> null
    }
    Surface(
        onClick = {
            if (enabled) {
                feedback.haptic(HapticCue.TAP)
                onClick()
            }
        },
        modifier = modifier
            .defaultMinSize(minHeight = minHeight)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = if (!enabled) {
                    "$label. $stateText"
                } else {
                    label
                }
                stateText?.let { stateDescription = it }
                if (!enabled) disabled()
            },
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        color = containerColor,
        contentColor = contentColor,
        border = if (highContrast) BorderStroke(2.dp, MaterialTheme.colorScheme.outline) else null
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(Dimens.IconSize)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center
            )
            if (stateText != null) {
                Text(
                    text = stateText,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/** Icon only button with a mandatory content description. */
@Composable
fun AccessibleIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.onSurface
) {
    val feedback = LocalFeedback.current
    androidx.compose.material3.IconButton(
        onClick = {
            feedback.haptic(HapticCue.TAP)
            onClick()
        },
        modifier = modifier
            .defaultMinSize(minWidth = Dimens.MinimumTouchTarget, minHeight = Dimens.MinimumTouchTarget)
            .semantics {
                role = Role.Button
                this.contentDescription = contentDescription
                if (!enabled) disabled()
            },
        enabled = enabled
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) tint else tint.copy(alpha = 0.5f),
            modifier = Modifier.size(Dimens.IconSize)
        )
    }
}

/** Row of up to three actions that wraps to a column on narrow or large text layouts. */
@Composable
fun ActionRow(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.ItemSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        content()
    }
}
