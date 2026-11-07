package com.riptide.ui.received

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.riptide.R
import com.riptide.ui.components.RiptideTopBar
import com.riptide.ui.model.ReceivedFileRecord
import com.riptide.ui.theme.MonospaceFontFamily
import com.riptide.ui.theme.RiptideColors
import com.riptide.util.dayLabel
import com.riptide.util.formatBytes
import com.riptide.util.timeLabel
import kotlinx.coroutines.launch

/**
 * States 17 (history) and 19 (empty). Grouped by day, newest first.
 * The actual saved name is prominent; a rename is explained. Tapping a
 * file opens it; a missing file reports "File no longer available" and
 * its stale record is removed. Removing a record never deletes the file.
 */
@Composable
fun ReceivedScreen(
    viewModel: ReceivedViewModel,
    onNavigateToSettings: () -> Unit,
    onGoToTransfer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val records by viewModel.records.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    Scaffold(
        topBar = { RiptideTopBar(onOpenSettings = onNavigateToSettings) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = RiptideColors.Background,
        modifier = modifier
    ) { padding ->
        BoxWithConstraints(modifier = Modifier.padding(padding).fillMaxSize()) {
            val gutter = if (maxWidth < 360.dp) 16.dp else 24.dp
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = gutter)
            ) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "ON THIS PHONE",
                    color = RiptideColors.OnSurfaceVariant,
                    fontFamily = MonospaceFontFamily,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    letterSpacing = 1.3.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Received",
                    color = RiptideColors.OnSurface,
                    fontSize = 32.sp,
                    lineHeight = 40.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Your received files, across sessions.",
                    color = RiptideColors.OnSurfaceVariant,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = RiptideColors.SurfaceRaised,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp
                ) {
                    Text(
                        text = "Downloads/Riptide",
                        color = RiptideColors.OnSurface,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
                if (records.isEmpty()) {
                    Text(
                        text = "Nothing received yet",
                        color = RiptideColors.OnSurface,
                        fontSize = 21.sp,
                        lineHeight = 28.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Files you receive from a PC will appear here.",
                        color = RiptideColors.OnSurfaceVariant,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = onGoToTransfer,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RiptideColors.Primary, contentColor = RiptideColors.OnPrimary)
                    ) { Text("Go to Transfer", fontSize = 16.sp, fontWeight = FontWeight.Medium) }
                } else {
                    val grouped = records.sortedByDescending { it.timestamp }.groupBy { dayLabel(it.timestamp) }
                    grouped.forEach { (day, dayRecords) ->
                        Text(
                            text = day,
                            color = RiptideColors.OnSurfaceVariant,
                            fontFamily = MonospaceFontFamily,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            letterSpacing = 1.3.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            dayRecords.forEach { record ->
                                ReceivedFileRow(
                                    record = record,
                                    onOpen = {
                                        viewModel.openRecord(record, context) { result ->
                                            when (result) {
                                                OpenResult.Missing -> scope.launch { snackbarHostState.showSnackbar("File no longer available") }
                                                OpenResult.NoHandler -> scope.launch { snackbarHostState.showSnackbar("No app can open this file") }
                                                OpenResult.Opened -> Unit
                                            }
                                        }
                                    },
                                    onRemove = { viewModel.removeRecord(record) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                    Text(
                        text = "Tap a file to open it on this phone.",
                        color = RiptideColors.OnSurfaceVariant,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ReceivedFileRow(record: ReceivedFileRecord, onOpen: () -> Unit, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    var menuOpen by remember { mutableStateOf(false) }
    Surface(
        onClick = onOpen,
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = RiptideColors.Surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                painter = painterResource(R.drawable.riptide_file),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = RiptideColors.Success
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.savedName,
                    color = RiptideColors.OnSurface,
                    fontSize = 17.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${formatBytes(record.sizeBytes)} · From ${record.sourcePeer} · ${timeLabel(record.timestamp)}",
                    color = RiptideColors.OnSurfaceVariant,
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )
                if (record.verified) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "SHA-256 verified",
                        color = RiptideColors.Success,
                        fontSize = 13.sp,
                        lineHeight = 20.sp
                    )
                }
                if (record.savedName != record.originalName) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Original: ${record.originalName} · Renamed to avoid overwrite",
                        color = RiptideColors.OnSurfaceVariant,
                        fontSize = 13.sp,
                        lineHeight = 20.sp
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(48.dp)) {
                Icon(
                    painter = painterResource(R.drawable.riptide_more),
                    contentDescription = "More options for ${record.savedName}",
                    modifier = Modifier.size(24.dp),
                    tint = RiptideColors.OnSurfaceVariant
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Remove from history", color = RiptideColors.OnSurface) },
                    onClick = { menuOpen = false; onRemove() }
                )
            }
        }
    }
}
