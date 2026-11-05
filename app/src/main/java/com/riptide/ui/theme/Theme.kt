package com.riptide.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = RiptideColors.PrimaryCoral,
    onPrimary = RiptideColors.OnPrimary,
    background = RiptideColors.Background,
    surface = RiptideColors.Surface,
    surfaceVariant = RiptideColors.SurfaceVariant,
    outline = RiptideColors.BorderOutline,
    onBackground = RiptideColors.TextPrimary,
    onSurface = RiptideColors.TextPrimary,
    onSurfaceVariant = RiptideColors.TextMuted
)

@Composable
fun RiptideTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColorScheme, typography = RiptideTypography, content = content)
}
