package org.perceivers25.warpinator.feature.home.panes

import android.content.ClipDescription
import android.net.Uri
import android.view.KeyEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Message
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.perceivers25.warpinator.*
import org.perceivers25.warpinator.R
import org.perceivers25.warpinator.core.data.WarpinatorViewModel
import org.perceivers25.warpinator.core.design.components.*
import org.perceivers25.warpinator.core.design.theme.WarpinatorTheme
import org.perceivers25.warpinator.core.model.ui.RemoteUi
import org.perceivers25.warpinator.core.model.ui.TransferKindUi
import org.perceivers25.warpinator.core.model.ui.TransferUi
import org.perceivers25.warpinator.core.notification.components.NotificationInhibitor
import org.perceivers25.warpinator.core.utils.KeyboardShortcuts
import org.perceivers25.warpinator.core.utils.ProfilePicturePainter
import org.perceivers25.warpinator.feature.home.components.RemoteLargeFlexibleTopAppBar
import org.perceivers25.warpinator.feature.home.components.TransferFloatingActionButton
import org.perceivers25.warpinator.feature.home.components.TransferListItem

@Composable
fun TransfersPane(
    remote: RemoteUi,
    paneMode: Boolean,
    onBack: () -> Unit,
    onOpenMessagesPane: () -> Unit,
    onFavoriteToggle: (String) -> Unit,
    viewModel: WarpinatorViewModel = hiltViewModel(),
) {
    val transfers by viewModel.getTransfers(remote.uuid)
        .collectAsStateWithLifecycle(listOf())

    NotificationInhibitor(
        remoteUuid = remote.uuid,
        transfers = true,
    )

    TransferPaneContent(
        remote = remote,
        transfers = transfers,
        paneMode = paneMode,
        onBack = onBack,
        onOpenMessagesPane = onOpenMessagesPane,
        onFavoriteToggle = onFavoriteToggle,
        onAcceptTransfer = { transferUuid ->
            viewModel.acceptTransfer(
                remote.uuid,
                transferUuid,
            )
        },
        onCancelTransfer = { transferUuid ->
            viewModel.cancelTransfer(
                remote.uuid,
                transferUuid,
            )
        },
        onStopTransfer = { transferUuid ->
            viewModel.stopTransfer(
                remote.uuid,
                transferUuid,
            )
        },
        onRetryTransfer = { transferUuid ->
            viewModel.retryTransfer(
                remote.uuid,
                transferUuid,
            )
        },
        onItemOpen = {},
        onSendTransferRequest = { uris ->
            viewModel.sendTransferRequest(
                remote.uuid,
                uris,
            )
        },
        removeTransfer = { transferUuid ->
            viewModel.removeTransfer(
                remote.uuid,
                transferUuid,
            )
        },
        onRemoveFinalStateTransfers = { remoteUuid ->
            viewModel.removeFinalStateTransfers(remoteUuid)
        },
        onReconnect = { viewModel.connectRemote(remote.uuid) },
        onMarkAsRead = { viewModel.markTransfersSeen(remote.uuid) },
    )
}

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
)
@Composable
private fun TransferPaneContent(
    remote: RemoteUi,
    transfers: List<TransferUi>,
    paneMode: Boolean,
    onBack: () -> Unit = {},
    onOpenMessagesPane: () -> Unit = {},
    onFavoriteToggle: (String) -> Unit = {},
    onSendTransferRequest: (List<Uri>) -> Unit = { _: List<Uri> -> },
    onAcceptTransfer: (String) -> Unit = {},
    onCancelTransfer: (String) -> Unit = {},
    onStopTransfer: (String) -> Unit = {},
    onRetryTransfer: (String) -> Unit = {},
    onItemOpen: (String) -> Unit = {},
    removeTransfer: (String) -> Unit = {},
    onRemoveFinalStateTransfers: (String) -> Unit = {},
    onReconnect: () -> Unit = {},
    onMarkAsRead: () -> Unit = {},
) {
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) onSendTransferRequest(uris)
    }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            onSendTransferRequest(listOf(uri))
        }

    }

    val fileDropTargetState = rememberDropTargetState(
        onUrisDropped = { uris ->
            if (uris.isEmpty()) return@rememberDropTargetState false

            onSendTransferRequest(uris)
            true
        },
        shouldStartDragAndDrop = shouldStartDragAndDrop@{ event ->
            val description = event.toAndroidDragEvent().clipDescription
                ?: return@shouldStartDragAndDrop false
            (0 until description.mimeTypeCount).any { mimeType ->
                description.getMimeType(mimeType) !in setOf(
                    ClipDescription.MIMETYPE_TEXT_PLAIN,
                    ClipDescription.MIMETYPE_TEXT_HTML,
                    ClipDescription.MIMETYPE_TEXT_INTENT,
                )
            }
        },
    )

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    LaunchedEffect(transfers.size) {
        onMarkAsRead()
    }

    KeyboardShortcuts { event ->
        when {
            event.isCtrlPressed && event.key == Key.One || event.isCtrlPressed && event.key == Key.O -> {
                filePicker.launch(arrayOf("*/*"))
                true
            }

            event.isCtrlPressed && event.key == Key.Two || event.isCtrlPressed && event.isShiftPressed && event.key == Key.O -> {
                folderPicker.launch(null)
                true
            }

            event.isCtrlPressed && event.key == Key.Three || event.isCtrlPressed && event.key == Key.M -> {
                onOpenMessagesPane()

                true
            }

            event.isCtrlPressed && event.key == Key.D -> {
                onFavoriteToggle(remote.uuid)
                true
            }

            event.isCtrlPressed && event.isShiftPressed && event.key == Key.R -> {
                onReconnect()
                true
            }

            event.isCtrlPressed && event.isShiftPressed && event.key == Key.Delete -> {
                onRemoveFinalStateTransfers(remote.uuid)
                true
            }

            else -> false
        }
    }

    Scaffold(
        topBar = {
            RemoteLargeFlexibleTopAppBar(
                remote = remote,
                navigationIcon = {
                    if (!paneMode) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.back_button_label),
                            )
                        }
                    }
                },
                actions = {
                    TooltipIconButton(
                        onClick = {
                            onRemoveFinalStateTransfers(remote.uuid)
                        },
                        icon = Icons.Rounded.ClearAll,
                        description = rememberShortcutLabelText(
                            KeyEvent.KEYCODE_DEL, ctrl = true, shift = true,
                            text = stringResource(R.string.clear_transfer_history_label),
                        ),
                    )

                    val favouriteButtonSemanticState =
                        if (remote.isFavorite) stringResource(R.string.favorite_label) else stringResource(
                            R.string.not_favorite_label,
                        )

                    TooltipIconButton(
                        onClick = { onFavoriteToggle(remote.uuid) },
                        icon = if (remote.isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        description = rememberShortcutLabelText(
                            KeyEvent.KEYCODE_D, ctrl = true,
                            text = if (remote.isFavorite) stringResource(R.string.remove_from_favorites_label) else stringResource(
                                R.string.add_to_favorites_label,
                            ),
                        ),
                        modifier = Modifier.semantics {
                            stateDescription = favouriteButtonSemanticState

                            toggleableState =
                                if (remote.isFavorite) ToggleableState.On else ToggleableState.Off

                        },
                    )

                    TooltipIconButton(
                        onClick = onOpenMessagesPane,
                        icon = Icons.AutoMirrored.Rounded.Message,
                        description = rememberShortcutLabelText(
                            keyCode = KeyEvent.KEYCODE_M, ctrl = true,
                            text = stringResource(R.string.messages),
                        ),
                        enabled = remote.messageSupport,
                        addBadge = remote.unreadMessages,
                    )
                },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            if (remote.state == RemoteState.Connected) {
                TransferFloatingActionButton(
                    onSendFile = { filePicker.launch(arrayOf("*/*")) },
                    onSendFolder = { folderPicker.launch(null) },
                    onSendMessage = onOpenMessagesPane,
                )
            }
        },
    ) { padding ->
        var expandedTransferID by rememberSaveable {
            mutableStateOf<String?>(
                null,
            )
        }

        val listContentDescription =
            stringResource(R.string.transfers_history_list_content_description)

        LazyColumn(
            contentPadding = padding.plus(
                PaddingValues(
                    bottom = 100.dp, start = 16.dp, end = 16.dp,
                ),
            ),
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .semantics {
                    contentDescription = listContentDescription
                }
                .fileDropTarget(fileDropTargetState),
        ) {

            item {
                ConnectionStatusCard(
                    remote.state,
                    transfers.size,
                ) { onReconnect() }
            }

            if (fileDropTargetState.uiMode != DragAndDropUiMode.None) {
                item {
                    FileDropTargetIndicator(
                        fileDropTargetState.uiMode,
                        text = stringResource(R.string.drop_here_to_send),
                        modifier = Modifier.fillParentMaxSize(),
                    )
                }
                return@LazyColumn
            }

            if (transfers.isEmpty()) {
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .height(400.dp)
                            .fillMaxWidth(),
                    ) {
                        Icon(
                            Icons.Rounded.Inbox,
                            tint = MaterialTheme.colorScheme.primary,
                            contentDescription = null,
                            modifier = Modifier.size(100.dp),
                        )
                        Text(
                            stringResource(R.string.no_transfers_yet),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }

            itemsIndexed(
                transfers,
                key = { _, transfer -> transfer.uuid },
            ) { index, transfer ->
                val expanded = transfer.uuid == expandedTransferID
                TransferListItem(
                    transfer = transfer,
                    expanded = expanded,
                    onExpandRequest = {
                        expandedTransferID =
                            if (expanded) null else transfer.uuid
                    },
                    onAccept = onAcceptTransfer,
                    onCancel = onCancelTransfer,
                    onStop = onStopTransfer,
                    onRetry = onRetryTransfer,
                    onItemOpen = onItemOpen,
                    onClear = removeTransfer,
                    itemIndex = index,
                    itemListCount = transfers.size,
                )

                Spacer(modifier = Modifier.height(ListItemDefaults.SegmentedGap))
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun ConnectionStatusCard(
    state: RemoteState, transfersCount: Int,
    onReconnect: () -> Unit,
) {
    Card(
        modifier = Modifier
            .padding(vertical = 16.dp)
            .fillMaxWidth()
            .semantics(true) {
                liveRegion = LiveRegionMode.Polite
            },
        colors = if (state is RemoteState.Error) CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ) else CardDefaults.cardColors(
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 8.dp),
        ) {
            // TODO(raresvanca): look at putting shape backgrounds for the status icons
            when (state) {
                RemoteState.AwaitingDuplex, RemoteState.Connecting -> {
                    LoadingIndicator(modifier = Modifier.padding(horizontal = 6.dp))
                }

                RemoteState.Connected -> {
                    Icon(
                        Icons.Rounded.SyncAlt,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .size(28.dp),
                    )
                }

                RemoteState.Disconnected -> {
                    Icon(
                        Icons.Rounded.SyncAlt,
                        null,
                        tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f),
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .size(28.dp),
                    )
                }

                is RemoteState.Error -> {
                    Icon(
                        Icons.Rounded.Error,
                        null,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .size(28.dp),
                    )
                }
            }


            Column {
                Text(
                    when (state) {
                        RemoteState.AwaitingDuplex -> stringResource(R.string.remote_awaiting_duplex)
                        RemoteState.Connecting -> stringResource(R.string.remote_connecting)
                        RemoteState.Connected -> stringResource(R.string.remote_connected)
                        RemoteState.Disconnected -> stringResource(R.string.remote_disconnected)
                        is RemoteState.Error -> stringResource(
                            R.string.remote_failed_to_connect,
                            state.v1.toString(),
                        )
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    pluralStringResource(
                        R.plurals.transfers_count,
                        transfersCount,
                        transfersCount,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = LocalContentColor.current.copy(alpha = 0.8f),
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            if (state == RemoteState.Disconnected || state is RemoteState.Error) {
                FilledTonalButton(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    colors = if (state is RemoteState.Error) ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ) else ButtonDefaults.filledTonalButtonColors(),
                    onClick = {
                        onReconnect()
                    },
                ) {
                    Text(stringResource(R.string.reconnect_button_label))
                }
            }
        }
    }
}

@Preview
@Composable
private fun TransfersPanePreview() {
    // Transfers covering different states and directions
    val transfers = listOf(
        TransferUi(
            uuid = "t1",
            kind = TransferKindUi.Outgoing,
            state = TransferState.InProgress,
            totalBytes = 100 * 1000 * 1000, // 100 MB
            bytesTransferred = 45 * 1000 * 1000, // 45 MB
            singleName = "sending_video.mp4",
            fileCount = 1,
        ),
        TransferUi(
            uuid = "t2",
            kind = TransferKindUi.Incoming,
            state = TransferState.InProgress,
            totalBytes = 50 * 1000 * 1000,
            bytesTransferred = 10 * 1000 * 1000,
            singleName = "receiving_document.pdf",
            fileCount = 1,
        ),
        TransferUi(
            uuid = "t3",
            kind = TransferKindUi.Incoming,
            state = TransferState.WaitingPermission,
            totalBytes = 2 * 1024 * 1024,
            singleName = "incoming_request.zip",
            fileCount = 1,
        ),
        TransferUi(
            uuid = "t4",
            kind = TransferKindUi.Outgoing,
            state = TransferState.Completed,
            totalBytes = 5 * 1024 * 1024,
            bytesTransferred = 5 * 1024 * 1024,
            singleName = "sent_image.jpg",
            fileCount = 1,
        ),
        TransferUi(
            uuid = "t5",
            kind = TransferKindUi.Incoming,
            state = TransferState.Completed,
            totalBytes = 3 * 1024 * 1024,
            bytesTransferred = 3 * 1024 * 1024,
            singleName = "received_song.mp3",
            fileCount = 1,
        ),
        TransferUi(
            uuid = "t6",
            kind = TransferKindUi.Incoming,
            state = TransferState.Denied,
            totalBytes = 1024,
            singleName = "declined_file.exe",
            fileCount = 1,
        ),
        TransferUi(
            uuid = "t7",
            kind = TransferKindUi.Outgoing,
            state = TransferState.Failed(TransferError.FAILED_TO_START_TRANSFER),
            totalBytes = 10 * 1024 * 1024,
            bytesTransferred = 1 * 1024 * 1024,
            singleName = "failed_upload.iso",
            fileCount = 1,
        ),
        TransferUi(
            uuid = "t8",
            kind = TransferKindUi.Incoming,
            state = TransferState.Failed(TransferError.STORAGE_FULL),
            totalBytes = 500L * 1000 * 1000 * 1000,
            singleName = "big_file.bin",
            fileCount = 1,
        ),
        TransferUi(
            uuid = "t9",
            kind = TransferKindUi.Outgoing,
            state = TransferState.Paused,
            totalBytes = 200 * 1024 * 1024,
            bytesTransferred = 100 * 1024 * 1024,
            singleName = "paused_backup.tar.gz",
            fileCount = 1,
        ),
        TransferUi(
            uuid = "t10",
            kind = TransferKindUi.Incoming,
            state = TransferState.Stopped,
            totalBytes = 15 * 1024 * 1024,
            bytesTransferred = 2 * 1024 * 1024,
            singleName = "stopped_download.apk",
            fileCount = 1,
        ),
        TransferUi(
            uuid = "t11",
            kind = TransferKindUi.Outgoing,
            state = TransferState.Failed(
                TransferError.FILES_NOT_FOUND,
            ),
            totalBytes = 0,
            singleName = "missing_file.txt",
            fileCount = 1,
        ),
        TransferUi(
            uuid = "t12",
            kind = TransferKindUi.Outgoing,
            state = TransferState.Initializing,
            totalBytes = 0,
            singleName = "initializing_folder",
            fileCount = 5,
        ),
    )

    val remote = RemoteUi(
        uuid = "remote2",
        displayName = "Favy",
        username = "favy",
        hostname = "favys-phone",
        ip = "192.168.0.100",
        state = RemoteState.Connected,
        picture = ProfilePicturePainter.getProfilePicture(
            "1",
            LocalContext.current,
        ),
        isFavorite = true,
    )

    WarpinatorTheme {
        TransferPaneContent(
            remote = remote,
            transfers = transfers,
            paneMode = false,
        )
    }
}

@Preview
@Composable
private fun TransfersPaneEmptyPreview() {
    val remote = RemoteUi(
        displayName = "Test Device",
        username = "user",
        hostname = "hostname",
    )

    WarpinatorTheme {
        TransferPaneContent(
            remote = remote,
            transfers = emptyList(),
            paneMode = false,
        )
    }
}

@PreviewLightDark
@Composable
private fun ConnectionStatusCardPreview() {
    WarpinatorTheme {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ConnectionStatusCard(
                state = RemoteState.Connecting, transfersCount = 3,
            ) {}
            ConnectionStatusCard(
                state = RemoteState.Connected, transfersCount = 5,
            ) {}
            ConnectionStatusCard(
                state = RemoteState.Disconnected, transfersCount = 5,
            ) {}
            ConnectionStatusCard(
                state = RemoteState.Error(RemoteConnectionError.DUPLEX_ERROR),
                transfersCount = 2,
            ) {}
        }
    }
}
