package com.riptide.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.riptide.ui.theme.MonospaceFontFamily
import com.riptide.ui.theme.RiptideColors

/**
 * Status pill: words with color (CONNECTED, NEW PC, HOST APPROVAL PENDING).
 * Raised-surface fill, no border. The containerColor argument is kept for
 * call compatibility; the pill fill is always the raised surface and the
 * text takes the status color.
 */
@Composable
fun RiptideBadge(text: String, containerColor: Color, contentColor: Color, modifier: Modifier = Modifier) {
    Box(modifier = modifier.background(RiptideColors.SurfaceRaised, RoundedCornerShape(16.dp)).padding(horizontal = 12.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
        Text(text = text, fontFamily = MonospaceFontFamily, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 1.3.sp, fontWeight = FontWeight.Medium, color = contentColor)
    }
}

@Composable
fun CodeBadge(code: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.background(RiptideColors.SurfaceVariant, RoundedCornerShape(8.dp)).padding(horizontal = 14.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
        Text(text = code, fontFamily = MonospaceFontFamily, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, color = RiptideColors.PrimaryCoral)
    }
}
