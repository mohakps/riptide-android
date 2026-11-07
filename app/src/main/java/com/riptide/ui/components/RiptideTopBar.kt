package com.riptide.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.riptide.R
import com.riptide.ui.theme.RiptideColors

/** The approved top bar: dot-wave + lowercase "riptide" wordmark, Settings gear at right. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RiptideTopBar(onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.riptide_dot_wave),
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = RiptideColors.OnSurface
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "riptide",
                    color = RiptideColors.OnSurface,
                    fontSize = 24.sp,
                    lineHeight = 32.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        },
        actions = {
            IconButton(onClick = onOpenSettings, modifier = Modifier.size(48.dp)) {
                Icon(
                    painter = painterResource(R.drawable.riptide_settings),
                    contentDescription = "Open Settings",
                    modifier = Modifier.size(24.dp),
                    tint = RiptideColors.OnSurface
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = RiptideColors.Background),
        modifier = modifier
    )
}
