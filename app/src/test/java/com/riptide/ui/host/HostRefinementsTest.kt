package com.riptide.ui.host

import androidx.compose.ui.unit.dp
import com.riptide.core.AddressFamily
import com.riptide.core.buildJoinLink
import com.riptide.core.copyTextFor
import com.riptide.util.derive6DigitCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
 *
 * v0.9.5 ("Create Server alignment and screen fit"): the waiting
 * status is two lines with an explicit break and one TalkBack
 * phrase; the header row centers the waiting group beside the
 * 148x76 dp code tile; address rows start at 108 dp with a 72 dp
 * action column and the Share cell in signature coral; the footer
 * (notice, then Back to addresses above Stop) is measured before
 * the body and anchored above the bottom navigation; the gap set
 * compacts first when the content exceeds the available height,
 * before any body-only scrolling.
 *
 * v0.9.6 ("Fix Create Server screen fit"): the measurement fault
 * is fixed — v0.9.5 had TWO weight(1f) siblings in one Column
 * (the body with fill = false, and a flexible Spacer outside
 * it), so the remaining space after the footer was split 50/50
 * and the body was capped at half the available height. The body
 * is now the single scrollable column filling the Scaffold's
 * content area between the app bar (top slot) and the footer
 * (bottom slot); the spacer is gone. The fit budget is body-only
 * (the footer is measured by the Scaffold outside it), the
 * role-swap hint is 12 dp below the list, and the scroll range
 * is zero whenever the body content fits.
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
    fun testMatchedRowHeightNeverBelow108() {
        assertEquals(108.dp, matchedRowHeight(0.dp, 0.dp))
        assertEquals(108.dp, matchedRowHeight(90.dp, 108.dp))
        assertEquals(108.dp, matchedRowHeight(108.dp, 108.dp))
    }

    @Test
    fun testMatchedRowHeightTakesTaller() {
        assertEquals(140.dp, matchedRowHeight(140.dp, 108.dp))
        assertEquals(140.dp, matchedRowHeight(108.dp, 140.dp))
        assertEquals(160.dp, matchedRowHeight(160.dp, 130.dp))
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

    @Test
    fun testWaitingStatusHasExplicitBreak() {
        assertEquals("Waiting\nfor client", WAITING_STATUS_TEXT)
        // Exactly one break, after "Waiting".
        assertEquals(1, WAITING_STATUS_TEXT.count { it == '\n' })
        assertTrue(WAITING_STATUS_TEXT.startsWith("Waiting\n"))
    }

    @Test
    fun testWaitingStatusTalkBackIsOnePhrase() {
        assertEquals("Waiting for client", WAITING_STATUS_TALKBACK)
        // No line break in the spoken phrase.
        assertTrue(!WAITING_STATUS_TALKBACK.contains('\n'))
    }

    @Test
    fun testHeaderAndRowSizes() {
        assertEquals(148.dp, CODE_TILE_WIDTH)
        assertEquals(76.dp, CODE_TILE_MIN_HEIGHT)
        assertEquals(72.dp, ACTION_COLUMN_WIDTH)
        assertEquals(108.dp, ROW_MIN_HEIGHT)
    }

    @Test
    fun testNormalGapsAreSpecValues() {
        assertEquals(24.dp, NORMAL_GAPS.headerToRows)
        assertEquals(14.dp, NORMAL_GAPS.betweenRows)
        assertEquals(8.dp, NORMAL_GAPS.advancedGap)
        assertEquals(16.dp, NORMAL_GAPS.requirementsGap)
        assertEquals(12.dp, NORMAL_GAPS.listToHint)
        assertEquals(8.dp, NORMAL_GAPS.footerButtonGap)
        assertEquals(12.dp, NORMAL_GAPS.footerBottom)
    }

    @Test
    fun testEstimatedBodyHeightTwoAddressStandard() {
        // Body only (the footer is measured by the Scaffold
        // outside it): 76 header + 24 gap + 2x108 rows + 14
        // between + 8 Advanced gap + 48 Advanced target = 386.
        assertEquals(386.dp, estimatedBodyHeightDp(2, false, NORMAL_GAPS))
    }

    @Test
    fun testEstimatedBodyHeightOneAddressStandard() {
        // 76 + 24 + 108 + 8 + 48 = 264.
        assertEquals(264.dp, estimatedBodyHeightDp(1, false, NORMAL_GAPS))
    }

    @Test
    fun testEstimatedBodyHeightAdvanced() {
        // 76 + 24 + 108 + 16 + 20 heading + 8 + 48 list + 12
        // hint gap + 48 role-swap hint = 360.
        assertEquals(360.dp, estimatedBodyHeightDp(1, true, NORMAL_GAPS))
    }

    @Test
    fun testEstimatedFooterHeight() {
        // Standard, no notice: 56 Stop + 12 below = 68.
        assertEquals(68.dp, estimatedFooterHeightDp(false, false))
        // The notice is one 18 dp line plus an 8 dp gap: +26.
        assertEquals(94.dp, estimatedFooterHeightDp(false, true))
        // Advanced adds Back to addresses (56) + 8 gap: 132.
        assertEquals(132.dp, estimatedFooterHeightDp(true, false))
        assertEquals(158.dp, estimatedFooterHeightDp(true, true))
    }

    @Test
    fun testBodyScrollRangeIsZeroWhenContentFits() {
        assertEquals(0.dp, bodyScrollRangeDp(386.dp, 476.dp))
        assertEquals(0.dp, bodyScrollRangeDp(360.dp, 412.dp))
        assertEquals(0.dp, bodyScrollRangeDp(264.dp, 264.dp))
    }

    @Test
    fun testBodyScrollRangeIsPositiveWhenContentOverflows() {
        assertEquals(90.dp, bodyScrollRangeDp(476.dp, 386.dp))
        assertEquals(1.dp, bodyScrollRangeDp(361.dp, 360.dp))
    }

    @Test
    fun testChooseGapsKeepsNormalWhenBodyFits() {
        assertEquals(NORMAL_GAPS, chooseGaps(476.dp, 2, false))
        assertEquals(NORMAL_GAPS, chooseGaps(412.dp, 1, true))
        assertEquals(NORMAL_GAPS, chooseGaps(386.dp, 2, false))
    }

    @Test
    fun testChooseGapsCompactsWhenBodyExceeds() {
        assertEquals(COMPACT_GAPS, chooseGaps(300.dp, 2, false))
        assertEquals(COMPACT_GAPS, chooseGaps(350.dp, 1, true))
        // Compact body heights: standard two-address 372,
        // Advanced 342 — both still overflow a 300 dp body and
        // then scroll on their own.
        assertEquals(372.dp, estimatedBodyHeightDp(2, false, COMPACT_GAPS))
        assertEquals(342.dp, estimatedBodyHeightDp(1, true, COMPACT_GAPS))
        assertEquals(72.dp, bodyScrollRangeDp(372.dp, 300.dp))
        assertEquals(42.dp, bodyScrollRangeDp(342.dp, 300.dp))
    }

    @Test
    fun testChooseGapsFits360x720() {
        // 360x720 dp portrait: the Scaffold's content area is
        // roughly 720 - 24 status bar - 72 app bar - 80 bottom
        // navigation = 544 dp for body + footer. The body's share
        // is that minus the footer's measured height: standard
        // 544 - 68 = 476, Advanced 544 - 132 = 412, and with the
        // notice present 450 and 386. Every view fits with the
        // normal gaps and zero scroll range — this is the
        // v0.9.5 fault's failing case: the old two-weight layout
        // capped the body at half of 476 (238 dp) and clipped
        // the second row, while the other half sat empty.
        assertEquals(NORMAL_GAPS, chooseGaps(476.dp, 2, false))
        assertEquals(NORMAL_GAPS, chooseGaps(476.dp, 1, false))
        assertEquals(NORMAL_GAPS, chooseGaps(412.dp, 1, true))
        assertEquals(NORMAL_GAPS, chooseGaps(450.dp, 2, false))
        assertEquals(NORMAL_GAPS, chooseGaps(386.dp, 1, true))
        assertEquals(0.dp, bodyScrollRangeDp(386.dp, 476.dp))
        assertEquals(0.dp, bodyScrollRangeDp(360.dp, 412.dp))
        assertEquals(0.dp, bodyScrollRangeDp(360.dp, 386.dp))
        assertEquals(0.dp, bodyScrollRangeDp(264.dp, 450.dp))
    }

    @Test
    fun testChooseGapsFitsScreenshotDeviceSize() {
        // The screenshot's device (Nothing Phone (2), about
        // 411x919 dp): content area roughly 919 - 24 - 72 - 80
        // = 743 dp. Body shares: standard 743 - 68 = 675,
        // Advanced 743 - 132 = 611, and with the notice 649 and
        // 585. All fit with normal gaps and zero scroll range.
        assertEquals(NORMAL_GAPS, chooseGaps(675.dp, 2, false))
        assertEquals(NORMAL_GAPS, chooseGaps(675.dp, 1, false))
        assertEquals(NORMAL_GAPS, chooseGaps(611.dp, 1, true))
        assertEquals(NORMAL_GAPS, chooseGaps(649.dp, 2, false))
        assertEquals(NORMAL_GAPS, chooseGaps(585.dp, 1, true))
        assertEquals(0.dp, bodyScrollRangeDp(386.dp, 675.dp))
        assertEquals(0.dp, bodyScrollRangeDp(360.dp, 611.dp))
    }

    @Test
    fun testShareLinksCarryEachRowsOwnAddress() {
        val v6 = "2001:db8:7f47:e96:e47e:1cff:fe96:ab5f"
        val lan = "192.168.1.20"
        val pub = "203.0.113.10"
        val v6Link = buildJoinLink(v6, AddressFamily.IPV6)
        val lanLink = buildJoinLink(lan, AddressFamily.IPV4)
        val pubLink = buildJoinLink(pub, AddressFamily.IPV4)
        assertEquals("riptide://join?host=[$v6]&port=50000", v6Link)
        assertEquals("riptide://join?host=$lan&port=50000", lanLink)
        assertEquals("riptide://join?host=$pub&port=50000", pubLink)
        // No row's link carries another row's address.
        assertTrue(!v6Link.contains(lan) && !v6Link.contains(pub))
        assertTrue(!lanLink.contains(v6) && !lanLink.contains(pub))
        assertTrue(!pubLink.contains(v6) && !pubLink.contains(lan))
    }

    @Test
    fun testCopyUsesBareAddressPerRow() {
        assertEquals("2001:db8:7f47:e96:e47e:1cff:fe96:ab5f", copyTextFor("[2001:db8:7f47:e96:e47e:1cff:fe96:ab5f]"))
        assertEquals("192.168.1.20", copyTextFor("192.168.1.20"))
        assertEquals("203.0.113.10", copyTextFor("203.0.113.10"))
    }
}
