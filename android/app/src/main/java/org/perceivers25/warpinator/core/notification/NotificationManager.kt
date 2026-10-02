package org.perceivers25.warpinator.core.notification

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import org.perceivers25.warpinator.R
import org.perceivers25.warpinator.app.MainActivity
import org.perceivers25.warpinator.core.service.MainService
import org.perceivers25.warpinator.core.service.StopSvcReceiver
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WarpinatorNotificationManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    private val notificationMgr: NotificationManagerCompat =
        NotificationManagerCompat.from(context)
    private var progressBuilder: NotificationCompat.Builder? = null

    var ignoredRemoteTransferUuid: String? = null
        set(value) {
            field = value
            if (value != null) {
                notificationMgr.activeNotifications.forEach { statusBarNotification ->
                    val notification = statusBarNotification.notification

                    if (notification.extras.getString("remote") == value && !notification.extras.getBoolean(
                            "message",
                            false,
                        )
                    ) {
                        notificationMgr.cancel(
                            statusBarNotification.tag,
                            statusBarNotification.id,
                        )
                    }
                }
            }
        }
    var ignoredRemoteMessageUuid: String? = null
        set(value) {
            field = value
            if (value != null) {
                notificationMgr.activeNotifications.forEach { statusBarNotification ->
                    val notification = statusBarNotification.notification

                    if (notification.extras.getString("remote") == value && notification.extras.getBoolean(
                            "message",
                            false,
                        )
                    ) {
                        notificationMgr.cancel(
                            statusBarNotification.tag,
                            statusBarNotification.id,
                        )
                    }
                }
            }
        }

    init {
        createNotificationChannels()
    }

    fun createForegroundNotification(): Notification {
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags =
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val immutable = PendingIntent.FLAG_IMMUTABLE
        val pendingIntent =
            PendingIntent.getActivity(context, 0, openIntent, immutable)

        val stopIntent = Intent(context, StopSvcReceiver::class.java).apply {
            action = MainService.ACTION_STOP
        }
        val stopPendingIntent =
            PendingIntent.getBroadcast(context, 0, stopIntent, immutable)

        return NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setContentTitle(context.getString(R.string.warpinator_notification_title))
            .setContentText(context.getString(R.string.warpinator_notification_subtitle))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .addAction(
                0,
                context.getString(R.string.warpinator_notification_button),
                stopPendingIntent,
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setShowWhen(false)
            .setOngoing(true)
            .build()
    }

    @SuppressLint("MissingPermission")
    fun showIncomingTransfer(
        remoteName: String?,
        remoteUuid: String,
        fileCount: Long,
        singleFileName: String?,
        transferUuid: String,
    ) {
        if (!hasPermission() || ignoredRemoteTransferUuid == remoteUuid) return

        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("remote", remoteUuid)
        }

        val notificationId = remoteUuid.hashCode() xor transferUuid.hashCode()

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_IMMUTABLE,
        )

        val contentText = if (fileCount == 1L) singleFileName
        else context.resources.getQuantityString(
            R.plurals.notification_file_count,
            fileCount.toInt(), fileCount,
        )

        val notification =
            NotificationCompat.Builder(context, CHANNEL_INCOMING_TRANSFERS)
                .setContentTitle(
                    context.getString(
                        R.string.incoming_transfer_notification,
                        remoteName ?: context.getString(
                            R.string.unknown,
                        ),
                    ),
                )
                .setContentText(contentText)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setGroup(GROUP_INCOMING)
                .setExtras(
                    Bundle().apply {
                        putString("remote", remoteUuid)
                    },
                )
                .build()

        notificationMgr.notify(notificationId, notification)
    }

    @SuppressLint("MissingPermission")
    fun showMessage(
        remoteName: String?,
        remoteBitmap: Bitmap?,
        remoteUuid: String,
        message: String,
        messageTimestamp: Long,
    ) {
        if (!hasPermission() || ignoredRemoteMessageUuid == remoteUuid) return

        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("remote", remoteUuid)
            putExtra("messages", true)
        }

        val notificationId =
            remoteUuid.hashCode() xor message.hashCode() xor messageTimestamp.hashCode()

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setContentTitle(
                context.getString(
                    R.string.new_message_from,
                    remoteName ?: context.getString(R.string.unknown),
                ),
            )
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setGroup(GROUP_MESSAGES)
            .setExtras(
                Bundle().apply {
                    putString("remote", remoteUuid)
                    putBoolean("message", true)
                },

                )

        if (remoteBitmap != null) {
            notification.setLargeIcon(remoteBitmap)
        }


        notificationMgr.notify(notificationId, notification.build())
    }

    /**
     * Updates the global progress notification.
     * @return True if transfers are running, False if finished/idle.
     */
    // @SuppressLint("MissingPermission")
    // fun updateProgressNotification(remotes: List<Remote>): Boolean {
    //     if (!hasPermission()) return false
    //
    //     if (progressBuilder == null) {
    //         progressBuilder = NotificationCompat.Builder(context, CHANNEL_PROGRESS)
    //             .setSmallIcon(R.drawable.ic_notification).setOngoing(true)
    //             .setPriority(NotificationCompat.PRIORITY_LOW).setOnlyAlertOnce(true)
    //     }
    //
    //     var runningTransfers = 0
    //     var bytesDone: Long = 0
    //     var bytesTotal: Long = 0
    //     var bytesPerSecond: Long = 0
    //
    //     for (r in remotes) {
    //         for (t in r.transfers) {
    //             if (t.status is Transfer.Status.Transferring) {
    //                 runningTransfers++
    //                 bytesDone += t.bytesTransferred
    //                 bytesTotal += t.totalSize
    //                 bytesPerSecond += t.bytesPerSecond
    //             }
    //         }
    //     }
    //
    //     val builder = progressBuilder!!
    //
    //     if (runningTransfers > 0) {
    //         val progress =
    //             if (bytesTotal > 0) (bytesDone.toFloat() / bytesTotal * 1000f).toInt() else 0
    //
    //         builder.setOngoing(true)
    //         builder.setProgress(1000, progress, false)
    //         builder.setContentTitle(
    //             String.format(
    //                 Locale.getDefault(),
    //                 context.getString(R.string.transfer_notification),
    //                 progress / 10f,
    //                 runningTransfers,
    //                 Formatter.formatFileSize(context, bytesPerSecond),
    //             ),
    //         )
    //         notificationMgr.notify(PROGRESS_NOTIFICATION_ID, builder.build())
    //         return true
    //     } else {
    //         // Transfers finished or stopped
    //         notificationMgr.cancel(PROGRESS_NOTIFICATION_ID)
    //         return false
    //     }
    // }

    fun cancelAll() {
        notificationMgr.cancelAll()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager =
                context.getSystemService(NotificationManager::class.java)
                    ?: return

            val serviceChannel = NotificationChannel(
                CHANNEL_SERVICE,
                context.getString(R.string.service_running),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description =
                    context.getString(R.string.notification_channel_description)
            }

            val incomingChannel = NotificationChannel(
                CHANNEL_INCOMING_TRANSFERS,
                context.getString(R.string.incoming_transfer_channel),
                NotificationManager.IMPORTANCE_HIGH,
            )

            val messagesChannel = NotificationChannel(
                CHANNEL_MESSAGES,
                context.getString(R.string.messages_channel),
                NotificationManager.IMPORTANCE_HIGH,
            )

            val progressChannel = NotificationChannel(
                CHANNEL_PROGRESS,
                context.getString(R.string.transfer_progress_channel),
                NotificationManager.IMPORTANCE_LOW,
            )

            manager.createNotificationChannel(serviceChannel)
            manager.createNotificationChannel(incomingChannel)
            manager.createNotificationChannel(messagesChannel)
            manager.createNotificationChannel(progressChannel)
        }
    }

    private fun hasPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    companion object {
        const val CHANNEL_SERVICE = "MainService"
        const val CHANNEL_INCOMING_TRANSFERS = "IncomingTransfer"
        const val CHANNEL_MESSAGES = "NewMessage"
        const val CHANNEL_PROGRESS = "TransferProgress"
        const val GROUP_INCOMING = "org.perceivers25.warpinator.INCOMING_GROUP"
        const val GROUP_MESSAGES = "org.perceivers25.warpinator.MESSAGES_GROUP"

        const val FOREGROUND_SERVICE_ID = 1
        const val PROGRESS_NOTIFICATION_ID = 2
    }
}