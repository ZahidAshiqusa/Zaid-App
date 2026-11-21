package com.zaidashiq.chat.data.network

import android.content.ContentResolver
import android.net.Uri
import android.os.SystemClock
import android.provider.OpenableColumns
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.IOException

data class UriFileMeta(
    val displayName: String,
    val sizeBytes: Long,
    val mimeType: String
)

fun resolveUriMeta(
    contentResolver: ContentResolver,
    uri: Uri,
    fallbackName: String,
    fallbackMime: String
): UriFileMeta {
    var name = fallbackName
    var size = -1L
    try {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0 && !cursor.isNull(nameIdx)) {
                        name = cursor.getString(nameIdx) ?: fallbackName
                    }
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                        size = cursor.getLong(sizeIdx)
                    }
                }
            }
    } catch (_: Exception) {
    }
    if (size <= 0L) {
        try {
            contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                if (afd.length > 0L) size = afd.length
            }
        } catch (_: Exception) {
        }
    }
    val mime = contentResolver.getType(uri) ?: fallbackMime
    return UriFileMeta(displayName = name, sizeBytes = size, mimeType = mime)
}

/**
 * Streams media bytes directly from a ContentResolver URI in 8 KB chunks without
 * loading large videos or voice notes into memory, emitting throttled network progress.
 */
class ProgressRequestBody(
    private val contentResolver: ContentResolver,
    private val uri: Uri,
    private val contentTypeStr: String,
    private val knownSize: Long,
    private val onProgress: (bytesWritten: Long, totalBytes: Long) -> Unit
) : RequestBody() {

    override fun contentType(): MediaType? = contentTypeStr.toMediaTypeOrNull()

    override fun contentLength(): Long = if (knownSize > 0L) knownSize else -1L

    override fun writeTo(sink: BufferedSink) {
        val input = contentResolver.openInputStream(uri)
            ?: throw IOException("Unable to open selected media stream")
        input.use { stream ->
            val buffer = ByteArray(8 * 1024)
            var uploaded = 0L
            var lastEmitMs = 0L
            onProgress(0L, knownSize)

            while (true) {
                val read = stream.read(buffer)
                if (read == -1) break
                sink.write(buffer, 0, read)
                uploaded += read

                val now = SystemClock.elapsedRealtime()
                if (now - lastEmitMs >= 65L || (knownSize > 0L && uploaded >= knownSize)) {
                    lastEmitMs = now
                    onProgress(uploaded, knownSize)
                }
            }
            sink.flush()
            onProgress(uploaded, if (knownSize > 0L) knownSize else uploaded)
        }
    }
}
