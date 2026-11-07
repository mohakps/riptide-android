package com.riptide.ui.approval

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.riptide.ui.components.FingerprintText
import com.riptide.ui.components.RiptideBadge
import com.riptide.ui.model.ApprovalPhase
import com.riptide.ui.model.ApprovalRequest
import com.riptide.ui.model.TrustStatus
import com.riptide.ui.theme.MonospaceFontFamily
import com.riptide.ui.theme.RiptideColors

/** Design copy for the local-trust action. */
fun getApprovalButtonLabel(status: TrustStatus): String = when (status) {
    TrustStatus.NEW -> "Allow connection"
    TrustStatus.CHANGED -> "Replace and accept"
    TrustStatus.KNOWN -> "Continue"
}

private fun trustPill(status: TrustStatus): Pair<String, androidx.compose.ui.graphics.Color> = when (status) {
    TrustStatus.NEW -> "NEW PC" to RiptideColors.OnSurface
    TrustStatus.KNOWN -> "KNOWN PC" to RiptideColors.Success
    TrustStatus.CHANGED -> "IDENTITY CHANGED" to RiptideColors.Error
}

/**
 * States 05/06/07 (local trust, HOST code) and 15 (host waiting, PHONE code).
 * One sheet that changes in place after Allow: the two codes come from
 * different data sources and must never be confused. Dismiss/Back at local
 * trust means Deny; during host waiting it cancels the pending socket.
 * Only the host's hello_ok opens the Session; local Allow alone never does.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeerApprovalSheet(
    request: ApprovalRequest,
    phase: ApprovalPhase,
    myCode: String,
    myDeviceName: String,
    myFingerprint: String,
    onAllow: () -> Unit,
    onDeny: () -> Unit,
    onCancelWaiting: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showFingerprint by remember { mutableStateOf(false) }
    val context = LocalContext.current
    ModalBottomSheet(
        onDismissRequest = { if (phase == ApprovalPhase.WAITING_HOST) onCancelWaiting() else onDeny() },
        containerColor = RiptideColors.Surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            if (phase == ApprovalPhase.WAITING_HOST) {
                RiptideBadge(text = "HOST APPROVAL PENDING", containerColor = RiptideColors.SurfaceRaised, contentColor = RiptideColors.Warning)
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Waiting for approval from host",
                    color = RiptideColors.OnSurface,
                    fontSize = 28.sp,
                    lineHeight = 36.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = request.host,
                    color = RiptideColors.OnSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    text = "YOUR PHONE'S VERIFICATION CODE",
                    color = RiptideColors.OnSurfaceVariant,
                    fontFamily = MonospaceFontFamily,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    letterSpacing = 1.3.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = myCode,
                        color = RiptideColors.OnSurface,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 36.sp,
                        lineHeight = 44.sp,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Riptide Code", myCode.filter { it.isDigit() }))
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.riptide_copy),
                            contentDescription = "Copy verification code",
                            modifier = Modifier.size(24.dp),
                            tint = RiptideColors.OnSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = myDeviceName.ifBlank { "This Phone" },
                    color = RiptideColors.OnSurface,
                    fontSize = 16.sp,
                    lineHeight = 24.sp
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Ask the host to compare this phone code with the incoming device on their PC. The session starts after they approve.",
                    color = RiptideColors.OnSurfaceVariant,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
                Spacer(Modifier.height(16.dp))
                TextButton(
                    onClick = { showFingerprint = !showFingerprint },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(
                        text = if (showFingerprint) "Hide phone fingerprint" else "View phone fingerprint",
                        color = RiptideColors.Primary,
                        fontSize = 14.sp
                    )
                }
                if (showFingerprint) {
                    Spacer(Modifier.height(8.dp))
                    FingerprintText(fingerprint = myFingerprint)
                }
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = onCancelWaiting,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RiptideColors.SurfaceRaised, contentColor = RiptideColors.OnSurface)
                ) {
                    CircularProgressIndicator(strokeWidth = 2.dp, color = RiptideColors.OnSurfaceVariant, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Cancel connection", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }
            } else {
                val (pillText, pillColor) = trustPill(request.trustStatus)
                RiptideBadge(text = pillText, containerColor = RiptideColors.SurfaceRaised, contentColor = pillColor)
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Check before you join.",
                    color = RiptideColors.OnSurface,
                    fontSize = 28.sp,
                    lineHeight = 36.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = request.host,
                    color = RiptideColors.OnSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    text = "PC VERIFICATION CODE",
                    color = RiptideColors.OnSurfaceVariant,
                    fontFamily = MonospaceFontFamily,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    letterSpacing = 1.3.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = request.code,
                    color = RiptideColors.OnSurface,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 36.sp,
                    lineHeight = 44.sp
                )
                if (request.oldCode != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Previously ${request.oldCode}",
                        color = RiptideColors.Warning,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = if (request.trustStatus == TrustStatus.CHANGED)
                        "This PC's identity has changed. Stop unless you can verify the new identity with its owner."
                    else
                        "Compare this code with the PC by a call or message. For sensitive files, compare the full fingerprint too.",
                    color = RiptideColors.OnSurfaceVariant,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
                if (request.trustStatus == TrustStatus.CHANGED) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "NEW FINGERPRINT",
                        color = RiptideColors.OnSurfaceVariant,
                        fontFamily = MonospaceFontFamily,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        letterSpacing = 1.3.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(8.dp))
                    FingerprintText(fingerprint = request.fullFingerprint)
                    request.oldFingerprint?.let { old ->
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "PREVIOUS FINGERPRINT",
                            color = RiptideColors.OnSurfaceVariant,
                            fontFamily = MonospaceFontFamily,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            letterSpacing = 1.3.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(8.dp))
                        FingerprintText(fingerprint = old)
                    }
                } else {
                    Spacer(Modifier.height(16.dp))
                    TextButton(
                        onClick = { showFingerprint = !showFingerprint },
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text(
                            text = if (showFingerprint) "Hide full fingerprint" else "View full fingerprint",
                            color = RiptideColors.Primary,
                            fontSize = 14.sp
                        )
                    }
                    if (showFingerprint) {
                        Spacer(Modifier.height(8.dp))
                        FingerprintText(fingerprint = request.fullFingerprint)
                    }
                }
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = onAllow,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RiptideColors.Primary, contentColor = RiptideColors.OnPrimary)
                ) {
                    Text(getApprovalButtonLabel(request.trustStatus), fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onDeny,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RiptideColors.SurfaceRaised, contentColor = RiptideColors.OnSurface)
                ) {
                    Text("Deny", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
