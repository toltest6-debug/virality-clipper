package com.viralityclipper.app.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavHostController
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.viralityclipper.app.R
import com.viralityclipper.app.data.JobClip
import com.viralityclipper.app.data.ShareUtils
import com.viralityclipper.app.ui.theme.LocalViralityColors
import com.viralityclipper.app.ui.viewmodels.JobUiState
import com.viralityclipper.app.ui.viewmodels.JobViewModel
import com.viralityclipper.app.ui.viewmodels.ViewModelFactory
import kotlinx.coroutines.launch

private enum class StepState { Pending, Current, Done, Failed }

private data class Step(val label: String, val icon: Int)

private fun resolveSteps(status: String, progressPct: Int): List<Pair<Step, StepState>> {
    val steps = listOf(
        Step("Transcript", R.drawable.ic_link),
        Step("AI analysis", R.drawable.ic_sparkle),
        Step("Render", R.drawable.ic_clip),
        Step("Ready", R.drawable.ic_check)
    )
    return when (status) {
        "completed" -> steps.map { it to StepState.Done }
        "failed" -> {
            val failedIdx = when {
                progressPct < 30 -> 0
                progressPct < 40 -> 1
                progressPct < 85 -> 2
                else -> 3
            }
            steps.mapIndexed { i, step ->
                if (i < failedIdx) step to StepState.Done
                else if (i == failedIdx) step to StepState.Failed
                else step to StepState.Pending
            }
        }
        else -> {
            val current = when (status) {
                "queued", "transcript" -> 0
                "ai_analyzing" -> 1
                "rendering" -> 2
                else -> 0
            }
            steps.mapIndexed { i, step ->
                when {
                    i < current -> step to StepState.Done
                    i == current -> step to StepState.Current
                    else -> step to StepState.Pending
                }
            }
        }
    }
}

@Composable
fun JobScreen(container: com.viralityclipper.app.data.AppContainer, jobId: String, navController: NavHostController) {
    val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<JobViewModel>(
        key = "job_$jobId",
        factory = ViewModelFactory(container, jobId)
    )
    val state by viewModel.state.collectAsState()
    val colors = LocalViralityColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val back = { navController.popBackStack() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.elevated)
                    .border(1.dp, colors.border, RoundedCornerShape(14.dp))
                    .clickable(onClick = back),
                contentAlignment = Alignment.Center
            ) {
                ViralityIcon(R.drawable.ic_back, contentDescription = "Back", tint = colors.text)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Viral Job", color = colors.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = state.videoTitle ?: "Analyzing your YouTube link…",
                    color = colors.textMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            StatusPill(state.status)
        }

        // Progress card
        ProgressCard(state = state, colors = colors)

        // AI mode note
        if (state.aiMode != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ViralityIcon(R.drawable.ic_sparkle, contentDescription = null, tint = colors.secondary, modifier = Modifier.size(16.dp))
                Text(
                    text = if (state.aiMode == "ai") "AI-powered (${state.provider ?: "LLM"})" else "Heuristic mode (add an AI key for smarter clips)",
                    color = colors.textMuted,
                    fontSize = 11.sp
                )
            }
        }

        // Error card
        if (state.status == "failed" || state.error != null) {
            StudioCard(modifier = Modifier.fillMaxWidth(), background = colors.errorDark, border = true) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ViralityIcon(R.drawable.ic_error, contentDescription = null, tint = colors.error, modifier = Modifier.size(22.dp))
                        Text("Job failed", color = colors.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        text = state.error ?: "Something went wrong. Check the link, or the backend configuration.",
                        color = colors.textMuted,
                        fontSize = 13.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = { back() }) { Text("Back to home", color = colors.text) }
                    }
                }
            }
        }

        // Clips
        if (state.clips.isNotEmpty()) {
            SectionLabel("Your Shorts (${state.clips.size})")
            state.clips.forEach { clip ->
                ClipCard(
                    clip = clip,
                    container = container,
                    viewModel = viewModel,
                    state = state,
                    onShare = { uri -> ShareUtils.shareVideo(context, uri) }
                )
            }
        } else if (state.status != "failed" && state.status != "queued") {
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = "Shorts appear here once rendering starts…",
                    color = colors.textMuted,
                    fontSize = 13.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun StatusPill(status: String) {
    val colors = LocalViralityColors.current
    val (label, color, bg) = when (status) {
        "completed" -> Triple("READY", colors.success, colors.success.copy(alpha = 0.15f))
        "failed" -> Triple("FAILED", colors.error, colors.error.copy(alpha = 0.15f))
        "queued" -> Triple("QUEUED", colors.textMuted, colors.elevated)
        "transcript" -> Triple("TRANSCRIPT", colors.secondary, colors.secondary.copy(alpha = 0.12f))
        "ai_analyzing" -> Triple("AI", colors.secondary, colors.secondary.copy(alpha = 0.12f))
        "rendering" -> Triple("RENDERING", colors.secondary, colors.secondary.copy(alpha = 0.12f))
        else -> Triple(status.uppercase(), colors.textMuted, colors.elevated)
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(text = label, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
    }
}

@Composable
private fun ProgressCard(state: JobUiState, colors: com.viralityclipper.app.ui.theme.ViralityThemeColors) {
    val steps = resolveSteps(state.status, state.progressPct)
    val progress = state.progressPct.coerceIn(0, 100)
    StudioCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "$progress%",
                    color = colors.text,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(92.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when (state.status) {
                            "queued", "transcript" -> "Fetching transcript…"
                            "ai_analyzing" -> "AI is finding viral moments…"
                            "rendering" -> "Rendering your Shorts…"
                            "completed" -> "Your shorts are ready!"
                            "failed" -> "Job failed"
                            else -> "Processing…"
                        },
                        color = colors.text,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = progressStatus(state),
                        color = colors.textMuted,
                        fontSize = 11.sp
                    )
                }
            }
            LinearProgressBar(progressPct = progress, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                steps.forEach { (step, stepState) ->
                    StepChip(step = step, stepState = stepState, colors = colors)
                }
            }
        }
    }
}

