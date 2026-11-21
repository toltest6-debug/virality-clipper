package com.viralityclipper.app.data

data class CreateJobPayload(
    val youtubeUrl: String,
    val clipCount: Int,
    val targetDurationSec: Int,
    val aspectRatio: String,
    val burnCaptions: Boolean,
    val apiKey: String? = null,
    val provider: String? = null
)

data class CreateJobResponse(val jobId: String)

data class JobClip(
    val clipId: String,
    val title: String,
    val hookCaption: String,
    val viralityScore: Int,
    val startSec: Double,
    val endSec: Double,
    val durationSec: Double,
    val downloadUrl: String?,
    val thumbnailUrl: String?,
    val status: String,
    val error: String?
)

data class JobDetail(
    val id: String,
    val status: String,
    val progressPct: Int,
    val aiMode: String?,
    val provider: String?,
    val aspectRatio: String,
    val videoTitle: String?,
    val error: String?,
    val clips: List<JobClip>,
    val elapsedSec: Double? = null
)

data class ShareLinkResult(val url: String)
