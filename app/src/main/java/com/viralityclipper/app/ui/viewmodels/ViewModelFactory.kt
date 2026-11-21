package com.viralityclipper.app.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.viralityclipper.app.data.AppContainer

/** Manual DI wiring — screens hold no direct singletons. */
class ViewModelFactory(
    private val container: AppContainer,
    private val jobId: String? = null
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return when {
            HomeViewModel::class.java.isAssignableFrom(modelClass) -> HomeViewModel(container)
            SavedShortsViewModel::class.java.isAssignableFrom(modelClass) -> SavedShortsViewModel(container)
            SettingsViewModel::class.java.isAssignableFrom(modelClass) -> SettingsViewModel(container)
            JobViewModel::class.java.isAssignableFrom(modelClass) ->
                JobViewModel(container, requireNotNull(jobId) { "JobViewModel requires a jobId" })
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        } as T
    }
}
