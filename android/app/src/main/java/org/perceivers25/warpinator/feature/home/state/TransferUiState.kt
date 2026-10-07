package org.perceivers25.warpinator.feature.home.state

import android.text.format.Formatter
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.core.text.BidiFormatter
import org.perceivers25.warpinator.R
import org.perceivers25.warpinator.TransferState
import org.perceivers25.warpinator.core.model.ui.TransferKindUi
import org.perceivers25.warpinator.core.model.ui.TransferUi

enum class TransferUiActionButtons {
    // Accept and decline(cancel) buttons
    AcceptAndDecline,

    // A stop button for when in progress
    Stop,

    /// A cancel button for when awaiting permission on outgoing transfers
    Cancel,

    /// A button to retry a failed/canceled outgoing transfer
    Retry,

    /// Open the destination folder for a completed transfer
    OpenFolder,

    /// No buttons
    None
}

enum class TransferUiProgressIndicator {
    Active, Static, None
}

data class TransferUiState(
    val id: String,
    val title: String,
    val statusText: String,
    val statusLongText: String,
    val totalSize: String,
    val isSending: Boolean,
    val progressFloat: Float,
    val iconColor: Color,
    val allowDismiss: Boolean,
    val actionButtons: TransferUiActionButtons,
    val progressIndicator: TransferUiProgressIndicator,
    val useCompression: Boolean = false,
)

@Composable
fun TransferUi.toUiState(): TransferUiState {
    val context = LocalContext.current

    val title = if (this.fileCount == 1L) {
        this.singleName ?: this.entryNames.firstOrNull() ?: "File"
    } else {
        pluralStringResource(
            R.plurals.transfer_files_count,
            this.fileCount.toInt(),
            this.fileCount.toInt(),
        )
    }

    val bidi = BidiFormatter.getInstance()

    val totalSizeStr = remember(this.totalBytes) {
        Formatter.formatFileSize(
            context,
            this.totalBytes,
        ).let { bidi.unicodeWrap(it) }
    }
    val transferredSizeStr = remember(this.bytesTransferred) {
        Formatter.formatFileSize(
            context,
            this.bytesTransferred,
        ).let { bidi.unicodeWrap(it) }
    }
    val transferSpeedSizeStr = remember(this.bytesPerSecond) {
        Formatter.formatFileSize(
            context,
            this.bytesPerSecond,
        )
    }

    val transferSpeedStr = stringResource(
        R.string.transfer_speed_fmt,
        transferSpeedSizeStr,
    ).let { bidi.unicodeWrap(it) }


    val (statusText, statusLongText) = this.getStatusStrings(
        transferredSizeStr,
        totalSizeStr,
        transferSpeedStr,
        bidi,
    )

    val progressFloat = if (this.totalBytes > 0L) {
        this.bytesTransferred.toFloat() / this.totalBytes.toFloat()
    } else 0f

    val isOutgoing = this.kind == TransferKindUi.Outgoing
    val isRetryable =
        isOutgoing && (state is TransferState.Failed || state == TransferState.Stopped || state is TransferState.Canceled)

    val allowDismiss =
        state is TransferState.Failed || state == TransferState.Stopped || state == TransferState.Canceled || state == TransferState.Completed

    val actionButtons = when {
        state == TransferState.WaitingPermission && !isOutgoing -> TransferUiActionButtons.AcceptAndDecline
        (state == TransferState.WaitingPermission && isOutgoing) -> TransferUiActionButtons.Cancel
        state == TransferState.InProgress -> TransferUiActionButtons.Stop
        isRetryable -> TransferUiActionButtons.Retry
        state == TransferState.Completed && !isOutgoing -> TransferUiActionButtons.OpenFolder
        else -> TransferUiActionButtons.None
    }

    val progressIndicator = when {
        state == TransferState.InProgress -> TransferUiProgressIndicator.Active
        state != TransferState.WaitingPermission && state != TransferState.Canceled -> TransferUiProgressIndicator.Static
        else -> TransferUiProgressIndicator.None
    }

    return TransferUiState(
        id = this.uuid,
        title = title,
        statusText = statusText,
        statusLongText = statusLongText,
        totalSize = totalSizeStr,
        isSending = isOutgoing,
        progressFloat = progressFloat,
        iconColor = this.getStatusColor(),
        allowDismiss = allowDismiss,
        actionButtons = actionButtons,
        progressIndicator = progressIndicator,
        useCompression = this.useCompression,
    )
}

