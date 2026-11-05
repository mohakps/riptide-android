package com.riptide.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.riptide.ui.components.RiptideCard
import com.riptide.ui.theme.MonospaceFontFamily
import com.riptide.ui.theme.RiptideColors

@Composable
fun QuickJoinCard(hostInput: String, isConnecting: Boolean, onHostChanged: (String) -> Unit, onJoinClicked: () -> Unit, modifier: Modifier = Modifier) {
    RiptideCard(modifier = modifier.fillMaxWidth()) {
        Text(text = "CONNECT TO PC", style = MaterialTheme.typography.bodySmall, color = RiptideColors.TextMuted)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = hostInput, onValueChange = onHostChanged, placeholder = { Text("metalvoid.ddns.net", color = RiptideColors.TextMuted) }, trailingIcon = { Text(text = ":50000", fontFamily = MonospaceFontFamily, color = RiptideColors.TextMuted, modifier = Modifier.padding(end = 12.dp)) }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp))
        Spacer(modifier = Modifier.height(14.dp))
        Button(onClick = onJoinClicked, enabled = hostInput.isNotBlank() && !isConnecting, modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp), shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = RiptideColors.PrimaryCoral, contentColor = RiptideColors.OnPrimary)) {
            if (isConnecting) {
                CircularProgressIndicator(strokeWidth = 2.dp, color = RiptideColors.OnPrimary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text("Connecting...")
            } else {
                Text("Join Server")
            }
        }
    }
}
