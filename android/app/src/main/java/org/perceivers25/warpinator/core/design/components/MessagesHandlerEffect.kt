package org.perceivers25.warpinator.core.design.components

import android.content.Context
import android.widget.Toast
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.Flow
import org.perceivers25.warpinator.core.model.ui.MessageType
import org.perceivers25.warpinator.core.model.ui.UiMessage
import org.perceivers25.warpinator.core.model.ui.UiMessageState

@Composable
fun MessagesHandlerEffect(
    messageProvider: Flow<UiMessage>,
    snackbarHostState: SnackbarHostState? = null,
) {
    val context = LocalContext.current

    val abstractMessage by messageProvider.collectAsStateWithLifecycle(
        initialValue = null,
    )
    val resolvedState = abstractMessage?.getState()

    LaunchedEffect(abstractMessage, resolvedState) {
        resolvedState?.let { state ->
            handleMessage(state, snackbarHostState, context)
        }
    }
}

private suspend fun handleMessage(
    state: UiMessageState,
    snackbarHost: SnackbarHostState? = null,
    context: Context? = null,
) {
    if (state.type == MessageType.Snackbar) {
        snackbarHost?.showSnackbar(
            message = state.message,
            duration = state.duration,
            withDismissAction = state.duration == SnackbarDuration.Indefinite,
        )
    } else {
        if (context == null) return
        Toast.makeText(
            context,
            state.message,
            if (state.duration == SnackbarDuration.Long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT,
        ).show()
    }
}