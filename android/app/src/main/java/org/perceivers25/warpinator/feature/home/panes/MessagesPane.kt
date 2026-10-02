package org.perceivers25.warpinator.feature.home.panes

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.perceivers25.warpinator.Direction
import org.perceivers25.warpinator.Message
import org.perceivers25.warpinator.R
import org.perceivers25.warpinator.RemoteState
import org.perceivers25.warpinator.core.data.WarpinatorViewModel
import org.perceivers25.warpinator.core.design.components.DynamicAvatarCircle
import org.perceivers25.warpinator.core.design.theme.WarpinatorTheme
import org.perceivers25.warpinator.core.model.ui.RemoteUi
import org.perceivers25.warpinator.core.notification.components.NotificationInhibitor
import org.perceivers25.warpinator.core.utils.ProfilePicturePainter
import org.perceivers25.warpinator.core.utils.RemoteDisplayInfo
import org.perceivers25.warpinator.feature.home.components.MessageBubble

@Composable
fun MessagesPane(
    remote: RemoteUi,
    paneMode: Boolean,
    onBack: () -> Unit,
    viewModel: WarpinatorViewModel = hiltViewModel(),
) {
    val messages by viewModel.getMessages(remote.uuid)
        .collectAsStateWithLifecycle(listOf())

    NotificationInhibitor(
        remoteUuid = remote.uuid,
        messages = true,
    )

    MessagesPaneContent(
        remote = remote,
        messages = messages.reversed(),
        paneMode = paneMode,
        onBack = onBack,
        onSendMessage = { message ->
            viewModel.sendMessage(
                remote.uuid,
                message,
            )
        },
        onMarkAsRead = { viewModel.markMessagesSeen(remote.uuid) },
        onDeleteMessage = { uuid ->
            viewModel.removeMessage(
                remote.uuid,
                uuid,
            )
        },
    )
}

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
)
@Composable
private fun MessagesPaneContent(
    remote: RemoteUi,
    messages: List<Message>,
    paneMode: Boolean,
    onBack: () -> Unit = {},
    onSendMessage: (String) -> Unit = {},
    onMarkAsRead: () -> Unit = {},
    onDeleteMessage: (String) -> Unit = {},
) {
    val titleFormat = RemoteDisplayInfo.fromRemote(remote)
    var messageText by rememberSaveable { mutableStateOf("") }

    val listState = rememberLazyListState()

    val remoteState = remote.state
    val isError = remoteState is RemoteState.Error
    val isConnecting =
        remoteState == RemoteState.Connecting || remoteState == RemoteState.AwaitingDuplex
    val isDisconnected = remoteState == RemoteState.Disconnected

    LaunchedEffect(messages.size) {
        onMarkAsRead()

        if (listState.firstVisibleItemIndex <= 1 || messages.first().direction == Direction.SENT) {
            listState.animateScrollToItem(0)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            TopAppBar(
                title = {
                    if (paneMode) Text(stringResource(R.string.messages)) else Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        DynamicAvatarCircle(
                            bitmap = remote.picture,
                            isFavorite = remote.isFavorite,
                            hasError = isError,
                            isLoading = isConnecting,
                            isDisabled = isDisconnected,
                        )
                        Text(
                            titleFormat.title,
                            modifier = Modifier.padding(8.dp, 0.dp),
                        )
                    }
                },
                navigationIcon = {
                    if (!paneMode) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back",
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },

        ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(innerPadding)
                .imePadding(),
        ) {
            val listContentDescription = stringResource(
                R.string.message_history_with_content_description,
                titleFormat.title,
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .semantics {
                        contentDescription = listContentDescription
                    },
                contentPadding = innerPadding + PaddingValues(
                    bottom = 92.dp,
                    top = 8.dp,
                ),
                reverseLayout = true,
                state = listState,
            ) {
                items(
                    items = messages,
                    key = { message -> message.uuid },
                ) { message ->
                    MessageBubble(
                        message,
                        onDeleteMessage = { onDeleteMessage(message.uuid) },
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(innerPadding)
                    .padding(16.dp)
                    .fillMaxWidth(),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                tonalElevation = 2.dp,
            ) {
                Row(
                    modifier = Modifier.padding(
                        horizontal = 8.dp,
                        vertical = 4.dp,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val sendingEnabled =
                        messageText.isNotBlank() && remote.state == RemoteState.Connected && remote.messageSupport
                    val onSend = {
                        if (messageText.isNotBlank()) {
                            onSendMessage(messageText)
                            messageText = ""
                        }
                    }

                    TextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        placeholder = { Text(stringResource(R.string.message_text_field_placeholder)) },
                        modifier = Modifier
                            .weight(1f)
                            .onPreviewKeyEvent { event ->
                                if (event.isCtrlPressed && event.key == Key.Enter && event.type == KeyEventType.KeyDown && sendingEnabled) {
                                    onSend()
                                    true // consumed, does NOT insert newline
                                } else {
                                    false // let Enter fall through to insert newline normally
                                }
                            },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                        maxLines = 3,
                    )

                    val sendButtonClickLabel =
                        stringResource(R.string.send_action)
                    val sendButtonStateDescription = when {
                        !remote.messageSupport -> stringResource(R.string.device_does_not_support_text_messages_state)
                        remote.state != RemoteState.Connected -> stringResource(
                            R.string.device_is_disconnected_state,
                        )

                        messageText.isBlank() -> stringResource(R.string.message_is_empty_state)
                        else -> ""
                    }


                    IconButton(
                        onClick = onSend,
                        enabled = sendingEnabled,
                        modifier = Modifier.semantics {
                            onClick(sendButtonClickLabel, null)

                            if (!sendingEnabled) {
                                stateDescription = sendButtonStateDescription
                            }

                        },
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Send,
                            contentDescription = stringResource(R.string.send_label),
                            tint = if (sendingEnabled) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun MessagesPanePreview() {
    // An AI generated conversation for testing purposes
    val messages = listOf(
        Message(
            uuid = "1",
            remoteUuid = "remote",
            direction = Direction.SENT,
            timestamp = 1U,
            content = "Hey! Are you around?",
        ),
        Message(
            uuid = "2",
            remoteUuid = "remote",
            direction = Direction.RECEIVED,
            timestamp = 2U,
            content = "Hey there! Yeah, just finished some work. What's up?",
        ),
        Message(
            uuid = "3",
            remoteUuid = "remote",
            direction = Direction.SENT,
            timestamp = 3U,
            content = "I was thinking about that new cafe downtown. Want to check it out?",
        ),
        Message(
            uuid = "4",
            remoteUuid = "remote",
            direction = Direction.RECEIVED,
            timestamp = 4U,
            content = "Oh, the one with the blue storefront? I've heard the espresso is incredible.",
        ),
        Message(
            uuid = "5",
            remoteUuid = "remote",
            direction = Direction.SENT,
            timestamp = 5U,
            content = "Exactly that one! They also have those huge croissants everyone is posting about.",
        ),
        Message(
            uuid = "6",
            remoteUuid = "remote",
            direction = Direction.RECEIVED,
            timestamp = 6U,
            content = "Haha, count me in. I'm a sucker for a good pastry.",
        ),
        Message(
            uuid = "7",
            remoteUuid = "remote",
            direction = Direction.SENT,
            timestamp = 7U,
            content = "Great! Does 4:00 PM work for you?",
        ),
        Message(
            uuid = "8",
            remoteUuid = "remote",
            direction = Direction.RECEIVED,
            timestamp = 8U,
            content = "Make it 4:15? I need to walk the dog first.",
        ),
        Message(
            uuid = "9",
            remoteUuid = "remote",
            direction = Direction.SENT,
            timestamp = 9U,
            content = "No problem at all. 4:15 it is.",
        ),
        Message(
            uuid = "10",
            remoteUuid = "remote",
            direction = Direction.RECEIVED,
            timestamp = 10U,
            content = "Perfect. See you there!",
        ),
        Message(
            uuid = "11",
            remoteUuid = "remote",
            direction = Direction.SENT,
            timestamp = 11U,
            content = "See ya!",
        ),
        Message(
            uuid = "12",
            remoteUuid = "remote",
            direction = Direction.RECEIVED,
            timestamp = 12U,
            content = "Wait, should I bring that book I borrowed from you?",
        ),
        Message(
            uuid = "13",
            remoteUuid = "remote",
            direction = Direction.SENT,
            timestamp = 13U,
            content = "Oh! If you've finished it, sure. Otherwise, no rush.",
        ),
        Message(
            uuid = "14",
            remoteUuid = "remote",
            direction = Direction.RECEIVED,
            timestamp = 14U,
            content = "I finished it last night. That ending was wild!",
        ),
        Message(
            uuid = "15",
            remoteUuid = "remote",
            direction = Direction.SENT,
            timestamp = 15U,
            content = "I told you! We definitely need to talk about it over coffee.",
        ),
        Message(
            uuid = "16",
            remoteUuid = "remote",
            direction = Direction.SENT,
            timestamp = 16U,
            content = "https://somerandomcaffe.com",
        ),
    ).reversed()

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
        MessagesPaneContent(
            remote = remote,
            messages = messages,
            paneMode = false,
            onBack = {},
        )
    }
}