package org.perceivers25.warpinator.core.design.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.window.PopupProperties

data class MenuAction(
    val title: String,
    val trailingIcon: ImageVector? = null,
    val leadingIcon: ImageVector? = null,
    val shortcutKeyCode: Int? = null,
    val shortcutKeyCtrl: Boolean = false,
    val shortcutKeyShift: Boolean = false,
    val shortcutKeyAlt: Boolean = false,
    val onClick: () -> Unit,
)

data class MenuGroup(
    val actions: List<MenuAction>,
    val errorGroup: Boolean = false,
)

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun MenuGroupsPopup(
    menuOpen: Boolean,
    menuGroups: List<MenuGroup>,
    groupInteractionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    onDismiss: () -> Unit = {},
    offset: DpOffset = DpOffset.Zero,
    properties: PopupProperties = PopupProperties(),
    minWidth: Dp? = null,
) {
    DropdownMenuPopup(
        expanded = menuOpen,
        onDismissRequest = onDismiss,
        offset = offset,
        properties = properties,
    ) {
        val colors = MenuDefaults.itemColors(
            textColor = MaterialTheme.colorScheme.onSecondaryContainer,
            leadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
            trailingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
        )

        val errorColors = MenuDefaults.itemColors(
            textColor = MaterialTheme.colorScheme.onErrorContainer,
            leadingIconColor = MaterialTheme.colorScheme.onErrorContainer,
            trailingIconColor = MaterialTheme.colorScheme.onErrorContainer,
        )


        menuGroups.forEachIndexed { index, group ->
            DropdownMenuGroup(
                shapes = MenuDefaults.groupShape(index, menuGroups.size),
                interactionSource = groupInteractionSource,
                containerColor = if (group.errorGroup) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
            ) {
                group.actions.forEach { action ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(action.title)
                                if (action.shortcutKeyCode != null) {
                                    ShortcutLabel(
                                        action.shortcutKeyCode,
                                        action.shortcutKeyCtrl,
                                        action.shortcutKeyShift,
                                        action.shortcutKeyAlt,
                                    )
                                }
                            }
                        },
                        leadingIcon = action.leadingIcon?.let { leadingIcon ->
                            {
                                Icon(
                                    leadingIcon, contentDescription = null,
                                )
                            }
                        },
                        trailingIcon = action.trailingIcon?.let { trailingIcon ->
                            {
                                Icon(
                                    trailingIcon, contentDescription = null,
                                )
                            }
                        },
                        onClick = {
                            action.onClick()
                            onDismiss()
                        },
                        colors = if (group.errorGroup) errorColors else colors,
                        modifier = minWidth?.let {
                            Modifier.widthIn(min = it)
                        } ?: Modifier,
                    )
                }

            }
            if (index != menuGroups.size - 1) {
                Spacer(Modifier.height(MenuDefaults.GroupSpacing))
            }
        }
    }
}