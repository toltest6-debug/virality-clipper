package com.viralityclipper.app.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viralityclipper.app.data.AppContainer
import com.viralityclipper.app.data.JobClip
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class JobUiState(
    val status: String = "queued",
    val progressPct: Int = 0,
    val clips: List<JobClip> = emptyList(),
    val error: String? = null,
    val videoTitle: String? = null,
    val aiMode: String? = null,
    val provider: String? = null,
    val aspectRatio: String = "9:16",
    val backendUrl: String = "",
    val savedClipIds: Set<String> = emptySet(),
    val savingClipIds: Set<String> = emptySet(),
    val lastError: String? = null
)

class JobViewModel(private val container: AppContainer, val jobId: String) : ViewModel() {

    private val _state = MutableStateFlow(JobUiState())
    val state: StateFlow<JobUiState> = _state.asStateFlow()

    init {
        poll()
    }

    fun cancel() {
        // no-op: backend job continues (the app has no cancel endpoint); backing the UI simply returns.
    }

    private fun poll() {
        viewModelScope.launch {
            while (true) {
                try {
                    val settings = container.settings.get()
                    if (settings.backendUrl.isBlank()) {
                        _state.value = _state.value.copy(lastError = "Backend URL missing — open Settings")
                        return@launch
                    }
                    val detail = container.api.getJob(settings.backendUrl, jobId)
                    val current = _state.value
                    _state.value = current.copy(
                        status = detail.status,
                        progressPct = detail.progressPct,
                        clips = detail.clips,
                        error = detail.error,
                        videoTitle = detail.videoTitle ?: current.videoTitle,
                        aiMode = detail.aiMode ?: current.aiMode,
                        provider = detail.provider ?: current.provider,
                        aspectRatio = detail.aspectRatio.ifBlank { current.aspectRatio },
                        backendUrl = settings.backendUrl,
                        lastError = null
                    )
                    if (detail.status == "completed" || detail.status == "failed") return@launch
                    delay(1800)
                } catch (e: Exception) {
                    _state.value = _state.value.copy(
                        lastError = e.message ?: "Failed to fetch job status"
                    )
                    delay(3000)
                }
            }
        }
    }

    /** Downloads + persists a clip to Movies/ViralityClipper. Returns the local file URI. */
    suspend fun saveClip(clipId: String): android.net.Uri? {
        val current = _state.value
        if (current.backendUrl.isBlank()) return null
        _state.value = current.copy(savingClipIds = current.savingClipIds + clipId)
        return try {
            val settings = container.settings.get()
            val file = File(container.cacheDir, "shorts/$clipId.mp4")
            file.parentFile?.mkdirs()
            container.api.downloadClip(settings.backendUrl, clipId, file) { _, _ -> }
            val clip = current.clips.firstOrNull { it.clipId == clipId }
            val uri = container.mediaSaver.saveVideoFile(file, clip?.title?.ifBlank { "Virality Clipper Short" } ?: "Virality Clipper Short")
            _state.value = _state.value.copy(
                savedClipIds = _state.value.savedClipIds + clipId,
                savingClipIds = _state.value.savingClipIds - clipId
            )
            uri
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                lastError = e.message ?: "Save failed",
                savingClipIds = _state.value.savingClipIds - clipId
            )
            null
        }
    }
}
