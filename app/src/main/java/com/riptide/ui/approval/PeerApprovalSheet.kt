package com.riptide.ui.approval

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.riptide.ui.components.CodeBadge
import com.riptide.ui.components.RiptideBadge
import com.riptide.ui.model.ApprovalRequest
import com.riptide.ui.model.TrustStatus
import com.riptide.ui.theme.MonospaceFontFamily
import com.riptide.ui.theme.RiptideColors

fun getApprovalButtonLabel(status: TrustStatus): String = when (status) {
    TrustStatus.NEW -> "Allow Connection"
    TrustStatus.CHANGED -> "Replace & Accept"
    TrustStatus.KNOWN -> "Continue"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeerApprovalSheet(request: ApprovalRequest, onAllow: () -> Unit, onDeny: () -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDeny, containerColor = RiptideColors.Surface, shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp), modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(text = request.host, fontFamily = MonospaceFontFamily, style = MaterialTheme.typography.titleMedium, color = RiptideColors.TextPrimary)
                if (request.trustStatus == TrustStatus.CHANGED) {
                    RiptideBadge(text = "FINGERPRINT CHANGED", containerColor = RiptideColors.ErrorRed, contentColor = RiptideColors.ErrorRed)
                } else {
                    RiptideBadge(text = "NEW HOST", containerColor = RiptideColors.WarningAmber, contentColor = RiptideColors.WarningAmber)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "HOST VERIFICATION CODE", style = MaterialTheme.typography.bodySmall, color = RiptideColors.TextMuted)
                    Spacer(modifier = Modifier.height(6.dp))
                    CodeBadge(code = request.code)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Verify this 6-digit code matches the PC screen out-of-band.", style = MaterialTheme.typography.bodyMedium, color = RiptideColors.TextMuted)
                }
            }
            if (request.trustStatus == TrustStatus.CHANGED) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = "SECURITY WARNING: The saved fingerprint for this host does not match! The host was either re-installed or an attacker is intercepting the connection.", style = MaterialTheme.typography.bodySmall, color = RiptideColors.ErrorRed)
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(text = if (expanded) "Hide full fingerprint details" else "View full fingerprint details", style = MaterialTheme.typography.bodySmall, color = RiptideColors.PrimaryCoral, modifier = Modifier.clickable { expanded = !expanded }.padding(vertical = 4.dp))
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    Text(text = "Offered Fingerprint:", style = MaterialTheme.typography.bodySmall, color = RiptideColors.TextMuted)
                    Text(text = request.fullFingerprint.chunked(4).joinToString(" "), fontFamily = MonospaceFontFamily, fontSize = 11.sp, color = RiptideColors.TextPrimary)
                    if (request.oldFingerprint != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "Previous Fingerprint:", style = MaterialTheme.typography.bodySmall, color = RiptideColors.TextMuted)
                        Text(text = request.oldFingerprint.chunked(4).joinToString(" "), fontFamily = MonospaceFontFamily, fontSize = 11.sp, color = RiptideColors.ErrorRed)
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onDeny, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, RiptideColors.BorderOutline), modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp)) { Text("Deny", color = RiptideColors.TextPrimary) }
                Button(onClick = onAllow, shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = RiptideColors.PrimaryCoral, contentColor = RiptideColors.OnPrimary), modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp)) { Text(getApprovalButtonLabel(request.trustStatus)) }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
