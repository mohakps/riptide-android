package com.riptide.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.riptide.ui.theme.RiptideColors

@Composable
fun RiptideCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier = modifier, shape = RoundedCornerShape(12.dp), color = RiptideColors.Surface, border = BorderStroke(1.dp, RiptideColors.BorderOutline)) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}
