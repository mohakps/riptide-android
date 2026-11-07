package com.riptide.ui.session

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.riptide.ui.theme.MonospaceFontFamily
import com.riptide.ui.theme.RiptideColors

/**
 * State 11: download request. The name must not be blank; the PC must
 * approve before any file is sent. The dialog stays usable while a
 * previous request is waiting — starting one request at a time.
 */
@Composable
fun DownloadRequestDialog(
    initialFilename: String = "",
    onDismiss: () -> Unit,
    onRequestDownload: (String) -> Unit
) {
    var filename by remember { mutableStateOf(initialFilename) }
    var triedSubmit by remember { mutableStateOf(false) }
    val blank = filename.isBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Download from PC", color = RiptideColors.OnSurface, fontSize = 21.sp, fontWeight = FontWeight.Medium)
        },
        text = {
            Column {
                Text(
                    text = "Enter a file name or relative path. The PC must approve your request.",
                    color = RiptideColors.OnSurfaceVariant,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "FILE NAME OR RELATIVE PATH",
                    color = RiptideColors.OnSurfaceVariant,
                    fontFamily = MonospaceFontFamily,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    letterSpacing = 1.3.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = filename,
                    onValueChange = { filename = it },
                    placeholder = { Text("report.pdf", color = RiptideColors.OnSurfaceVariant) },
                    singleLine = true,
                    isError = triedSubmit && blank,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = RiptideColors.SurfaceRaised,
                        unfocusedContainerColor = RiptideColors.SurfaceRaised,
                        focusedTextColor = RiptideColors.OnSurface,
                        unfocusedTextColor = RiptideColors.OnSurface,
                        focusedBorderColor = RiptideColors.Outline,
                        unfocusedBorderColor = RiptideColors.Outline,
                        cursorColor = RiptideColors.Primary,
                        errorBorderColor = RiptideColors.Error
                    ),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp)
                )
                if (triedSubmit && blank) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Enter a file name first.",
                        color = RiptideColors.Error,
                        fontSize = 13.sp,
                        lineHeight = 20.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (blank) {
                        triedSubmit = true
                    } else {
                        onRequestDownload(filename.trim())
                        onDismiss()
                    }
                },
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RiptideColors.Primary, contentColor = RiptideColors.OnPrimary),
                modifier = Modifier.heightIn(min = 48.dp)
            ) { Text("Request download", fontSize = 16.sp, fontWeight = FontWeight.Medium) }
        },
        dismissButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RiptideColors.SurfaceRaised, contentColor = RiptideColors.OnSurface),
                modifier = Modifier.heightIn(min = 48.dp)
            ) { Text("Cancel", fontSize = 16.sp, fontWeight = FontWeight.Medium) }
        },
        containerColor = RiptideColors.Surface,
        shape = RoundedCornerShape(28.dp)
    )
}
