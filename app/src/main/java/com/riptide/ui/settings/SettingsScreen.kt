package com.riptide.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.riptide.R
import com.riptide.ui.theme.MonospaceFontFamily
import com.riptide.ui.theme.RiptideColors

/**
 * Settings. One scrollable column ordered Device, Storage, Identity,
 * About, Diagnostics. Cards are #1C1C1C with 24 dp corners and no
 * elevation; section labels are 12/16 muted uppercase monospace with
 * 1.3 sp tracking. Version is the actual build version, never a
 * hardcoded string.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onShowFingerprint: () -> Unit,
    modifier: Modifier = Modifier,
    onPickReceiveFolder: () -> Unit = {},
    onShareDiagnostics: () -> Unit = {}
) {
    val deviceName by viewModel.deviceName.collectAsState()
    val myCode by viewModel.myCode.collectAsState()
    val receiveFolder by viewModel.receiveFolder.collectAsState()
    val canReset by viewModel.canResetIdentity.collectAsState()
    val resetError by viewModel.resetError.collectAsState()
    var showRename by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var resetting by remember { mutableStateOf(false) }
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
                SectionLabel("DEVICE")
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = RiptideColors.Surface,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 72.dp)
                            .clickable { showRename = true }
                            .semantics { contentDescription = "Edit device name"; role = Role.Button }
                            .padding(16.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.riptide_phone),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = RiptideColors.OnSurfaceVariant
                        )
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Device name",
                                color = RiptideColors.OnSurface,
                                fontSize = 16.sp,
                                lineHeight = 22.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = deviceName,
                                color = RiptideColors.OnSurfaceVariant,
                                fontSize = 14.sp,
                                lineHeight = 20.sp
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                        Icon(
                            painter = painterResource(R.drawable.riptide_edit),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = RiptideColors.OnSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                SectionLabel("STORAGE")
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = RiptideColors.Surface,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 72.dp)
                            .clickable { onPickReceiveFolder() }
                            .semantics { contentDescription = "Change receive folder"; role = Role.Button }
                            .padding(16.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.riptide_folder),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = RiptideColors.OnSurfaceVariant
                        )
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Receive folder",
                                color = RiptideColors.OnSurface,
                                fontSize = 16.sp,
                                lineHeight = 22.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = receiveFolder.displayName,
                                color = RiptideColors.OnSurfaceVariant,
                                fontSize = 14.sp,
                                lineHeight = 20.sp
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                        Icon(
                            painter = painterResource(R.drawable.riptide_edit),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = RiptideColors.OnSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                SectionLabel("IDENTITY")
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = RiptideColors.Surface,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                painter = painterResource(R.drawable.riptide_shield),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = RiptideColors.OnSurfaceVariant
                            )
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "VERIFICATION CODE",
                                    color = RiptideColors.OnSurfaceVariant,
                                    fontFamily = MonospaceFontFamily,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                    letterSpacing = 1.3.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = myCode,
                                    color = RiptideColors.OnSurface,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 29.sp,
                                    lineHeight = 36.sp
                                )
                                TextButton(
                                    onClick = onShowFingerprint,
                                    modifier = Modifier.heightIn(min = 48.dp)
                                ) {
                                    Text("View full fingerprint", color = RiptideColors.OnSurface, fontSize = 14.sp)
                                }
                            }
                        }
                        HorizontalDivider(color = RiptideColors.SurfaceVariant, thickness = 1.dp)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .clickable(enabled = canReset) { showResetConfirm = true }
                                .semantics {
                                    contentDescription = "Reset identity"
                                    role = Role.Button
                                }
                                .padding(vertical = 4.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.riptide_reset),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = if (canReset) RiptideColors.Error else RiptideColors.OnSurfaceVariant
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = "Reset identity",
                                color = if (canReset) RiptideColors.Error else RiptideColors.OnSurfaceVariant,
                                fontSize = 16.sp,
                                lineHeight = 22.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        if (!canReset) {
                            Text(
                                text = "Disconnect before resetting your identity.",
                                color = RiptideColors.OnSurfaceVariant,
                                fontSize = 13.sp,
                                lineHeight = 20.sp
                            )
                        }
                        resetError?.let { err ->
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = err,
                                color = RiptideColors.Error,
                                fontSize = 13.sp,
                                lineHeight = 20.sp
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                SectionLabel("ABOUT")
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = RiptideColors.Surface,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Riptide for Android",
                                color = RiptideColors.OnSurface,
                                fontSize = 16.sp,
                                lineHeight = 22.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "v$versionName",
                                color = RiptideColors.OnSurfaceVariant,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp,
                                lineHeight = 20.sp
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Direct, encrypted file transfer.",
                            color = RiptideColors.OnSurfaceVariant,
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )
                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider(color = RiptideColors.SurfaceVariant, thickness = 1.dp)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .clickable {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/mohakps/riptide-android"))
                                    context.startActivity(intent)
                                }
                                .semantics { contentDescription = "GitHub repository"; role = Role.Button }
                                .padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = "GitHub repository",
                                color = RiptideColors.OnSurface,
                                fontSize = 16.sp,
                                lineHeight = 22.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                painter = painterResource(R.drawable.riptide_external),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = RiptideColors.OnSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                SectionLabel("DIAGNOSTICS")
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = RiptideColors.Surface,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(
                            text = "Connection events and session errors for troubleshooting.",
                            color = RiptideColors.OnSurfaceVariant,
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )
                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider(color = RiptideColors.SurfaceVariant, thickness = 1.dp)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .clickable { onShareDiagnostics() }
                                .semantics { contentDescription = "Share diagnostics log"; role = Role.Button }
                                .padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = "Share diagnostics log",
                                color = RiptideColors.OnSurface,
                                fontSize = 16.sp,
                                lineHeight = 22.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                painter = painterResource(R.drawable.riptide_share),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = RiptideColors.OnSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showRename) {
        var name by remember { mutableStateOf(deviceName) }
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text("Edit device name", color = RiptideColors.OnSurface) },
            text = {
                Column {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
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
                    if (name.isBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "A name is required.",
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
                        if (viewModel.saveDeviceName(name)) showRename = false
                    },
                    enabled = name.trim().isNotBlank(),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RiptideColors.Primary, contentColor = RiptideColors.OnPrimary)
                ) { Text("Save") }
            },
            dismissButton = {
                Button(
                    onClick = { showRename = false },
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RiptideColors.SurfaceRaised, contentColor = RiptideColors.OnSurface)
                ) { Text("Cancel") }
            },
            containerColor = RiptideColors.Surface,
            shape = RoundedCornerShape(28.dp)
        )
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { if (!resetting) showResetConfirm = false },
            icon = {
                Icon(
                    painter = painterResource(R.drawable.riptide_reset),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = RiptideColors.Error
                )
            },
            title = { Text("Reset this device’s identity?", color = RiptideColors.OnSurface) },
            text = {
                Text(
                    "This creates a new fingerprint. Previously connected devices will need to verify your fingerprint again.",
                    color = RiptideColors.OnSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        resetting = true
                        viewModel.resetIdentity { ok ->
                            resetting = false
                            if (ok) showResetConfirm = false
                        }
                    },
                    enabled = !resetting,
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RiptideColors.Primary, contentColor = RiptideColors.OnPrimary)
                ) { Text("Reset identity") }
            },
            dismissButton = {
                Button(
                    onClick = { showResetConfirm = false },
                    enabled = !resetting,
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RiptideColors.SurfaceRaised, contentColor = RiptideColors.OnSurface)
                ) { Text("Cancel") }
            },
            containerColor = RiptideColors.Surface,
            shape = RoundedCornerShape(28.dp)
        )
    }
}

/** 12/16 muted uppercase monospace section label, 1.3 sp tracking. */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = RiptideColors.OnSurfaceVariant,
        fontFamily = MonospaceFontFamily,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 1.3.sp,
        fontWeight = FontWeight.Medium
    )
}
