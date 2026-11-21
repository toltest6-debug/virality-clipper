package com.viralityclipper.app.ui.viewmodels

import androidx.lifecycle.ViewModel
import com.viralityclipper.app.data.AppContainer
import com.viralityclipper.app.data.SavedShortsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SavedState(
    val videos: List<SavedShortsRepository.SavedVideo> = emptyList(),
    val error: String? = null
)

class SavedShortsViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(SavedState())
    val state: StateFlow<SavedState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        val videos = try {
            container.savedShorts.getSavedVideos()
        } catch (_: Exception) {
            emptyList()
        }
        _state.value = _state.value.copy(videos = videos, error = null)
    }

    fun delete(video: SavedShortsRepository.SavedVideo) {
        try {
            container.savedShorts.delete(video)
        } finally {
            refresh()
        }
    }
}
