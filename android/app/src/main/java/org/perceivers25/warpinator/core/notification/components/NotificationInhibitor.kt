package org.perceivers25.warpinator.core.notification.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import dagger.hilt.android.lifecycle.HiltViewModel
import org.perceivers25.warpinator.core.notification.WarpinatorNotificationManager
import javax.inject.Inject

@HiltViewModel
internal class NotificationInhibitorViewModel @Inject constructor(
    val notificationManager: WarpinatorNotificationManager,
) : ViewModel()

@Composable
fun NotificationInhibitor(
    remoteUuid: String,
    transfers: Boolean = false,
    messages: Boolean = false,
) {
    val viewModel: NotificationInhibitorViewModel = hiltViewModel()
    val notificationManager = viewModel.notificationManager

    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(
        key1 = remoteUuid,
        key2 = messages,
    ) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (messages) notificationManager.ignoredRemoteMessageUuid =
                    remoteUuid
                if (transfers) notificationManager.ignoredRemoteTransferUuid =
                    remoteUuid
            } else if (event == Lifecycle.Event.ON_PAUSE) {
                if (messages) notificationManager.ignoredRemoteMessageUuid =
                    null
                if (transfers) notificationManager.ignoredRemoteTransferUuid =
                    null
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)

            if (messages) notificationManager.ignoredRemoteMessageUuid = null
            if (transfers) notificationManager.ignoredRemoteTransferUuid = null
        }
    }
}