@Composable
private fun TransferUi.getStatusStrings(
    transferredSizeStr: String,
    totalSizeStr: String,
    transferSpeedStr: String,
    bidi: BidiFormatter,
): Pair<String, String> {
    return when (this.state) {
        TransferState.WaitingPermission -> {
            val str = if (this.overwriteWarning) {
                stringResource(R.string.transfer_state_waiting_permission_overwrite_warning)
            } else {
                stringResource(R.string.transfer_state_waiting_permission)
            }
            str to str
        }

        TransferState.InProgress -> {
            val remaining = this.getRemainingTime()
            val remainingString = when {
                remaining == null -> stringResource(R.string.time_indefinite)
                remaining <= 5 -> stringResource(R.string.time_few_seconds_remaining)
                remaining < 60 -> pluralStringResource(
                    R.plurals.time_seconds_remaining,
                    remaining,
                    remaining,
                )

                remaining < 3600 -> {
                    val seconds = (remaining % 60).let {
                        pluralStringResource(
                            R.plurals.duration_seconds_short,
                            it,
                            it,
                        )
                    }
                    val minutes = (remaining / 60).let {
                        pluralStringResource(
                            R.plurals.duration_minutes_short,
                            it,
                            it,
                        )
                    }

                    stringResource(
                        R.string.time_details_remaining_fmt,
                        minutes,
                        seconds,
                    )
                }

                remaining < 86400 -> {
                    val hours = (remaining / 3600).let {
                        pluralStringResource(
                            R.plurals.duration_hours_short,
                            it,
                            it,
                        )
                    }
                    val minutes = ((remaining % 3600) / 60).let {
                        pluralStringResource(
                            R.plurals.duration_minutes_short,
                            it,
                            it,
                        )
                    }

                    stringResource(
                        R.string.time_details_remaining_fmt,
                        hours,
                        minutes,
                    )
                }

                else -> stringResource(R.string.time_over_day)
            }.let { bidi.unicodeWrap(it) }

            val short = stringResource(
                R.string.transfer_short_status_fmt,
                transferredSizeStr,
                totalSizeStr,
            )
            val long = stringResource(
                R.string.transfer_long_status_fmt,
                transferredSizeStr,
                totalSizeStr,
                transferSpeedStr,
                remainingString,
            )

            short to long
        }

        TransferState.Canceled -> {
            val str = stringResource(R.string.transfer_state_canceled)

            str to str
        }

        is TransferState.Failed -> {
            val str = stringResource(R.string.transfer_state_failed)
            val details = (state as TransferState.Failed).v1.toString()

            str to "$str\n$details"
        }

        TransferState.Completed -> {
            val str = stringResource(R.string.transfer_state_finished)

            str to str
        }

        TransferState.Initializing -> {
            val str = stringResource(R.string.transfer_state_init)

            str to str
        }

        TransferState.Paused -> {
            val str = stringResource(R.string.transfer_state_paused)

            str to str
        }

        TransferState.Stopped -> {
            val str = stringResource(R.string.transfer_state_stopped)

            str to str

        }

        TransferState.Denied -> {
            val str = stringResource(R.string.transfer_state_declined)

            str to str
        }
    }
}

private fun TransferUi.getRemainingTime(): Int? {
    if (bytesPerSecond == 0L) {
        return null
    }
    val secondsRemaining =
        ((totalBytes - bytesTransferred) / bytesPerSecond).toInt()
    return secondsRemaining
}

@Composable
private fun TransferUi.getStatusColor(): Color {
    return when (state) {
        is TransferState.Failed -> MaterialTheme.colorScheme.error
        TransferState.Completed -> MaterialTheme.colorScheme.primary
        TransferState.InProgress -> MaterialTheme.colorScheme.tertiary
        else -> LocalContentColor.current
    }
}