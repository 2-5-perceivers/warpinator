package org.perceivers25.warpinator.feature.home.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import org.perceivers25.warpinator.R

@Composable
fun SendMessageDialog(
    onSendMessage: (String) -> Unit,
    onDismiss: () -> Unit = {},
) {
    var text by rememberSaveable { mutableStateOf("") }


    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null)
        },
        title = { Text(stringResource(R.string.send_message_dialog_title)) },
        confirmButton = {
            Button(
                onClick = {
                    onDismiss()
                    onSendMessage(text)
                },
                enabled = text.isNotBlank(),
            ) { Text(stringResource(R.string.send_label)) }
        },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                },
                placeholder = { Text(stringResource(R.string.message_text_field_placeholder)) },
            )
        },
    )
}

@Preview
@Composable
fun SendMessageDialogPreview() {
    SendMessageDialog(
        onSendMessage = {},
        onDismiss = {},
    )
}