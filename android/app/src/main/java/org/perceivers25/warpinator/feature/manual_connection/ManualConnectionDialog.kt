package org.perceivers25.warpinator.feature.manual_connection

import android.content.ClipData
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlendModeColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.launch
import org.perceivers25.warpinator.ManualConnectionException
import org.perceivers25.warpinator.R
import org.perceivers25.warpinator.WarpException
import org.perceivers25.warpinator.core.data.WarpinatorViewModel
import org.perceivers25.warpinator.core.design.shapes.segmentedDynamicShapes
import org.perceivers25.warpinator.core.design.theme.WarpinatorTheme
import org.perceivers25.warpinator.core.model.preferences.RecentRemote
import org.perceivers25.warpinator.core.utils.QRCodeBitmaps
import org.perceivers25.warpinator.core.utils.transformers.IPAddressTransformer
import org.perceivers25.warpinator.core.utils.transformers.ProtocolAddressInputValidator

sealed interface ManualConnectionResult {
    data object Success : ManualConnectionResult
    data class Exception(val exception: ManualConnectionException) :
            ManualConnectionResult
}

data class RecentRemoteOption(
    val address: String,
    val name: String? = null,
    val fromClipboard: Boolean = false,
)

fun List<RecentRemote>.toRecentRemoteOptions(): List<RecentRemoteOption> {
    return this.map { RecentRemoteOption(it.host, it.hostname, false) }
}

sealed interface ManualConnectionDialogState {
    data object QRCode : ManualConnectionDialogState
    data object QuickSelect : ManualConnectionDialogState
    data class Connecting(val address: String) : ManualConnectionDialogState
}

