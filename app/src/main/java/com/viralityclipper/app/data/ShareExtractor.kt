package com.viralityclipper.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri

/** Holder for the URL shared into the app via ACTION_SEND (YouTube's Share button). */
object ShareIntentHolder {
    /** Pending share payload set by MainActivity; consumed by HomeScreen. */
    var pending: String? = null
}

/** Extracts the first YouTube URL from a shared plain-text payload. */
object ShareExtractor {
    private val patterns = listOf(
        Regex("(?:https?:\\/\\/)?(?:www\\.)?(?:youtube\\.com\\/watch\\?(?:[^#&?]+&)*v=|youtube\\.com\\/shorts\\/|youtube\\.com\\/embed\\/|youtube\\.com\\/live\\/|youtube\\.com\\/@[^/]+\\/videos\\/|youtu\\.be\\/)([a-zA-Z0-9_-]{11})")
    )

    fun extractUrl(text: String?): String? {
        if (text.isNullOrBlank()) return null
        for (pattern in patterns) {
            val match = pattern.find(text) ?: continue
            val videoId = match.groupValues[1]
            return "https://www.youtube.com/watch?v=$videoId"
        }
        return null
    }
}

object ShareUtils {
    fun shareVideo(context: Context, uri: Uri, extraText: String = "Check out my Virality Clipper Short!") {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, extraText)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Short"))
    }

    fun shareText(context: Context, text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, "Share"))
    }
}
