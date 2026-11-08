package com.riptide.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.riptide.ui.theme.RiptideColors

/**
 * Shared slide-up confirmation sheet, used by Session Disconnect and
 * Settings Reset identity. Same component reference as the Show
 * fingerprint sheet: charcoal #1C1C1C surface, 28 dp top corners,
 * centered drag handle and backdrop scrim from ModalBottomSheet, and
 * it opens fully on the first render (skipPartiallyExpanded).
 *
 * Layout: a 24 dp coral outline icon, a 26/32 sp left-aligned title
 * (a heading for screen readers) and muted 15/22 sp body copy share
 * one start edge inside 24 dp horizontal padding, then two full-width
 * pill buttons in a vertical stack: the coral confirm first, the
 * neutral tonal Cancel below it. Buttons are a minimum 48 dp touch
 * target with 8 dp between them and 12 dp after the last button; the
 * sheet already applies the navigation inset once, so that 12 dp
 * lands inside the safe area, above gesture or three-button
 * navigation.
 *
 * On a short screen or at large text sizes the icon and text area
 * scroll independently (weight 1f, fill false) while the actions stay
 * visible above system navigation. Cancel, system Back, an outside
 * tap and a swipe all route through [onDismiss] without confirming;
 * the explicit confirmation button routes to [onConfirm] once. While
 * [confirmEnabled] is false the confirm button is disabled, and while
 * [dismissEnabled] is false the sheet cannot be dismissed (used while
 * a reset is running).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmationSheet(
    iconRes: Int,
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    confirmEnabled: Boolean = true,
    dismissEnabled: Boolean = true
) {
    ModalBottomSheet(
        onDismissRequest = { if (dismissEnabled) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = RiptideColors.Surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            // Scrollable icon + title + body. Weight 1f, fill false:
            // the area only grows as far as the content needs, and
            // scrolls when the sheet is short or the text is large.
            Column(modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = RiptideColors.Primary
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = title,
                    color = RiptideColors.OnSurface,
                    fontSize = 26.sp,
                    lineHeight = 32.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.semantics { heading() }
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = body,
                    color = RiptideColors.OnSurfaceVariant,
                    fontSize = 15.sp,
                    lineHeight = 22.sp
                )
            }
            // Fixed action area, outside the body's scroll.
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onConfirm,
                enabled = confirmEnabled,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RiptideColors.Primary, contentColor = RiptideColors.OnPrimary)
            ) { Text(confirmLabel, fontSize = 15.sp, fontWeight = FontWeight.Medium) }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onDismiss,
                enabled = dismissEnabled,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RiptideColors.SurfaceRaised, contentColor = RiptideColors.OnSurface)
            ) { Text("Cancel", fontSize = 15.sp, fontWeight = FontWeight.Medium) }
            Spacer(Modifier.height(12.dp))
        }
    }
}
