package org.perceivers25.warpinator.feature.share

import android.content.Context
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.perceivers25.warpinator.R
import org.perceivers25.warpinator.RemoteState
import org.perceivers25.warpinator.core.data.WarpinatorViewModel
import org.perceivers25.warpinator.core.design.components.DynamicAvatarCircle
import org.perceivers25.warpinator.core.design.components.TooltipIconButton
import org.perceivers25.warpinator.core.design.shapes.segmentedDynamicShapes
import org.perceivers25.warpinator.core.design.shapes.segmentedHorizontalDynamicShapes
import org.perceivers25.warpinator.core.design.theme.WarpinatorTheme
import org.perceivers25.warpinator.core.model.ui.RemoteUi
import org.perceivers25.warpinator.core.utils.RemoteDisplayInfo
import org.perceivers25.warpinator.core.utils.Utils
import org.perceivers25.warpinator.feature.manual_connection.ManualConnectionDialog
import org.perceivers25.warpinator.feature.share.components.ShareMenu

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareDialog(
    onDismiss: () -> Unit,
    uris: List<Uri>,
    text: String?,
    viewModel: WarpinatorViewModel = hiltViewModel(),
    onOpenRemote: (String, Boolean) -> Unit,
) {
    val remotes = viewModel.remoteListState.collectAsStateWithLifecycle()

    var showManualConnectionDialog by remember { mutableStateOf(false) }

    val onSendUris = { remoteUuid: String, uris: List<Uri> ->
        // viewModel.sendUris(remoteUuid, uris, false)
        onOpenRemote(remoteUuid, false)
        onDismiss()
    }

    val onSendText = { remoteUuid: String, text: String ->
        viewModel.sendMessage(remoteUuid, text)
        onOpenRemote(remoteUuid, true)
        onDismiss()
    }

    val onShowManualConnectionDialog = {
        showManualConnectionDialog = true
    }

    BoxWithConstraints {
        if (maxWidth < 600.dp) {
            ShareDialogFullscreenWrapper(
                onDismiss = onDismiss,
                remotes = remotes.value,
                uris = uris,
                text = text,
                onShowManualConnectionDialog = onShowManualConnectionDialog,
            )
        } else {
            ShareDialogFloatingWrapper(
                onDismiss = onDismiss,
                remotes = remotes.value,
                uris = uris,
                text = text,
                onSendUris = onSendUris,
                onSendText = onSendText,
                onShowManualConnectionDialog = onShowManualConnectionDialog,
            )
        }
    }

    if (showManualConnectionDialog) ManualConnectionDialog(
        onDismiss = { showManualConnectionDialog = false },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShareDialogFullscreenWrapper(
    onDismiss: () -> Unit,
    remotes: List<RemoteUi>,
    uris: List<Uri>,
    text: String?,
    onSendUris: (String, List<Uri>) -> Unit = { _: String, _: List<Uri> -> },
    onSendText: (String, String) -> Unit = { _: String, _: String -> },
    onShowManualConnectionDialog: () -> Unit = {},
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false, decorFitsSystemWindows = false,
        ),
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                    title = {
                        Text(
                            stringResource(R.string.share_with_title),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = stringResource(android.R.string.cancel),
                            )
                        }
                    },
                )
            },
        ) { innerPadding ->
            ShareDialogContent(
                innerPadding = innerPadding + PaddingValues(horizontal = 16.dp),
                remotes = remotes,
                uris = uris,
                text = text,
                onSendUris = onSendUris,
                onSendText = onSendText,
                onShowManualConnectionDialog = onShowManualConnectionDialog,
            )
        }
    }
}

