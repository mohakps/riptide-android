package com.riptide.ui.settings

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.riptide.R
import com.riptide.core.DiagLog
import com.riptide.ui.theme.MonospaceFontFamily
import com.riptide.ui.theme.RiptideColors

/**
 * State 14. Back restores the originating tab. Device name edits live
 * here; the name is shown to the PC and is not the identity. Version is
 * the actual build version, never a hardcoded string.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onShowFingerprint: () -> Unit,
    modifier: Modifier = Modifier
) {
    val deviceName by viewModel.deviceName.collectAsState()
    val myCode by viewModel.myCode.collectAsState()
    // v0.3.4 test build: export status for the diagnostics log button.
    var exportStatus by remember { mutableStateOf<String?>(null) }
    // The actual installed version, read from the package — never hardcoded.
    val context = LocalContext.current
    val versionName = remember {
        try {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
        } catch (_: Exception) {
            "?"
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        color = RiptideColors.OnSurface,
                        fontSize = 24.sp,
                        lineHeight = 32.sp,
                        fontWeight = FontWeight.Medium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Icon(
                            painter = painterResource(R.drawable.riptide_back),
                            contentDescription = "Back",
                            modifier = Modifier.size(24.dp),
                            tint = RiptideColors.OnSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = RiptideColors.Background)
            )
        },
        containerColor = RiptideColors.Background,
        modifier = modifier
    ) { padding ->
        BoxWithConstraints(modifier = Modifier.padding(padding).fillMaxSize()) {
            val gutter = if (maxWidth < 360.dp) 16.dp else 24.dp
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = gutter)
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Keep it yours.",
                    color = RiptideColors.OnSurfaceVariant,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "DEVICE NAME",
                    color = RiptideColors.OnSurfaceVariant,
                    fontFamily = MonospaceFontFamily,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    letterSpacing = 1.3.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = deviceName,
                    onValueChange = viewModel::setDeviceName,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = RiptideColors.SurfaceRaised,
                        unfocusedContainerColor = RiptideColors.SurfaceRaised,
                        focusedTextColor = RiptideColors.OnSurface,
                        unfocusedTextColor = RiptideColors.OnSurface,
                        focusedBorderColor = RiptideColors.Outline,
                        unfocusedBorderColor = RiptideColors.Outline,
                        cursorColor = RiptideColors.Primary
                    ),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Shown to the PC. This isn't your identity.",
                    color = RiptideColors.OnSurfaceVariant,
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    text = "RECEIVED FILES",
                    color = RiptideColors.OnSurfaceVariant,
                    fontFamily = MonospaceFontFamily,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    letterSpacing = 1.3.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Downloads/Riptide",
                    color = RiptideColors.OnSurface,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 17.sp,
                    lineHeight = 24.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Your receive folder.",
                    color = RiptideColors.OnSurfaceVariant,
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    text = "YOUR IDENTITY",
                    color = RiptideColors.OnSurfaceVariant,
                    fontFamily = MonospaceFontFamily,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    letterSpacing = 1.3.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = myCode,
                    color = RiptideColors.OnSurface,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 26.sp,
                    lineHeight = 32.sp
                )
                TextButton(onClick = onShowFingerprint, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("View full fingerprint", color = RiptideColors.Primary, fontSize = 14.sp)
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "RIPTIDE FOR ANDROID",
                    color = RiptideColors.OnSurfaceVariant,
                    fontFamily = MonospaceFontFamily,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    letterSpacing = 1.3.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "$versionName · Direct, encrypted file transfer.",
                    color = RiptideColors.OnSurface,
                    fontSize = 16.sp,
                    lineHeight = 24.sp
                )
                Text(
                    text = "No account. No cloud.",
                    color = RiptideColors.OnSurfaceVariant,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Compatible with Riptide PC v1.3.",
                    color = RiptideColors.OnSurfaceVariant,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    text = "DIAGNOSTICS",
                    color = RiptideColors.OnSurfaceVariant,
                    fontFamily = MonospaceFontFamily,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    letterSpacing = 1.3.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Test build only. The app logs connects, pings, pongs and session failures with timestamps.",
                    color = RiptideColors.OnSurfaceVariant,
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )
                TextButton(
                    onClick = {
                        exportStatus = if (DiagLog.exportToDownloads(context)) {
                            "Saved to Downloads/Riptide/riptide-diagnostics.log"
                        } else {
                            "Export failed. No diagnostics log yet."
                        }
                    },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Share diagnostics log", color = RiptideColors.Primary, fontSize = 14.sp)
                }
                exportStatus?.let { status ->
                    Text(
                        text = status,
                        color = RiptideColors.OnSurfaceVariant,
                        fontSize = 13.sp,
                        lineHeight = 20.sp
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
