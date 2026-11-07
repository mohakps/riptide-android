package com.riptide.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.riptide.R
import com.riptide.ui.theme.RiptideColors

/**
 * Quiet tonal info panel used at the bottom of Session and Devices.
 * Only an outlined info icon and the message, no heading or label.
 * The icon is decorative for TalkBack: the adjacent text carries the
 * message. Surface #1C1C1C, radius 20 dp, padding 16 dp, minimum
 * height 64 dp, icon/text #B8B6B1, 14/20 sp message, 12 dp icon gap.
 */
@Composable
fun BottomInfoPanel(message: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth().heightIn(min = 64.dp),
        shape = RoundedCornerShape(20.dp),
        color = RiptideColors.Surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.riptide_info),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = RiptideColors.OnSurfaceVariant
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = message,
                color = RiptideColors.OnSurfaceVariant,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
