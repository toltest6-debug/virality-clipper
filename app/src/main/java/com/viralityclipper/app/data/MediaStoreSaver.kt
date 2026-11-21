package com.viralityclipper.app.data

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.media.MediaStore
import android.net.Uri
import java.io.File

/**
 * Writes videos to app-private hidden storage so they appear under
 * "Movies/ViralityClipper" in the system gallery on Android 10+.
 */
class MediaStoreSaver(context: Context) {

    private val appContext = context.applicationContext

    /**
     * Copies [file] into MediaStore and returns the created [Uri].
     * Deletes the inserted row on failure.
     */
    fun saveVideoFile(file: File, title: String): Uri? {
        val resolver = appContext.contentResolver
        val displayName = title.ifBlank { "ViralityClipper short" }

        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/ViralityClipper")
            put(MediaStore.Video.Media.SIZE, file.length())
            put(MediaStore.Video.Media.IS_PENDING, 0)
            put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
            put(MediaStore.Video.Media.DATE_MODIFIED, System.currentTimeMillis() / 1000)
        }

        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw MediaStoreSaverException("MediaStore refused to insert the video")

        try {
            val outputStream = resolver.openOutputStream(uri)
                ?: throw MediaStoreSaverException("Could not open output stream for the video")
            outputStream.use { out -> file.inputStream().use { it.copyTo(out) } }
            return uri
        } catch (e: Exception) {
            try {
                resolver.delete(uri, null, null)
            } catch (_: Exception) { /* ignore */ }
            throw MediaStoreSaverException("Failed to write video to storage: ${e.message}", e)
        }
    }

    class MediaStoreSaverException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
}
