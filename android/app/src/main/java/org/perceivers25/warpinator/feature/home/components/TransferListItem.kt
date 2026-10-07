package org.perceivers25.warpinator.feature.home.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.perceivers25.warpinator.R
import org.perceivers25.warpinator.TransferState
import org.perceivers25.warpinator.core.design.components.ExpandableSegmentedListItem
import org.perceivers25.warpinator.core.design.components.TooltipIconButton
import org.perceivers25.warpinator.core.design.theme.WarpinatorTheme
import org.perceivers25.warpinator.core.model.ui.TransferKindUi
import org.perceivers25.warpinator.core.model.ui.TransferUi
import org.perceivers25.warpinator.feature.home.state.TransferUiActionButtons
import org.perceivers25.warpinator.feature.home.state.TransferUiProgressIndicator
import org.perceivers25.warpinator.feature.home.state.toUiState
import kotlin.math.abs

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TransferListItem(
    transfer: TransferUi,
    expanded: Boolean,
    onExpandRequest: () -> Unit,
    // Callbacks for actions
    onItemOpen: (String) -> Unit = {},
    onAccept: (String) -> Unit = {},
    onCancel: (String) -> Unit = {},
    onStop: (String) -> Unit = {},
    onRetry: (String) -> Unit = {},
    onClear: (String) -> Unit = {},
    itemIndex: Int,
    itemListCount: Int,
) {
    val uiState = transfer.toUiState()
    val swipeToDismissState = rememberSwipeToDismissBoxState()
    val coroutineScope = rememberCoroutineScope()

    val haptics = LocalHapticFeedback.current

    val onDelete = {
        coroutineScope.launch {
            haptics.performHapticFeedback(HapticFeedbackType.GestureEnd)
            onClear(transfer.uuid)
        }
    }

    val deleteTransferActionLabel =
        stringResource(R.string.delete_transfer_action)
    val acceptTransferActionLabel =
        stringResource(R.string.accept_transfer_action)
    val declineTransferActionLabel =
        stringResource(R.string.decline_transfer_action)
    val stopTransferActionLabel = stringResource(R.string.stop_transfer_action)
    val cancelTransferActionLabel =
        stringResource(R.string.cancel_transfer_action)
    val retryTransferActionLabel =
        stringResource(R.string.retry_transfer_action)
    val openTransferActionLabel =
        stringResource(R.string.open_transfer_item_action)

    val semanticCustomActions =
        remember(uiState.actionButtons, uiState.allowDismiss) {
            buildList {
                if (uiState.allowDismiss) add(
                    CustomAccessibilityAction(deleteTransferActionLabel) {
                        onDelete()
                        true
                    },
                )

                when (uiState.actionButtons) {
                    TransferUiActionButtons.AcceptAndDecline -> {
                        add(
                            CustomAccessibilityAction(acceptTransferActionLabel) {
                                onAccept(transfer.uuid)
                                true
                            },
                        )
                        add(
                            CustomAccessibilityAction(declineTransferActionLabel) {
                                onCancel(transfer.uuid)
                                true
                            },
                        )
                    }

                    TransferUiActionButtons.Stop -> {
                        add(
                            CustomAccessibilityAction(stopTransferActionLabel) {
                                onStop(transfer.uuid)
                                true
                            },
                        )
                    }

                    TransferUiActionButtons.Cancel -> {
                        add(
                            CustomAccessibilityAction(cancelTransferActionLabel) {
                                onStop(transfer.uuid)
                                true
                            },
                        )
                    }

                    TransferUiActionButtons.Retry -> {
                        add(
                            CustomAccessibilityAction(retryTransferActionLabel) {
                                onRetry(transfer.uuid)
                                true
                            },
                        )
                    }

                    TransferUiActionButtons.OpenFolder -> {
                        add(
                            CustomAccessibilityAction(openTransferActionLabel) {
                                onItemOpen(transfer.uuid)
                                true
                            },
                        )
                    }

                    TransferUiActionButtons.None -> {}
                }
            }
        }

    val tileInteractionSource = remember { MutableInteractionSource() }
    val clearInteractionSource = remember { MutableInteractionSource() }

    val isTileHovered by tileInteractionSource.collectIsHoveredAsState()
    val isTileFocused by tileInteractionSource.collectIsFocusedAsState()
    val isButtonHovered by clearInteractionSource.collectIsHoveredAsState()
    val isButtonPressed by clearInteractionSource.collectIsPressedAsState()

    val showClearAction =
        isTileHovered || isTileFocused || isButtonHovered || isButtonPressed


    SwipeToDismissBox(
        state = swipeToDismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = uiState.allowDismiss && !expanded,
        backgroundContent = {
            DismissBackground(swipeToDismissState)
        },
        onDismiss = {
            onDelete()
        },
        content = {
            ExpandableSegmentedListItem(
                isExpanded = expanded,
                toggleExpand = onExpandRequest,
                content = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = uiState.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "(${uiState.totalSize})",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                },
                supportingContent = {
                    Text(
                        text = uiState.statusText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                leadingContent = {
                    Icon(
                        imageVector = if (uiState.isSending) Icons.Rounded.Upload else Icons.Rounded.Download,
                        contentDescription = if (uiState.isSending) stringResource(
                            R.string.transfer_direction_send_short,
                        ) else stringResource(
                            R.string.transfer_direction_receive_short,
                        ),
                        tint = uiState.iconColor,
                    )
                },
                trailingContent = {
                    Row {
                        if (showClearAction && uiState.allowDismiss) {
                            TooltipIconButton(
                                onClick = { onDelete() },
                                icon = Icons.Rounded.Delete,
                                description = stringResource(R.string.delete_transfer_action),
                                interactionSource = clearInteractionSource,
                            )
                        }

                        when (uiState.actionButtons) {
                            TransferUiActionButtons.AcceptAndDecline -> {
                                TooltipIconButton(
                                    onClick = { onAccept(transfer.uuid) },
                                    icon = Icons.Rounded.Check,
                                    description = stringResource(R.string.accept_label),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                TooltipIconButton(
                                    onClick = { onCancel(transfer.uuid) },
                                    icon = Icons.Rounded.Close,
                                    description = stringResource(R.string.decline_label),
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }

                            TransferUiActionButtons.Stop -> {
                                TooltipIconButton(
                                    onClick = { onStop(transfer.uuid) },
                                    icon = Icons.Rounded.Stop,
                                    description = stringResource(R.string.stop_label),
                                )

                            }

                            TransferUiActionButtons.Retry -> {
                                TooltipIconButton(
                                    description = stringResource(R.string.retry_label),
                                    onClick = { onRetry(transfer.uuid) },
                                    icon = Icons.Rounded.Refresh,
                                )
                            }

                            TransferUiActionButtons.OpenFolder -> {
                                TooltipIconButton(
                                    description = stringResource(R.string.open_item_label),
                                    onClick = { onItemOpen(transfer.uuid) },
                                    icon = Icons.Rounded.FolderOpen,
                                )
                            }

                            TransferUiActionButtons.Cancel -> {
                                TooltipIconButton(
                                    onClick = { onCancel(transfer.uuid) },
                                    icon = Icons.Rounded.Clear,
                                    description = stringResource(R.string.cancel_label),
                                )
                            }

                            TransferUiActionButtons.None -> {}
                        }
                    }
                },
                subItemBuilder = { subItemIndex, containerColor, shape ->
                    when (subItemIndex) {
                        0 -> Surface(
                            color = containerColor,
                            shape = shape,
                        ) {
                            Column {
                                // Detailed stats (Speed, Transferred/Total)
                                ListItem(
                                    headlineContent = {
                                        val directionText =
                                            if (uiState.isSending) stringResource(
                                                R.string.outgoing_transfer,
                                            ) else stringResource(
                                                R.string.incoming_transfer,
                                            )
                                        Text(
                                            if (uiState.useCompression) {
                                                stringResource(
                                                    R.string.compressed_label,
                                                    directionText,
                                                )
                                            } else {
                                                directionText
                                            },
                                        )
                                    },
                                    supportingContent = { Text(uiState.statusLongText) },
                                    colors = ListItemDefaults.colors(
                                        containerColor = Color.Transparent,
                                    ),
                                )

                                when (uiState.progressIndicator) {
                                    TransferUiProgressIndicator.Active -> {
                                        LinearWavyProgressIndicator(
                                            progress = { uiState.progressFloat },
                                            amplitude = { 0.5f },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(8.dp, 12.dp),
                                            trackColor = MaterialTheme.colorScheme.surface,
                                        )
                                    }

                                    TransferUiProgressIndicator.Static -> {
                                        // Static progress bar for finished/failed states
                                        LinearProgressIndicator(
                                            progress = { uiState.progressFloat },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(8.dp, 12.dp),
                                            trackColor = MaterialTheme.colorScheme.surface,
                                        )
                                    }

                                    TransferUiProgressIndicator.None -> {}
                                }
                            }
                        }

                        1 -> Surface(
                            color = containerColor,
                            shape = shape,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                            ) {

                                when (uiState.actionButtons) {
                                    TransferUiActionButtons.AcceptAndDecline -> {
                                        Button(
                                            onClick = { onAccept(transfer.uuid) },
                                            modifier = Modifier.weight(1f),
                                        ) {
                                            Icon(
                                                Icons.Rounded.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(
                                                    ButtonDefaults.IconSize,
                                                ),
                                            )
                                            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                                            Text(stringResource(R.string.accept_label))
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Button(
                                            onClick = { onCancel(transfer.uuid) },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.error,
                                                contentColor = MaterialTheme.colorScheme.onError,
                                            ),
                                        ) {
                                            Icon(
                                                Icons.Rounded.Close,
                                                contentDescription = null,
                                                modifier = Modifier.size(
                                                    ButtonDefaults.IconSize,
                                                ),
                                            )
                                            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                                            Text(stringResource(R.string.decline_label))
                                        }
                                    }

                                    TransferUiActionButtons.Stop -> {
                                        Button(
                                            onClick = { onStop(transfer.uuid) },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.secondary,
                                                contentColor = MaterialTheme.colorScheme.onSecondary,
                                            ),
                                        ) {
                                            Icon(
                                                Icons.Rounded.Stop,
                                                contentDescription = null,
                                                modifier = Modifier.size(
                                                    ButtonDefaults.IconSize,
                                                ),
                                            )
                                            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                                            Text(stringResource(R.string.stop_label))
                                        }
                                    }

                                    TransferUiActionButtons.Retry -> {
                                        Button(
                                            onClick = { onRetry(transfer.uuid) },
                                            modifier = Modifier.weight(1f),
                                        ) {
                                            Icon(
                                                Icons.Rounded.Refresh,
                                                contentDescription = null,
                                                modifier = Modifier.size(
                                                    ButtonDefaults.IconSize,
                                                ),
                                            )
                                            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                                            Text(stringResource(R.string.retry_label))
                                        }
                                    }

                                    TransferUiActionButtons.OpenFolder -> {
                                        Button(
                                            onClick = { onItemOpen(transfer.uuid) },
                                            modifier = Modifier.weight(1f),
                                        ) {
                                            Icon(
                                                Icons.Rounded.FolderOpen,
                                                contentDescription = null,
                                                modifier = Modifier.size(
                                                    ButtonDefaults.IconSize,
                                                ),
                                            )
                                            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                                            Text(stringResource(R.string.open_item_label))
                                        }
                                    }

                                    TransferUiActionButtons.Cancel -> {
                                        Button(
                                            onClick = { onCancel(transfer.uuid) },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.secondary,
                                                contentColor = MaterialTheme.colorScheme.onSecondary,
                                            ),
                                        ) {
                                            Icon(
                                                Icons.Rounded.Close,
                                                contentDescription = null,
                                                modifier = Modifier.size(
                                                    ButtonDefaults.IconSize,
                                                ),
                                            )
                                            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                                            Text(stringResource(R.string.cancel_label))
                                        }
                                    }

                                    TransferUiActionButtons.None -> {}
                                }

                            }
                        }
                    }
                },
                subItemCount = if (uiState.actionButtons != TransferUiActionButtons.None) 2 else 1,
                itemIndex = itemIndex,
                listItemCount = itemListCount,
                listItemModifier = Modifier.semantics {
                    customActions = semanticCustomActions
                },
                interactionSource = tileInteractionSource,
            )
        },
    )
}

