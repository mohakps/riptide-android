package com.riptide.ui.model

enum class TrustStatus { KNOWN, NEW, CHANGED }
enum class TransferDirection { SEND, RECEIVE }
enum class TransferStatus { SUCCESS, FAILED, CANCELLED }

data class SavedPeer(val name: String, val host: String, val port: Int = 50000, val trustStatus: TrustStatus = TrustStatus.KNOWN, val lastSeenTimestamp: Long = System.currentTimeMillis())

data class ApprovalRequest(val host: String, val code: String, val fullFingerprint: String, val trustStatus: TrustStatus, val oldCode: String? = null, val oldFingerprint: String? = null)

data class HomeUiState(val myDeviceName: String = "", val myCode: String = "------", val myFullFingerprint: String = "", val hostInput: String = "", val isConnecting: Boolean = false, val connectionError: String? = null, val savedPeers: List<SavedPeer> = emptyList(), val approvalRequest: ApprovalRequest? = null)

data class ActiveTransfer(val filename: String, val direction: TransferDirection, val totalBytes: Long, val transferredBytes: Long, val speedFormatted: String, val progress: Float, val etaFormatted: String?)

data class TransferRecord(val id: String, val filename: String, val savedFilename: String, val wasRenamed: Boolean, val sizeBytes: Long, val direction: TransferDirection, val status: TransferStatus, val sha256Verified: Boolean, val timestamp: Long = System.currentTimeMillis())

data class SessionUiState(val peerName: String = "", val peerHost: String = "", val pingMs: Long = 0L, val isConnected: Boolean = true, val activeTransfer: ActiveTransfer? = null, val transferHistory: List<TransferRecord> = emptyList(), val canStartTransfer: Boolean = true)
