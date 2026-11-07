package com.riptide.ui.approval

import com.riptide.ui.model.ApprovalRequest
import com.riptide.ui.model.TrustStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class PeerApprovalSheetTest {
    @Test
    fun testButtonLabelForNewHost() {
        val request = ApprovalRequest(host = "metalvoid.ddns.net", code = "193-046", fullFingerprint = "1234567890abcdef", trustStatus = TrustStatus.NEW)
        assertEquals("Allow connection", getApprovalButtonLabel(request.trustStatus))
    }

    @Test
    fun testButtonLabelForChangedHost() {
        val request = ApprovalRequest(host = "metalvoid.ddns.net", code = "193-046", fullFingerprint = "1234567890abcdef", trustStatus = TrustStatus.CHANGED, oldCode = "482-610")
        assertEquals("Replace and accept", getApprovalButtonLabel(request.trustStatus))
    }

    @Test
    fun testButtonLabelForKnownHost() {
        val request = ApprovalRequest(host = "metalvoid.ddns.net", code = "193-046", fullFingerprint = "1234567890abcdef", trustStatus = TrustStatus.KNOWN)
        assertEquals("Continue", getApprovalButtonLabel(request.trustStatus))
    }
}
