package org.perceivers25.warpinator.feature.settings.components

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.perceivers25.warpinator.R
import org.perceivers25.warpinator.core.design.components.FileDropTargetIndicator
import org.perceivers25.warpinator.core.design.components.fileDropTarget
import org.perceivers25.warpinator.core.design.components.rememberDropTargetState
import org.perceivers25.warpinator.core.utils.ProfilePicturePainter

/**
 * A dialog composable that allows the user to view and select a profile picture.
 *
 * It provides a grid of predefined profile avatars and an option to select a custom image.
 * The currently selected image is displayed prominently at the top.
 *
 * @param currentKey The key representing the currently active profile picture (e.g., "1", "2", or "profilePic.png").
 * @param onDismiss Callback invoked when the user attempts to close the dialog (e.g., clicks the close button or back).
 * @param onSelectKey Callback invoked when the user confirms their selection by clicking "Save". Passes the selected image key.
 * @param onSelectCustom Callback invoked when the user clicks the button to add a custom photo.
 * @param imageSignature A signature (timestamp/hash) used to force a refresh of the image painter when the underlying custom image file changes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilePictureDialog(
    currentKey: String,
    onDismiss: () -> Unit,
    onSelectKey: (String) -> Unit,
    onSelectCustom: (Uri) -> Unit,
    imageSignature: Long,
) {
    var selectedKey by remember { mutableStateOf<String>(currentKey) }
    val context = LocalContext.current

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            } catch (_: Exception) {
                // Ignore if specific permission grant fails (file might still be readable once)
            }
            onSelectCustom(uri)
        }
    }

    val fileDropTargetState = rememberDropTargetState(
        onUrisDropped = onUrisDropped@{ uris ->
            if (uris.size != 1) return@onUrisDropped false
            onSelectCustom(uris.first())
            true
        },
        shouldStartDragAndDrop = shouldStartDragAndDrop@{ event ->
            val description = event.toAndroidDragEvent().clipDescription
                ?: return@shouldStartDragAndDrop false
            return@shouldStartDragAndDrop when {
                description.mimeTypeCount != 1 -> false
                description.hasMimeType("image/*") -> true
                else -> false
            }
        },
    )

    LaunchedEffect(imageSignature) {
        if (imageSignature > 0 && selectedKey != "profilePic.png") {
            selectedKey = "profilePic.png"
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false, decorFitsSystemWindows = false,
        ),
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            stringResource(R.string.change_profile_picture_title),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.close_label),
                            )
                        }
                    },
                    actions = {
                        Button(
                            onClick = {
                                onSelectKey(selectedKey)
                            },
                            modifier = Modifier.padding(horizontal = 8.dp),
                        ) {
                            Text(stringResource(R.string.save_label))
                        }
                    },
                )
            },
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .fileDropTarget(fileDropTargetState),
                contentAlignment = Alignment.TopCenter,
            ) {
                Surface(
                    modifier = Modifier
                        .padding(
                            top = 160.dp,
                            start = 16.dp,
                            end = 16.dp,
                            bottom = 32.dp,
                        )
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.extraLarge),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    LazyVerticalGrid(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        columns = GridCells.Adaptive(64.dp),
                        contentPadding = PaddingValues(
                            top = 96.dp,
                            start = 16.dp,
                            end = 16.dp,
                            bottom = 16.dp,
                        ),
                    ) {
                        item {
                            Box(contentAlignment = Alignment.Center) {
                                FilledIconButton(
                                    onClick = {
                                        imagePickerLauncher.launch(
                                            PickVisualMediaRequest(
                                                ActivityResultContracts.PickVisualMedia.ImageOnly,
                                            ),
                                        )
                                    },
                                    shape = CircleShape,
                                    modifier = Modifier
                                        .height(64.dp)
                                        .aspectRatio(1f),
                                ) {
                                    Icon(
                                        Icons.Default.AddPhotoAlternate,
                                        contentDescription = stringResource(R.string.add_custom_picture),
                                    )
                                }
                            }
                        }

                        items(ProfilePicturePainter.colorsLength) { index ->
                            val key = index.toString()
                            val isSelected = (key == selectedKey)
                            val bmp = remember(key) {
                                ProfilePicturePainter.getProfilePicture(
                                    key, context,
                                )
                            }

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .semantics {
                                            selected = isSelected
                                        }
                                        .clickable { selectedKey = key },
                                ) {
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = stringResource(
                                            R.string.profile_picture_label,
                                            index + 1,
                                        ),
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                    if (isSelected) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .border(
                                                    4.dp,
                                                    MaterialTheme.colorScheme.tertiary,
                                                    CircleShape,
                                                ),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    FileDropTargetIndicator(
                        fileDropTargetState.uiMode,
                        text = stringResource(R.string.drop_image_here_to_set),
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Box(
                    modifier = Modifier.offset(y = (80).dp),
                    contentAlignment = Alignment.Center,
                ) {
                    val currentBitmap = remember(selectedKey, imageSignature) {
                        ProfilePicturePainter.getProfilePicture(
                            selectedKey,
                            context,
                            true,
                        )
                    }

                    Image(
                        bitmap = currentBitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(160.dp)
                            .border(
                                8.dp,
                                MaterialTheme.colorScheme.surfaceContainerHigh,
                                CircleShape,
                            )
                            .padding(8.dp)
                            .clip(CircleShape),
                    )
                }
            }
        }
    }
}

@Preview
@Composable
fun ProfilePictureDialogPreview() {

    ProfilePictureDialog(
        currentKey = "1",
        onDismiss = {},
        onSelectKey = {},
        onSelectCustom = {},
        imageSignature = 0,
    )

}