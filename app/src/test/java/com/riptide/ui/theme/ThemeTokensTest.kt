package com.riptide.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeTokensTest {
    @Test
    fun testColorHexValuesMatchSpec() {
        // Final approved tokens (tokens.json v6).
        assertEquals(Color(0xFF101010), RiptideColors.Background)
        assertEquals(Color(0xFF1C1C1C), RiptideColors.Surface)
        assertEquals(Color(0xFF282828), RiptideColors.SurfaceRaised)
        assertEquals(Color(0xFF555555), RiptideColors.BorderOutline)
        assertEquals(Color(0xFFFF6B6B), RiptideColors.PrimaryCoral)
        assertEquals(Color(0xFF101010), RiptideColors.OnPrimary)
        assertEquals(Color(0xFF78DC8C), RiptideColors.SuccessMint)
        assertEquals(Color(0xFFFFD166), RiptideColors.WarningAmber)
        assertEquals(Color(0xFFF2F0EB), RiptideColors.TextPrimary)
        assertEquals(Color(0xFFB8B6B1), RiptideColors.TextMuted)
        assertEquals(Color(0xFFF2F0EB), RiptideColors.CodeContainer)
        assertEquals(Color(0xFF101010), RiptideColors.OnCodeContainer)
    }

    @Test
    fun testReferenceTokensMatchTheme() {
        assertEquals(RiptideColors.Background, ApprovedRiptideTokens.Background)
        assertEquals(RiptideColors.Surface, ApprovedRiptideTokens.Surface)
        assertEquals(RiptideColors.SurfaceRaised, ApprovedRiptideTokens.SurfaceRaised)
        assertEquals(RiptideColors.TextPrimary, ApprovedRiptideTokens.OnSurface)
        assertEquals(RiptideColors.TextMuted, ApprovedRiptideTokens.OnSurfaceVariant)
        assertEquals(RiptideColors.PrimaryCoral, ApprovedRiptideTokens.Primary)
        assertEquals(RiptideColors.CodeContainer, ApprovedRiptideTokens.CodeContainer)
        // The final gap is 32 dp; the 16 dp refinement is already inside it.
        assertEquals(32, ApprovedRiptideTokens.IdentityToJoinGap.value.toInt())
        assertEquals(12, ApprovedRiptideTokens.IdentityGap.value.toInt())
    }
}
