package com.viralityclipper.app.ui.viewmodels

import androidx.lifecycle.ViewModel
import com.viralityclipper.app.data.AppContainer
import com.viralityclipper.app.data.AppSettings
import com.viralityclipper.app.data.CreateJobPayload
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/** State for the home screen. */
class HomeViewModel(private val container: AppContainer) : ViewModel() {

    val settings: Flow<AppSettings> = container.settings.settingsFlow

    /**
     * Creates a job from the current form input, persists the defaults (clip count /
     * length / burn captions / aspect ratio) and returns the new job id.
     */
    suspend fun startJob(
        youtubeUrl: String,
        clipCount: Int,
        targetDurationSec: Int,
        aspectRatio: String,
        burnCaptions: Boolean
    ): String {
        val current = container.settings.get()
        if (current.backendUrl.isBlank()) {
            throw IllegalStateException("Backend URL is not set. Open Settings and add your backend URL first.")
        }

        val payload = CreateJobPayload(
            youtubeUrl = youtubeUrl,
            clipCount = clipCount,
            targetDurationSec = targetDurationSec,
            aspectRatio = aspectRatio,
            burnCaptions = burnCaptions,
            apiKey = current.apiKey.ifBlank { null },
            provider = current.apiProvider.ifBlank { null }
        )
        val response = container.api.createJob(current.backendUrl, payload)

        // persist the user's selections as defaults for the next visit
        container.settings.setClipCount(clipCount)
        container.settings.setTargetSeconds(targetDurationSec)
        container.settings.setAspectRatio(aspectRatio)
        container.settings.setBurnCaptions(burnCaptions)

        return response.jobId
    }

    suspend fun currentSettings(): AppSettings = container.settings.get()
}
