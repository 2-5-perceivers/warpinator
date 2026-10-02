package org.perceivers25.warpinator.core.system

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.core.net.toUri
import org.perceivers25.warpinator.VirtualEntry
import org.perceivers25.warpinator.VirtualFilesystem
import org.perceivers25.warpinator.VirtualFilesystemException
import org.perceivers25.warpinator.VirtualMetadata

class WarpinatorVirtualFilesystem(val context: Context) : VirtualFilesystem {
    override suspend fun metadata(path: String): VirtualMetadata {
        val uri = path.toUri()
        return try {
            val isTreeUri = DocumentsContract.isTreeUri(uri)

            if (isTreeUri) {
                // For tree Uris, we need to get the document ID first
                val documentId = DocumentsContract.getTreeDocumentId(uri)
                val documentUri =
                    DocumentsContract.buildDocumentUriUsingTree(uri, documentId)
                queryMetadata(documentUri)
            } else {
                // For single document Uris
                queryMetadata(uri)
            }
        } catch (e: Exception) {
            Log.e(TAG, "error: $e")
            throw VirtualFilesystemException.FileNotFound()
        }
    }

    private fun queryMetadata(uri: Uri): VirtualMetadata {
        val cursor = context.contentResolver.query(
            uri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE,
            ),
            null,
            null,
            null,
        )

