package com.viralityclipper.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.viralityclipper.app.data.ShareIntentHolder
import com.viralityclipper.app.data.ShareExtractor
import com.viralityclipper.app.ui.ViralityClipperRoot
import com.viralityclipper.app.ui.theme.ViralityClipperTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ViralityClipperTheme {
                ViralityClipperRoot(container = (application as ViralityClipperApp).container)
            }
        }
        handleShareIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent) {
        if (intent.action != Intent.ACTION_SEND) return
        val type = intent.type
        if (type != null && type != "text/plain") return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
        if (text.isNotEmpty() && ShareExtractor.extractUrl(text) != null) {
            ShareIntentHolder.pending = text
        }
    }
}
