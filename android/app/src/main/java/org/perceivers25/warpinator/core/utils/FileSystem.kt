package org.perceivers25.warpinator.core.utils

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import androidx.core.net.toUri
import java.io.File

fun checkWillOverwrite(
    context: Context,
    entries: List<String>,
    pathUri: Uri,
): Boolean {
    if (entries.isEmpty()) return false

    val treeDocId = DocumentsContract.getTreeDocumentId(pathUri)
    val childrenUri =
        DocumentsContract.buildChildDocumentsUriUsingTree(pathUri, treeDocId)

    context.contentResolver.query(
        childrenUri,
        arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
        null, null, null,
    )?.use { cursor ->
        val nameIndex =
            cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        while (cursor.moveToNext()) {
            if (cursor.getString(nameIndex) in entries) return true
        }
    }

    return false
}

fun getDefaultDownloadDir(): File = File(
    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
    "Warpinator",
)

/**
 * Validates that the currently saved download directory URI:
 * Has a persisted read+write URI permission granted to this app, AND
 * The directory can actually be queried (i.e. it hasn't been deleted).
 *
 * For devices below API 29, the legacy Files path is used and the directory is
 * created if it doesn't exist yet — no SAF permission needed.
 *
 * @return `true` if the directory is valid and ready to use, `false` if setup is needed.
 */
fun validateDownloadDir(context: Context, savedUri: String?): Boolean {
    // Legacy path: API < Q — use the public Downloads/Warpinator folder directly
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        val dir = getDefaultDownloadDir()
        if (dir.exists()) return true
        return dir.mkdirs() // false if storage is unavailable or not writable
    }

    // No URI has ever been saved
    if (savedUri.isNullOrEmpty()) return false

    // Content-URI path: verify the app still holds the persisted permission
    if (!savedUri.startsWith("content://")) {
        // Unexpected format – treat as invalid
        return false
    }

    val uri = savedUri.toUri()

    // Check that a persisted permission still exists for this URI
    val hasPermission = context.contentResolver.persistedUriPermissions.any {
        it.uri == uri && it.isReadPermission && it.isWritePermission
    }
    if (!hasPermission) return false

    // Verify the folder itself still exists by querying its children
    return try {
        val treeDocId = DocumentsContract.getTreeDocumentId(uri)
        val childrenUri =
            DocumentsContract.buildChildDocumentsUriUsingTree(uri, treeDocId)
        context.contentResolver.query(
            childrenUri,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID),
            null, null, null,
        )?.use { true } ?: false
    } catch (_: Exception) {
        false
    }
}