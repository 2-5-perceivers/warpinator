package org.perceivers25.warpinator.app

import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.IntentCompat
import androidx.core.util.Consumer
import org.perceivers25.warpinator.core.utils.transformers.ProtocolAddressInputValidator
import org.perceivers25.warpinator.feature.manual_connection.ManualConnectionDialog
import org.perceivers25.warpinator.feature.manual_connection.ManualConnectionDialogState
import org.perceivers25.warpinator.feature.share.ShareDialog

@Composable
fun WarpinatorIntentHandler(
    onOpenRemote: (uuid: String, openMessages: Boolean) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity

    var showConnectDialog by rememberSaveable { mutableStateOf(false) }
    var connectAddress by rememberSaveable { mutableStateOf<String?>(null) }

    var showShareDialog by rememberSaveable { mutableStateOf(false) }
    var sharedUris by rememberSaveable { mutableStateOf<List<Uri>>(emptyList()) }
    var sharedText by rememberSaveable { mutableStateOf<String?>(null) }

    fun handleIntent(intent: Intent?) {
        if (intent == null) return

        val remoteUuid = intent.getStringExtra("remote")
        if (remoteUuid != null) {
            val openMessages = intent.getBooleanExtra("messages", false)
            onOpenRemote(remoteUuid, openMessages)
        }

        when (intent.action) {
            Intent.ACTION_VIEW -> {
                val data = intent.data
                if (data != null && data.scheme == "warpinator") {
                    val address = data.schemeSpecificPart.removePrefix("//")
                    if (ProtocolAddressInputValidator.isValidIp(
                            address,
                            false,
                        )
                    ) {
                        connectAddress = address
                        showConnectDialog = true
                    }
                }
            }

            Intent.ACTION_SEND -> {
                val uri = IntentCompat.getParcelableExtra(
                    intent,
                    Intent.EXTRA_STREAM,
                    Uri::class.java,
                )
                if (uri != null) {
                    sharedUris = listOf(uri)
                    showShareDialog = true
                } else if (intent.type?.startsWith("text/") == true) {
                    sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                    showShareDialog = true
                }
            }

            Intent.ACTION_SEND_MULTIPLE -> {
                val uris = IntentCompat.getParcelableArrayListExtra(
                    intent,
                    Intent.EXTRA_STREAM,
                    Uri::class.java,
                )
                if (!uris.isNullOrEmpty()) {
                    sharedUris = uris
                    showShareDialog = true
                }
            }
        }

        intent.data = null
        intent.replaceExtras(null)
    }


    LaunchedEffect(Unit) {
        handleIntent(activity?.intent)

    }

    DisposableEffect(Unit) {
        val listener = Consumer<Intent> { intent ->
            handleIntent(intent)
        }
        activity?.addOnNewIntentListener(listener)
        onDispose { activity?.removeOnNewIntentListener(listener) }
    }

    if (showConnectDialog && connectAddress != null) {
        ManualConnectionDialog(
            onDismiss = {
                showConnectDialog = false
                connectAddress = null
            },
            dialogState = ManualConnectionDialogState.Connecting(connectAddress!!),
        )
    }

    if (showShareDialog && (sharedUris.isNotEmpty() || sharedText != null)) {
        ShareDialog(
            onDismiss = {
                showShareDialog = false
                sharedUris = emptyList()
                sharedText = null
            },
            onOpenRemote = onOpenRemote,
            uris = sharedUris,
            text = sharedText,
        )
    }
}