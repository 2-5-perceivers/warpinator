package org.perceivers25.warpinator.core.model.ui

import org.perceivers25.warpinator.TransferState

enum class TransferKindUi {
    Outgoing, Incoming,
}

data class TransferUi(
    var uuid: String = "",
    var remoteUuid: String = "",
    var state: TransferState = TransferState.Initializing,
    var timestamp: Long = 0,
    var totalBytes: Long = 0,
    var bytesTransferred: Long = 0,
    var bytesPerSecond: Long = 0,
    var fileCount: Long = 0,
    var entryNames: List<String> = emptyList(),
    var singleName: String? = null,
    var singleMimeType: String? = null,
    var kind: TransferKindUi = TransferKindUi.Outgoing,
    var overwriteWarning: Boolean = false,
    var useCompression: Boolean = false,
)
