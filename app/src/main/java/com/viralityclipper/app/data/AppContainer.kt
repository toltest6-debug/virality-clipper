package com.viralityclipper.app.data

import android.content.Context
import java.io.File

/**
 * Manual DI container — created once in [ViralityClipperApp] and passed
 * to every ViewModel / screen. No third-party injection framework.
 */
class AppContainer(val context: Context) {

    val appContext: Context = context.applicationContext
    val cacheDir: File = appContext.cacheDir

    /** DataStore-backed user settings (backend URL, AI key, defaults). */
    val settings = SettingsPreferences(appContext)

    /** OkHttp client for the backend API. */
    val api = ApiClient(appContext)

    /** Downloads finished clips into Movies/ViralityClipper via MediaStore. */
    val mediaSaver = MediaStoreSaver(appContext)

    /** Query / delete the shorts saved via [mediaSaver]. */
    val savedShorts = SavedShortsRepository(appContext)
}