        return cursor?.use {
            if (it.moveToFirst()) {
                val name =
                    it.getString(it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME))
                val size =
                    it.getLong(it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE))
                val mimeType =
                    it.getString(it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE))
                val isDirectory =
                    mimeType == DocumentsContract.Document.MIME_TYPE_DIR

                VirtualMetadata(isDirectory, name, size.toULong(), 0.toULong())
            } else {
                throw VirtualFilesystemException.FileNotFound()
            }
        } ?: throw VirtualFilesystemException.FileNotFound()
    }

    override suspend fun readDir(path: String): List<VirtualMetadata> {
        val uri = path.toUri()
        val results = mutableListOf<VirtualMetadata>()

        try {
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                uri,
                DocumentsContract.getTreeDocumentId(uri),
            )

            val cursor = context.contentResolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_SIZE,
                ),
                null,
                null,
                null,
            )

            cursor?.use {
                while (it.moveToNext()) {
                    val documentId =
                        it.getString(it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID))
                    val name =
                        it.getString(it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME))
                    val mimeType =
                        it.getString(it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE))
                    val size =
                        it.getLong(it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE))

                    val isDirectory =
                        mimeType == DocumentsContract.Document.MIME_TYPE_DIR

                    val fileInfo = if (isDirectory) {
                        val (fileCount, totalSize) = calculateDirectoryStats(
                            context,
                            uri,
                            documentId,
                        )
                        VirtualMetadata(
                            isDir = true,
                            name = name,
                            size = totalSize.toULong(),
                            fileCount = fileCount.toULong(),
                        )
                    } else {
                        VirtualMetadata(
                            isDir = false,
                            name = name,
                            size = size.toULong(),
                            fileCount = 1.toULong(),
                        )
                    }
                    results.add(fileInfo)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error listing directory", e)
        }

        return results
    }

    override suspend fun listDir(path: String): List<VirtualEntry> {
        val uri = path.toUri()
        val results = mutableListOf<VirtualEntry>()

        try {
            val documentId = try {
                if (DocumentsContract.isDocumentUri(context, uri)) {
                    DocumentsContract.getDocumentId(uri)
                } else {
                    DocumentsContract.getTreeDocumentId(uri)
                }
            } catch (_: Exception) {
                return emptyList()
            }

            val childrenQueryUri =
                DocumentsContract.buildChildDocumentsUriUsingTree(
                    uri,
                    documentId,
                )

            val cursor = context.contentResolver.query(
                childrenQueryUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                ),
                null,
                null,
                null,
            )

            cursor?.use {
                while (it.moveToNext()) {
                    val documentId =
                        it.getString(it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID))
                    val name =
                        it.getString(it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME))
                    val mimeType =
                        it.getString(it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE))

                    val isDirectory =
                        mimeType == DocumentsContract.Document.MIME_TYPE_DIR

                    val childUri = DocumentsContract.buildDocumentUriUsingTree(
                        uri,
                        documentId,
                    ).toString()

                    val entryInfo = VirtualEntry(
                        name = name,
                        path = childUri,
                        isDir = isDirectory,
                    )
                    results.add(entryInfo)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error listing directory", e)
        }

        Log.w(TAG, "listDir: $results")

        return results
    }

    override suspend fun createDir(path: String, folder: String): String {
        val uri = path.toUri()
        try {
            val resolver = context.contentResolver

            var currentDocId = DocumentsContract.getTreeDocumentId(uri)
            var currentUri =
                DocumentsContract.buildDocumentUriUsingTree(uri, currentDocId)

            val parts = folder.split("/").filter { it.isNotBlank() }

            for (part in parts) {
                val childrenUri =
                    DocumentsContract.buildChildDocumentsUriUsingTree(
                        uri,
                        currentDocId,
                    )
                var foundDocId: String? = null
                resolver.query(
                    childrenUri,
                    arrayOf(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_MIME_TYPE,
                    ),
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    val idIndex =
                        cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameIndex =
                        cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeIndex =
                        cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)

                    while (cursor.moveToNext()) {
                        val name = cursor.getString(nameIndex)
                        val mimeType = cursor.getString(mimeIndex)

                        if (name == part && mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                            foundDocId = cursor.getString(idIndex)
                            break
                        }
                    }
                }

                if (foundDocId != null) {
                    currentDocId = foundDocId
                    currentUri = DocumentsContract.buildDocumentUriUsingTree(
                        uri,
                        currentDocId,
                    )
                } else {
                    val newDirUri = DocumentsContract.createDocument(
                        resolver,
                        currentUri,
                        DocumentsContract.Document.MIME_TYPE_DIR,
                        part,
                    ) ?: throw VirtualFilesystemException.FileCreateException()

                    currentDocId = DocumentsContract.getDocumentId(newDirUri)
                    currentUri = newDirUri
                }

            }
            if (currentDocId == null) {
                throw VirtualFilesystemException.FileCreateException()
            } else {
                return currentUri.toString()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating directory", e)
            throw VirtualFilesystemException.FileCreateException()
        }
    }

    override suspend fun openFile(path: String): Int {
        /// Load a file descriptor and detach it
        val uri = path.toUri()
        return try {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                ?: throw VirtualFilesystemException.FileNotFound()
            pfd.detachFd()
        } catch (e: Exception) {
            Log.e(TAG, "Error opening file", e)
            throw VirtualFilesystemException.FileNotFound()
        }
    }

    override suspend fun createFile(path: String, file: String): Int {
        val uri = path.toUri()
        return try {
            val resolver = context.contentResolver

            val lastSlashIndex = file.lastIndexOf('/')
            val folderPath = if (lastSlashIndex != -1) file.substring(
                0,
                lastSlashIndex,
            ) else ""
            val fileName =
                if (lastSlashIndex != -1) file.substring(lastSlashIndex + 1) else file

            if (fileName.isBlank()) {
                throw VirtualFilesystemException.FileCreateException()
            }

            val parentDocId = if (folderPath.isNotEmpty()) {
                val dirUri = createDir(path, folderPath).toUri()
                DocumentsContract.getDocumentId(dirUri)
            } else {
                DocumentsContract.getTreeDocumentId(uri)
            }

            val parentDirUri =
                DocumentsContract.buildDocumentUriUsingTree(uri, parentDocId)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                uri,
                parentDocId,
            )

            var foundFileUri: Uri? = null

            resolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                ),
                null,
                null,
                null,
            )?.use { cursor ->
                val idIndex =
                    cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIndex =
                    cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)

                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIndex)
                    if (name == fileName) {
                        val docId = cursor.getString(idIndex)
                        foundFileUri =
                            DocumentsContract.buildDocumentUriUsingTree(
                                uri,
                                docId,
                            )
                        break
                    }
                }
            }

            val fileUri = if (foundFileUri != null) {
                foundFileUri
            } else {
                // Best effort to guess the MIME type, fallback to binary stream
                val extension = fileName.substringAfterLast('.', "")
                val mimeType = MimeTypeMap.getSingleton()
                    .getMimeTypeFromExtension(extension)
                    ?: "application/octet-stream"

                DocumentsContract.createDocument(
                    resolver,
                    parentDirUri,
                    mimeType,
                    fileName,
                ) ?: throw VirtualFilesystemException.FileCreateException()
            }

            val pfd = resolver.openFileDescriptor(fileUri, "w")
                ?: throw VirtualFilesystemException.FileCreateException()

            pfd.detachFd()
        } catch (e: Exception) {
            Log.e(TAG, "Error creating file", e)
            throw VirtualFilesystemException.FileCreateException()
        }
    }

    fun calculateDirectoryStats(
        context: Context,
        treeUri: Uri,
        documentId: String,
    ): Pair<Int, Long> {
        var totalFileCount = 0
        var totalSize = 0L

        try {
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                treeUri,
                documentId,
            )

            val cursor = context.contentResolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_SIZE,
                ),
                null,
                null,
                null,
            )

            cursor?.use {
                while (it.moveToNext()) {
                    val childDocumentId =
                        it.getString(it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID))
                    val mimeType =
                        it.getString(it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE))
                    val size =
                        it.getLong(it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE))

                    val isDirectory =
                        mimeType == DocumentsContract.Document.MIME_TYPE_DIR

                    if (isDirectory) {
                        val (childFileCount, childSize) = calculateDirectoryStats(
                            context,
                            treeUri,
                            childDocumentId,
                        )
                        totalFileCount += childFileCount
                        totalSize += childSize
                    } else {
                        totalFileCount += 1
                        totalSize += size
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating stats", e)
        }

        return Pair(totalFileCount, totalSize)
    }

    companion object {
        const val TAG = "WarpinatorVirtualFilesystem"

    }
}