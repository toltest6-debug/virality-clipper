package com.viralityclipper.app.data

import android.content.Context
import android.provider.MediaStore
import android.net.Uri

class SavedShortsRepository(context: Context) {

    private val appContext = context.applicationContext

    data class SavedVideo(
        val id: Long,
        val name: String,
        val uri: Uri,
        val sizeBytes: Long,
        val durationMs: Long,
        val dateAddedMs: Long
    )

    /** Lists videos saved under Movies/ViralityClipper (newest first). */
    fun getSavedVideos(): List<SavedVideo> {
        val cursor = try {
            appContext.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                arrayOf(
                    MediaStore.Video.Media._ID,
                    MediaStore.Video.Media.DISPLAY_NAME,
                    MediaStore.Video.Media.SIZE,
                    MediaStore.Video.Media.DURATION,
                    MediaStore.Video.Media.DATE_ADDED
                ),
                "${MediaStore.Video.Media.RELATIVE_PATH} = ?",
                arrayOf("Movies/ViralityClipper"),
                "${MediaStore.Video.Media.DATE_ADDED} DESC"
            )
        } catch (_: Exception) {
            null
        } ?: return emptyList()

        try {
            val result = mutableListOf<SavedVideo>()
            while (cursor.moveToNext()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID))
                val name = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)) ?: "Short"
                val size = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE))
                val duration = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION))
                val added = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED))
                val uri = Uri.withAppendedPath(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id.toString())
                result.add(SavedVideo(id, name, uri, size, duration, added * 1000))
            }
            return result
        } finally {
            cursor.close()
        }
    }

    fun delete(video: SavedVideo): Boolean = deleteByUri(video.uri)

    fun deleteByUri(uri: Uri): Boolean {
        return try {
            appContext.contentResolver.delete(uri, null, null) > 0
        } catch (_: Exception) {
            false
        }
    }
}
