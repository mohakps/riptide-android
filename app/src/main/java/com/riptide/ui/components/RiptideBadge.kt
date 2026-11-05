package com.riptide.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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

@Composable
fun RiptideBadge(text: String, containerColor: Color, contentColor: Color, modifier: Modifier = Modifier) {
    Box(modifier = modifier.background(containerColor.copy(alpha = 0.15f), RoundedCornerShape(6.dp)).border(1.dp, containerColor.copy(alpha = 0.5f), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 4.dp), contentAlignment = Alignment.Center) {
        Text(text = text, fontFamily = MonospaceFontFamily, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = contentColor)
    }
}

@Composable
fun CodeBadge(code: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.background(RiptideColors.SurfaceVariant, RoundedCornerShape(8.dp)).border(1.dp, RiptideColors.BorderOutline, RoundedCornerShape(8.dp)).padding(horizontal = 14.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
        Text(text = code, fontFamily = MonospaceFontFamily, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, color = RiptideColors.PrimaryCoral)
    }
}
