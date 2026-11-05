package com.riptide.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val MonospaceFontFamily = FontFamily.Monospace

val RiptideTypography = Typography(
    titleLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = RiptideColors.TextPrimary),
    titleMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = RiptideColors.TextPrimary),
    bodyLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 15.sp, color = RiptideColors.TextPrimary),
    bodyMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 14.sp, color = RiptideColors.TextMuted),
    bodySmall = TextStyle(fontFamily = MonospaceFontFamily, fontWeight = FontWeight.Normal, fontSize = 11.sp, letterSpacing = 0.5.sp, color = RiptideColors.TextMuted),
    labelLarge = TextStyle(fontFamily = MonospaceFontFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = RiptideColors.PrimaryCoral)
)
