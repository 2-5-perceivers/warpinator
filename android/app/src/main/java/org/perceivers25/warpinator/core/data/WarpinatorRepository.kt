package org.perceivers25.warpinator.core.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.perceivers25.warpinator.*
import org.perceivers25.warpinator.core.model.preferences.SavedFavourite
import org.perceivers25.warpinator.core.model.ui.RemoteUi
import org.perceivers25.warpinator.core.model.ui.TransferKindUi
import org.perceivers25.warpinator.core.model.ui.TransferUi
import org.perceivers25.warpinator.core.model.ui.UiMessage
import org.perceivers25.warpinator.core.system.AutoAcceptValue
import org.perceivers25.warpinator.core.system.PreferenceManager
import org.perceivers25.warpinator.core.system.WarpinatorPowerManager
import org.perceivers25.warpinator.core.system.WarpinatorVirtualFilesystem
import org.perceivers25.warpinator.core.utils.ProfilePicturePainter
import org.perceivers25.warpinator.core.utils.Utils
import org.perceivers25.warpinator.core.utils.checkWillOverwrite
import org.perceivers25.warpinator.core.utils.messages.NoDownloadDirSet
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

@Singleton
class WarpinatorRepository @Inject constructor(
    @param:ApplicationContext val appContext: Context,
    @param:ApplicationScope val applicationScope: CoroutineScope,
    val powerManager: WarpinatorPowerManager,
) {
    // Server
    var server: Warpinator? = null

    // States
    private val _remoteListState = MutableStateFlow<List<RemoteUi>>(emptyList())
    private val _transfersState =
        ConcurrentHashMap<String, MutableStateFlow<List<TransferUi>>>()
    private val _messagesState =
        ConcurrentHashMap<String, MutableStateFlow<List<Message>>>()
    private val _serviceState = MutableStateFlow<ServiceState>(ServiceState.Ok)
    private val _networkState = MutableStateFlow(NetworkState())
    private val _uiMessages = Channel<UiMessage>(Channel.BUFFERED)
    private val uiMessagesSeenMarkers = mutableSetOf<Any>()

    // Observables
    val remoteListState = _remoteListState.asStateFlow()
    val serviceState = _serviceState.asStateFlow()
    val networkState = _networkState.asStateFlow()
    val uiMessages = _uiMessages.receiveAsFlow()

    val prefs = PreferenceManager(appContext)

    var currentIPInfo: Utils.IPInfo? = null
    val currentIPStr: String?
        get() = (currentIPInfo ?: Utils.iPAddress(
            appContext,
            prefs.networkInterface,
        ))?.address?.hostAddress

    init {
        prefs.loadSettings()
        setTracingSubscriber("WarpinatorLib", LogLevel.DEBUG)
        setVirtualFilesystem(WarpinatorVirtualFilesystem(appContext))
    }

    fun start() {
        // Render profile picture if any
        val stream: ByteArrayOutputStream? =
            prefs.profilePicture?.let { ByteArrayOutputStream() }
        stream?.use {
            ProfilePicturePainter.getProfilePicture(
                picture = prefs.profilePicture!!,
                appContext,
                true,
            ).compress(Bitmap.CompressFormat.PNG, 80, stream)
        }

        // Configure server
        val ipInfo = Utils.iPAddress(appContext, prefs.networkInterface)
        currentIPInfo = ipInfo
        val ipStr = ipInfo?.address?.hostAddress

        val config = UserConfig(
            port = prefs.port.toUShort(),
            regPort = prefs.authPort.toUShort(),
            bindAddrV4 = ipStr,
            bindAddrV6 = null,
            groupCode = prefs.groupCode,
            hostname = Utils.getDeviceName(appContext),
            username = prefs.displayName.replace(" ", "-").lowercase(),
            displayName = prefs.displayName,
            picture = stream?.toByteArray(),
        )

        server = Warpinator(
            config,
            null,
            prefs.serviceUuid ?: Utils.generateServiceName(appContext).let {
                prefs.saveServiceUuid(it)
                it
            },
            powerManager,
        )
        server!!.start(
            object : WarpEventListener {
                override suspend fun onRemoteAdded(uuid: String) {
                    try {
                        val remote = server!!.remote(uuid)
                        addOrUpdateRemote(
                            RemoteUi(
                                uuid = remote.uuid,
                                ip = remote.ip,
                                displayName = remote.displayName,
                                username = remote.username,
                                hostname = remote.hostname,
                                picture = if (remote.picture) server!!.remotePicture(
                                    uuid,
                                ).let {
                                    BitmapFactory.decodeByteArray(
                                        it,
                                        0,
                                        it.size,
                                    )
                                } else null,
                                pictureVersion = remote.pictureVersion.toByte(),
                                state = remote.state,
                                messageSupport = remote.messageSupport,
                                isFavorite = prefs.favourites.contains(
                                    SavedFavourite(uuid),
                                ),
                            ),
                        )
                    } catch (e: WarpException) {
                        // TODO: add message
                    }
                }

                override suspend fun onRemoteUpdated(uuid: String) {
                    try {
                        val remote = server!!.remote(uuid)

                        updateRemoteSuspend(uuid) {
                            it.copy(
                                ip = remote.ip,
                                displayName = remote.displayName,
                                username = remote.username,
                                hostname = remote.hostname,
                                state = remote.state,
                                picture = if (remote.picture) server!!.remotePicture(
                                    uuid,
                                ).let { picture ->
                                        if (remote.pictureVersion.toByte() != it.pictureVersion) {
                                            BitmapFactory.decodeByteArray(
                                                picture,
                                                0,
                                                picture.size,
                                            )
                                        } else it.picture
                                    } else null,
                                pictureVersion = remote.pictureVersion.toByte(),
                                messageSupport = remote.messageSupport,
                            )
                        }
                    } catch (e: WarpException) {
                        // TODO: add message
                    }
                }

                override suspend fun onTransferAdded(
                    remoteUuid: String,
                    transferUuid: String,
                ) {
                    try {
                        val transfer =
                            server!!.transfer(remoteUuid, transferUuid)

                        val transferUi = TransferUi(
                            uuid = transfer.uuid,
                            remoteUuid = transfer.remoteUuid,
                            state = transfer.state,
                            timestamp = transfer.timestamp.toLong(),
                            totalBytes = transfer.totalBytes.toLong(),
                            bytesTransferred = transfer.bytesTransferred.toLong(),
                            bytesPerSecond = transfer.bytesPerSecond.toLong(),
                            fileCount = transfer.fileCount.toLong(),
                            entryNames = transfer.entryNames,
                            singleName = transfer.singleName,
                            singleMimeType = transfer.singleMimeType,
                            kind = when (transfer.kind) {
                                is TransferKind.Incoming -> TransferKindUi.Incoming
                                is TransferKind.Outgoing -> TransferKindUi.Outgoing
                            },
                            overwriteWarning = if (transfer.kind is TransferKind.Incoming && prefs.downloadDirUri != null) checkWillOverwrite(
                                appContext,
                                transfer.entryNames,
                                prefs.downloadDirUri!!.toUri(),
                            ) else false,
                        )

                        addOrUpdateTransfer(
                            remoteUuid,
                            transferUi,
                        )

                        if (transfer.kind is TransferKind.Incoming) {
                            updateRemote(remoteUuid) {
                                it.copy(unreadTransfers = true)
                            }

                            if (!transferUi.overwriteWarning) {
                                when (prefs.autoAccept) {
                                    AutoAcceptValue.Nobody -> {}
                                    AutoAcceptValue.Favourites -> {
                                        if (prefs.favourites.contains(
                                                SavedFavourite(remoteUuid),
                                            )
                                        ) {
                                            acceptTransfer(
                                                remoteUuid,
                                                transferUuid,
                                            )
                                        }
                                    }

                                    AutoAcceptValue.Everyone -> {
                                        acceptTransfer(remoteUuid, transferUuid)
                                    }
                                }
                            }
                        }
                    } catch (e: WarpException) {

                    }
                }

                override suspend fun onTransferUpdated(
                    remoteUuid: String,
                    transferUuid: String,
                ) {
                    try {
                        val transfer =
                            server!!.transfer(remoteUuid, transferUuid)
                        addOrUpdateTransfer(
                            remoteUuid,
                            TransferUi(
                                uuid = transfer.uuid,
                                remoteUuid = transfer.remoteUuid,
                                state = transfer.state,
                                timestamp = transfer.timestamp.toLong(),
                                totalBytes = transfer.totalBytes.toLong(),
                                bytesTransferred = transfer.bytesTransferred.toLong(),
                                bytesPerSecond = transfer.bytesPerSecond.toLong(),
                                fileCount = transfer.fileCount.toLong(),
                                entryNames = transfer.entryNames,
                                singleName = transfer.singleName,
                                singleMimeType = transfer.singleMimeType,
                                kind = when (transfer.kind) {
                                    is TransferKind.Incoming -> TransferKindUi.Incoming
                                    is TransferKind.Outgoing -> TransferKindUi.Outgoing
                                },
                            ),
                        )
                    } catch (e: WarpException) {
                    }
                }

                override suspend fun onTransferRemoved(
                    remoteUuid: String,
                    transferUuid: String,
                ) {
                    _transfersState.getOrPut(remoteUuid) {
                        MutableStateFlow(
                            emptyList(),
                        )
                    }.update { currentList ->
                            currentList.filter { it.uuid != transferUuid }
                        }
                }

                override suspend fun onMessageAdded(
                    remoteUuid: String,
                    messageUuid: String,
                ) {
                    try {
                        val message = server!!.message(remoteUuid, messageUuid)
                        _messagesState.getOrPut(remoteUuid) {
                            MutableStateFlow(
                                emptyList(),
                            )
                        }.update { currentList ->
                                val newList = currentList + message
                                newList
                            }

                        if (message.direction == Direction.RECEIVED) {
                            updateRemote(remoteUuid) {
                                it.copy(unreadMessages = true)
                            }
                        }
                    } catch (e: WarpException) {
                    }
                }

                override suspend fun onMessageRemoved(
                    remoteUuid: String,
                    messageUuid: String,
                ) {
                    _messagesState.getOrPut(remoteUuid) {
                        MutableStateFlow(
                            emptyList(),
                        )
                    }.update { currentList ->
                            currentList.filter { it.uuid != messageUuid }
                        }
                }
            },
        )
    }

    fun restart() {

        try {
            _serviceState.value = ServiceState.Restart
            server?.stop()

            start()
            _serviceState.value = ServiceState.Ok
        } catch (e: WarpException) {

        }

    }

    fun updateServiceState(newState: ServiceState) {
        _serviceState.value = newState
    }

    fun updateNetworkState(function: (NetworkState) -> NetworkState) {
        _networkState.update(function)
    }

    fun addOrUpdateRemote(newRemote: RemoteUi) {
        _remoteListState.update { currentList ->
            val index = currentList.indexOfFirst { it.uuid == newRemote.uuid }
            val newList = if (index != -1) {
                currentList.toMutableList().apply { set(index, newRemote) }
            } else {
                currentList + newRemote
            }
            sortRemotes(newList)
        }
    }

    fun addOrUpdateTransfer(remoteUuid: String, newTransfer: TransferUi) {
        _transfersState.getOrPut(remoteUuid) { MutableStateFlow(emptyList()) }
            .update { currentList ->
                val index =
                    currentList.indexOfFirst { it.uuid == newTransfer.uuid }
                val newList = if (index != -1) {
                    val oldTransfer = currentList[index]

                    // Only update on state changes or if more than 0.5% progress was made (with a minimum of 5KiB)
                    if ((oldTransfer.state != newTransfer.state) || (newTransfer.bytesTransferred - oldTransfer.bytesTransferred > max(
                            oldTransfer.totalBytes / 200,
                            5120,
                        ))
                    ) {
                        currentList.toMutableList().apply {
                            set(
                                index,
                                newTransfer.copy(
                                    overwriteWarning = oldTransfer.overwriteWarning,
                                ),
                            )
                        }

                    } else {
                        currentList
                    }

                } else {
                    currentList + newTransfer
                }
                newList
            }
    }

    suspend fun emitMessage(message: UiMessage, oneTime: Boolean = false) {
        if (oneTime) {
            if (uiMessagesSeenMarkers.contains(message.id)) return
            uiMessagesSeenMarkers.add(message.id!!)
        }
        _uiMessages.send(message)
    }

    // Remotes methods
    fun getRemoteFlow(uuid: String): Flow<RemoteUi?> {
        return _remoteListState.map { list ->
            list.find { it.uuid == uuid }
        }.distinctUntilChanged()
    }

    fun clearRemotes() {
        _remoteListState.value = emptyList()
    }

    fun connectRemote(uuid: String) {
        applicationScope.launch {
            try {
                server?.connectRemote(uuid)
            } catch (e: WarpException) {
            }
        }
    }

    suspend fun manualConnectRemote(address: String) {
        server?.manualConnection(address)
    }

    fun toggleFavorite(uuid: String) {
        updateRemote(uuid) {
            it.copy(isFavorite = prefs.toggleFavorite(uuid))
        }
    }

    fun updateRemote(uuid: String, transform: (RemoteUi) -> RemoteUi) {
        _remoteListState.update { currentList ->
            val newList = currentList.map {
                if (it.uuid == uuid) transform(it) else it
            }
            sortRemotes(newList)
        }
    }

    suspend fun updateRemoteSuspend(
        uuid: String,
        suspendTransform: suspend (RemoteUi) -> RemoteUi,
    ) {
        _remoteListState.update { currentList ->
            val newList = currentList.map {
                if (it.uuid == uuid) {
                    suspendTransform(it)
                } else it
            }
            sortRemotes(newList)
        }
    }

    private fun sortRemotes(list: List<RemoteUi>): List<RemoteUi> {
        return list.sortedWith(
            compareByDescending<RemoteUi> { it.isFavorite }.thenBy {
                it.displayName.takeIf { name -> name.isNotBlank() }
                    ?: it.hostname
            },
        )
    }

    fun getTransfersFlow(remoteUuid: String): Flow<List<TransferUi>> {
        return _transfersState.getOrPut(remoteUuid) { MutableStateFlow(emptyList()) }
            .asStateFlow()
    }

    fun getMessagesFlow(remoteUuid: String): Flow<List<Message>> {
        return _messagesState.getOrPut(remoteUuid) { MutableStateFlow(emptyList()) }
            .asStateFlow()
    }

    fun sendTransferRequest(remoteUuid: String, uris: List<String>) {
        applicationScope.launch {
            try {
                server?.sendTransferRequest(remoteUuid, uris)
            } catch (e: WarpException) {
            }
        }
    }

    suspend fun sendMessage(remoteUuid: String, message: String) {
        try {
            server?.sendMessage(remoteUuid, message)
        } catch (e: WarpException) {
        }
    }

    fun acceptTransfer(remoteUuid: String, transferUuid: String) {
        applicationScope.launch {
            val uri = prefs.downloadDirUri
            if (uri.isNullOrEmpty()) {
                _uiMessages.send(NoDownloadDirSet())
                return@launch
            }
            try {
                server?.acceptTransfer(
                    remoteUuid,
                    transferUuid,
                    prefs.downloadDirUri!!,
                )
            } catch (e: WarpException) {
            }
        }
    }

    fun acceptTransferTo(remoteUuid: String, transferUuid: String, path: Uri) {
        applicationScope.launch {
            try {
                server?.acceptTransfer(
                    remoteUuid,
                    transferUuid,
                    path.toString(),
                )
            } catch (e: WarpException) {
            }
        }
    }

    suspend fun cancelTransfer(remoteUuid: String, transferUuid: String) {
        try {
            server?.cancelTransfer(remoteUuid, transferUuid)
        } catch (e: WarpException) {
        }
    }

    suspend fun stopTransfer(remoteUuid: String, transferUuid: String) {
        try {
            server?.stopTransfer(remoteUuid, transferUuid, false)
        } catch (e: WarpException) {
        }
    }

    suspend fun removeTransfer(remoteUuid: String, transferUuid: String) {
        try {
            server?.removeTransfer(remoteUuid, transferUuid)
        } catch (e: WarpException) {
        }
    }

    suspend fun removeFinalStateTransfers(remoteUuid: String) {
        val finalStateUuids =
            _transfersState.getOrPut(remoteUuid) { MutableStateFlow(emptyList()) }.value.filter {
                it.state is TransferState.Completed || it.state is TransferState.Failed || it.state is TransferState.Canceled || it.state is TransferState.Stopped
            }.map { it.uuid }

        try {
            finalStateUuids.forEach { transferUuid ->
                server?.removeTransfer(
                    remoteUuid,
                    transferUuid,
                )
            }
        } catch (e: WarpException) {
        }

    }

    fun markTransfersSeen(remoteUuid: String) {
        updateRemote(remoteUuid) {
            it.copy(unreadTransfers = false)
        }
    }

    suspend fun removeMessage(remoteUuid: String, messageUuid: String) {
        try {
            server?.removeMessage(remoteUuid, messageUuid)
        } catch (e: WarpException) {
        }
    }

    suspend fun removeAllMessages(remoteUuid: String) {
        val uuids =
            _messagesState.getOrPut(remoteUuid) { MutableStateFlow(emptyList()) }.value.map { it.uuid }
        try {
            uuids.forEach { messageUuid ->
                server?.removeMessage(
                    remoteUuid,
                    messageUuid,
                )
            }
        } catch (e: WarpException) {
        }
    }

    fun markMessagesSeen(remoteUuid: String) {
        updateRemote(remoteUuid) {
            it.copy(unreadMessages = false)
        }
    }
}