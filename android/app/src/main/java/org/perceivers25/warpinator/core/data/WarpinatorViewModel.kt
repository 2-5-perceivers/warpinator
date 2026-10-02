package org.perceivers25.warpinator.core.data

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.perceivers25.warpinator.Message
import org.perceivers25.warpinator.core.model.ui.RemoteUi
import org.perceivers25.warpinator.core.model.ui.TransferUi
import org.perceivers25.warpinator.core.system.PreferenceManager
import org.perceivers25.warpinator.core.utils.LogFileWriter
import javax.inject.Inject

@HiltViewModel
class WarpinatorViewModel @Inject constructor(
    val repository: WarpinatorRepository,
    private val preferenceManager: PreferenceManager,
) : ViewModel() {
    // UI States
    val remoteListState = repository.remoteListState.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList(),
    )

    val serviceState = repository.serviceState.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), ServiceState.Ok,
    )

    val networkState = repository.networkState.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000),
        NetworkState(
            // Set isConnected to true so the UI doesn't show the disconnected state before the Server actually cheks connection
            // TODO: Check in code now, the server doesn't check networks anymore
            isConnected = true, isHotspot = false,
        ),
    )

    val address: String
        get() {
            return "${repository.currentIPStr}:${preferenceManager.authPort}"
        }

    val uiMessages = repository.uiMessages

    fun restart() = repository.restart()

    // Remotes

    fun getRemote(uuid: String): Flow<RemoteUi?> =
        repository.getRemoteFlow(uuid)

    fun getTransfers(uuid: String): Flow<List<TransferUi>> =
        repository.getTransfersFlow(uuid)

    fun getMessages(uuid: String): Flow<List<Message>> =
        repository.getMessagesFlow(uuid)

    fun connectRemote(uuid: String) = repository.connectRemote(uuid)
    suspend fun manualConnectRemote(address: String) =
        repository.manualConnectRemote(address)

    fun toggleFavorite(uuid: String) = repository.toggleFavorite(uuid)

    fun markTransfersSeen(uuid: String) = repository.markTransfersSeen(uuid)
    fun markMessagesSeen(uuid: String) = repository.markMessagesSeen(uuid)

    fun removeTransfer(uuid: String, transferUuid: String) {
        viewModelScope.launch {
            repository.removeTransfer(uuid, transferUuid)
        }
    }

    fun removeFinalStateTransfers(uuid: String) {
        viewModelScope.launch {
            repository.removeFinalStateTransfers(uuid)
        }
    }

    fun removeMessage(uuid: String, messageUuid: String) {
        viewModelScope.launch {
            repository.removeMessage(uuid, messageUuid)
        }
    }

    fun removeAllMessages(remoteUuid: String) {
        viewModelScope.launch {
            repository.removeAllMessages(remoteUuid)
        }
    }

    fun sendTransferRequest(uuid: String, uris: List<Uri>) {
        repository.sendTransferRequest(uuid, uris.map { it.toString() })
    }

    fun sendMessage(uuid: String, message: String) {
        viewModelScope.launch {
            repository.sendMessage(uuid, message)
        }
    }

    fun acceptTransfer(remoteUuid: String, transferUuid: String) {
        repository.acceptTransfer(remoteUuid, transferUuid)

    }

    fun acceptTransferTo(remoteUuid: String, transferUuid: String, path: Uri) {
        repository.acceptTransferTo(remoteUuid, transferUuid, path)
    }

    fun cancelTransfer(remoteUuid: String, transferUuid: String) {
        viewModelScope.launch {
            repository.cancelTransfer(remoteUuid, transferUuid)
        }
    }

    fun stopTransfer(remoteUuid: String, transferUuid: String) {
        viewModelScope.launch {
            repository.stopTransfer(remoteUuid, transferUuid)
        }
    }

    fun retryTransfer(remoteUuid: String, transferUuid: String) {
        viewModelScope.launch {
            // repository.retryTransfer(remoteUuid, transferUuid) TODO
        }
    }

    // Utils
    fun saveLog(uri: Uri) = LogFileWriter.writeLog(
        uri,
        repository.applicationScope,
        repository.appContext,
        repository::emitMessage,
    )
}