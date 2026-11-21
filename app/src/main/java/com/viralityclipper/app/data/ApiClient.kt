package com.viralityclipper.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/** OkHttp-based client for the Virality Clipper backend. */
class ApiClient(context: Context) {

    class ApiException(message: String) : RuntimeException(message)

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private fun normalize(raw: String): String {
        val trimmed = raw.trim().removeSuffix("/")
        return trimmed
    }

    // ------------------------------------------------------------------ health

    suspend fun health(rawUrl: String): Boolean = withContext(Dispatchers.IO) {
        val base = normalize(rawUrl)
        if (base.isBlank()) return@withContext false
        try {
            val request = Request.Builder().url("$base/api/health").get().build()
            client.newCall(request).execute().use { resp -> resp.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }

    // ------------------------------------------------------------------ jobs

    suspend fun createJob(rawUrl: String, payload: CreateJobPayload): CreateJobResponse = withContext(Dispatchers.IO) {
        val base = normalize(rawUrl)
        if (base.isBlank()) throw ApiException("Backend URL is empty. Set it in Settings first.")

        val body = JSONObject().apply {
            put("youtube_url", payload.youtubeUrl)
            put("clip_count", payload.clipCount)
            put("target_duration_sec", payload.targetDurationSec)
            put("aspect_ratio", payload.aspectRatio)
            put("burn_captions", payload.burnCaptions)
            if (!payload.apiKey.isNullOrBlank()) put("api_key", payload.apiKey.trim())
            if (!payload.provider.isNullOrBlank() && payload.provider != "auto") put("provider", payload.provider)
        }

        val request = Request.Builder()
            .url("$base/api/jobs")
            .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        client.newCall(request).execute().use { resp ->
            val text = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                throw ApiException(errorFromBody(resp.code, text, "Failed to create job"))
            }
            try {
                val json = JSONObject(text)
                val jobId = json.optString("job_id", "")
                if (jobId.isBlank()) throw ApiException("Backend did not return a job_id")
                CreateJobResponse(jobId = jobId)
            } catch (e: org.json.JSONException) {
                throw ApiException("Malformed response from backend")
            }
        }
    }

    suspend fun getJob(rawUrl: String, jobId: String): JobDetail = withContext(Dispatchers.IO) {
        val base = normalize(rawUrl)
        if (base.isBlank()) throw ApiException("Backend URL is empty. Set it in Settings first.")
        val request = Request.Builder().url("$base/api/jobs/$jobId").get().build()
        client.newCall(request).execute().use { resp ->
            val text = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                throw ApiException(errorFromBody(resp.code, text, "Failed to fetch job"))
            }
            val json = try {
                JSONObject(text)
            } catch (e: org.json.JSONException) {
                throw ApiException("Malformed response from backend")
            }
            JobDetail(
                id = json.optString("id", jobId),
                status = json.optString("status", "queued"),
                progressPct = json.optInt("progress_pct", 0),
                aiMode = json.optString("ai_mode").takeIf { it.isNotBlank() },
                provider = json.optString("provider").takeIf { it.isNotBlank() },
                aspectRatio = json.optString("aspect_ratio", "9:16"),
                videoTitle = json.optString("video_title").takeIf { it.isNotBlank() },
                error = json.optString("error").takeIf { it.isNotBlank() },
                clips = parseClips(json),
                elapsedSec = if (json.has("elapsed_sec") && !json.isNull("elapsed_sec")) {
                    json.optDouble("elapsed_sec", -1.0).takeIf { it >= 0 }
                } else null
            )
        }
    }

    private fun parseClips(jobJson: JSONObject): List<JobClip> {
        val arr: JSONArray = jobJson.optJSONArray("clips") ?: JSONArray()
        val result = mutableListOf<JobClip>()
        for (i in 0 until arr.length()) {
            val c = arr.optJSONObject(i) ?: continue
            result.add(
                JobClip(
                    clipId = c.optString("clip_id", ""),
                    title = c.optString("title", ""),
                    hookCaption = c.optString("hook_caption", ""),
                    viralityScore = c.optInt("virality_score", 0),
                    startSec = c.optDouble("start_sec", 0.0),
                    endSec = c.optDouble("end_sec", 0.0),
                    durationSec = c.optDouble("duration_sec", 0.0),
                    downloadUrl = c.optString("download_url").takeIf { it.isNotBlank() },
                    thumbnailUrl = c.optString("thumbnail_url").takeIf { it.isNotBlank() },
                    status = c.optString("status", "pending"),
                    error = c.optString("error").takeIf { it.isNotBlank() }
                )
            )
        }
        return result
    }

    private fun errorFromBody(code: Int, text: String, fallback: String): String {
        return try {
            val json = JSONObject(text)
            when {
                json.has("detail") && !json.isNull("detail") -> json.optString("detail") ?: fallback
                else -> "$fallback (HTTP $code)"
            }
        } catch (_: Exception) {
            "$fallback (HTTP $code)"
        }
    }

    // ------------------------------------------------------------------ files

    /** Downloads a clip into [outFile]. [onProgress] is called with (bytesRead, total). */
    suspend fun downloadClip(
        rawUrl: String,
        clipId: String,
        outFile: File,
        onProgress: (Long, Long) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val base = normalize(rawUrl)
        if (base.isBlank()) throw ApiException("Backend URL is empty.")
        val url = "$base/api/clips/$clipId.mp4"
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw ApiException("Download failed (HTTP ${resp.code})")
            }
            val body = resp.body ?: throw ApiException("Empty response body")
            val total = body.contentLength().coerceAtLeast(0L)
            outFile.parentFile?.mkdirs()
            var read = 0L
            outFile.outputStream().use { out ->
                body.byteStream().use { stream ->
                    val buffer = ByteArray(128 * 1024)
                    while (true) {
                        val n = stream.read(buffer)
                        if (n == -1) break
                        out.write(buffer, 0, n)
                        read += n
                        onProgress(read, total)
                    }
                }
            }
        }
        outFile
    }

    suspend fun loadThumbnail(rawUrl: String, clipId: String): Bitmap? = withContext(Dispatchers.IO) {
        val base = normalize(rawUrl)
        if (base.isBlank()) return@withContext null
        try {
            val request = Request.Builder().url("$base/api/clips/$clipId.jpg").get().build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val bytes = resp.body?.bytes() ?: return@withContext null
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        } catch (_: Exception) {
            null
        }
    }

}
