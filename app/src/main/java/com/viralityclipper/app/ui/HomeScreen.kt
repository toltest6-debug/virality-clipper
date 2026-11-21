package com.viralityclipper.app.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.navigation.NavHostController
import com.viralityclipper.app.R
import com.viralityclipper.app.data.AppSettings
import com.viralityclipper.app.data.ShareExtractor
import com.viralityclipper.app.data.ShareIntentHolder
import com.viralityclipper.app.ui.theme.LocalViralityColors
import com.viralityclipper.app.ui.viewmodels.HomeViewModel
import com.viralityclipper.app.ui.viewmodels.ViewModelFactory
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(container: com.viralityclipper.app.data.AppContainer, navController: NavHostController) {
    val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<HomeViewModel>(
        factory = ViewModelFactory(container)
    )
    val settings by viewModel.settings.collectAsState(initial = AppSettings())
    val colors = LocalViralityColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var url by remember { mutableStateOf("") }
    var clipCount by remember { mutableStateOf(3) }
    var targetSec by remember { mutableStateOf(45) }
    var aspectRatio by remember { mutableStateOf("9:16") }
    var burnCaptions by remember { mutableStateOf(true) }
    var isSharedLinkDetected by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var shareBannerActive by remember { mutableStateOf(false) }

    val startJob: () -> Unit = {
        val canonical = ShareExtractor.extractUrl(url)
        if (canonical == null) {
            errorMessage = "Paste a valid YouTube link to generate shorts"
        } else if (settings.backendUrl.isBlank()) {
            errorMessage = "Backend URL is missing — open Settings to add your server"
        } else {
            loading = true
            errorMessage = null
            scope.launch {
                try {
                    val jobId = viewModel.startJob(canonical, clipCount, targetSec, aspectRatio, burnCaptions)
                    shareBannerActive = false
                    navController.navigate("job/$jobId")
                } catch (e: Exception) {
                    loading = false
                    errorMessage = e.message ?: "Failed to start the job"
                }
            }
        }
    }

    // Share intent from YouTube's Share button (auto-fill + auto-start)
    LaunchedEffect(Unit) {
        val pending = ShareIntentHolder.pending
        if (pending != null) {
            ShareIntentHolder.pending = null
            val canonical = ShareExtractor.extractUrl(pending)
            if (canonical != null) {
                url = canonical
                shareBannerActive = true
                isSharedLinkDetected++
            } else {
                errorMessage = "Shared text doesn't contain a YouTube link"
            }
        }
    }

    // Load persisted defaults once (not on every DataStore emission)
    var defaultsApplied by remember { mutableStateOf(false) }
    LaunchedEffect(settings) {
        if (defaultsApplied) return@LaunchedEffect
        defaultsApplied = true
        if (settings.clipCount in 3..5) clipCount = settings.clipCount
        if (settings.targetDurationSec in 30..60) targetSec = settings.targetDurationSec
        if (settings.aspectRatio in listOf("9:16", "16:9", "1:1")) aspectRatio = settings.aspectRatio
        burnCaptions = settings.burnCaptions
    }

    // Auto-start after a share link is detected
    LaunchedEffect(isSharedLinkDetected) {
        if (isSharedLinkDetected > 0 && url.isNotBlank() && settings.backendUrl.isNotBlank()) {
            startJob()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Brush.linearGradient(listOf(colors.primary, colors.secondary)))
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    ViralityIcon(R.drawable.ic_sparkle, contentDescription = null, modifier = Modifier.size(24.dp), tint = Color.White)
                }
                Column {
                    Text(
                        text = "Virality Clipper",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.text
                    )
                    Text(text = "AI Shorts Studio", fontSize = 11.sp, color = colors.textMuted)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillIconButton(R.drawable.ic_folder, "Saved shorts") { navController.navigate("saved") }
                PillIconButton(R.drawable.ic_settings, "Settings") { navController.navigate("settings") }
            }
        }

        // Share banner
        if (shareBannerActive) {
            StudioCard(modifier = Modifier.fillMaxWidth(), background = colors.secondary.copy(alpha = 0.12f), border = true) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ViralityIcon(R.drawable.ic_sparkle, contentDescription = null, tint = colors.secondary, modifier = Modifier.size(20.dp))
                    Text(
                        text = "Shared link detected — preparing your shorts…",
                        fontSize = 12.sp,
                        color = colors.text,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Backend warning
        if (settings.backendUrl.isBlank()) {
            StudioCard(modifier = Modifier.fillMaxWidth(), background = colors.errorDark, border = true) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ViralityIcon(R.drawable.ic_error, contentDescription = null, tint = colors.error, modifier = Modifier.size(20.dp))
                    Text(
                        text = "Backend URL missing — open Settings to add your server.",
                        fontSize = 12.sp,
                        color = colors.text,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { navController.navigate("settings") }) {
                        Text("Open", color = colors.secondary, fontSize = 12.sp)
                    }
                }
            }
        }

        // URL input
        SectionLabel("YouTube URL")
        StudioCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Paste a link — or use YouTube's Share button",
                        fontSize = 11.sp,
                        color = colors.textMuted,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.elevated)
                            .border(1.dp, colors.border, RoundedCornerShape(12.dp))
                            .clickable { pasteUrl(context) { value -> url = value } },
                        contentAlignment = Alignment.Center
                    ) {
                        ViralityIcon(R.drawable.ic_paste, contentDescription = "Paste from clipboard", modifier = Modifier.size(22.dp), tint = colors.secondary)
                    }
                }

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("https://www.youtube.com/watch?v=…", color = colors.textMuted, fontSize = 13.sp) },
                    textStyle = androidx.compose.material3.LocalTextStyle.current.copy(color = colors.text, fontSize = 14.sp),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Uri
                    ),
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.primary,
                        unfocusedBorderColor = colors.border,
                        focusedTextColor = colors.text,
                        unfocusedTextColor = colors.text,
                        cursorColor = colors.secondary,
                        focusedLabelColor = colors.secondary,
                        unfocusedLabelColor = colors.textMuted
                    )
                )
            }
        }

        // Options
        SectionLabel("Options")
        ChipRow(
            label = "Clip count",
            options = listOf("3", "5"),
            selected = clipCount.toString(),
            onSelect = { clipCount = it.toInt() }
        )
        ChipRow(
            label = "Target length",
            options = listOf("30s", "45s", "60s"),
            selected = "${targetSec}s",
            onSelect = { targetSec = it.removeSuffix("s").toInt() }
        )
        ChipRow(
            label = "Aspect ratio",
            options = listOf("9:16", "16:9", "1:1"),
            selected = aspectRatio,
            onSelect = { aspectRatio = it }
        )

        // Burn captions toggle
        StudioCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { burnCaptions = !burnCaptions }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Burn captions", color = colors.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("Subtitles burned into the video frame", color = colors.textMuted, fontSize = 11.sp)
                }
                Switch(
                    checked = burnCaptions,
                    onCheckedChange = { burnCaptions = it },
                    colors = androidx.compose.material3.SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = colors.primary,
                        uncheckedThumbColor = colors.textMuted,
                        uncheckedTrackColor = colors.elevated
                    )
                )
            }
        }

        // Summary + CTA
        StudioCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ViralityIcon(R.drawable.ic_sparkle, contentDescription = null, modifier = Modifier.size(18.dp), tint = colors.secondary)
                    Text(
                        text = "$aspectRatio · $clipCount clips · ${targetSec}s each",
                        color = colors.textMuted,
                        fontSize = 13.sp
                    )
                }
                GradientCta(
                    text = if (loading) "Starting…" else "Generate Viral Clips",
                    enabled = !loading,
                    loading = loading,
                    onClick = startJob,
                    icon = if (loading) null else R.drawable.ic_sparkle
                )
                if (errorMessage != null) {
                    Text(text = errorMessage ?: "", color = colors.error, fontSize = 12.sp, textAlign = TextAlign.Center)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun PillIconButton(resId: Int, description: String, onClick: () -> Unit) {
    val colors = LocalViralityColors.current
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.elevated)
            .border(1.dp, colors.border, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        ViralityIcon(resId, contentDescription = description, modifier = Modifier.size(22.dp))
    }
}

private fun pasteUrl(context: Context, onPasted: (String) -> Unit) {
    try {
        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val text = clipboardManager?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
        if (!text.isNullOrBlank()) {
            val canonical = ShareExtractor.extractUrl(text)
            onPasted(canonical ?: text.trim())
        }
    } catch (_: Exception) {
        // ignore — UI keeps the current value
    }
}
