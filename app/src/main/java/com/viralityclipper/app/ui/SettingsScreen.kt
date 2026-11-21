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
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.viralityclipper.app.R
import com.viralityclipper.app.data.AppSettings
import com.viralityclipper.app.ui.theme.LocalViralityColors
import com.viralityclipper.app.ui.viewmodels.SettingsViewModel
import com.viralityclipper.app.ui.viewmodels.ViewModelFactory
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(container: com.viralityclipper.app.data.AppContainer, navController: NavHostController) {
    val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<SettingsViewModel>(
        factory = ViewModelFactory(container)
    )
    val settings by viewModel.settingsFlow.collectAsState(initial = AppSettings())
    val colors = LocalViralityColors.current
    val scope = rememberCoroutineScope()

    // Local (editable, unsaved) mirror of settings — edits persist immediately on change
    var url by remember { mutableStateOf("") }
    var provider by remember { mutableStateOf("auto") }
    var apiKey by remember { mutableStateOf("") }
    var clipCount by remember { mutableStateOf(3) }
    var targetSec by remember { mutableStateOf(45) }
    var aspectRatio by remember { mutableStateOf("9:16") }
    var burnCaptions by remember { mutableStateOf(true) }
    var connectionStatus by remember { mutableStateOf<ConnectionStatus>(ConnectionStatus.Idle) }
    var isTesting by remember { mutableStateOf(false) }

    var defaultsLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(settings) {
        if (defaultsLoaded) return@LaunchedEffect
        defaultsLoaded = true
        url = settings.backendUrl
        provider = settings.apiProvider
        apiKey = settings.apiKey
        clipCount = settings.clipCount
        targetSec = settings.targetDurationSec
        aspectRatio = settings.aspectRatio
        burnCaptions = settings.burnCaptions
    }

    fun persistBackendUrl() {
        scope.launch { viewModel.setBackendUrl(url) }
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
            Text("Settings", color = colors.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        // Backend URL
        SectionLabel("Backend server")
        StudioCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Backend Server URL", color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it; persistBackendUrl() },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("http://10.0.2.2:8000", color = colors.textMuted, fontSize = 13.sp) },
                    textStyle = androidx.compose.material3.LocalTextStyle.current.copy(color = colors.text, fontSize = 14.sp),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Uri),
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.primary,
                        unfocusedBorderColor = colors.border,
                        focusedTextColor = colors.text,
                        unfocusedTextColor = colors.text,
                        cursorColor = colors.secondary
                    ),
                    trailingIcon = {
                        if (isTesting) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = colors.secondary,
                                strokeWidth = 2.5.dp
                            )
                        }
                    }
                )
                ConnectionStatusRow(status = connectionStatus, isTesting = isTesting, colors = colors)
                Button(
                    onClick = {
                        isTesting = true
                        connectionStatus = ConnectionStatus.Testing
                        scope.launch {
                            val ok = try {
                                viewModel.testConnection(url)
                            } catch (_: Exception) {
                                false
                            }
                            isTesting = false
                            connectionStatus = if (ok) ConnectionStatus.Success else ConnectionStatus.Error
                        }
                    },
                    enabled = !isTesting,
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = colors.primary,
                        contentColor = androidx.compose.ui.graphics.Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ViralityIcon(R.drawable.ic_link, contentDescription = null, modifier = Modifier.size(18.dp), tint = androidx.compose.ui.graphics.Color.White)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Test Connection", color = androidx.compose.ui.graphics.Color.White)
                }
                Text(
                    text = "For an emulator use http://10.0.2.2:8000 or your LAN address. Any HTTPS URL works.",
                    color = colors.textMuted,
                    fontSize = 10.sp
                )
            }
        }

        // AI provider
        SectionLabel("AI provider (optional)")
        StudioCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Provider", color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf("auto", "openai", "gemini", "anthropic").forEach { p ->
                        SelectChip(
                            label = p.replaceFirstChar { it.uppercase() },
                            selected = provider == p,
                            onClick = { picked ->
                                provider = picked.lowercase()
                                scope.launch { viewModel.setProvider(provider) }
                            }
                        )
                    }
                }
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it; scope.launch { viewModel.setApiKey(it) } },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("sk-… (optional — AI keys are never stored on the backend)", color = colors.textMuted, fontSize = 12.sp) },
                    textStyle = androidx.compose.material3.LocalTextStyle.current.copy(color = colors.text, fontSize = 13.sp),
                    visualTransformation = PasswordVisualTransformation(),
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.primary,
                        unfocusedBorderColor = colors.border,
                        focusedTextColor = colors.text,
                        unfocusedTextColor = colors.text,
                        cursorColor = colors.secondary
                    )
                )
                Text(
                    text = if (provider == "auto") "Auto: picks OpenAI → Anthropic → Gemini based on the key you pass below (or the backend's env keys)."
                    else "Using ${provider.replaceFirstChar { it.uppercase() }} — set a matching API key above, or configure it on the backend.",
                    color = colors.textMuted,
                    fontSize = 10.sp
                )
            }
        }

        // Defaults
        SectionLabel("Defaults")
        ChipRow(
            label = "Default clip count",
            options = listOf("3", "5"),
            selected = clipCount.toString(),
            onSelect = { clipCount = it.toInt(); scope.launch { viewModel.setClipCount(clipCount) } }
        )
        ChipRow(
            label = "Default target length",
            options = listOf("30s", "45s", "60s"),
            selected = "${targetSec}s",
            onSelect = { targetSec = it.removeSuffix("s").toInt(); scope.launch { viewModel.setTargetSeconds(targetSec) } }
        )
        ChipRow(
            label = "Default aspect ratio",
            options = listOf("9:16", "16:9", "1:1"),
            selected = aspectRatio,
            onSelect = { aspectRatio = it; scope.launch { viewModel.setAspectRatio(aspectRatio) } }
        )

        // Burn captions default toggle
        StudioCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        burnCaptions = !burnCaptions
                        scope.launch { viewModel.setBurnCaptions(burnCaptions) }
                    }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Burn captions", color = colors.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("Default for new jobs", color = colors.textMuted, fontSize = 11.sp)
                }
                Switch(
                    checked = burnCaptions,
                    onCheckedChange = {
                        burnCaptions = it
                        scope.launch { viewModel.setBurnCaptions(it) }
                    },
                    colors = androidx.compose.material3.SwitchDefaults.colors(
                        checkedThumbColor = androidx.compose.ui.graphics.Color.White,
                        checkedTrackColor = colors.primary,
                        uncheckedThumbColor = colors.textMuted,
                        uncheckedTrackColor = colors.elevated
                    )
                )
            }
        }

        // App info
        StudioCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Virality Clipper", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.text)
                Text("Version ${com.viralityclipper.app.BuildConfig.VERSION_NAME}", color = colors.textMuted, fontSize = 11.sp)
                Text(
                    "Built with FastAPI + yt-dlp + ExoPlayer. Ships as a private app for your own clips.",
                    color = colors.textMuted,
                    fontSize = 10.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

private sealed class ConnectionStatus {
    object Idle : ConnectionStatus()
    object Testing : ConnectionStatus()
    object Success : ConnectionStatus()
    object Error : ConnectionStatus()
}

@Composable
private fun ConnectionStatusRow(status: ConnectionStatus, isTesting: Boolean, colors: com.viralityclipper.app.ui.theme.ViralityThemeColors) {
    val (text, color) = when (status) {
        is ConnectionStatus.Idle -> "Not tested yet" to colors.textMuted
        is ConnectionStatus.Testing -> "Testing connection…" to colors.secondary
        is ConnectionStatus.Success -> "Connected — backend is reachable" to colors.success
        is ConnectionStatus.Error -> "Couldn't reach the backend — check URL and server" to colors.error
    }
    Text(text = text, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
}
