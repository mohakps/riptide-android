package com.riptide.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * Final dark scheme. Dynamic (wallpaper-derived) color is disabled, and
 * surfaceTint is set to the surface color itself so Material never
 * recolors the exact fills with a tonal overlay.
 */
private val DarkColorScheme = darkColorScheme(
    primary = RiptideColors.PrimaryCoral,
    onPrimary = RiptideColors.OnPrimary,
    background = RiptideColors.Background,
    surface = RiptideColors.Surface,
    surfaceVariant = RiptideColors.SurfaceVariant,
    surfaceTint = RiptideColors.Surface,
    outline = RiptideColors.BorderOutline,
    onBackground = RiptideColors.TextPrimary,
    onSurface = RiptideColors.TextPrimary,
    onSurfaceVariant = RiptideColors.TextMuted,
    error = RiptideColors.ErrorRed,
    onError = RiptideColors.OnPrimary,
    tertiary = RiptideColors.SuccessMint
)

@Composable
fun RiptideTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColorScheme, typography = RiptideTypography, content = content)
}
