package com.viralityclipper.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first

val Context.appSettingsDataStore by preferencesDataStore(name = "virality_clipper_settings")

const val DEFAULT_BACKEND_URL = ""
const val DEFAULT_PROVIDER = "auto"
const val DEFAULT_API_KEY = ""
const val DEFAULT_CLIP_COUNT = 3
const val DEFAULT_TARGET_SECONDS = 45
const val DEFAULT_ASPECT_RATIO = "9:16"
const val DEFAULT_BURN_CAPTIONS = true

val KEY_BACKEND_URL = stringPreferencesKey("backend_url")
val KEY_PROVIDER = stringPreferencesKey("ai_provider")
val KEY_API_KEY = stringPreferencesKey("ai_api_key")
val KEY_CLIP_COUNT = intPreferencesKey("default_clip_count")
val KEY_TARGET_SECONDS = intPreferencesKey("default_target_seconds")
val KEY_ASPECT_RATIO = stringPreferencesKey("default_aspect_ratio")
val KEY_BURN_CAPTIONS = booleanPreferencesKey("default_burn_captions")

/** Persisted user settings. */
data class AppSettings(
    val backendUrl: String = DEFAULT_BACKEND_URL,
    val apiProvider: String = DEFAULT_PROVIDER,
    val apiKey: String = DEFAULT_API_KEY,
    val clipCount: Int = DEFAULT_CLIP_COUNT,
    val targetDurationSec: Int = DEFAULT_TARGET_SECONDS,
    val aspectRatio: String = DEFAULT_ASPECT_RATIO,
    val burnCaptions: Boolean = DEFAULT_BURN_CAPTIONS
)

class SettingsPreferences(context: Context) {
    private val appContext = context.applicationContext

    val settingsFlow: Flow<AppSettings> = appContext.appSettingsDataStore.data.map { prefs ->
        AppSettings(
            backendUrl = prefs[KEY_BACKEND_URL] ?: DEFAULT_BACKEND_URL,
            apiProvider = prefs[KEY_PROVIDER] ?: DEFAULT_PROVIDER,
            apiKey = prefs[KEY_API_KEY] ?: DEFAULT_API_KEY,
            clipCount = prefs[KEY_CLIP_COUNT] ?: DEFAULT_CLIP_COUNT,
            targetDurationSec = prefs[KEY_TARGET_SECONDS] ?: DEFAULT_TARGET_SECONDS,
            aspectRatio = prefs[KEY_ASPECT_RATIO] ?: DEFAULT_ASPECT_RATIO,
            burnCaptions = prefs[KEY_BURN_CAPTIONS] ?: DEFAULT_BURN_CAPTIONS
        )
    }

    suspend fun get(): AppSettings = settingsFlow.first()

    suspend fun setBackendUrl(value: String) {
        val normalized = value.trim().removeSuffix("/")
        appContext.appSettingsDataStore.edit { it[KEY_BACKEND_URL] = normalized }
    }

    suspend fun setProvider(value: String) {
        appContext.appSettingsDataStore.edit { it[KEY_PROVIDER] = value }
    }

    suspend fun setApiKey(value: String) {
        appContext.appSettingsDataStore.edit { it[KEY_API_KEY] = value.trim() }
    }

    suspend fun setClipCount(value: Int) {
        appContext.appSettingsDataStore.edit { it[KEY_CLIP_COUNT] = value.coerceIn(3, 5) }
    }

    suspend fun setTargetSeconds(value: Int) {
        appContext.appSettingsDataStore.edit { it[KEY_TARGET_SECONDS] = value.coerceIn(30, 60) }
    }

    suspend fun setAspectRatio(value: String) {
        val v = if (value in listOf("9:16", "16:9", "1:1")) value else DEFAULT_ASPECT_RATIO
        appContext.appSettingsDataStore.edit { it[KEY_ASPECT_RATIO] = v }
    }

    suspend fun setBurnCaptions(value: Boolean) {
        appContext.appSettingsDataStore.edit { it[KEY_BURN_CAPTIONS] = value }
    }
}
