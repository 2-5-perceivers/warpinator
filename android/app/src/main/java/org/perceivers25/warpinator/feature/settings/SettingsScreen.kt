package org.perceivers25.warpinator.feature.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.perceivers25.warpinator.R
import org.perceivers25.warpinator.app.LocalNavigator
import org.perceivers25.warpinator.core.design.components.DynamicAvatarCircle
import org.perceivers25.warpinator.core.design.components.MessagesHandlerEffect
import org.perceivers25.warpinator.core.design.shapes.segmentedDynamicShapes
import org.perceivers25.warpinator.core.design.theme.WarpinatorTheme
import org.perceivers25.warpinator.core.model.preferences.ThemeOptions
import org.perceivers25.warpinator.core.system.AutoAcceptValue
import org.perceivers25.warpinator.core.system.PreferenceManager
import org.perceivers25.warpinator.core.utils.ProfilePicturePainter
import org.perceivers25.warpinator.feature.settings.components.*
import org.perceivers25.warpinator.feature.settings.state.SettingsUiState
import org.perceivers25.warpinator.feature.settings.state.SettingsViewModel

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    launchDirPicker: Boolean = false,
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val navigator = LocalNavigator.current

    val snackbarHostState = remember { SnackbarHostState() }

    MessagesHandlerEffect(
        messageProvider = viewModel.uiMessages,
        snackbarHostState = snackbarHostState,
    )

    val dirPickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
                viewModel.setDirectory(uri)
            }
        }

    // Auto-launch picker if requested via Intent
    LaunchedEffect(Unit) {
        if (launchDirPicker) dirPickerLauncher.launch(null)
    }

    SettingsScreenContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onBackClick = { navigator?.goBack() },
        onDisplayNameChange = viewModel::setDisplayName,
        onProfilePictureChange = viewModel::setProfilePicture,
        onPickCustomProfilePicture = viewModel::handleCustomProfilePicture,
        onPickDownloadDir = { dirPickerLauncher.launch(null) },
        onResetDownloadDir = viewModel::resetDirectory,
        onNotifyIncomingChange = viewModel::setNotifyIncoming,
        onAutoAcceptChange = viewModel::setAutoAccept,
        onUseCompressionChange = viewModel::setUseCompression,
        onAutoStopChange = viewModel::setAutoStop,
        onDebugLogChange = viewModel::setDebugLog,
        onGroupCodeChange = viewModel::setGroupCode,
        onServerPortChange = viewModel::setServerPort,
        onAuthPortChange = viewModel::setAuthPort,
        onNetworkInterfaceChange = viewModel::setNetworkInterface,
        onThemeChange = viewModel::updateTheme,
        onUseDynamicColorsChange = viewModel::setUseDynamicColors,
    )
}

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
)
@Composable
fun SettingsScreenContent(
    state: SettingsUiState,
    snackbarHostState: SnackbarHostState,
    onBackClick: () -> Unit,
    onDisplayNameChange: (String) -> Unit,
    onProfilePictureChange: (String) -> Unit,
    onPickCustomProfilePicture: (Uri) -> Unit,
    onPickDownloadDir: () -> Unit,
    onResetDownloadDir: () -> Unit,
    onNotifyIncomingChange: (Boolean) -> Unit,
    onAutoAcceptChange: (AutoAcceptValue) -> Unit,
    onUseCompressionChange: (Boolean) -> Unit,
    onAutoStopChange: (Boolean) -> Unit,
    onDebugLogChange: (Boolean) -> Unit,
    onGroupCodeChange: (String) -> Unit,
    onServerPortChange: (String) -> Unit,
    onAuthPortChange: (String) -> Unit,
    onNetworkInterfaceChange: (String) -> Unit,
    onThemeChange: (ThemeOptions) -> Unit,
    onUseDynamicColorsChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var showProfileDialog by remember { mutableStateOf(false) }
    var editDialogTitle by remember { mutableStateOf<Int?>(null) }
    var editDialogValue by remember { mutableStateOf("") }
    var editDialogIsNumber by remember { mutableStateOf(false) }
    var onEditConfirm by remember { mutableStateOf<(String) -> Unit>({}) }
    var showEditDialog by remember { mutableStateOf(false) }

    var showThemeDialog by remember { mutableStateOf(false) }
    var showInterfaceDialog by remember { mutableStateOf(false) }

    val editSemanticModifierBase = Modifier.semantics {
        onClick("Edit", null)
    }

    fun openEdit(
        titleRes: Int,
        currentValue: String,
        isNumber: Boolean = false,
        onConfirm: (String) -> Unit,
    ) {
        editDialogTitle = titleRes
        editDialogValue = currentValue
        editDialogIsNumber = isNumber
        onEditConfirm = onConfirm
        showEditDialog = true
    }

    val listItemColors = ListItemDefaults.segmentedColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
    )
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },

                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                scrollBehavior = scrollBehavior,

                )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxSize(),
            contentPadding = innerPadding,
        ) {
            item {
                SettingsCategoryLabel(stringResource(R.string.identity_settings_category))

                SegmentedListItem(
                    content = { Text(stringResource(R.string.display_settings_title)) },
                    supportingContent = { Text(state.displayName) },
                    onClick = {
                        openEdit(
                            R.string.display_settings_title, state.displayName,
                        ) { onDisplayNameChange(it) }
                    },
                    shapes = ListItemDefaults.segmentedDynamicShapes(0, 3),
                    colors = listItemColors,
                    modifier = editSemanticModifierBase.padding(bottom = ListItemDefaults.SegmentedGap),
                )
                val profilePictureBitmap = remember(
                    state.profilePictureKey, state.profileImageSignature,
                ) {
                    ProfilePicturePainter.getProfilePicture(
                        state.profilePictureKey,
                        context,
                    )
                }
                SegmentedListItem(
                    content = { Text(stringResource(R.string.picture_settings_title)) },
                    trailingContent = {
                        DynamicAvatarCircle(
                            size = 32.dp,
                            bitmap = profilePictureBitmap,
                        )
                    },
                    onClick = { showProfileDialog = true },
                    shapes = ListItemDefaults.segmentedDynamicShapes(2, 3),
                    colors = listItemColors,
                    modifier = editSemanticModifierBase,
                )
            }

            item {
                SettingsCategoryLabel(stringResource(R.string.transfer_settings_category))

                SegmentedListItem(
                    content = { Text(stringResource(R.string.download_dir_settings_title)) },
                    supportingContent = { Text(state.downloadDirSummary) },
                    onClick = { onPickDownloadDir() },
                    trailingContent = {
                        AnimatedVisibility(
                            visible = state.canResetDir,
                            enter = fadeIn(),
                            exit = fadeOut(),
                        ) {
                            IconButton(onClick = onResetDownloadDir) {
                                Icon(
                                    Icons.Default.Restore,
                                    contentDescription = "Reset to default",
                                )
                            }
                        }
                    },
                    shapes = ListItemDefaults.segmentedDynamicShapes(0, 3),
                    colors = listItemColors,
                    modifier = editSemanticModifierBase.padding(bottom = ListItemDefaults.SegmentedGap),
                )

                SwitchListItem(
                    title = stringResource(R.string.notification_settings_title),
                    summary = stringResource(R.string.notification_settings_summary),
                    checked = state.notifyIncoming,
                    onCheckedChange = onNotifyIncomingChange,
                    shapes = ListItemDefaults.segmentedDynamicShapes(1, 3),
                    colors = listItemColors,
                )

                SegmentedListItem(
                    onClick = { onAutoAcceptChange(AutoAcceptValue.entries[(state.autoAccept.ordinal + 1) % AutoAcceptValue.entries.size]) },
                    content = { Text("Automatically accept transfers from") },
                    supportingContent = {
                        val acceptOptions = AutoAcceptValue.entries.map {
                            when (it) {
                                AutoAcceptValue.Nobody -> "Nobody"
                                AutoAcceptValue.Favourites -> "Favourites"
                                AutoAcceptValue.Everyone -> "Everyone"
                            }
                        }

                        Row(
                            modifier = Modifier.padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(
                                ButtonGroupDefaults.ConnectedSpaceBetween,
                                alignment = Alignment.CenterHorizontally,
                            ),
                        ) {

                            acceptOptions.forEachIndexed { index, label ->
                                ToggleButton(
                                    checked = state.autoAccept.ordinal == index,
                                    onCheckedChange = {
                                        onAutoAcceptChange(AutoAcceptValue.entries[index])
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .widthIn(max = 50.dp)
                                        .semantics { role = Role.RadioButton },
                                    shapes = when (index) {
                                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                        acceptOptions.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                                    },
                                ) {
                                    Text(label)
                                }
                            }
                        }
                    },
                    shapes = ListItemDefaults.segmentedDynamicShapes(1, 3),
                    colors = listItemColors,
                    modifier = Modifier.padding(bottom = ListItemDefaults.SegmentedGap),
                )

                SwitchListItem(
                    title = stringResource(R.string.compression_settings_title),
                    checked = state.useCompression,
                    onCheckedChange = onUseCompressionChange,
                    shapes = ListItemDefaults.segmentedDynamicShapes(2, 3),
                    colors = listItemColors,
                )
            }

            item {
                SettingsCategoryLabel(stringResource(R.string.app_behavior_settings_category))

                SwitchListItem(
                    title = stringResource(R.string.stop_service_when_leaving_title),
                    summary = if (state.autoStop) stringResource(R.string.stop_service_when_leaving_summary_on)
                    else stringResource(R.string.stop_service_when_leaving_summary_off),
                    checked = state.autoStop,
                    onCheckedChange = onAutoStopChange,
                    shapes = ListItemDefaults.segmentedDynamicShapes(0, 2),
                    colors = listItemColors,
                )

                SwitchListItem(
                    title = stringResource(R.string.export_log_settings_title),
                    summary = "Android/data/org.perceivers25.warpinator/files/", // Hardcoded per XML
                    checked = state.debugLog,
                    onCheckedChange = onDebugLogChange,
                    shapes = ListItemDefaults.segmentedDynamicShapes(1, 2),
                    colors = listItemColors,
                )

            }

            item {
                SettingsCategoryLabel(stringResource(R.string.network_settings_category))

                SegmentedListItem(
                    content = { Text(stringResource(R.string.group_code_settings_title)) },
                    supportingContent = { Text(state.groupCode) },
                    onClick = {
                        openEdit(
                            R.string.group_code_settings_title, state.groupCode,
                        ) { onGroupCodeChange(it) }
                    },
                    shapes = ListItemDefaults.segmentedDynamicShapes(0, 3),
                    colors = listItemColors,
                    modifier = editSemanticModifierBase.padding(bottom = ListItemDefaults.SegmentedGap),
                )

                SegmentedListItem(
                    content = { Text(stringResource(R.string.port_settings_title)) },
                    supportingContent = { Text(state.port) },
                    onClick = {
                        openEdit(
                            R.string.port_settings_title,
                            state.port,
                            isNumber = true,
                        ) { onServerPortChange(it) }
                    },
                    shapes = ListItemDefaults.segmentedDynamicShapes(1, 3),
                    colors = listItemColors,
                    modifier = editSemanticModifierBase.padding(bottom = ListItemDefaults.SegmentedGap),
                )

                SegmentedListItem(
                    content = { Text(stringResource(R.string.auth_port_settings_title)) },
                    supportingContent = { Text(state.authPort) },
                    onClick = {
                        openEdit(
                            R.string.auth_port_settings_title,
                            state.authPort,
                            isNumber = true,
                        ) { onAuthPortChange(it) }
                    },
                    shapes = ListItemDefaults.segmentedDynamicShapes(1, 3),
                    colors = listItemColors,
                    modifier = editSemanticModifierBase.padding(bottom = ListItemDefaults.SegmentedGap),
                )

                SegmentedListItem(
                    content = { Text(stringResource(R.string.network_interface_settings_title)) },
                    supportingContent = {
                        Text(
                            if (state.networkInterface != PreferenceManager.DEFAULT_NETWORK_INTERFACE) stringResource(
                                R.string.network_interface_settings_summary,
                                state.networkInterface,
                            )
                            else "Auto",
                        )
                    },
                    onClick = { showInterfaceDialog = true },
                    shapes = ListItemDefaults.segmentedDynamicShapes(2, 3),
                    colors = listItemColors,
                    modifier = editSemanticModifierBase,
                )
            }

            item {
                SettingsCategoryLabel(stringResource(R.string.aspect_settings_category))

                val themeLabelResId = state.themeMode.label
                val dynamicColorsSupported =
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

                SegmentedListItem(
                    content = { Text(stringResource(R.string.theme_settings_title)) },
                    supportingContent = { Text(stringResource(themeLabelResId)) },
                    onClick = { showThemeDialog = true },
                    shapes = ListItemDefaults.segmentedDynamicShapes(0, 2),
                    colors = listItemColors,
                    modifier = editSemanticModifierBase.padding(bottom = ListItemDefaults.SegmentedGap),
                )

                SwitchListItem(
                    title = stringResource(R.string.use_dynamic_colors_title),
                    checked = state.dynamicColors,
                    onCheckedChange = onUseDynamicColorsChange,
                    shapes = ListItemDefaults.segmentedDynamicShapes(1, 2),
                    colors = listItemColors,
                    enabled = dynamicColorsSupported,
                )

                Spacer(Modifier.height(32.dp))
            }
        }
    }

    if (showProfileDialog) {
        ProfilePictureDialog(
            currentKey = state.profilePictureKey,
            onDismiss = { showProfileDialog = false },
            onSelectKey = {
                onProfilePictureChange(it)
                showProfileDialog = false
            },
            onSelectCustom = onPickCustomProfilePicture,
            imageSignature = state.profileImageSignature,
        )
    } else if (showEditDialog) {
        TextInputDialog(
            titleRes = editDialogTitle!!,
            initialValue = editDialogValue,
            isNumber = editDialogIsNumber,
            onDismiss = { showEditDialog = false },
            onConfirm = { newVal ->
                onEditConfirm(newVal)
                showEditDialog = false
            },
        )
    } else if (showThemeDialog) {
        val values = ThemeOptions.entries

        OptionsDialog(
            title = stringResource(R.string.theme_settings_title),
            options = values.map { e -> stringResource(e.label) }.toList(),
            currentSelectionIndex = values.indexOf(state.themeMode),
            onDismiss = { showThemeDialog = false },
            onOptionSelected = { idx -> onThemeChange(values[idx]) },
        )
    } else if (showInterfaceDialog) {
        val options = state.interfaceEntries.map { it.first }
        val values = state.interfaceEntries.map { it.second }

        OptionsDialog(
            title = stringResource(R.string.network_interface_settings_title),
            options = options,
            currentSelectionIndex = maxOf(
                values.indexOf(state.networkInterface),
                0,
            ),
            onDismiss = { showInterfaceDialog = false },
            onOptionSelected = { idx -> onNetworkInterfaceChange(values[idx]) },
        )
    }
}

@PreviewLightDark
@Composable
fun SettingsScreenPreview() {
    WarpinatorTheme {
        SettingsScreenContent(
            state = SettingsUiState(),
            snackbarHostState = SnackbarHostState(),
            onBackClick = {},
            onDisplayNameChange = {},
            onProfilePictureChange = {},
            onPickCustomProfilePicture = {},
            onPickDownloadDir = {},
            onResetDownloadDir = {},
            onNotifyIncomingChange = {},
            onAutoAcceptChange = {},
            onUseCompressionChange = {},
            onAutoStopChange = {},
            onDebugLogChange = {},
            onGroupCodeChange = {},
            onServerPortChange = {},
            onAuthPortChange = {},
            onNetworkInterfaceChange = {},
            onThemeChange = {},
            onUseDynamicColorsChange = {},
        )
    }
}