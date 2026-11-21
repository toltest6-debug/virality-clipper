package com.viralityclipper.app.ui

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Button
import androidx.compose.material3.Dialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.viralityclipper.app.R
import com.viralityclipper.app.data.ShareUtils
import com.viralityclipper.app.data.SavedShortsRepository
import com.viralityclipper.app.ui.theme.LocalViralityColors
import com.viralityclipper.app.ui.viewmodels.SavedShortsViewModel
import com.viralityclipper.app.ui.viewmodels.ViewModelFactory

@Composable
fun SavedShortsScreen(
    container: com.viralityclipper.app.data.AppContainer,
    navController: NavHostController
) {
    val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<SavedShortsViewModel>(
        factory = ViewModelFactory(container)
    )
    val state by viewModel.state.collectAsState()
    val colors = LocalViralityColors.current
    val context = LocalContext.current
    var pendingDelete by remember { mutableStateOf<SavedShortsRepository.SavedVideo?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* result handled implicitly; on deny we keep the last known list */ }

    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.READ_MEDIA_VIDEO)
        }
        // refresh each time the screen comes back (e.g. returning after generate)
        viewModel.refresh()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header
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
                    .clickable { navController.popBackStack() },
                contentAlignment = Alignment.Center
            ) {
                ViralityIcon(R.drawable.ic_back, contentDescription = "Back", tint = colors.text)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Saved Shorts", color = colors.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("${state.videos.size} in Movies/ViralityClipper", color = colors.textMuted, fontSize = 12.sp)
            }
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.elevated)
                    .border(1.dp, colors.border, RoundedCornerShape(14.dp))
                    .clickable { viewModel.refresh() },
                contentAlignment = Alignment.Center
            ) {
                ViralityIcon(R.drawable.ic_refresh, contentDescription = "Refresh", tint = colors.secondary)
            }
        }

        if (state.videos.isEmpty()) {
            EmptyState()
        } else {
            SectionLabel("Recently saved")
            state.videos.forEach { video ->
                SavedClipRow(
                    video = video,
                    onShare = { ShareUtils.shareVideo(context, video.uri) },
                    onDelete = { pendingDelete = video }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    // Delete confirmation dialog
    pendingDelete?.let { video ->
        Dialog(onDismissRequest = { pendingDelete = null }) {
            StudioCard(
                modifier = Modifier.fillMaxWidth(0.88f),
                background = colors.surface,
                border = true
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Delete this short?", color = colors.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = "“${video.name}” will be permanently removed from Movies/ViralityClipper.",
                        color = colors.textMuted,
                        fontSize = 12.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { pendingDelete = null },
                            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = colors.text),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                viewModel.delete(video)
                                pendingDelete = null
                                Toast.makeText(context, "Short deleted", Toast.LENGTH_SHORT).show()
                            },
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = colors.error,
                                contentColor = Color.Black
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Delete")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    val colors = LocalViralityColors.current
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier.size(84.dp).clip(RoundedCornerShape(24.dp)).background(colors.elevated),
                contentAlignment = Alignment.Center
            ) {
                ViralityIcon(R.drawable.ic_folder, contentDescription = null, modifier = Modifier.size(40.dp), tint = colors.textMuted)
            }
            Text("No shorts yet", color = colors.text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(
                text = "Generate clips from the home screen — they save here automatically.",
                color = colors.textMuted,
                fontSize = 12.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.width(260.dp)
            )
        }
    }
}

@Composable
private fun SavedClipRow(
    video: SavedShortsRepository.SavedVideo,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = LocalViralityColors.current
    StudioCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(colors.elevated),
                contentAlignment = Alignment.Center
            ) {
                ViralityIcon(R.drawable.ic_clip, contentDescription = null, tint = colors.secondary, modifier = Modifier.size(24.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = video.name,
                    color = colors.text,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${formatDuration(video.durationMs / 1000.0)} · ${formatBytes(video.sizeBytes)}",
                    color = colors.textMuted,
                    fontSize = 11.sp
                )
            }
            Box(
                modifier = Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(colors.elevated).clickable(onClick = onShare),
                contentAlignment = Alignment.Center
            ) {
                ViralityIcon(R.drawable.ic_share, contentDescription = "Share", modifier = Modifier.size(18.dp), tint = colors.secondary)
            }
            Box(
                modifier = Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(colors.error.copy(alpha = 0.12f)).clickable(onClick = onDelete),
                contentAlignment = Alignment.Center
            ) {
                ViralityIcon(R.drawable.ic_delete, contentDescription = "Delete", modifier = Modifier.size(18.dp), tint = colors.error)
            }
        }
    }
}
