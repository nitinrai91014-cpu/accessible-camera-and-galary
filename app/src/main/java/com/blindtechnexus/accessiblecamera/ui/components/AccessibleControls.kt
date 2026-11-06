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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.blindtechnexus.accessiblecamera.domain.model.HapticCue
import com.blindtechnexus.accessiblecamera.ui.theme.Dimens
import com.blindtechnexus.accessiblecamera.ui.theme.LocalHighContrast

/** Section heading that screen readers treat as a heading for fast navigation. */
@Composable
fun SectionHeading(
    text: String,
    modifier: Modifier = Modifier,
    supporting: String? = null
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() }
        )
        if (supporting != null) {
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(Dimens.SmallSpacing))
    }
}

/**
 * A labelled setting row with a switch. The whole row is a single toggleable target so it
 * works with a swipe plus double tap and with switch access scanning.
 */
@Composable
fun AccessibleSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    description: String? = null,
    unavailableReason: String? = null
) {
    val feedback = LocalFeedback.current
    val highContrast = LocalHighContrast.current
    val stateText = when {
        !enabled -> unavailableReason ?: "Not available on this device"
        checked -> "On"
        else -> "Off"
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = Dimens.ListRowHeight)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = {
                    feedback.haptic(HapticCue.SELECTION)
                    feedback.announce("$label ${if (it) "on" else "off"}")
                    onCheckedChange(it)
                }
            )
            .semantics(mergeDescendants = true) {
                contentDescription = label
                stateDescription = stateText
                description?.let { }
                if (!enabled) disabled()
            },
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = if (highContrast) BorderStroke(2.dp, MaterialTheme.colorScheme.outline) else null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Dimens.ItemSpacing, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = label, style = MaterialTheme.typography.bodyLarge)
                val secondary = when {
                    !enabled && unavailableReason != null -> unavailableReason
                    description != null -> description
                    else -> null
                }
                if (secondary != null) {
                    Text(
                        text = secondary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.width(Dimens.ItemSpacing))
            Switch(checked = checked, onCheckedChange = null, enabled = enabled)
        }
    }
}

/** Cycles through options when activated. Announces the new value. */
@Composable
fun AccessibleCycleRow(
    label: String,
    value: String,
    onActivate: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    description: String? = null,
    unavailableReason: String? = null
) {
    val feedback = LocalFeedback.current
    val highContrast = LocalHighContrast.current
    Surface(
        onClick = {
            if (enabled) {
                feedback.haptic(HapticCue.SELECTION)
                onActivate()
            }
        },
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = Dimens.ListRowHeight)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = label
                stateDescription = if (!enabled) {
                    unavailableReason ?: "Not available on this device"
                } else {
                    value
                }
                if (!enabled) disabled()
            },
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = if (highContrast) BorderStroke(2.dp, MaterialTheme.colorScheme.outline) else null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Dimens.ItemSpacing, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = label, style = MaterialTheme.typography.bodyLarge)
                val secondary = when {
                    !enabled && unavailableReason != null -> unavailableReason
                    description != null -> description
                    else -> null
                }
                if (secondary != null) {
                    Text(
                        text = secondary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.width(Dimens.ItemSpacing))
            Text(
                text = if (enabled) value else "Unavailable",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/** Slider row with a spoken value and configurable stepping. */
@Composable
fun AccessibleSliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    valueLabel: (Float) -> String,
    enabled: Boolean = true,
    description: String? = null,
    onChangeFinished: (Float) -> Unit = {}
) {
    val feedback = LocalFeedback.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.SmallSpacing)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = valueLabel(value),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value,
            onValueChange = {
                onValueChange(it)
                feedback.announceValue(label, "$label ${valueLabel(it)}")
            },
            valueRange = valueRange,
            steps = steps,
            enabled = enabled,
            onValueChangeFinished = {
                feedback.haptic(HapticCue.SELECTION)
                onChangeFinished(value)
            },
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = Dimens.MinimumTouchTarget)
                .semantics {
                    contentDescription = label
                    stateDescription = valueLabel(value)
                }
        )
        if (description != null) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Radio style list used when the number of options is small. */
@Composable
fun AccessibleChoiceRow(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null
) {
    val feedback = LocalFeedback.current
    val highContrast = LocalHighContrast.current
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = Dimens.ListRowHeight)
            .selectable(
                selected = selected,
                onClick = {
                    feedback.haptic(HapticCue.SELECTION)
                    onSelect()
                },
                role = Role.RadioButton
            )
            .semantics(mergeDescendants = true) {
                contentDescription = label
                stateDescription = if (selected) "Selected" else "Not selected"
            },
        color = if (selected) {
            MaterialTheme.colorScheme.surfaceVariant
        } else {
            MaterialTheme.colorScheme.surface
        },
        shape = MaterialTheme.shapes.medium,
        border = if (highContrast) BorderStroke(2.dp, MaterialTheme.colorScheme.outline) else null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Dimens.ItemSpacing, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = selected, onClick = null)
            Spacer(Modifier.width(Dimens.SmallSpacing))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = label, style = MaterialTheme.typography.bodyLarge)
                if (supporting != null) {
                    Text(
                        text = supporting,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** Simple informational banner that is also announced when it appears. */
@Composable
fun AccessibleBanner(
    text: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val highContrast = LocalHighContrast.current
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = text
            },
        color = if (isError) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        contentColor = if (isError) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(
            width = if (highContrast) 3.dp else 1.dp,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
        )
    ) {
        Column(modifier = Modifier.padding(Dimens.ItemSpacing)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (isError) "Problem: " else "Note: ",
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(Modifier.width(4.dp))
                Text(text = text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            }
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(Dimens.SmallSpacing))
                AccessibleSecondaryButton(text = actionLabel, onClick = onAction)
            }
        }
    }
}

/** Groups related rows with a heading and consistent spacing. */
@Composable
fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimens.SmallSpacing)
    ) {
        SectionHeading(text = title, supporting = supporting)
        content()
    }
}
