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
 *
 * v0.9.4: the two regular address rows share one height (162 dp
 * minimum), and long IPv6 values break at colon boundaries in the
 * display string only — Copy and Share still use the full address.
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

    @Test
    fun testMatchedRowHeightNeverBelow162() {
        assertEquals(162.dp, matchedRowHeight(0.dp, 0.dp))
        assertEquals(162.dp, matchedRowHeight(120.dp, 162.dp))
        assertEquals(162.dp, matchedRowHeight(162.dp, 162.dp))
    }

    @Test
    fun testMatchedRowHeightTakesTaller() {
        assertEquals(200.dp, matchedRowHeight(200.dp, 162.dp))
        assertEquals(200.dp, matchedRowHeight(162.dp, 200.dp))
        assertEquals(220.dp, matchedRowHeight(220.dp, 180.dp))
    }

    @Test
    fun testDisplayAddressBreaksAtColons() {
        val display = displayAddressWithBreaks("2001:db8:7f47:e96:e47e:1cff:fe96:ab5f")
        // Removing the break opportunities recovers the full
        // underlying address: Copy and Share use that, never this.
        assertEquals("2001:db8:7f47:e96:e47e:1cff:fe96:ab5f", display.replace("\u200B", ""))
        // One break opportunity after every colon (7 colons).
        assertEquals(7, display.count { it == '\u200B' })
        // IPv4 has no colons and is unchanged.
        assertEquals("192.168.1.20", displayAddressWithBreaks("192.168.1.20"))
        assertEquals("203.0.113.10", displayAddressWithBreaks("203.0.113.10"))
    }
}
