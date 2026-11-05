package com.riptide.ui.session

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.riptide.ui.theme.RiptideColors

@Composable
fun DownloadRequestDialog(onDismiss: () -> Unit, onRequestDownload: (String) -> Unit) {
    var filename by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(text = "Download File from PC", style = MaterialTheme.typography.titleMedium, color = RiptideColors.TextPrimary) }, text = {
        Column {
            Text(text = "Type the exact name or relative path of the file on the remote PC. The PC host must approve the request.", style = MaterialTheme.typography.bodyMedium, color = RiptideColors.TextMuted)
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(value = filename, onValueChange = { filename = it }, placeholder = { Text("e.g. document.pdf", color = RiptideColors.TextMuted) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
    }, confirmButton = {
        Button(onClick = { if (filename.isNotBlank()) { onRequestDownload(filename.trim()); onDismiss() } }, shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = RiptideColors.PrimaryCoral, contentColor = RiptideColors.OnPrimary), modifier = Modifier.defaultMinSize(minHeight = 48.dp)) { Text("Request Download") }
    }, dismissButton = {
        OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, RiptideColors.BorderOutline), modifier = Modifier.defaultMinSize(minHeight = 48.dp)) { Text("Cancel", color = RiptideColors.TextPrimary) }
    }, containerColor = RiptideColors.Surface)
}
