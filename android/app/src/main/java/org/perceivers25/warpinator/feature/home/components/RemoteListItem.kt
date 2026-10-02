package org.perceivers25.warpinator.feature.home.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.tooling.preview.PreviewDynamicColors
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.perceivers25.warpinator.R
import org.perceivers25.warpinator.RemoteConnectionError
import org.perceivers25.warpinator.RemoteState
import org.perceivers25.warpinator.core.design.components.DynamicAvatarCircle
import org.perceivers25.warpinator.core.design.components.TooltipIconButton
import org.perceivers25.warpinator.core.design.shapes.segmentedDynamicShapes
import org.perceivers25.warpinator.core.design.theme.WarpinatorTheme
import org.perceivers25.warpinator.core.model.ui.RemoteUi
import org.perceivers25.warpinator.core.utils.RemoteDisplayInfo
import kotlin.math.abs

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RemoteListItem(
    remote: RemoteUi,
    onFavoriteToggle: () -> Unit, onClick: () -> Unit,
    index: Int, itemCount: Int,
) {
    val isFavorite = remote.isFavorite
    val status = remote.state

    val isError = status is RemoteState.Error
    val isConnecting =
        status == RemoteState.Connecting || status == RemoteState.AwaitingDuplex
    val isDisconnected = status == RemoteState.Disconnected

    val swipeToDismissState = rememberSwipeToDismissBoxState()
    val coroutineScope = rememberCoroutineScope()

    val displayInfo = remember(remote) { RemoteDisplayInfo.fromRemote(remote) }
    val haptics = LocalHapticFeedback.current

    val favoriteLabel = stringResource(R.string.favorite_label)
    val connectionStateLabel = when {
        isError -> stringResource(R.string.connection_error)
        isConnecting -> stringResource(R.string.remote_connecting)
        isDisconnected -> stringResource(R.string.remote_disconnected)
        else -> stringResource(R.string.remote_connected)
    }

    val tileInteractionSource = remember { MutableInteractionSource() }
    val buttonInteractionSource = remember { MutableInteractionSource() }

    val isTileHovered by tileInteractionSource.collectIsHoveredAsState()
    val isTileFocused by tileInteractionSource.collectIsFocusedAsState()
    val isButtonHovered by buttonInteractionSource.collectIsHoveredAsState()
    val isButtonPressed by buttonInteractionSource.collectIsPressedAsState()

    val showFavoriteAction =
        isTileHovered || isTileFocused || isButtonHovered || isButtonPressed

    val accessibilityState =
        remember(isFavorite, isError, isConnecting, isDisconnected) {
            buildString {
                if (isFavorite) append(favoriteLabel, " ")
                append(connectionStateLabel)
            }
        }

    Box(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .padding(bottom = ListItemDefaults.SegmentedGap),
    ) {
        SwipeToDismissBox(
            state = swipeToDismissState,
            enableDismissFromStartToEnd = false,
            backgroundContent = {
                SwipeBackground(swipeToDismissState, isFavorite)
            },
            onDismiss = {
                coroutineScope.launch {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    swipeToDismissState.reset()
                    onFavoriteToggle()
                }
            },
            content = {
                val onClickLabel = stringResource(R.string.select_device_action)
                val toggleActionLabel =
                    stringResource(R.string.toggle_favorite_action)
                SegmentedListItem(
                    onClick = onClick,
                    modifier = Modifier.semantics {
                        stateDescription = accessibilityState
                        onClick(onClickLabel, null)
                        customActions = listOf(
                            CustomAccessibilityAction(label = toggleActionLabel) {
                                coroutineScope.launch {
                                    haptics.performHapticFeedback(
                                        HapticFeedbackType.Confirm,
                                    )
                                    swipeToDismissState.reset()
                                    onFavoriteToggle()
                                }
                                true
                            },
                        )
                    },
                    shapes = ListItemDefaults.segmentedDynamicShapes(
                        index = index,
                        count = itemCount,
                    ),
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    content = {
                        Text(displayInfo.title)
                    },
                    supportingContent = { Text(displayInfo.subtitle) },
                    leadingContent = {
                        DynamicAvatarCircle(
                            bitmap = remote.picture,
                            isFavorite = isFavorite,
                            hasError = isError,
                            isLoading = isConnecting,
                            isDisabled = isDisconnected,
                        )
                    },
                    trailingContent = {
                        if (showFavoriteAction) {
                            TooltipIconButton(
                                description = if (remote.isFavorite) stringResource(
                                    R.string.remove_from_favorites_label,
                                ) else stringResource(
                                    R.string.add_to_favorites_label,
                                ),
                                onClick = {
                                    haptics.performHapticFeedback(
                                        HapticFeedbackType.Confirm,
                                    )
                                    onFavoriteToggle()
                                },
                                icon = if (isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                interactionSource = buttonInteractionSource,
                            )
                        } else {
                            Icon(
                                Icons.Rounded.ChevronRight,
                                contentDescription = null,
                            )
                        }
                    },
                    interactionSource = tileInteractionSource,
                )
            },
        )
    }
}

@Composable
private fun SwipeBackground(
    state: SwipeToDismissBoxState, isFavorite: Boolean,
) {
    val density = LocalDensity.current
    val offsetInDp = try {
        val offset = abs(state.requireOffset())
        with(density) {
            offset.toDp()
        }
    } catch (_: IllegalStateException) {
        0f.dp
    }

    val backgroundColor = if (isFavorite) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }

    val favoriteIcon =
        if (isFavorite) Icons.Rounded.FavoriteBorder else Icons.Rounded.Favorite
    val favoriteIconColor =
        if (isFavorite) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer


    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = backgroundColor,
            modifier = Modifier
                .fillMaxHeight()
                .width(offsetInDp),
        ) {
            Box(
                contentAlignment = Alignment.CenterEnd,

                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentWidth(align = Alignment.End, unbounded = true),
            ) {
                Icon(
                    imageVector = favoriteIcon,
                    contentDescription = null,
                    tint = favoriteIconColor,
                    modifier = Modifier
                        .padding(end = 24.dp)
                        .requiredSize(24.dp),
                )

            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@PreviewLightDark
@PreviewDynamicColors
@Composable
fun RemoteListItemPreview() {
    // Mock Data
    val mockConnectedRemote = RemoteUi(
        displayName = "Connected remote",
        username = "user",
        hostname = "device",
        state = RemoteState.Connected,
    )

    val mockErrorRemote = RemoteUi(
        displayName = "Error remote",
        username = "error",
        hostname = "192.168.0.999",
        state = RemoteState.Error(RemoteConnectionError.DUPLEX_ERROR),
    )

    val mockConnectingRemote = RemoteUi(
        displayName = "Connecting remote",
        username = "",
        hostname = "",
        state = RemoteState.Connecting,
    )

    val mockDisconnectedRemote = RemoteUi(
        hostname = "phone",
        username = "",
        displayName = "",
        state = RemoteState.Disconnected,
    )

    WarpinatorTheme {
        Scaffold { scaffoldPadding ->
            Column(
                Modifier
                    .padding(scaffoldPadding)
                    .padding(top = 16.dp),
            ) {
                // Connected & Favorite
                RemoteListItem(
                    remote = mockConnectedRemote.copy(isFavorite = true),
                    onFavoriteToggle = {},
                    onClick = {}, index = 0, itemCount = 6,
                )
                // Connected
                RemoteListItem(
                    remote = mockConnectedRemote,
                    onFavoriteToggle = {},
                    onClick = {}, index = 1, itemCount = 6,
                )
                // Error
                RemoteListItem(
                    remote = mockErrorRemote,
                    onFavoriteToggle = {},
                    onClick = {}, index = 2, itemCount = 6,
                )
                // Error & Favorite
                RemoteListItem(
                    remote = mockErrorRemote.copy(isFavorite = true),
                    onFavoriteToggle = {},
                    onClick = {}, index = 3, itemCount = 6,
                )
                // Connecting (Animated)
                RemoteListItem(
                    remote = mockConnectingRemote,
                    onFavoriteToggle = {},
                    onClick = {}, index = 4, itemCount = 6,
                )
                // Disconnected
                RemoteListItem(
                    remote = mockDisconnectedRemote,
                    onFavoriteToggle = {},
                    onClick = {}, index = 5, itemCount = 6,
                )
            }
        }
    }
}