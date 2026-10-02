package org.perceivers25.warpinator.feature.home.panes

import android.net.Uri
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DevicesOther
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.PortableWifiOff
import androidx.compose.material.icons.rounded.SignalWifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.perceivers25.warpinator.R
import org.perceivers25.warpinator.RemoteConnectionError
import org.perceivers25.warpinator.RemoteState
import org.perceivers25.warpinator.core.data.ServiceState
import org.perceivers25.warpinator.core.data.WarpinatorViewModel
import org.perceivers25.warpinator.core.design.components.MessagesHandlerEffect
import org.perceivers25.warpinator.core.design.theme.WarpinatorTheme
import org.perceivers25.warpinator.core.model.ui.RemoteUi
import org.perceivers25.warpinator.core.utils.KeyboardShortcuts
import org.perceivers25.warpinator.core.utils.ProfilePicturePainter
import org.perceivers25.warpinator.feature.home.components.HomeMenu
import org.perceivers25.warpinator.feature.home.components.RemoteListItem
import org.perceivers25.warpinator.feature.manual_connection.ManualConnectionDialog

private enum class RemoteListUiState {
    Normal, Empty, Stopped, FailedToStart, NetworkChangeRestart, Restart
}

const val CONNECTION_ISSUES_HELP_URL =
    "https://slowscript.xyz/warpinator-android/connection-issues/"

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalLayoutApi::class,
)
@Composable
fun RemoteListPane(
    paneMode: Boolean,
    onRemoteClick: (RemoteUi) -> Unit,
    onFavoriteToggle: (RemoteUi) -> Unit,
    viewModel: WarpinatorViewModel = hiltViewModel(),
) {
    val currentRemotes by viewModel.remoteListState.collectAsStateWithLifecycle()
    val currentServiceState by viewModel.serviceState.collectAsStateWithLifecycle()
    val currentNetworkState by viewModel.networkState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    MessagesHandlerEffect(
        messageProvider = viewModel.uiMessages,
        snackbarHostState = snackbarHostState,
    )

    // Dialog states
    var showManualConnectionDialog by rememberSaveable { mutableStateOf(false) }

    RemoteListPaneContent(
        remotes = currentRemotes,
        onRemoteClick = onRemoteClick,
        onFavoriteToggle = onFavoriteToggle,
        state = currentServiceState,
        isOnline = currentNetworkState.isOnline,
        paneMode = paneMode,
        onSaveLog = viewModel::saveLog,
        onShowManualConnectionDialog = { showManualConnectionDialog = true },
        onRestart = viewModel::restart,
        snackbarHostState = snackbarHostState,
    )

    if (showManualConnectionDialog) ManualConnectionDialog(
        onDismiss = { showManualConnectionDialog = false },
    )
}

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
)
@Composable
fun RemoteListPaneContent(
    remotes: List<RemoteUi>,
    state: ServiceState,
    isOnline: Boolean = true,
    onRemoteClick: (RemoteUi) -> Unit,
    onFavoriteToggle: (RemoteUi) -> Unit,
    onSaveLog: (uri: Uri) -> Unit = {},
    onShowManualConnectionDialog: () -> Unit = {},
    onRestart: () -> Unit = {},
    paneMode: Boolean = false,
    snackbarHostState: SnackbarHostState? = null,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        canScroll = { !paneMode },
    )

    val filteredRemotes =
        remotes.filter { !(it.state is RemoteState.Error && (it.state as RemoteState.Error).v1 == RemoteConnectionError.GROUP_CODE_MISMATCH) }

    // UIStates
    val uiState: RemoteListUiState = (when {

        state is ServiceState.Stopped -> RemoteListUiState.Stopped
        state is ServiceState.InitializationFailed -> RemoteListUiState.FailedToStart
        state is ServiceState.NetworkChangeRestart -> RemoteListUiState.NetworkChangeRestart
        state is ServiceState.Restart -> RemoteListUiState.Restart
        filteredRemotes.isEmpty() -> RemoteListUiState.Empty
        else -> RemoteListUiState.Normal
    })

    val uriHandler = LocalUriHandler.current

    KeyboardShortcuts { event ->
        when {
            event.isCtrlPressed && event.key == Key.K -> {
                onShowManualConnectionDialog()
                true
            }

            event.key == Key.F1 -> {
                uriHandler.openUri(CONNECTION_ISSUES_HELP_URL)
                true
            }

            else -> false
        }
    }

    Scaffold(
        snackbarHost = { snackbarHostState?.let { SnackbarHost(it) } },
        topBar = {
            if (paneMode) TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    HomeMenu(
                        onManualConnectionClick = onShowManualConnectionDialog,
                        onRestart = onRestart,
                        onSaveLog = onSaveLog,
                    )
                },
                scrollBehavior = scrollBehavior,
            ) else LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    HomeMenu(
                        onManualConnectionClick = onShowManualConnectionDialog,
                        onRestart = onRestart,
                        onSaveLog = onSaveLog,
                    )
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Crossfade(
            targetState = uiState,
            label = "RemoteListPaneContent",
            modifier = Modifier.fillMaxSize(),
        ) { listUiState ->
            LazyColumn(
                contentPadding = padding,
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
            ) {
                if (!isOnline) {
                    item {
                        Card(
                            modifier = Modifier.padding(
                                16.dp, 12.dp,
                            ),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            ),
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Rounded.SignalWifiOff,
                                    contentDescription = null,
                                )
                                Spacer(modifier = Modifier.size(16.dp))
                                Column {
                                    Text(
                                        stringResource(R.string.no_internet_connection_title),
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                    Text(
                                        stringResource(R.string.no_internet_connection_subtitle),
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                }
                            }
                        }
                    }
                }


                if (listUiState != RemoteListUiState.Normal) {
                    item {
                        Column(
                            modifier = Modifier.fillParentMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            when (listUiState) {
                                RemoteListUiState.Empty -> {
                                    Icon(
                                        Icons.Rounded.DevicesOther,
                                        tint = MaterialTheme.colorScheme.primary,
                                        contentDescription = null,
                                        modifier = Modifier.size(150.dp),
                                    )
                                    Text(
                                        stringResource(R.string.no_devices_found),
                                        style = MaterialTheme.typography.titleLarge,
                                        modifier = Modifier.padding(32.dp),
                                    )
                                    Button(
                                        onClick = onShowManualConnectionDialog,
                                    ) {
                                        Text(stringResource(R.string.manual_connection_label))
                                    }
                                }

                                RemoteListUiState.NetworkChangeRestart, RemoteListUiState.Restart -> {
                                    LoadingIndicator(
                                        modifier = Modifier.size(200.dp),
                                        polygons = listOf(
                                            // Some shapes to represent Warpinator, sending files and starting
                                            MaterialShapes.Arrow,
                                            MaterialShapes.Ghostish,
                                            MaterialShapes.Slanted,
                                            MaterialShapes.ClamShell,
                                        ),
                                    )
                                    Text(
                                        stringResource(
                                            R.string.restarting_warpinator_service,
                                        ),
                                        style = MaterialTheme.typography.titleLarge,
                                        modifier = Modifier.padding(
                                            top = 24.dp,
                                            bottom = 8.dp,
                                        ),
                                    )
                                    if (listUiState == RemoteListUiState.NetworkChangeRestart) {
                                        Text(
                                            stringResource(R.string.network_changed_state),
                                            style = MaterialTheme.typography.labelLarge,
                                        )
                                    }
                                }

                                RemoteListUiState.Stopped -> {
                                    Icon(
                                        Icons.Rounded.PortableWifiOff,
                                        tint = MaterialTheme.colorScheme.primary,
                                        contentDescription = null,
                                        modifier = Modifier.size(150.dp),
                                    )
                                    Text(
                                        stringResource(R.string.stopping_warpinator_service),
                                        style = MaterialTheme.typography.titleLarge,
                                        modifier = Modifier.padding(24.dp),
                                    )
                                }

                                RemoteListUiState.FailedToStart -> {
                                    val state =
                                        state as ServiceState.InitializationFailed


                                    Icon(
                                        Icons.Rounded.ErrorOutline,
                                        tint = MaterialTheme.colorScheme.error,
                                        contentDescription = null,
                                        modifier = Modifier.size(150.dp),
                                    )
                                    Text(
                                        stringResource(R.string.failed_to_start_warpinator),
                                        style = MaterialTheme.typography.titleLarge,
                                        modifier = Modifier.padding(
                                            top = 24.dp,
                                            bottom = 8.dp,
                                        ),

                                        )
                                    if (state.interfaces != null) {
                                        Text(
                                            state.interfaces,
                                            style = MaterialTheme.typography.labelLarge,
                                        )
                                    }
                                    Text(
                                        state.exception,
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                }

                                else -> {}
                            }
                        }
                    }
                    return@LazyColumn
                }

                item {
                    Text(
                        stringResource(R.string.available_devices_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(horizontal = 26.dp)
                            .padding(
                                top = 32.dp, bottom = 12.dp,
                            )
                            .semantics {
                                heading()
                            },
                    )
                }

                itemsIndexed(
                    filteredRemotes,
                    key = { index, remote -> remote.uuid },
                ) { index, remote ->
                    Box {
                        RemoteListItem(
                            remote = remote,
                            onFavoriteToggle = { onFavoriteToggle(remote) },
                            onClick = { onRemoteClick(remote) },
                            index = index,
                            itemCount = filteredRemotes.size,
                        )
                    }
                }
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun RemotePanePreview() {
    val context = LocalContext.current

    val remote = RemoteUi(
        uuid = "remote",
        displayName = "User",
        username = "user",
        hostname = "my-other-device",
        ip = "192.168.0.100",
        state = RemoteState.Connected,
        picture = ProfilePicturePainter.getProfilePicture("8", context),
        isFavorite = false,
    )

    val remote2 = RemoteUi(
        uuid = "remote2",
        displayName = "Favy",
        username = "favy",
        hostname = "favys-phone",
        ip = "192.168.0.100",
        state = RemoteState.Connected,
        picture = ProfilePicturePainter.getProfilePicture("1", context),
        isFavorite = true,
    )


    WarpinatorTheme {
        RemoteListPaneContent(
            remotes = listOf(remote, remote2),
            onRemoteClick = {},
            onFavoriteToggle = {},
            state = ServiceState.Ok,
        )
    }
}

@Preview
@Composable
private fun RemotePaneEmptyPreview() {

    WarpinatorTheme {
        RemoteListPaneContent(
            remotes = listOf(),
            onRemoteClick = {},
            onFavoriteToggle = {},
            state = ServiceState.Ok,
        )
    }
}

@Preview
@Composable
private fun RemotePaneStoppingServicePreview() {
    WarpinatorTheme {
        RemoteListPaneContent(
            remotes = listOf(),
            state = ServiceState.Stopped,
            onRemoteClick = {},
            onFavoriteToggle = {},
        )
    }
}

@Preview
@Composable
private fun RemotePaneNetworkChangeRestartPreview() {
    WarpinatorTheme {
        RemoteListPaneContent(
            remotes = listOf(),
            state = ServiceState.NetworkChangeRestart,
            onRemoteClick = {},
            onFavoriteToggle = {},
        )
    }
}

@Preview
@Composable
private fun RemotePaneInitializationFailedPreview() {
    WarpinatorTheme {
        RemoteListPaneContent(
            remotes = listOf(),
            state = ServiceState.InitializationFailed(
                "interfaces",
                "exception",
            ),
            onRemoteClick = {},
            onFavoriteToggle = {},
        )
    }
}

@Preview
@Composable
private fun RemotePaneNotOnlinePreview() {
    WarpinatorTheme {
        RemoteListPaneContent(
            remotes = listOf(),
            isOnline = false,
            onRemoteClick = {},
            onFavoriteToggle = {},
            state = ServiceState.Ok,
        )
    }
}

@Preview
@Composable
private fun RemotePaneNotOnlineContentPreview() {
    val remote = RemoteUi(
        uuid = "remote",
        displayName = "Test Device",
        username = "user",
        hostname = "hostname",
        ip = "192.168.0.100",
        state = RemoteState.Connected,
        picture = null,
        isFavorite = false,
    )


    WarpinatorTheme {
        RemoteListPaneContent(
            remotes = listOf(remote),
            isOnline = false,
            onRemoteClick = {},
            onFavoriteToggle = {},
            state = ServiceState.Ok,
        )
    }
}
