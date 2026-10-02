package org.perceivers25.warpinator.feature.settings.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import org.perceivers25.warpinator.core.design.shapes.segmentedDynamicShapes

/**
 * A composable list item component featuring a switch toggle.
 *
 * This component is typically used in settings screens to represent boolean preferences or
 * toggleable options. It combines a text label with a switch control, handling the layout
 * and interaction states appropriate for a list item context.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SwitchListItem(
    title: String,
    summary: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
    shapes: ListItemShapes,
    colors: ListItemColors,
) {
    SegmentedListItem(
        content = { Text(title) },
        supportingContent = if (summary != null) {
            { Text(summary) }
        } else null,
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
            )
        },
        enabled = enabled,
        checked = checked,
        onCheckedChange = { onCheckedChange(!checked) },
        shapes = shapes,
        colors = colors,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = ListItemDefaults.SegmentedGap),
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Preview
@Composable
fun SwitchListItemPreview() {
    SwitchListItem(
        title = "Example Switch",
        summary = "This is a summary",
        checked = true,
        onCheckedChange = {},
        shapes = ListItemDefaults.segmentedDynamicShapes(0, 1),
        colors = ListItemDefaults.segmentedColors(),
    )
}