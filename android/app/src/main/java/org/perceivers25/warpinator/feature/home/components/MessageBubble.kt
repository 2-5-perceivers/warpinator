package org.perceivers25.warpinator.feature.home.components

import android.content.ClipData
import android.content.Intent
import android.text.format.DateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.launch
import org.perceivers25.warpinator.Direction
import org.perceivers25.warpinator.Message
import org.perceivers25.warpinator.R
import org.perceivers25.warpinator.core.design.components.MenuAction
import org.perceivers25.warpinator.core.design.components.MenuGroup
import org.perceivers25.warpinator.core.design.components.MenuGroupsPopup
import org.perceivers25.warpinator.core.design.theme.WarpinatorTheme
import org.perceivers25.warpinator.core.utils.rememberAnnotatedLinkText
import java.util.Date

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MessageBubble(message: Message, onDeleteMessage: () -> Unit = {}) {
    val isSent = message.direction == Direction.SENT
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()

    val interactionSource = remember { MutableInteractionSource() }

    var showMenu by remember { mutableStateOf(false) }
    var showTimestamp by remember { mutableStateOf(false) }

    val backgroundColor =
        if (isSent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest
    val textColor =
        if (isSent) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    val accentColor =
        if (isSent) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.primary
    val overlayColor =
        if (isSent) MaterialTheme.colorScheme.surfaceContainerLowest else textColor
    val bubbleShape = if (isSent) {
        RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
    } else {
        RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)
    }

    val timeString = remember(message.timestamp) {
        DateFormat.getTimeFormat(context)
            .format(Date(message.timestamp.toLong()))
    }

    val annotatedMessage =
        rememberAnnotatedLinkText(message.content, accentColor)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)

                        if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                            showMenu = true
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
            }
            .combinedClickable(
                onClick = { showTimestamp = !showTimestamp },
                onLongClick = { showMenu = true },
                onClickLabel = if (showTimestamp) stringResource(R.string.hide_timestamp_label) else stringResource(
                    R.string.show_timestamp_label,
                ),
                onLongClickLabel = stringResource(R.string.open_message_options_label),
                // Don't show the ripple over the padded container. This allows the user to tap next
                // to message, while looking like they're tapping the bubble.
                indication = null,
                interactionSource = interactionSource,
            )
            .semantics(mergeDescendants = true) {
                liveRegion = LiveRegionMode.Polite
            },
        contentAlignment = if (isSent) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Surface(
            color = backgroundColor,
            modifier = Modifier
                .widthIn(max = 360.dp)
                .padding(
                    if (isSent) PaddingValues(
                        start = 48.dp,
                        end = 16.dp,
                    ) else PaddingValues(end = 48.dp, start = 16.dp),
                )
                .clip(bubbleShape)
                .indication(
                    interactionSource,
                    ripple(
                        color = overlayColor,
                    ),
                ),
        ) {
            Column(
                modifier = Modifier.padding(
                    horizontal = 16.dp,
                    vertical = 10.dp,
                ),
            ) {
                val messageContentDescription = if (isSent) stringResource(
                    R.string.sent_message_content_description,
                    message.content,
                ) else stringResource(
                    R.string.received_message_content_description,
                    message.content,
                )

                val timestampContentDescription = if (isSent) stringResource(
                    R.string.sent_at_content_description,
                    timeString,
                ) else stringResource(
                    R.string.received_at_content_description,
                    timeString,
                )

                Text(
                    text = annotatedMessage,
                    style = MaterialTheme.typography.bodyLarge.copy(color = textColor),
                    modifier = Modifier.semantics {
                        contentDescription = messageContentDescription
                    },
                )
                AnimatedVisibility(
                    visible = showTimestamp,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(
                        text = timeString,
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor.copy(alpha = 0.7f),
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .semantics {
                                contentDescription = timestampContentDescription
                            },
                    )
                }
            }
        }

        Box {
            MenuGroupsPopup(
                menuOpen = showMenu,
                onDismiss = { showMenu = false },
                offset = DpOffset(if (isSent) (-24).dp else 24.dp, 0.dp),
                properties = PopupProperties(
                    // Allow the keyboard to remain active while the popup is opened
                    focusable = false,
                    dismissOnClickOutside = true,
                ),
                minWidth = 160.dp,
                menuGroups = listOf(
                    MenuGroup(
                        listOf(
                            MenuAction(
                                title = stringResource(R.string.copy_label),
                                leadingIcon = Icons.Rounded.ContentCopy,
                                onClick = {
                                    coroutineScope.launch {
                                        val clipData = ClipData.newPlainText(
                                            "Message",
                                            message.content,
                                        )
                                        clipboard.setClipEntry(clipData.toClipEntry())
                                    }
                                    showMenu = false
                                },
                            ),
                            MenuAction(
                                title = stringResource(R.string.share_label),
                                leadingIcon = Icons.Rounded.Share,
                                onClick = {
                                    val sendIntent =
                                        Intent(Intent.ACTION_SEND).apply {
                                            putExtra(
                                                Intent.EXTRA_TEXT,
                                                message.content,
                                            )
                                            type = "text/plain"
                                        }
                                    val shareIntent =
                                        Intent.createChooser(sendIntent, null)
                                    context.startActivity(shareIntent)
                                    showMenu = false
                                },
                            ),
                        ),
                    ),
                    MenuGroup(
                        listOf(
                            MenuAction(
                                title = stringResource(R.string.delete_label),
                                leadingIcon = Icons.Rounded.Delete,
                                onClick = onDeleteMessage,
                            ),
                        ),
                        true,
                    ),
                ),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MessageBubbleSentPreview() {
    WarpinatorTheme {
        MessageBubble(
            message = Message(
                uuid = "uuid",
                remoteUuid = "remote",
                direction = Direction.SENT,
                timestamp = System.currentTimeMillis().toULong(),
                content = "This is a sent message with a link: https://www.google.com",
            ),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MessageBubbleReceivedPreview() {
    WarpinatorTheme {
        MessageBubble(
            message = Message(
                uuid = "uuid",
                remoteUuid = "remote",
                direction = Direction.RECEIVED,
                timestamp = System.currentTimeMillis().toULong(),
                content = "This is a received message with a link: https://www.google.com",
            ),
        )
    }
}
