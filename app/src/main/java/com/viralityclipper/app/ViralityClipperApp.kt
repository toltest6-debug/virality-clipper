package com.viralityclipper.app

import android.app.Application
import com.viralityclipper.app.data.AppContainer
import com.viralityclipper.app.data.ShareIntentHolder

class ViralityClipperApp : Application() {
    companion object {
        /** Set once after [onCreate]; safe to touch after the app has started. */
        var instance: ViralityClipperApp? = null
            private set
    }

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        instance = this
    }
}
