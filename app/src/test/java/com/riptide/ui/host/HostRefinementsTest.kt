package com.riptide.ui.host

import androidx.compose.ui.unit.dp
import com.riptide.util.derive6DigitCode
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * v0.9.3 Create Server refinements: the code tile copies the
 * displayed code verbatim (hyphen included), the Advanced footer
 * buttons share one height, and the requirement bullets align with
 * the first line of text.
 */
class HostRefinementsTest {

    @Test
    fun testCodeTileCopyKeepsHyphen() {
        assertEquals("244-910", copyTextForCodeValue("244-910"))
        assertEquals("236-831", copyTextForCodeValue("236-831"))
        assertEquals("------", copyTextForCodeValue("------"))
    }

    @Test
    fun testCodeTileCopyMatchesDisplayedCode() {
        // The displayed code comes from derive6DigitCode, which
        // always inserts the hyphen; the copy must not strip it.
        val displayed = derive6DigitCode("123456" + "00".repeat(29))
        assertEquals("193-046", displayed)
        assertEquals(displayed, copyTextForCodeValue(displayed))
    }

    @Test
    fun testMatchedFooterHeightNeverBelow56() {
        assertEquals(56.dp, matchedFooterHeight(0.dp, 0.dp))
        assertEquals(56.dp, matchedFooterHeight(48.dp, 48.dp))
        assertEquals(56.dp, matchedFooterHeight(56.dp, 56.dp))
    }

    @Test
    fun testMatchedFooterHeightTakesTaller() {
        assertEquals(72.dp, matchedFooterHeight(48.dp, 72.dp))
        assertEquals(72.dp, matchedFooterHeight(72.dp, 48.dp))
        assertEquals(80.dp, matchedFooterHeight(80.dp, 64.dp))
    }

    @Test
    fun testBulletTopOffsetCentersOnFirstLine() {
        // 20 sp line at scale 1: half the line (10) minus half
        // the 6 dp bullet (3) = 7.
        assertEquals(7f, requirementBulletTopOffsetPx(20f, 6f), 0.001f)
        // Enlarged text (26 sp line): 13 - 3 = 10.
        assertEquals(10f, requirementBulletTopOffsetPx(26f, 6f), 0.001f)
    }

    @Test
    fun testBulletTopOffsetNeverNegative() {
        assertEquals(0f, requirementBulletTopOffsetPx(6f, 6f), 0.001f)
        assertEquals(0f, requirementBulletTopOffsetPx(4f, 6f), 0.001f)
    }
}
