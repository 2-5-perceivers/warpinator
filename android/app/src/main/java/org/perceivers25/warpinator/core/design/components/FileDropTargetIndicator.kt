package org.perceivers25.warpinator.core.design.components

import android.net.Uri
import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.toAndroidDragEvent

enum class DragAndDropUiMode {
    None, DragActive, DragHover,
}

data class DropTargetState(
    val target: DragAndDropTarget? = null,
    val shouldStartDragAndDrop: (DragAndDropEvent) -> Boolean = { false },
    val uiMode: DragAndDropUiMode = DragAndDropUiMode.None,
)

@Composable
fun rememberDropTargetState(
    onUrisDropped: (List<Uri>) -> Boolean,
    shouldStartDragAndDrop: (DragAndDropEvent) -> Boolean = { true },
): DropTargetState {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
        return DropTargetState()
    }

    var fileDropTargetState by remember { mutableStateOf(DragAndDropUiMode.None) }
    val activity = LocalActivity.current

    val currentOnDrop by rememberUpdatedState(onUrisDropped)
    val currentShouldStartDragAndDrop by rememberUpdatedState(
        shouldStartDragAndDrop,
    )

    val fileDropTarget = remember(activity) {
        object : DragAndDropTarget {
            override fun onStarted(event: DragAndDropEvent) {
                fileDropTargetState = DragAndDropUiMode.DragActive
            }

            override fun onEntered(event: DragAndDropEvent) {
                fileDropTargetState = DragAndDropUiMode.DragHover
            }

            override fun onEnded(event: DragAndDropEvent) {
                fileDropTargetState = DragAndDropUiMode.None
            }

            override fun onExited(event: DragAndDropEvent) {
                fileDropTargetState = DragAndDropUiMode.DragActive
            }

            override fun onDrop(event: DragAndDropEvent): Boolean {
                fileDropTargetState = DragAndDropUiMode.None

                if (activity == null) return false

                activity.requestDragAndDropPermissions(event.toAndroidDragEvent())

                val clipData =
                    event.toAndroidDragEvent().clipData ?: return false
                val uris = (0 until clipData.itemCount).mapNotNull { i ->
                    clipData.getItemAt(i).uri
                }

                if (uris.isNotEmpty()) {
                    return currentOnDrop(uris)
                }
                return false
            }
        }
    }

    return DropTargetState(
        target = fileDropTarget,
        uiMode = fileDropTargetState,
        shouldStartDragAndDrop = currentShouldStartDragAndDrop,
    )
}

fun Modifier.fileDropTarget(state: DropTargetState): Modifier {
    if (state.target == null) return this

    return this.dragAndDropTarget(
        shouldStartDragAndDrop = state.shouldStartDragAndDrop,
        target = state.target,
    )
}

@Composable
fun FileDropTargetIndicator(
    dropMode: DragAndDropUiMode,
    text: String,
    modifier: Modifier = Modifier,
) {
    if (dropMode == DragAndDropUiMode.None) return

    val textColor = when (dropMode) {
        DragAndDropUiMode.DragHover -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    Surface(
        modifier = modifier,
        color = when (dropMode) {
            DragAndDropUiMode.DragHover -> MaterialTheme.colorScheme.secondaryContainer
            else -> MaterialTheme.colorScheme.surfaceContainerHighest
        },
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text,
                style = MaterialTheme.typography.titleLarge,
                color = textColor,
            )
        }
    }
}