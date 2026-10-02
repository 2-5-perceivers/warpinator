package org.perceivers25.warpinator.core.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.perceivers25.warpinator.core.model.ui.UiMessage
import org.perceivers25.warpinator.core.utils.messages.FailedToWriteLog
import org.perceivers25.warpinator.core.utils.messages.SucceededToWriteLog
import java.io.File

object LogFileWriter {
    suspend fun generateDumpLog(context: Context): File? =
        withContext(Dispatchers.IO) {
            Log.d(TAG, "Saving log...")

            val cacheDir = context.externalCacheDir ?: context.cacheDir
            val output = File(cacheDir, "dump.log")

            try {
                val cmd = arrayOf("logcat", "-d", "-f", output.absolutePath)
                val process = Runtime.getRuntime().exec(cmd)
                process.waitFor()
                output
            } catch (e: Exception) {
                Log.e(TAG, "Failed to dump log", e)
                null
            }
        }

    fun writeLog(
        uri: Uri,
        scope: CoroutineScope,
        context: Context,
        emitMessage: suspend (UiMessage) -> Unit,
    ) {
        try {
            scope.launch {
                val file = generateDumpLog(context)
                file?.let {
                    context.contentResolver.openOutputStream(uri)
                        ?.use { outputStream ->
                            file.inputStream().use { inputStream ->
                                inputStream.copyTo(outputStream)
                            }
                        }
                    emitMessage(SucceededToWriteLog())
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not save log to file", e)
            scope.launch {
                emitMessage(FailedToWriteLog(e))
            }
        }
    }

    private const val TAG = "LogFileWriter"
}