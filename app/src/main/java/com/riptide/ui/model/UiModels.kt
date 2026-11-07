package com.riptide.ui.model

enum class TrustStatus { KNOWN, NEW, CHANGED }
enum class TransferDirection { SEND, RECEIVE }
enum class TransferStatus { SUCCESS, FAILED, CANCELLED }

/** Phase of the trust sheet. LOCAL_TRUST shows the HOST code; WAITING_HOST
 * shows the PHONE's code after local approval, until the host acknowledges. */
enum class ApprovalPhase { NONE, LOCAL_TRUST, WAITING_HOST }

/** Busy phases that block starting a second transfer. */
enum class BusyPhase { PREPARING, TRANSFERRING, VERIFYING, WAITING_ACK }

/** State of a phone-initiated download request. */
sealed class DownloadState {
    object Idle : DownloadState()
    data class Waiting(val filename: String) : DownloadState()
    data class Denied(val filename: String) : DownloadState()
}

data class SavedPeer(
    val name: String,
    val host: String,
    val port: Int = 50000,
    val trustStatus: TrustStatus = TrustStatus.KNOWN,
    /** Epoch ms of the last successful connection; 0 = never connected. Recent
     * lists sort by this descending; failed attempts never change it. */
    val lastSuccessfulConnection: Long = 0L
)

data class ApprovalRequest(val host: String, val code: String, val fullFingerprint: String, val trustStatus: TrustStatus, val oldCode: String? = null, val oldFingerprint: String? = null)

data class HomeUiState(
    val myDeviceName: String = "",
    val myCode: String = "------",
    val myFullFingerprint: String = "",
    val hostInput: String = "",
    val isConnecting: Boolean = false,
    val connectionError: String? = null,
    val savedPeers: List<SavedPeer> = emptyList(),
    val approvalRequest: ApprovalRequest? = null
)

data class ActiveTransfer(val filename: String, val direction: TransferDirection, val totalBytes: Long, val transferredBytes: Long, val speedFormatted: String, val progress: Float, val etaFormatted: String?)

/** One transfer in the current session's feed. Plain text only; peer-controlled
 * strings are never rendered as markup. */
data class TransferRecord(val id: String, val filename: String, val savedFilename: String, val wasRenamed: Boolean, val sizeBytes: Long, val direction: TransferDirection, val status: TransferStatus, val sha256Verified: Boolean, val timestamp: Long = System.currentTimeMillis())

/** A successfully received file, persisted across restarts. Only added after
 * SHA-256 verification AND saving succeed. Sends, failures and cancellations
 * are never recorded here. */
data class ReceivedFileRecord(
    val id: String,
    /** Final MediaStore content URI of the saved file. */
    val uri: String,
    val originalName: String,
    val savedName: String,
    val sourcePeer: String,
    val sizeBytes: Long,
    val verified: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

data class SessionUiState(
    val peerName: String = "",
    val peerHost: String = "",
    val pingMs: Long = 0L,
    val isConnected: Boolean = false,
    val activeTransfer: ActiveTransfer? = null,
    val transferHistory: List<TransferRecord> = emptyList(),
    val canStartTransfer: Boolean = false,
    val busyPhase: BusyPhase? = null,
    val downloadState: DownloadState = DownloadState.Idle,
    /** True when a live session was lost with a transfer unfinished. */
    val showInterrupted: Boolean = false,
    val sessionError: String? = null
)
