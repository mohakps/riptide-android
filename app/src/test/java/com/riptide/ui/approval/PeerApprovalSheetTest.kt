package com.riptide.ui.approval

import com.riptide.ui.model.ApprovalRequest
import com.riptide.ui.model.TrustStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class PeerApprovalSheetTest {
    @Test
    fun testButtonLabelForNewHost() {
        val request = ApprovalRequest(host = "metalvoid.ddns.net", code = "193-046", fullFingerprint = "1234567890abcdef", trustStatus = TrustStatus.NEW)
        assertEquals("Allow Connection", getApprovalButtonLabel(request.trustStatus))
    }

    @Test
    fun testButtonLabelForChangedHost() {
        val request = ApprovalRequest(host = "metalvoid.ddns.net", code = "193-046", fullFingerprint = "1234567890abcdef", trustStatus = TrustStatus.CHANGED, oldCode = "000-001")
        assertEquals("Replace & Accept", getApprovalButtonLabel(request.trustStatus))
    }
}
