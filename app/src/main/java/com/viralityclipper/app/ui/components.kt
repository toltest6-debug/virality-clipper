package com.viralityclipper.app.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.viralityclipper.app.ui.theme.LocalViralityColors
import com.viralityclipper.app.R
import com.viralityclipper.app.data.ApiClient

// --------------------------------------------------------------- icons

@Composable
fun ViralityIcon(
    resId: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalViralityColors.current.text
) {
    Icon(painter = painterResource(resId), contentDescription = contentDescription, modifier = modifier, tint = tint)
}

// --------------------------------------------------------------- cards

@Composable
fun StudioCard(
    modifier: Modifier = Modifier,
    background: Color? = null,
    border: Boolean = true,
    content: @Composable () -> Unit
) {
    val colors = LocalViralityColors.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(background ?: colors.surface)
            .then(
                if (border) Modifier.border(1.dp, colors.border, RoundedCornerShape(20.dp)) else Modifier
            )
    ) {
        content()
    }
}

// --------------------------------------------------------------- chips

@Composable
fun SelectChip(
    label: String,
    selected: Boolean,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalViralityColors.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) colors.primary else colors.elevated)
            .border(1.dp, if (selected) colors.primary else colors.border, RoundedCornerShape(18.dp))
            .clickable { onClick(label) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else colors.textMuted,
            fontSize = 13.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
        )
    }
}

@Composable
fun ChipRow(
    label: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(label)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            options.forEach { option ->
                SelectChip(label = option, selected = option == selected, onClick = onSelect)
            }
        }
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        fontSize = 11.sp,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
        color = LocalViralityColors.current.textMuted,
        letterSpacing = 1.5.sp
    )
}

// --------------------------------------------------------------- buttons

@Composable
fun GradientCta(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    icon: Int? = null
) {
    val colors = LocalViralityColors.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (enabled) Brush.linearGradient(listOf(colors.primary, colors.secondary))
                else Brush.linearGradient(listOf(colors.elevated, colors.elevated))
            )
            .border(1.dp, if (enabled) colors.secondary.copy(alpha = 0.6f) else colors.border, RoundedCornerShape(18.dp))
            .shadow(if (enabled) 10.dp else 0.dp, RoundedCornerShape(18.dp), clip = false)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = Color.White,
                strokeWidth = 2.5.dp
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    ViralityIcon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color.White)
                }
                Text(
                    text = text,
                    color = if (enabled) Color.White else colors.textMuted,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
    }
}

// --------------------------------------------------------------- progress

@Composable
fun LinearProgressBar(progressPct: Int, modifier: Modifier = Modifier, color: Color = LocalViralityColors.current.primary) {
    val colors = LocalViralityColors.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(colors.elevated)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction = (progressPct.coerceIn(0, 100) / 100f))
                .fillMaxHeight()
                .background(Brush.horizontalGradient(listOf(colors.primary, colors.secondary)))
        )
    }
}

// --------------------------------------------------------------- stat + badges

@Composable
fun ScoreBadge(score: Int, modifier: Modifier = Modifier) {
    val colors = LocalViralityColors.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.linearGradient(listOf(colors.primary, colors.secondary)))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "$score / 100",
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
        )
    }
}

@Composable
fun StepLabel(label: String) {
    Text(
        text = label.uppercase(),
        fontSize = 10.sp,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
        color = LocalViralityColors.current.textMuted,
        letterSpacing = 1.5.sp
    )
}

// --------------------------------------------------------------- thumbnail

@Composable
fun ThumbnailImage(
    api: ApiClient,
    baseUrl: String,
    clipId: String,
    modifier: Modifier = Modifier,
    cornerRadius: androidx.compose.ui.unit.Dp = 12.dp
) {
    var bitmap by remember(clipId, baseUrl) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var loaded by remember(clipId, baseUrl) { mutableStateOf(false) }

    LaunchedEffect(clipId, baseUrl) {
        bitmap = api.loadThumbnail(baseUrl, clipId)
        loaded = true
    }

    val colors = LocalViralityColors.current

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(colors.elevated)
            .border(1.dp, colors.border, RoundedCornerShape(cornerRadius)),
        contentAlignment = Alignment.Center
    ) {
        val bmp = bitmap
        if (bmp != null) {
            androidx.compose.foundation.Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else if (!loaded) {
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                color = colors.primary,
                strokeWidth = 2.5.dp
            )
        } else {
            ViralityIcon(R.drawable.ic_sparkle, contentDescription = null, modifier = Modifier.size(36.dp), tint = colors.textMuted)
        }
    }
}

// --------------------------------------------------------------- helpers

fun formatDuration(totalSec: Double): String {
    val s = totalSec.toInt()
    if (s < 60) return "${s}s"
    val m = s / 60
    val sec = s % 60
    return "$m:${sec.toString().padStart(2, '0')}"
}

fun formatBytes(size: Long): String {
    if (size < 1024) return "$size B"
    if (size < 1024 * 1024) return "${size / 1024} KB"
    if (size < 1024 * 1024 * 1024) return "%.1f MB".format(size / (1024f * 1024f))
    return "%.1f GB".format(size / (1024f * 1024f * 1024f))
}
