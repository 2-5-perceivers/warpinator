package org.perceivers25.warpinator.feature.settings.components

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import org.perceivers25.warpinator.R

/**
 * A reusable AlertDialog composable that prompts the user for a text input.
 *
 * It features a title, a single-line text field, and standard "OK" (Confirm) and
 * "Cancel" (Dismiss) buttons.
 *
 * @param titleRes The string resource ID for the dialog title.
 * @param initialValue The starting text value to be displayed in the text field.
 * @param isNumber If true, configures the keyboard for numeric input; otherwise defaults to standard text input.
 * @param onDismiss Callback invoked when the user dismisses the dialog (either via the "Cancel" button or clicking outside).
 * @param onConfirm Callback invoked when the user clicks the "OK" button. Passes the current text value as a parameter.
 */
@Composable
fun TextInputDialog(
    titleRes: Int,
    initialValue: String,
    isNumber: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleRes)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                keyboardOptions = if (isNumber) KeyboardOptions(keyboardType = KeyboardType.Number)
                else KeyboardOptions.Default,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text); onDismiss() }) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    stringResource(android.R.string.cancel),
                )
            }
        },
    )
}

@Preview(showBackground = true)
@Composable
fun TextInputDialogPreview() {
    Scaffold { innerPadding ->
        innerPadding
        TextInputDialog(
            titleRes = R.string.port_settings_title,
            initialValue = "8080",
            onDismiss = {},
            onConfirm = {},
        )
    }

}