package org.perceivers25.warpinator.core.design.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TooltipIconButton(
    description: String,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
    icon: ImageVector,
    tint: Color = if (enabled) LocalContentColor.current else LocalContentColor.current.copy(
        alpha = 0.38f,
    ),
    addBadge: Boolean = false,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
            TooltipAnchorPosition.Below,
        ),
        tooltip = { PlainTooltip { Text(description) } },
        state = rememberTooltipState(),
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier,
            interactionSource = interactionSource,
        ) {
            BadgedBox(
                badge = {
                    if (addBadge) {
                        Badge(containerColor = MaterialTheme.colorScheme.tertiary)
                    }
                },
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = description,
                    tint = tint,
                )
            }
        }
    }
}