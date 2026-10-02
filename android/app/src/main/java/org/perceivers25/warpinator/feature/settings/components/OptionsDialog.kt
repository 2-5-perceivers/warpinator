package org.perceivers25.warpinator.feature.settings.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/**
 * A Composable function that displays a modal dialog allowing the user to select one option from a list using radio buttons.
 *
 * This dialog manages its own selection state internally. The final selection is only communicated back to the caller
 * via [onOptionSelected] when the user clicks the "OK" button. Clicking "Cancel" or dismissing the dialog
 * discards any changes.
 *
 * @param title The text displayed at the top of the dialog.
 * @param options A list of strings representing the available options to choose from.
 * @param currentSelectionIndex The index of the currently active option (initial state).
 * @param onDismiss A callback invoked when the dialog is dismissed (e.g., clicking outside, back press, or "Cancel").
 * @param onOptionSelected A callback invoked with the index of the selected option when the "OK" button is clicked.
 */
@Composable
fun OptionsDialog(
    title: String,
    options: List<String>,
    currentSelectionIndex: Int,
    onDismiss: () -> Unit,
    onOptionSelected: (Int) -> Unit,
) {
    var selectedIndex by remember(currentSelectionIndex) {
        mutableIntStateOf(
            currentSelectionIndex,
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
        ) {
            Column(
                modifier = Modifier.selectableGroup(), // Accessibility optimization

            ) {
                Box(
                    Modifier
                        .padding(
                            PaddingValues(
                                bottom = 16.dp,
                                top = 24.dp,
                                start = 24.dp,
                                end = 24.dp,
                            ),
                        )
                        .align(
                            Alignment.Start,
                        ),
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                HorizontalDivider()
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .weight(1f, false),
                ) {
                    options.forEachIndexed { index, label ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = (index == selectedIndex),
                                    onClick = { selectedIndex = index },
                                    role = Role.RadioButton,
                                ),

                            ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp) // Minimum touch target height
                                    .padding(24.dp, 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = (index == selectedIndex),
                                    onClick = null, // Null because the Row handles the click
                                )
                                Spacer(Modifier.width(16.dp))
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
                HorizontalDivider()
                Box(modifier = Modifier.align(Alignment.End)) {
                    val textStyle = MaterialTheme.typography.bodyMedium
                    val buttonContentColor = MaterialTheme.colorScheme.primary

                    FlowRow(
                        modifier = Modifier.padding(24.dp, 16.dp, 24.dp, 24.dp),
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text(
                                stringResource(android.R.string.cancel),
                                style = textStyle,
                                color = buttonContentColor,
                            )

                        }
                        TextButton(
                            onClick = {
                                onOptionSelected(selectedIndex)
                                onDismiss()
                            },
                        ) {
                            Text(
                                stringResource(android.R.string.ok),
                                style = textStyle,
                                color = buttonContentColor,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun OptionsDialogPreview() {
    Scaffold { innerPadding ->
        innerPadding
        OptionsDialog(
            title = "Options",
            options = listOf("Option 1", "Option 2", "Option 3"),
            currentSelectionIndex = 1,
            onDismiss = {},
            onOptionSelected = {},
        )
    }
}