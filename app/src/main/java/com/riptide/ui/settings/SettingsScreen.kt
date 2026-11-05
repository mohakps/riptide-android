package com.riptide.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.riptide.ui.components.RiptideCard
import com.riptide.ui.theme.MonospaceFontFamily
import com.riptide.ui.theme.RiptideColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val deviceName by viewModel.deviceName.collectAsState()
    val myCode by viewModel.myCode.collectAsState()
    val fingerprint by viewModel.fullFingerprint.collectAsState()
    Scaffold(topBar = {
        TopAppBar(title = { Text("Settings", color = RiptideColors.TextPrimary) }, navigationIcon = {
            IconButton(onClick = onBack, modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = RiptideColors.TextPrimary)
            }
        }, colors = TopAppBarDefaults.topAppBarColors(containerColor = RiptideColors.Background))
    }, containerColor = RiptideColors.Background, modifier = modifier) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
            Spacer(modifier = Modifier.height(12.dp))
            RiptideCard(modifier = Modifier.fillMaxWidth()) {
                Text(text = "DEVICE NAME", style = MaterialTheme.typography.bodySmall, color = RiptideColors.TextMuted)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = deviceName, onValueChange = viewModel::setDeviceName, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "My code: $myCode", fontFamily = MonospaceFontFamily, style = MaterialTheme.typography.bodyMedium, color = RiptideColors.TextPrimary)
                if (fingerprint.isNotBlank()) {
                    Text(text = fingerprint, fontFamily = MonospaceFontFamily, style = MaterialTheme.typography.bodySmall, color = RiptideColors.TextMuted)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            RiptideCard(modifier = Modifier.fillMaxWidth()) {
                Text(text = "RECEIVE FOLDER", style = MaterialTheme.typography.bodySmall, color = RiptideColors.TextMuted)
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = "Downloads/Riptide", fontFamily = MonospaceFontFamily, style = MaterialTheme.typography.titleMedium, color = RiptideColors.TextPrimary)
                Text(text = "Fixed in v0.1 via Scoped MediaStore storage.", style = MaterialTheme.typography.bodySmall, color = RiptideColors.TextMuted)
            }
            Spacer(modifier = Modifier.height(16.dp))
            RiptideCard(modifier = Modifier.fillMaxWidth()) {
                Text(text = "ABOUT", style = MaterialTheme.typography.bodySmall, color = RiptideColors.TextMuted)
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = "Riptide Android v0.1 (Build 1)", style = MaterialTheme.typography.bodyLarge, color = RiptideColors.TextPrimary)
                Text(text = "Compatible with Riptide PC v1.3 wire protocol.\nEncrypted peer-to-peer file transfer. Zero cloud.", style = MaterialTheme.typography.bodyMedium, color = RiptideColors.TextMuted)
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
