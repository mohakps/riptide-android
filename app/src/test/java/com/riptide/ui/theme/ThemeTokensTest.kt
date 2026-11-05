package com.riptide.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeTokensTest {
    @Test
    fun testColorHexValuesMatchSpec() {
        assertEquals(Color(0xFF0D0E11), RiptideColors.Background)
        assertEquals(Color(0xFF16181D), RiptideColors.Surface)
        assertEquals(Color(0xFF2A2E38), RiptideColors.BorderOutline)
        assertEquals(Color(0xFFFF6B6B), RiptideColors.PrimaryCoral)
        assertEquals(Color(0xFF78DC8C), RiptideColors.SuccessMint)
        assertEquals(Color(0xFFFFD166), RiptideColors.WarningAmber)
        assertEquals(Color(0xFFE8E8E8), RiptideColors.TextPrimary)
        assertEquals(Color(0xFF71717A), RiptideColors.TextMuted)
    }
}