@Composable
fun ManualConnectionDialog(
    address: String? = null,
    onDismiss: () -> Unit = {},
    viewModel: WarpinatorViewModel = hiltViewModel(),
    dialogState: ManualConnectionDialogState = ManualConnectionDialogState.QRCode,
) {
    var dialogState by remember { mutableStateOf(dialogState) }

    val clipboard = LocalClipboard.current

    val address = address ?: viewModel.address

    val onShowQuickSelectDialog = {
        dialogState = ManualConnectionDialogState.QuickSelect
    }

    when (dialogState) {
        ManualConnectionDialogState.QRCode -> {
            QRCodeDialog(
                address,
                clipboard,
                onDismiss,
                onShowQuickSelectDialog,
            )
        }

        ManualConnectionDialogState.QuickSelect -> {
            val recentRemotes = viewModel.repository.prefs.recentRemotes
            QuickSelectRemoteDialog(
                clipboard,
                recentRemotes.toRecentRemoteOptions(),
                onDismiss,
            ) { address ->
                dialogState = ManualConnectionDialogState.Connecting(address)
            }
        }

        is ManualConnectionDialogState.Connecting -> {
            ConnectingToRemoteDialog(
                onDismiss,
                (dialogState as ManualConnectionDialogState.Connecting).address,
                viewModel::manualConnectRemote,
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun ConnectingToRemoteDialog(
    onDismiss: () -> Unit,
    address: String,
    onTryRegisterWithHost: suspend (String) -> Unit,
    forceState: ManualConnectionResult? = null,
) {
    stringResource(R.string.unknown_error)

    val connectionResult by produceState(
        initialValue = forceState, key1 = address,
    ) {
        value = try {
            onTryRegisterWithHost(address)
            ManualConnectionResult.Success
        } catch (e: ManualConnectionException) {
            ManualConnectionResult.Exception(e)
        }
    }

    @Composable
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    fun warningContainer(warningText: String) {
        Surface(
            modifier = Modifier
                .padding(top = 32.dp)
                .size(124.dp),
            color = MaterialTheme.colorScheme.tertiaryContainer,
            shape = MaterialShapes.Cookie9Sided.toShape(),
        ) {
            Icon(
                Icons.Rounded.PriorityHigh,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.padding(24.dp),
            )
        }

        Text(
            warningText,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.padding(top = 8.dp),
            textAlign = TextAlign.Center,
        )
    }

    @Composable
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    fun errorContainer(errorMessage: String, errorDetails: String? = null) {
        Surface(
            modifier = Modifier
                .padding(top = 32.dp)
                .size(124.dp),
            color = MaterialTheme.colorScheme.errorContainer,
            shape = MaterialShapes.SoftBurst.toShape(),
        ) {
            Icon(
                Icons.Rounded.PriorityHigh,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(24.dp),
            )
        }

        Text(
            errorMessage,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 8.dp),
            textAlign = TextAlign.Center,
        )

        if (errorDetails != null) Text(
            errorDetails,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.error.copy(alpha = 0.9f),
            modifier = Modifier.padding(top = 8.dp),
            textAlign = TextAlign.Center,
        )
    }

    @Composable
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    fun successContainer() {
        Surface(
            modifier = Modifier
                .padding(top = 32.dp)
                .size(124.dp),
            color = MaterialTheme.colorScheme.primary,
            shape = MaterialShapes.Cookie9Sided.toShape(),
        ) {
            Icon(
                Icons.Rounded.Done,
                contentDescription = stringResource(R.string.manual_connection_success_label),
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(24.dp),
            )
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.manual_connection_connecting_title)) },
        icon = { Icon(Icons.Rounded.AddLink, contentDescription = null) },
        modifier = Modifier.widthIn(max = 350.dp),
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close_label))
            }
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    address,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )

                when (connectionResult) {
                    is ManualConnectionResult.Exception -> {
                        when (val exception =
                            (connectionResult as ManualConnectionResult.Exception).exception) {
                            is ManualConnectionException.AlreadyConnected -> warningContainer(
                                stringResource(R.string.manual_connection_already_connected),
                            )

                            is ManualConnectionException.AlreadyConnecting -> warningContainer(
                                stringResource(R.string.manual_connection_already_connecting),
                            )

                            is ManualConnectionException.FailedToConnect -> errorContainer(
                                stringResource(R.string.manual_connection_failed),
                                exception.v1,
                            )

                            is ManualConnectionException.FailedToRegister -> errorContainer(
                                stringResource(R.string.manual_connection_failed),
                                "Failed to register with remote",
                            )

                            is ManualConnectionException.InvalidUrl -> errorContainer(
                                "Invalid address",
                                "The address you entered is invalid",
                            )

                            is ManualConnectionException.RemoteInternal -> errorContainer(
                                stringResource(R.string.manual_connection_failed),
                                "The other device encountered an error",
                            )

                            is ManualConnectionException.RemoteUnimplemented -> errorContainer(
                                stringResource(R.string.manual_connection_failed),
                                stringResource(R.string.manual_connection_not_supported),
                            )

                            is ManualConnectionException.RuntimeException -> errorContainer(
                                stringResource(R.string.manual_connection_failed),
                                exception.v1.toString(),
                            )

                            is ManualConnectionException.Unavailable -> errorContainer(
                                stringResource(R.string.manual_connection_failed),
                                "Unavailable",
                            )
                        }
                    }

                    ManualConnectionResult.Success -> {
                        successContainer()
                    }

                    null -> {
                        LoadingIndicator(
                            modifier = Modifier
                                .padding(top = 32.dp)
                                .size(124.dp),
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun QRCodeDialog(
    address: String,
    clipboard: Clipboard,
    onDismiss: () -> Unit,
    onShowQuickSelectDialog: () -> Unit,
) {
    val clipboardCoroutineScope = rememberCoroutineScope()
    val url = ProtocolAddressInputValidator.getFullAddressUrl(address)

    val copyUrl: () -> Unit = {
        // Copy the url to the clipboard
        clipboardCoroutineScope.launch {
            val clipData = ClipData.newPlainText("url", url)
            clipboard.setClipEntry(ClipEntry(clipData))
        }
    }

    val qrCode by produceState(null as Bitmap?, address) {
        value = QRCodeBitmaps.generate(url)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(max = 350.dp),
        title = { Text(stringResource(R.string.manual_connection_label)) },
        confirmButton = {
            Row {
                TextButton(onClick = onShowQuickSelectDialog) {
                    Text(stringResource(R.string.add_connection_label))
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.close_label))
                }
            }
        },
        icon = { Icon(Icons.Rounded.AddLink, contentDescription = null) },
        text = {
            val copyUrlActionLabel = stringResource(R.string.copy_url_action)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                qrCode?.asImageBitmap()?.let {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        modifier = Modifier
                            .height(256.dp)
                            .aspectRatio(1f)
                            .fillMaxWidth()
                            .padding(
                                bottom = 12.dp,
                            )
                            .clip(RoundedCornerShape(24.dp))
                            .clickable(
                                onClick = copyUrl,
                            )
                            .semantics(mergeDescendants = true) {
                                onClick(
                                    label = copyUrlActionLabel,
                                    action = null,
                                )
                            },
                    ) {
                        Image(
                            it,
                            contentDescription = stringResource(R.string.qr_code_content_description),
                            colorFilter = BlendModeColorFilter(
                                MaterialTheme.colorScheme.primary,
                                BlendMode.SrcIn,
                            ),
                            contentScale = ContentScale.FillHeight,
                            filterQuality = FilterQuality.None, // Force image to be crisp
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
                Text(
                    stringResource(R.string.manual_connect_text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Justify,
                )

                TextButton(
                    onClick = copyUrl,
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .semantics {
                            onClick(label = copyUrlActionLabel, action = null)
                        },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),

                    ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(address)
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Icon(
                            Icons.Rounded.ContentCopy,
                            null,
                            modifier = Modifier.size(ButtonDefaults.IconSize),
                        )
                    }
                }
            }

        },
    )
}

@Composable
private fun QuickSelectRemoteDialog(
    clipboard: Clipboard,
    recentRemotes: List<RecentRemoteOption>?,
    onDismiss: () -> Unit,
    onStartConnection: (String) -> Unit,
) {
    val textFieldState = rememberTextFieldState()
    val recentRemotes = remember(recentRemotes) {
        (recentRemotes ?: emptyList()).toMutableStateList()
    }
    val validAddressSelected by remember {
        derivedStateOf {
            textFieldState.text.isNotEmpty() && ProtocolAddressInputValidator.isValidIp(
                textFieldState.text.toString(), false,
            )
        }
    }

    var showValidationError by remember { mutableStateOf(false) }

    val fromClipboardLabel =
        stringResource(R.string.from_clipboard_recent_remote_label)
    LaunchedEffect("GET_CLIPBOARD") {
        val clipData = clipboard.getClipEntry()?.clipData
        if ((clipData?.itemCount ?: 0) == 0) return@LaunchedEffect

        var clipText =
            clipData?.getItemAt(0)?.text?.toString() ?: return@LaunchedEffect
        if (!clipText.startsWith(ProtocolAddressInputValidator.scheme)) return@LaunchedEffect

        clipText = clipText.removePrefix(ProtocolAddressInputValidator.scheme)
        if (!ProtocolAddressInputValidator.isValidIp(
                clipText,
                false,
            )
        ) return@LaunchedEffect

        recentRemotes.add(
            0,
            RecentRemoteOption(
                clipText,
                fromClipboardLabel,
                true,
            ),
        )
    }

    val onSubmit = {
        if (validAddressSelected) onStartConnection(textFieldState.text.toString())
        else showValidationError = true
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(max = 350.dp),
        title = { Text(stringResource(R.string.manual_connection_label)) },
        confirmButton = {
            Button(onClick = onSubmit) {
                Text(stringResource(R.string.add_connection_label))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close_label))
            }
        },
        icon = { Icon(Icons.Rounded.AddLink, contentDescription = null) },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                OutlinedTextField(
                    textFieldState,
                    label = { Text(stringResource(R.string.ip_address_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    prefix = {
                        Text(ProtocolAddressInputValidator.scheme)
                    },
                    isError = !validAddressSelected && showValidationError,
                    supportingText = {
                        if (!validAddressSelected && showValidationError) {
                            Text(stringResource(R.string.invalid_address_error))
                        }
                    },
                    lineLimits = TextFieldLineLimits.SingleLine,
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Done,
                        keyboardType = KeyboardType.Unspecified,
                    ),
                    inputTransformation = ProtocolAddressInputValidator(),
                    outputTransformation = IPAddressTransformer(MaterialTheme.colorScheme.onSurfaceVariant),
                    onKeyboardAction = { performDefaultAction ->
                        onSubmit()
                        performDefaultAction()
                    },
                )

                Text(
                    stringResource(R.string.recent_remotes_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .fillMaxWidth(),
                )

                if (recentRemotes.isEmpty()) {
                    Icon(
                        Icons.Rounded.HistoryToggleOff,
                        null,
                        modifier = Modifier
                            .padding(vertical = 16.dp)
                            .size(32.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        stringResource(R.string.no_recent_remotes),
                        style = MaterialTheme.typography.titleSmall,
                    )
                } else {
                    Spacer(modifier = Modifier.size(16.dp))
                }

                recentRemotes.forEachIndexed { index, remote ->

                    RecentRemoteSegmentedListTile(
                        textFieldState, remote, index, recentRemotes.size,
                    )
                }
            }

        },
    )
}

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun RecentRemoteSegmentedListTile(
    textFieldState: TextFieldState,
    remote: RecentRemoteOption,
    index: Int,
    listCount: Int,
) {
    SegmentedListItem(
        onClick = {
            textFieldState.setTextAndPlaceCursorAtEnd(remote.address)
        },
        selected = textFieldState.text == remote.address,
        shapes = ListItemDefaults.segmentedDynamicShapes(index, listCount),
        content = {
            Text(
                buildAnnotatedString {
                    val sections = remote.address.split(":")

                    append(sections[0])
                    withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                        append(":")
                        append(sections[1])
                    }
                },
            )
        },
        supportingContent = {
            remote.name?.let {
                Text(
                    it,
                )
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        trailingContent = {
            if (remote.fromClipboard) Icon(
                Icons.Rounded.ContentPasteGo,
                contentDescription = stringResource(R.string.paste_address_from_clipboard_label),

                )
            else Icon(Icons.Rounded.ChevronRight, contentDescription = null)
        },
    )
}

@Preview(showBackground = true)
@Composable
fun ManualConnectionDialogPreview() {
    WarpinatorTheme {
        Scaffold { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                QRCodeDialog(
                    address = "192.168.0.100:100",
                    onDismiss = {},
                    clipboard = LocalClipboard.current,
                    onShowQuickSelectDialog = {},
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ManualConnectionQuickSelectDialogPreview() {
    WarpinatorTheme {
        Scaffold { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                QuickSelectRemoteDialog(
                    clipboard = LocalClipboard.current,
                    onDismiss = {},
                    recentRemotes = listOf(),
                    onStartConnection = {},
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ManualConnectionQuickSelectRecentsDialogPreview() {
    WarpinatorTheme {
        Scaffold { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                QuickSelectRemoteDialog(
                    clipboard = LocalClipboard.current, onDismiss = {},
                    recentRemotes = listOf(
                        RecentRemoteOption(
                            "192.168.0.90:42001",
                            "From clipboard",
                            true,
                        ),
                        RecentRemoteOption("192.168.0.89:42001", "Device 1"),
                        RecentRemoteOption("192.168.0.233:42002", "Device 2"),
                    ),
                    onStartConnection = {},
                )
            }
        }
    }
}

class ConnectionResultProvider :
        PreviewParameterProvider<ManualConnectionResult?> {
    override val values = sequenceOf(
        null,
        ManualConnectionResult.Success,
        ManualConnectionResult.Exception(ManualConnectionException.AlreadyConnected()),
        ManualConnectionResult.Exception(ManualConnectionException.AlreadyConnecting()),
        ManualConnectionResult.Exception(
            ManualConnectionException.FailedToConnect("Network failure"),
        ),
        ManualConnectionResult.Exception(ManualConnectionException.FailedToRegister()),
        ManualConnectionResult.Exception(ManualConnectionException.InvalidUrl()),
        ManualConnectionResult.Exception(ManualConnectionException.RemoteInternal()),
        ManualConnectionResult.Exception(ManualConnectionException.RemoteUnimplemented()),
        ManualConnectionResult.Exception(
            ManualConnectionException.RuntimeException(WarpException.RuntimeException()),
        ),
        ManualConnectionResult.Exception(ManualConnectionException.Unavailable()),
    )

    override fun getDisplayName(index: Int): String? {
        return when (val value = values.elementAt(index)) {
            null -> "Connecting"
            ManualConnectionResult.Success -> "Success"
            is ManualConnectionResult.Exception -> when (value.exception) {
                is ManualConnectionException.AlreadyConnected -> "Already Connected"
                is ManualConnectionException.AlreadyConnecting -> "Already Connecting"
                is ManualConnectionException.FailedToConnect -> "Failed to Connect"
                is ManualConnectionException.FailedToRegister -> "Failed to Register"
                is ManualConnectionException.InvalidUrl -> "Invalid URL"
                is ManualConnectionException.RemoteInternal -> "Remote Internal"
                is ManualConnectionException.RemoteUnimplemented -> "Remote Unimplemented"
                is ManualConnectionException.RuntimeException -> "Runtime Exception"
                is ManualConnectionException.Unavailable -> "Unavailable"
            }
        }
    }
}

@Preview(showBackground = true, group = "Connection States")
@Composable
fun ManualConnectionConnectingToDialogPreview(
    @PreviewParameter(ConnectionResultProvider::class) result: ManualConnectionResult?,
) {
    WarpinatorTheme {
        Scaffold { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                ConnectingToRemoteDialog(
                    address = "192.168.0.100",
                    forceState = result,
                    onDismiss = {},
                    onTryRegisterWithHost = { result },
                )
            }
        }
    }
}
