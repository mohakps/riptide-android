package com.riptide.ui.model

import com.riptide.core.HostPhase
import com.riptide.core.SessionManager
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
    val approvalRequest: ApprovalRequest? = null,
    /** True after Join PC is tapped with a blank address: the helper
     * slot shows "Enter a PC address." until a nonblank address is typed. */
    val showBlankAddressError: Boolean = false,
    /** Live capability line (J1): "This device: IPv4 + IPv6" and
     * friends, or "No network. Connect and retry." offline. */
    val capabilityText: String = "",
    /** Live validation message for the current input (J2/J3/J4), or
     * null when the input is blank or usable. */
    val validationError: String? = null,
    /** False while the current input is a validation failure: Join
     * stays disabled for that address. Blank input keeps it enabled
     * (tapping shows the blank-field warning). */
    val joinEnabled: Boolean = true,
    /** Current dial attempt (J7): "Trying IPv6…" and friends, or null
     * when not dialling. */
    val attemptLabel: String? = null
)

/** Create Server screen state (C1-C8). The listener only runs while
 * phase is WAITING; the empty states (mobile IPv4-only, offline) show
 * no waiting status, code tile, or Stop. */
data class HostUiState(
    val phase: HostPhase = HostPhase.OFFLINE,
    val globalIPv6: String? = null,
    val lanIPv4: String? = null,
    val publicIPv4: String? = null,
    val publicIPv4Loading: Boolean = false,
    val advancedOpen: Boolean = false,
    val showNetworkChanged: Boolean = false,
    val myCode: String = "------",
    val isListening: Boolean = false
)

data class ActiveTransfer(val filename: String, val direction: TransferDirection, val totalBytes: Long, val transferredBytes: Long, val speedFormatted: String, val progress: Float, val etaFormatted: String?)

/** Phase of one file inside an outgoing batch. */
enum class BatchItemPhase { QUEUED, PREPARING, SENDING, VERIFYING, SENT, FAILED }

/** One file in an outgoing batch. The ID is a stable UUID, so two
 * files with the same name remain distinct. The URI is stored as a
 * string (the model must stay unit-testable without Android); the
 * manager parses it back when it opens the file. The provider size
 * is what the content provider reported (null when it reported
 * nothing usable); the spooled size is the exact size of the bytes
 * that will be sent, known once the file has been prepared. */
data class BatchItem(
    val id: String,
    val uri: String,
    val name: String,
    val providerSize: Long? = null,
    val spooledSize: Long? = null,
    val transferredBytes: Long = 0,
    val phase: BatchItemPhase = BatchItemPhase.QUEUED,
    val savedName: String? = null
) {
    /** Best-known size for this file: the spooled size when available,
     * else the provider size. Null when neither is known. */
    val knownSize: Long? get() = spooledSize ?: providerSize
    /** Progress of this file's own bytes, or null when its size is unknown. */
    val progress: Float? get() = knownSize?.let { if (it > 0) transferredBytes.toFloat() / it else null }
}

/** Outcome of a finished batch. */
enum class BatchOutcome { SENT, FAILED, CANCELLED }

/** One session-scoped outgoing batch: the ordered list, the current
 * position, and per-file phase and progress. The list is snapshotted
 * at the start; files are sent in picker result order, one at a time,
 * and the next starts only after the current file is confirmed. */
data class BatchState(
    val items: List<BatchItem>,
    val activeIndex: Int = 0,
    val sentCount: Int = 0,
    val startedAtMs: Long = System.currentTimeMillis(),
    /** Non-null once the batch has finished (success, failure, or cancel). */
    val outcome: BatchOutcome? = null,
    /** Overall speed, formatted, for the expanded details. Blank while unknown. */
    val overallSpeedFormatted: String = "",
    /** Batch ETA text for the subtitle: "2m 13s remaining",
     * "Estimating time…", or "Verifying on PC". */
    val batchEtaText: String = "Estimating time…"
) {
    val activeItem: BatchItem? get() = items.getOrNull(activeIndex)
    /** Total selected bytes, when every file's size is known; null
     * otherwise, in which case the overall bar is indeterminate. */
    val totalBytes: Long? get() = if (items.all { it.knownSize != null }) items.sumOf { it.knownSize!! } else null
    /** Bytes of confirmed files (exact, from their spools). */
    val confirmedBytes: Long get() = items.filter { it.phase == BatchItemPhase.SENT }.sumOf { it.spooledSize ?: 0L }
    /** Confirmed bytes plus bytes written for the active file. */
    val overallTransferred: Long get() = confirmedBytes + (activeItem?.transferredBytes ?: 0L)
    /** Overall progress by bytes, or null when the total is unknown
     * (indeterminate bar). A zero-byte total is indeterminate until
     * confirmed, never a fake 100%. */
    val overallProgress: Float? get() = totalBytes?.let { if (it > 0) overallTransferred.toFloat() / it else null }
    /** True when every byte is written and the last acknowledgement
     * is pending: the panel shows "Verifying on PC", and 100% is
     * reserved for confirmed completion. */
    val isVerifying: Boolean get() = outcome == null && totalBytes != null && totalBytes!! > 0 &&
        overallTransferred >= totalBytes!! && activeItem?.phase == BatchItemPhase.VERIFYING
    /** Whole percent for the summary, or null when indeterminate. */
    val overallPercent: Int? get() = overallProgress?.let { (it * 100).toInt().coerceIn(0, 99) }
}

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
    /** True when the connection was lost while a transfer was actually
     * in progress. False for an idle disconnect. */
    val interruptedTransfer: Boolean = false,
    /** Display name of the current receive folder, for the ready panel. */
    val receiveFolderName: String = "Downloads/Riptide",
    /** A verified file waiting for the folder-recovery choice. */
    val pendingReceive: SessionManager.PendingReceive? = null,
    val sessionError: String? = null,
    /** The running outgoing batch, or null when no batch is active. */
    val batchState: BatchState? = null,
    /** Outcome of the last finished batch ("Batch sent · 5 files",
     * "2 of 5 files sent"), cleared when a new transfer starts. */
    val batchMessage: String? = null
)