@Composable
private fun DismissBackground(
    state: SwipeToDismissBoxState,
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

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.error,
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
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onError,
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
@Composable
fun TransferListItemPreview() {
    var expandedId by remember { mutableStateOf<String?>("2") }

    // Incoming Transfer
    val incoming = TransferUi(
        kind = TransferKindUi.Incoming,
        state = TransferState.WaitingPermission,
        singleName = "holiday_photos.zip",
        fileCount = 1,
        totalBytes = 1000 * 1000 * 5,
        entryNames = listOf("holiday_photos.zip"),
    )

    // Outgoing Transfer
    val outgoing = TransferUi(
        kind = TransferKindUi.Outgoing,
        state = TransferState.InProgress,
        fileCount = 12,
        totalBytes = 1000 * 1000 * 100, // 100MB
        bytesTransferred = 1000 * 1000 * 45, // 45MB
        bytesPerSecond = 1000 * 1000 * 30, // 30MB/s
        useCompression = true,
    )

    // Finished Transfer
    val finished = TransferUi(
        kind = TransferKindUi.Outgoing,
        state = TransferState.Completed,
        singleName = "contract.pdf",
        fileCount = 1,
        totalBytes = 1000 * 500, // 500KB
        bytesTransferred = 1000 * 500, // 500KB
        entryNames = listOf("contract.pdf"),
    )

    WarpinatorTheme {
        Scaffold { paddingValues ->
            LazyColumn(
                contentPadding = paddingValues,
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                item {
                    // Incoming (Waiting)
                    TransferListItem(
                        transfer = incoming,
                        expanded = expandedId == "1",
                        onExpandRequest = {
                            expandedId = if (expandedId == "1") null else "1"
                        },
                        itemIndex = 0,
                        itemListCount = 3,
                    )

                }
                item {
                    // Outgoing (Progress)
                    TransferListItem(
                        transfer = outgoing,
                        expanded = expandedId == "2",
                        onExpandRequest = {
                            expandedId = if (expandedId == "2") null else "2"
                        },
                        itemIndex = 1,
                        itemListCount = 3,
                    )
                }
                item {
                    // Finished
                    TransferListItem(
                        transfer = finished,
                        expanded = expandedId == "3",
                        onExpandRequest = {
                            expandedId = if (expandedId == "3") null else "3"
                        },
                        itemIndex = 2,
                        itemListCount = 3,
                    )
                }
            }
        }
    }
}
