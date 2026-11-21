package com.viralityclipper.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Dark creator-studio palette, reachable everywhere via [LocalViralityColors]. */
data class ViralityThemeColors(
    val background: Color = Background,
    val surface: Color = Surface,
    val elevated: Color = Elevated,
    val primary: Color = PrimaryViolet,
    val primaryDark: Color = PrimaryVioletDark,
    val secondary: Color = SecondaryCyan,
    val success: Color = Success,
    val text: Color = TextPrimary,
    val textMuted: Color = TextMuted,
    val border: Color = BorderSubtle,
    val error: Color = Error,
    val errorDark: Color = ErrorDark
)

val LocalViralityColors = staticCompositionLocalOf { ViralityThemeColors() }

val viralityDarkScheme = darkColorScheme(
    primary = PrimaryViolet,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = PrimaryVioletDark,
    onPrimaryContainer = Color(0xFFF3F6FC),
    secondary = SecondaryCyan,
    onSecondary = Color(0xFF06363D),
    secondaryContainer = Color(0xFF0A3340),
    onSecondaryContainer = Color(0xFCF1F8FD),
    background = Background,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = Elevated,
    onSurfaceVariant = TextMuted,
    error = Error,
    onError = Color(0xFF1A0D0E),
    outline = BorderSubtle
)

@Composable
private fun viralityShapes() = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/**
 * Always renders the dark creator-studio palette regardless of system setting.
 */
@Composable
fun ViralityClipperTheme(content: @Composable () -> Unit) {
    val themeColors = ViralityThemeColors()
    androidx.compose.runtime.CompositionLocalProvider(
        LocalViralityColors provides themeColors
    ) {
        MaterialTheme(
            colorScheme = viralityDarkScheme,
            shapes = viralityShapes(),
            content = content
        )
    }
}
