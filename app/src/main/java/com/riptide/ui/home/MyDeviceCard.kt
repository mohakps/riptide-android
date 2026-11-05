package com.riptide.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.riptide.ui.components.CodeBadge
import com.riptide.ui.components.RiptideCard
import com.riptide.ui.theme.MonospaceFontFamily
import com.riptide.ui.theme.RiptideColors

@Composable
fun MyDeviceCard(deviceName: String, code: String, fullFingerprint: String, onCopyCode: () -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    RiptideCard(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(text = "THIS DEVICE", style = MaterialTheme.typography.bodySmall, color = RiptideColors.TextMuted)
                Text(text = deviceName.ifBlank { "This Phone" }, style = MaterialTheme.typography.titleMedium, color = RiptideColors.TextPrimary)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                CodeBadge(code = code)
                IconButton(onClick = onCopyCode, modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)) {
                    Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy Verification Code", tint = RiptideColors.TextMuted)
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(text = "Your 6-digit identity code. Compare out-of-band with peer.", style = MaterialTheme.typography.bodyMedium, color = RiptideColors.TextMuted)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = if (expanded) "Hide full fingerprint" else "View full fingerprint", style = MaterialTheme.typography.bodySmall, color = RiptideColors.PrimaryCoral, modifier = Modifier.clickable { expanded = !expanded }.padding(vertical = 4.dp))
        AnimatedVisibility(visible = expanded) {
            Text(text = fullFingerprint.chunked(4).joinToString(" "), fontFamily = MonospaceFontFamily, fontSize = 11.sp, color = RiptideColors.TextMuted, modifier = Modifier.padding(top = 8.dp))
        }
    }
}
