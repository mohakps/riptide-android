package com.riptide.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.riptide.ui.theme.RiptideColors

/** Group a hex fingerprint in fours, e.g. "1234 5678 …". All 64 characters, plain selectable text. */
fun groupedFingerprint(fingerprint: String): String =
    fingerprint.filter { it.isLetterOrDigit() }.chunked(4).joinToString(" ")

/**
 * State 20: full local fingerprint sheet. Dismissing preserves the
 * underlying screen state. Copy copies the raw hex (no spaces).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneFingerprintSheet(
    deviceName: String,
    code: String,
    fingerprint: String,
    onCopyFingerprint: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    ModalBottomSheet(
        onDismissRequest = onDone,
        containerColor = RiptideColors.Surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Text(
                text = "Phone fingerprint",
                color = RiptideColors.OnSurface,
                fontSize = 24.sp,
                lineHeight = 32.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "$deviceName · My code $code",
                color = RiptideColors.OnSurfaceVariant,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Compare all groups for sensitive transfers.",
                color = RiptideColors.OnSurfaceVariant,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
            Spacer(Modifier.height(16.dp))
            SelectionContainer {
                Text(
                    text = groupedFingerprint(fingerprint).ifBlank { "Fingerprint unavailable" },
                    color = RiptideColors.OnSurface,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 16.sp,
                    lineHeight = 28.sp
                )
            }
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onCopyFingerprint,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RiptideColors.Primary, contentColor = RiptideColors.OnPrimary)
            ) {
                Text("Copy fingerprint", fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RiptideColors.SurfaceRaised, contentColor = RiptideColors.OnSurface)
            ) {
                Text("Done", fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** Small helper so callers don't import the whole selectable API. */
@Composable
fun FingerprintText(fingerprint: String, modifier: Modifier = Modifier) {
    SelectionContainer(modifier = modifier) {
        Text(
            text = groupedFingerprint(fingerprint),
            color = RiptideColors.OnSurface,
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp,
            lineHeight = 22.sp
        )
    }
}
