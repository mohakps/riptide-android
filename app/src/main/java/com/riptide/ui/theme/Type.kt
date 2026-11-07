package com.riptide.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val MonospaceFontFamily = FontFamily.Monospace

/** Final typography (tokens.json): sans for names/body/controls, monospace for codes/metadata. */
val RiptideTypography = Typography(
    // App name 24/32 medium sans.
    titleLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 24.sp, lineHeight = 32.sp, color = RiptideColors.TextPrimary),
    // Device name 21/28 medium sans.
    titleMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 21.sp, lineHeight = 28.sp, color = RiptideColors.TextPrimary),
    // Recent peer name 17/24 medium sans.
    titleSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 17.sp, lineHeight = 24.sp, color = RiptideColors.TextPrimary),
    bodyLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, color = RiptideColors.TextPrimary),
    bodyMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, color = RiptideColors.TextPrimary),
    // Section metadata labels: 12/16 medium monospace, tracking 1.3.
    bodySmall = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 1.3.sp, color = RiptideColors.TextMuted),
    // Helper / peer host: 13/20 regular sans.
    labelMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 20.sp, color = RiptideColors.TextMuted),
    // Navigation label: 12/16 medium sans.
    labelSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, color = RiptideColors.TextMuted),
    labelLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp, color = RiptideColors.TextPrimary),
    // Join PC heading: 32/40 medium sans.
    headlineLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 32.sp, lineHeight = 40.sp, color = RiptideColors.TextPrimary),
    headlineMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 32.sp, lineHeight = 40.sp, color = RiptideColors.TextPrimary)
)