private fun progressStatus(state: JobUiState): String {
    return when (state.status) {
        "rendering" -> "${state.clips.count { it.status == "done" }} / ${state.clips.size} clips rendered"
        "completed" -> "All clips done · ${state.clips.size} shorts ready"
        "failed" -> "Stopped at ${state.clips.count { it.status == "done" }} of ${state.clips.size} clip(s)"
        else -> "Step ${resolveStep(state.status)} of 4"
    }
}

private fun resolveStep(status: String): Int = when (status) {
    "queued", "transcript" -> 1
    "ai_analyzing" -> 2
    "rendering" -> 3
    "completed", "failed" -> 4
    else -> 1
}

@Composable
private fun StepChip(step: Step, stepState: StepState, colors: com.viralityclipper.app.ui.theme.ViralityThemeColors) {
    val (color, bg) = when (stepState) {
        StepState.Done -> colors.success to colors.success.copy(alpha = 0.12f)
        StepState.Current -> colors.secondary to colors.secondary.copy(alpha = 0.12f)
        StepState.Failed -> colors.error to colors.error.copy(alpha = 0.12f)
        StepState.Pending -> colors.textMuted to colors.elevated
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(bg).padding(vertical = 10.dp)
    ) {
        ViralityIcon(step.icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = step.label,
            color = if (stepState == StepState.Pending) colors.textMuted else color,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun ClipCard(
    clip: JobClip,
    container: com.viralityclipper.app.data.AppContainer,
    viewModel: JobViewModel,
    state: JobUiState,
    onShare: (android.net.Uri) -> Unit
) {
    val colors = LocalViralityColors.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    val isSaved = clip.clipId in state.savedClipIds
    val isSaving = clip.clipId in state.savingClipIds
    val clipUrl = if (clip.downloadUrl != null && state.backendUrl.isNotBlank()) {
        "${state.backendUrl}${clip.downloadUrl}"
    } else null

    StudioCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (expanded && clipUrl != null) {
                PlayerBlock(url = clipUrl, aspectRatio = state.aspectRatio, colors = colors)
                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { expanded = false },
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = colors.text)
                    ) {
                        Text("Hide preview", fontSize = 12.sp)
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    // thumbnail (9:16 mini)
                    ThumbnailImage(
                        api = container.api,
                        baseUrl = state.backendUrl,
                        clipId = clip.clipId,
                        modifier = Modifier.width(64.dp).height(114.dp),
                        cornerRadius = 10.dp
                    )
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ScoreBadge(clip.viralityScore)
                            ClipStatusPill(clip.status, colors)
                        }
                        Text(
                            text = clip.title.ifBlank { "Viral moment" },
                            color = colors.text,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = clip.hookCaption,
                            color = colors.textMuted,
                            fontSize = 11.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(text = "⏱ ${formatDuration(clip.durationSec)}", color = colors.textMuted, fontSize = 11.sp)
                            Text(text = "${clip.startSec.toInt()}s → ${clip.endSec.toInt()}s", color = colors.textMuted, fontSize = 11.sp)
                        }
                    }
                }
                // Action row
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val uri = viewModel.saveClip(clip.clipId)
                                if (uri == null) {
                                    // saving or sharing both go through save; failure shows via state
                                }
                            }
                        },
                        enabled = !isSaving && clip.status == "done" && clip.clipId.isNotBlank(),
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = colors.secondary),
                        modifier = Modifier.weight(1f)
                    ) {
                        ViralityIcon(R.drawable.ic_save, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.secondary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isSaving) "Saving…" else if (isSaved) "Saved ✓" else "Save", fontSize = 13.sp)
                    }
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val uri = viewModel.saveClip(clip.clipId)
                                if (uri != null) onShare(uri)
                            }
                        },
                        enabled = !isSaving && clip.status == "done" && clip.clipId.isNotBlank(),
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = colors.primary),
                        modifier = Modifier.weight(1f)
                    ) {
                        ViralityIcon(R.drawable.ic_share, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.primary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share", fontSize = 13.sp)
                    }
                    if (clipUrl != null) {
                        OutlinedButton(
                            onClick = { expanded = true },
                            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = colors.text),
                            modifier = Modifier.weight(1f)
                        ) {
                            ViralityIcon(R.drawable.ic_play, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Preview", fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ClipStatusPill(status: String, colors: com.viralityclipper.app.ui.theme.ViralityThemeColors) {
    val (label, color, bg) = when (status) {
        "done" -> Triple("DONE", colors.success, colors.success.copy(alpha = 0.12f))
        "failed" -> Triple("FAILED", colors.error, colors.error.copy(alpha = 0.12f))
        "pending" -> Triple("QUEUED", colors.textMuted, colors.elevated)
        else -> Triple(status.uppercase(), colors.secondary, colors.secondary.copy(alpha = 0.10f))
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .padding(horizontal = 9.dp, vertical = 4.dp)
    ) {
        Text(text = label, color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    }
}

@Composable
private fun PlayerBlock(url: String, aspectRatio: String, colors: com.viralityclipper.app.ui.theme.ViralityThemeColors) {
    val context = LocalContext.current
    val player = remember(context, url) { ExoPlayer.Builder(context).build() }
    DisposableEffect(player) { onDispose { player.release() } }

    LaunchedEffect(url) {
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
    }

    val aspect = when (aspectRatio) {
        "16:9" -> 16f / 9f
        "1:1" -> 1f
        else -> 9f / 16f
    }
    val width = when (aspectRatio) {
        "16:9" -> 300.dp
        "1:1" -> 300.dp
        else -> 230.dp
    }

    Box(
        modifier = Modifier
            .width(width)
            .aspectRatio(aspect)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black)
            .border(1.dp, colors.border, RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx -> PlayerView(ctx).apply { this.player = player } },
            update = { view -> if (view.player != player) view.player = player },
            modifier = Modifier.fillMaxSize()
        )
    }
}