@Composable
private fun ShareDialogFloatingWrapper(
    onDismiss: () -> Unit,
    remotes: List<RemoteUi>,
    uris: List<Uri>,
    text: String?,
    onSendUris: (String, List<Uri>) -> Unit = { _: String, _: List<Uri> -> },
    onSendText: (String, String) -> Unit = { _: String, _: String -> },
    onShowManualConnectionDialog: () -> Unit = {},
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.share_with_title)) },
        confirmButton = { TextButton(onDismiss) { Text(stringResource(android.R.string.cancel)) } },
        icon = { Icon(Icons.Rounded.Share, contentDescription = null) },
        text = {
            ShareDialogContent(
                innerPadding = PaddingValues(),
                remotes = remotes,
                uris = uris,
                text = text,
                onSendUris = onSendUris,
                onSendText = onSendText,
                onShowManualConnectionDialog = onShowManualConnectionDialog,
            )
        },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ShareDialogContent(
    innerPadding: PaddingValues,
    remotes: List<RemoteUi>,
    uris: List<Uri>,
    text: String?,
    onSendUris: (String, List<Uri>) -> Unit,
    onSendText: (String, String) -> Unit,
    onShowManualConnectionDialog: () -> Unit,
) {
    var editedText by rememberSaveable { mutableStateOf(text) }
    var isEditing by remember { mutableStateOf(false) }
    val textMode = uris.isEmpty() && text != null

    var listTileHeight by remember { mutableStateOf(1.dp) }
    val density = LocalDensity.current
    val context = LocalContext.current

    val filteredRemotes = /*if (textMode) {
        remotes.filter { it.supportsTextMessages && it.status == RemoteState.Connected }
    } else*/ //{
        remotes.filter { it.state == RemoteState.Connected }
    // }

    val supportingContent =
        if (textMode) stringResource(R.string.tap_to_edit_hint) else rememberFormattedFileNames(
            uris,
            context,
        )

    LazyColumn(
        contentPadding = innerPadding,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
            ) {
                AnimatedContent(
                    targetState = isEditing,
                    modifier = Modifier.weight(1f),
                    label = "TextEditAnimation",
                    transitionSpec = {
                        fadeIn() togetherWith fadeOut() using SizeTransform()
                    },
                ) { editing ->

                    if (editing) {
                        val focusRequester = remember { FocusRequester() }
                        OutlinedTextField(
                            value = editedText.orEmpty(),
                            onValueChange = { editedText = it },
                            suffix = {
                                TooltipIconButton(
                                    onClick = { isEditing = false },
                                    icon = Icons.Rounded.Check,
                                    description = stringResource(R.string.confirm_edit_label),
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                unfocusedSuffixColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                focusedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                focusedTextColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                focusedSuffixColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                unfocusedBorderColor = MaterialTheme.colorScheme.secondaryContainer,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                            ),
                            shape = MaterialTheme.shapes.large,
                            modifier = Modifier
                                .focusRequester(focusRequester)
                                .fillMaxWidth(),
                        )

                        LaunchedEffect(Unit) {
                            focusRequester.requestFocus()
                        }
                    } else SegmentedListItem(
                        onClick = {
                            isEditing = true
                        },
                        enabled = textMode,
                        content = {
                            Text(
                                if (textMode) editedText.orEmpty() else pluralStringResource(
                                    R.plurals.files_selected,
                                    uris.size,
                                    uris.size,
                                ),
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        supportingContent = supportingContent?.let { text ->
                            {
                                Text(
                                    text = text,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        },
                        shapes = ListItemDefaults.segmentedHorizontalDynamicShapes(
                            index = 0,
                            count = 2,
                        ),
                        colors = ListItemDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            supportingContentColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(
                                alpha = 0.7f,
                            ),
                            disabledContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            disabledContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            disabledSupportingContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                        modifier = Modifier.onSizeChanged {
                            listTileHeight = with(density) { it.height.toDp() }
                        },
                    )
                }
                AnimatedVisibility(
                    visible = !isEditing,
                    enter = expandHorizontally(expandFrom = Alignment.Start) + fadeIn(),
                    exit = shrinkHorizontally(shrinkTowards = Alignment.Start) + fadeOut(),
                ) {
                    ShareMenu(
                        size = listTileHeight,
                        onManualConnectionClick = onShowManualConnectionDialog,
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }


        if (filteredRemotes.isEmpty()) {
            item {
                Icon(
                    Icons.Rounded.DevicesOther,
                    tint = MaterialTheme.colorScheme.primary,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                )
                Text(
                    stringResource(R.string.no_devices_found),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(12.dp),
                )
            }
        } else items(filteredRemotes.size) { index ->
            val remote = filteredRemotes[index]
            val isFavorite = remote.isFavorite

            val displayInfo =
                remember(remote) { RemoteDisplayInfo.fromRemote(remote) }

            SegmentedListItem(
                onClick = {
                    if (textMode) {
                        onSendText(remote.uuid, editedText!!)
                    } else {
                        onSendUris(remote.uuid, uris)
                    }
                },
                shapes = ListItemDefaults.segmentedDynamicShapes(
                    index = index,
                    count = filteredRemotes.size,
                ),
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                content = {
                    Text(displayInfo.title)
                },
                supportingContent = { Text(displayInfo.subtitle) },
                leadingContent = {
                    DynamicAvatarCircle(
                        // bitmap = remote.picture,
                        isFavorite = isFavorite,
                    )
                },
                trailingContent = {
                    Icon(Icons.Rounded.ChevronRight, contentDescription = null)
                },
                modifier = Modifier.padding(bottom = ListItemDefaults.SegmentedGap),
            )
        }
    }
}

@Composable
fun rememberFormattedFileNames(uris: List<Uri>, context: Context): String? {
    return remember(uris) {
        val validNames = uris.mapNotNull { uri ->
            Utils.getNameFromUri(context, uri)
        }

        when (val count = validNames.size) {
            0 -> null
            1 -> validNames[0]
            2 -> "${validNames[0]}, ${validNames[1]}"
            else -> {
                val firstTwo = validNames.take(2).joinToString(", ")
                val remainingCount = count - 2
                context.resources.getQuantityString(
                    R.plurals.and_more,
                    remainingCount,
                    firstTwo,
                    remainingCount,
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "Floating - Files")
@Composable
fun PreviewFloatingFiles() {
    WarpinatorTheme {
        ShareDialogFloatingWrapper(
            onDismiss = {},
            remotes = previewRemotes,
            uris = listOf(Uri.EMPTY, Uri.EMPTY),
            text = null,
        )
    }

}

@Preview(showBackground = true, name = "Floating - Text")
@Composable
fun PreviewFloatingText() {
    WarpinatorTheme {
        ShareDialogFloatingWrapper(
            onDismiss = {},
            remotes = previewRemotes,
            uris = emptyList(),
            text = "Hello, this is a shared text snippet!",
        )
    }

}

@Preview(name = "Fullscreen - Files")
@Composable
fun PreviewFullscreenFiles() {
    WarpinatorTheme {
        ShareDialogFullscreenWrapper(
            onDismiss = {},
            remotes = previewRemotes,
            uris = listOf(Uri.EMPTY, Uri.EMPTY),
            text = null,
        )
    }

}

private val previewRemotes = listOf(
    RemoteUi(
        uuid = "remote",
        displayName = "Test Device 1",
        username = "user",
        hostname = "hostname",
        state = RemoteState.Connected,
        picture = null,
        ip = "",
        isFavorite = true,
    ),
    RemoteUi(
        uuid = "remote",
        displayName = "Test Device 2",
        username = "user",
        hostname = "hostname",
        state = RemoteState.Connected,
        picture = null,
        ip = "",
        isFavorite = false,
    ),
    RemoteUi(
        uuid = "remote",
        displayName = "Test Device 3",
        username = "user",
        hostname = "hostname",
        state = RemoteState.Connected,
        picture = null,
        ip = "",
        isFavorite = true,
    ),
)
