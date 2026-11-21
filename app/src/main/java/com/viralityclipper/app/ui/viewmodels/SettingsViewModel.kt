package com.viralityclipper.app.ui.viewmodels

import androidx.lifecycle.ViewModel
import com.viralityclipper.app.data.AppContainer
import com.viralityclipper.app.data.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    val settingsFlow: Flow<AppSettings> = container.settings.settingsFlow

    suspend fun setBackendUrl(value: String) = container.settings.setBackendUrl(value)
    suspend fun setProvider(value: String) = container.settings.setProvider(value)
    suspend fun setApiKey(value: String) = container.settings.setApiKey(value)
    suspend fun setClipCount(value: Int) = container.settings.setClipCount(value)
    suspend fun setTargetSeconds(value: Int) = container.settings.setTargetSeconds(value)
    suspend fun setAspectRatio(value: String) = container.settings.setAspectRatio(value)
    suspend fun setBurnCaptions(value: Boolean) = container.settings.setBurnCaptions(value)

    suspend fun current(): AppSettings = container.settings.get()

    suspend fun testConnection(url: String): Boolean = container.api.health(url)
}
