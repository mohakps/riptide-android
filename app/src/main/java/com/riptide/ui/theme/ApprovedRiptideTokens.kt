package com.riptide.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Final design values. Copy into the app; do not enable wallpaper dynamic color. */
object ApprovedRiptideTokens {
    val Background = Color(0xFF101010)
    val Surface = Color(0xFF1C1C1C)
    val SurfaceRaised = Color(0xFF282828)
    val OnSurface = Color(0xFFF2F0EB)
    val OnSurfaceVariant = Color(0xFFB8B6B1)
    val Outline = Color(0xFF555555)
    val Primary = Color(0xFFFF6B6B)
    val OnPrimary = Background
    val CodeContainer = OnSurface
    val OnCodeContainer = Background
    val Success = Color(0xFF78DC8C)
    val Warning = Color(0xFFFFD166)
    val Error = Primary
    val WidgetRadius = 28.dp
    val IdentityGap = 12.dp
    val IdentityToJoinGap = 32.dp // Final value; do not add 16 dp again.
    val Gutter = 24.dp
}
