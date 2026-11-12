package com.riptide.core

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import com.riptide.data.PeerRepository
import com.riptide.data.ReceiveFolder
import com.riptide.data.ReceiveFolderRepository
import com.riptide.data.ReceivedHistoryRepository
import com.riptide.data.SavedPeerRecord
import com.riptide.service.TransferService
import com.riptide.ui.model.ActiveTransfer
import com.riptide.ui.model.ApprovalPhase
import com.riptide.ui.model.ApprovalRequest
import com.riptide.ui.model.BatchItem
import com.riptide.ui.model.BatchItemPhase
import com.riptide.ui.model.BatchOutcome
import com.riptide.ui.model.BatchState
import com.riptide.ui.model.BusyPhase
import com.riptide.ui.model.DownloadState
import com.riptide.ui.model.ReceivedFileRecord
import com.riptide.ui.model.TransferDirection
import com.riptide.ui.model.TransferRecord
import com.riptide.ui.model.TransferStatus
import com.riptide.ui.model.TrustStatus
import com.riptide.util.basenameOnly
import com.riptide.util.certFingerprint
import com.riptide.util.derive6DigitCode
import com.riptide.util.formatRemainingTime
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.UnknownHostException
import java.security.MessageDigest
import java.security.cert.Certificate
import java.security.cert.X509Certificate
import java.util.Locale
import java.util.UUID
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import android.util.Base64 as AndroidBase64

enum class ConnectionState { IDLE, CONNECTING, CONNECTED, FAILED }

/** Total window for one Join attempt across all candidates (J7/J8). */
private const val JOIN_TOTAL_WINDOW_MS = 30_000L

/** Timeout failure shown on Home (J8): title line first, then the
 * host disclaimer and the role swap. Home splits on the newlines. */
const val JOIN_TIMEOUT_MESSAGE =
    "Couldn’t reach the server.\nCheck the disclaimer on the host phone: is port 50000 forwarded, and is the network behind CGNAT?\nOr ask the other phone to Create instead."

/** Bytes counted and SHA-256 of a stream, read in one pass. Pure and unit tested. */
internal fun hashAndCount(input: java.io.InputStream): Pair<Long, String> {
    val digest = MessageDigest.getInstance("SHA-256")
    val buf = ByteArray(256 * 1024)
    var counted = 0L
    while (true) {
        val r = input.read(buf)
        if (r < 0) break
        if (r > 0) {
            digest.update(buf, 0, r)
            counted += r
        }
    }
    return counted to digest.digest().joinToString("") { "%02x".format(it) }
}

class SessionManager(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val identity = IdentityManager(context)
    private val peers = PeerRepository(context)
    private val receiveFolderRepo = ReceiveFolderRepository(context)
    private val receivedHistoryRepo = ReceivedHistoryRepository(context.filesDir)

    /** Live network state: capability for Join validation (J1-J5),
     * transport and addresses for Create Server. One monitor for the
     * whole app, so Join and host never disagree about the network. */
    val networkMonitor = NetworkMonitor(context)

    val myCode: String get() = try { identity.code() } catch (_: Exception) { "------" }
    val myFingerprint: String get() = try { identity.fingerprint() } catch (_: Exception) { "" }
    val deviceName: String get() = peers.deviceName()

    /** Current dial attempt (J7), or null when not dialling. */
    private val _attemptLabel = MutableStateFlow<String?>(null)
    val attemptLabel: StateFlow<String?> = _attemptLabel.asStateFlow()

    private val _connection = MutableStateFlow(ConnectionState.IDLE)
    val connection: StateFlow<ConnectionState> = _connection.asStateFlow()
    private val _approval = MutableStateFlow<ApprovalRequest?>(null)
    val approval: StateFlow<ApprovalRequest?> = _approval.asStateFlow()
    /** LOCAL_TRUST shows the HOST code; WAITING_HOST shows the PHONE code in
     * the same sheet after local approval, until the host acknowledges. */
    private val _approvalPhase = MutableStateFlow(ApprovalPhase.NONE)
    val approvalPhase: StateFlow<ApprovalPhase> = _approvalPhase.asStateFlow()
    private val _peerName = MutableStateFlow("")
    val peerName: StateFlow<String> = _peerName.asStateFlow()
    private val _peerHost = MutableStateFlow("")
    val peerHost: StateFlow<String> = _peerHost.asStateFlow()
    private val _pingMs = MutableStateFlow(0L)
    val pingMs: StateFlow<Long> = _pingMs.asStateFlow()
    /** Wall-clock time of the last pong received. verifyAlive watches this
     * move: a ping write that succeeds into a dead socket proves nothing,
     * the pong coming back does. */
    private val _lastPongAt = MutableStateFlow(0L)
    private val _active = MutableStateFlow<ActiveTransfer?>(null)
    val active: StateFlow<ActiveTransfer?> = _active.asStateFlow()
    /** Non-null while preparing, verifying or waiting for a send ack. Blocks a second transfer. */
    private val _busyPhase = MutableStateFlow<BusyPhase?>(null)
    val busyPhase: StateFlow<BusyPhase?> = _busyPhase.asStateFlow()
    /** The running outgoing batch, or null when no batch is active. A
     * single send leaves this null and uses [_active]/[_busyPhase]
     * only; a batch holds the slot through every file. */
    private val _batch = MutableStateFlow<BatchState?>(null)
    val batch: StateFlow<BatchState?> = _batch.asStateFlow()
    /** Outcome of the last finished batch ("Batch sent · 5 files"),
     * cleared when a new transfer starts. */
    private val _batchMessage = MutableStateFlow<String?>(null)
    val batchMessage: StateFlow<String?> = _batchMessage.asStateFlow()
    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()
    private val _history = MutableStateFlow<List<TransferRecord>>(emptyList())
    val history: StateFlow<List<TransferRecord>> = _history.asStateFlow()
    private val _receivedHistory = MutableStateFlow<List<ReceivedFileRecord>>(emptyList())
    val receivedHistory: StateFlow<List<ReceivedFileRecord>> = _receivedHistory.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    /** Transfer failures (send / receive / download). Kept separate from
     * [_error] on purpose: a failed transfer must never be shown on Home
     * as "Couldn't reach this PC". Home shows session errors only; the
     * Session screen shows transfer errors. */
    private val _transferError = MutableStateFlow<String?>(null)
    val transferError: StateFlow<String?> = _transferError.asStateFlow()
    private val _savedPeers = MutableStateFlow<List<SavedPeerRecord>>(emptyList())
    /** Sorted by last successful connection, most recent first. */
    val savedPeers: StateFlow<List<SavedPeerRecord>> = _savedPeers.asStateFlow()
    /** Where received files are saved. Null tree URI means the default,
     * Downloads/Riptide. Starts at the default and loads the saved
     * choice in the background, so construction never blocks on disk. */
    private val _receiveFolder = MutableStateFlow(
        ReceiveFolder(null, ReceiveFolderRepository.DEFAULT_DISPLAY_NAME)
    )
    val receiveFolder: StateFlow<ReceiveFolder> = _receiveFolder.asStateFlow()
    /** A verified file waiting for the user to pick a folder after the
     * saved destination lost access. The part file is kept until the
     * user chooses; the ack and history wait for that choice. */
    data class PendingReceive(val part: File, val name: String, val size: Long, val session: DialResult)
    private val _pendingReceive = MutableStateFlow<PendingReceive?>(null)
    val pendingReceive: StateFlow<PendingReceive?> = _pendingReceive.asStateFlow()

    private var socket: SSLSocket? = null
    private var readerJob: Job? = null
    private var heartbeatJob: Job? = null
    private var connectJob: Job? = null
    /** Incremented on every cancel; a dial that finishes after its generation
     * changed is stale and must not open a session. */
    @Volatile private var attemptGeneration = 0
    /** Incremented when a batch is cancelled: the runner checks it
     * before starting the next file, so a late acknowledgement can
     * never start a queued file after the user cancelled. */
    @Volatile private var batchGeneration = 0
    /** Monotonic generation for connection attempts. disconnect() captures
     * it so a late cleanup never clobbers a session that started after
     * the disconnect was tapped. */
    @Volatile private var connectGeneration = 0
    /** Serializes an identity reset with the hello exchange (the only
     * place the identity is read to sign). The trust-approval wait sits
     * before the exchange, so the lock is never held across it. */
    private val identityMutex = Mutex()
    private val sendMutex = Mutex()
    private var approvalDecision: ((Boolean) -> Unit)? = null
    private var currentHost: String = ""
    /** Host of the last session that reached CONNECTED. Set only on a
     * successful connect, cleared on a deliberate disconnect or cancel,
     * so an automatic rejoin never fires after the user chose to leave
     * or after a Join that never connected. */
    private var lastConnectedHost: String? = null
    /** Completed by the reader when the PC acks the file being sent. Only one
     * transfer runs at a time, so the next ack is always for this send. */
    private data class AckResult(val name: String, val ok: Boolean, val reason: String)
    @Volatile private var pendingAck: CompletableDeferred<AckResult?>? = null

    init {
        DiagLog.log("session", "SessionManager created")
        scope.launch {
            _savedPeers.value = peers.loadPeers().sortedByDescending { it.lastSuccessfulConnection }
            _receivedHistory.value = receivedHistoryRepo.load()
            _receiveFolder.value = receiveFolderRepo.load()
        }
    }

    fun refreshPeers() {
        scope.launch { _savedPeers.value = peers.loadPeers().sortedByDescending { it.lastSuccessfulConnection } }
    }

    fun removeReceivedRecord(id: String) {
        scope.launch {
            receivedHistoryRepo.remove(id)
            _receivedHistory.value = receivedHistoryRepo.load()
        }
    }

    fun setDeviceName(name: String) {
        peers.setDeviceName(name)
    }

    /** Save a folder the user picked with the system picker. Returns
     * true only when the access was retained and the folder is actually
     * writable, so a choice that cannot be used is never saved. */
    fun setReceiveFolder(treeUri: Uri): Boolean {
        val uriStr = treeUri.toString()
        if (!receiveFolderRepo.takePersistableAccess(treeUri)) return false
        if (!receiveFolderRepo.isWritable(uriStr)) return false
        val display = receiveFolderRepo.displayNameFor(treeUri)
        receiveFolderRepo.save(uriStr, display)
        _receiveFolder.value = ReceiveFolder(uriStr, display)
        DiagLog.log("session", "receive folder set: $display")
        return true
    }

    /** Back to the default, Downloads/Riptide. */
    fun clearReceiveFolder() {
        receiveFolderRepo.clear()
        _receiveFolder.value = ReceiveFolder(null, ReceiveFolderRepository.DEFAULT_DISPLAY_NAME)
        DiagLog.log("session", "receive folder cleared to default")
    }

    /** A verified file is waiting because the saved folder lost access.
     * Use the default folder and retry the save. */
    fun resolvePendingReceiveUseDefault() {
        val pending = _pendingReceive.value ?: return
        _pendingReceive.value = null
        scope.launch {
            clearReceiveFolder()
            val saved = saveReceived(pending.part, pending.name)
            completePendingReceive(pending, saved)
        }
    }

    /** A verified file is waiting because the saved folder lost access.
     * Save the newly picked folder and retry the save there. */
    fun resolvePendingReceiveChooseFolder(treeUri: Uri) {
        val pending = _pendingReceive.value ?: return
        _pendingReceive.value = null
        scope.launch {
            if (!setReceiveFolder(treeUri)) {
                // The new folder cannot be used either: keep the file
                // in the default folder rather than losing it.
                clearReceiveFolder()
            }
            val saved = saveReceived(pending.part, pending.name)
            completePendingReceive(pending, saved)
        }
    }

    /** Dismiss a pending receive without saving. The file is lost, but
     * the PC is told so it does not hang waiting for an ack. */
    fun dismissPendingReceive() {
        val pending = _pendingReceive.value ?: return
        _pendingReceive.value = null
        scope.launch {
            try { pending.part.delete() } catch (_: Exception) {}
            addHistory(pending.name, pending.name, false, pending.size, TransferDirection.RECEIVE, TransferStatus.FAILED, false)
            _transferError.value = "Could not save ${pending.name}: the receive folder is no longer accessible"
            sendAck(pending.session, pending.name, false, "save failed")
        }
    }

    /** True when an identity reset is allowed: no session, handshake or
     * transfer is active. The Settings row uses this to enable or
     * disable Reset identity. */
    fun canResetIdentity(): Boolean {
        return _connection.value == ConnectionState.IDLE &&
            _active.value == null &&
            _busyPhase.value == null &&
            _approval.value == null &&
            _approvalPhase.value == ApprovalPhase.NONE
    }

    /** Regenerate the identity. Serialized with the hello exchange by
     * [identityMutex], so a reset can never swap the identity while a
     * handshake is signing with it. Rechecks eligibility inside the
     * lock. Returns true on success; on failure the prior identity is
     * kept and false is returned. */
    suspend fun resetIdentity(): Boolean {
        return identityMutex.withLock {
            if (!canResetIdentity()) return@withLock false
            DiagLog.log("session", "resetIdentity")
            identity.reset()
        }
    }

    fun forgetPeer(host: String) {
        scope.launch {
            peers.forgetPeer(host)
            _savedPeers.value = peers.loadPeers().sortedByDescending { it.lastSuccessfulConnection }
        }
    }

    fun clearError() { _error.value = null; _transferError.value = null }

    fun clearBatchMessage() { _batchMessage.value = null }

    fun clearDownloadDenied() {
        if (_downloadState.value is DownloadState.Denied) _downloadState.value = DownloadState.Idle
    }

    fun approveConnection(allow: Boolean) {
        if (allow) {
            // The same sheet now becomes "Waiting for approval from host"
            // and shows the PHONE's code while the hello exchange runs.
            _approvalPhase.value = ApprovalPhase.WAITING_HOST
        }
        approvalDecision?.invoke(allow)
        approvalDecision = null
        if (!allow) {
            _approval.value = null
            _approvalPhase.value = ApprovalPhase.NONE
            _connection.value = ConnectionState.IDLE
        }
    }

    /** Abort a pending Join (connecting, local trust, or host waiting).
     * Closes the socket, cancels the attempt, and invalidates any late
     * success callback via the generation counter. */
    fun cancelConnect() {
        DiagLog.log("session", "cancelConnect (user)")
        attemptGeneration++
        connectJob?.cancel()
        connectJob = null
        approvalDecision?.invoke(false)
        approvalDecision = null
        _approval.value = null
        _approvalPhase.value = ApprovalPhase.NONE
        _attemptLabel.value = null
        _error.value = null
        _transferError.value = null
        lastConnectedHost = null
        closeSocket()
        _connection.value = ConnectionState.IDLE
    }

    fun connect(hostInput: String) {
        if (_connection.value == ConnectionState.CONNECTING) return
        // Validate against live capability first: an unsupported or
        // malformed address never dials (J2/J3/J4), and an offline
        // device never attempts (J6 offline). Home validates the same
        // way live; this is the second check for rejoin and saved-peer
        // paths that bypass the form.
        val capability = networkMonitor.snapshot()
        when (val validation = parseJoinInput(hostInput, capability)) {
            is JoinValidation.Blank -> return
            is JoinValidation.Offline -> {
                _error.value = "No network. Connect and retry."
                _connection.value = ConnectionState.FAILED
                return
            }
            is JoinValidation.Malformed, is JoinValidation.Unsupported -> {
                _error.value = validation.errorMessage()
                _connection.value = ConnectionState.FAILED
                return
            }
            is JoinValidation.Valid -> startConnect(validation, capability)
        }
    }

    private fun startConnect(validation: JoinValidation.Valid, capability: DeviceCapability) {
        connectGeneration++
        DiagLog.log("session", "connect start host=${validation.host} hostname=${validation.isHostname}")
        // Set CONNECTING synchronously, before launching: two rapid
        // connects (double tap, or a resume rejoin racing a manual
        // Join) must not both dial.
        _connection.value = ConnectionState.CONNECTING
        val generation = attemptGeneration
        connectJob = scope.launch {
            _error.value = null
            _transferError.value = null
            _attemptLabel.value = null
            try {
                // A hostname resolves on submit (J5); an unresolvable
                // name is malformed, and all-resolved-unsupported shows
                // J2/J3 before any dial. A literal is one candidate.
                val candidates: List<DialCandidate> = if (validation.isHostname) {
                    val resolved = try {
                        withContext(Dispatchers.IO) { InetAddress.getAllByName(validation.host).toList() }
                    } catch (e: UnknownHostException) {
                        throw IllegalStateException("That doesn’t look like an IP address or hostname.")
                    }
                    when (val classified = classifyResolved(resolved, capability)) {
                        is ResolvedCandidates.Supported -> classified.candidates
                        is ResolvedCandidates.Unsupported -> throw IllegalStateException(
                            if (classified.family == AddressFamily.IPV6)
                                "This address is IPv6, but this device has no IPv6."
                            else
                                "This address is IPv4, but this device has no IPv4."
                        )
                        ResolvedCandidates.Empty -> throw IllegalStateException("That doesn’t look like an IP address or hostname.")
                    }
                } else {
                    val family = validation.family ?: AddressFamily.IPV4
                    val kind = when (family) {
                        AddressFamily.IPV6 -> CandidateKind.IPV6
                        AddressFamily.IPV4 -> if (isLanIPv4Input(validation.host)) CandidateKind.LAN_IPV4 else CandidateKind.PUBLIC_IPV4
                    }
                    listOf(DialCandidate(validation.host, family, kind))
                }
                val host = candidates.first().host
                currentHost = host
                _peerHost.value = host
                val session = dialCandidates(candidates)
                DiagLog.log("session", "dial ok host=$host device=${session.peerDevice}")
                // A late success after Cancel must never open a session.
                if (generation != attemptGeneration) {
                    try { session.socket.close() } catch (_: Exception) {}
                    return@launch
                }
                _peerName.value = session.peerDevice.ifBlank { host }
                _approval.value = null
                _approvalPhase.value = ApprovalPhase.NONE
                _attemptLabel.value = null
                _connection.value = ConnectionState.CONNECTED
                DiagLog.log("session", "CONNECTED host=$host")
                lastConnectedHost = host
                // A new session starts with an empty transfer list: the
                // Session page shows THIS SESSION only, never the
                // previous session's transfers. The durable received
                // history (Received tab) is separate and untouched.
                _history.value = emptyList()
                _batchMessage.value = null
                // The PC's device name arrives in hello_ok. Pass it so
                // the saved peer shows (device name, ip) instead of
                // (ip, ip): dial saved a new peer as name = host
                // because the name was not known yet.
                peers.markConnected(host, session.peerDevice)
                _savedPeers.value = peers.loadPeers().sortedByDescending { it.lastSuccessfulConnection }
                startSessionService()
                startReader(session)
                startHeartbeat(session)
            } catch (e: Exception) {
                if (generation != attemptGeneration) return@launch
                DiagLog.log("session", "connect failed: ${e::class.simpleName}: ${e.message}")
                _approval.value = null
                _approvalPhase.value = ApprovalPhase.NONE
                _attemptLabel.value = null
                if ("denied the fingerprint" in (e.message ?: "")) {
                    // A deliberate Deny is not an error state.
                    _error.value = null
                    _connection.value = ConnectionState.IDLE
                } else {
                    _error.value = friendlyError(e)
                    _connection.value = ConnectionState.FAILED
                }
                closeSocket()
            }
        }
    }

    private fun isLanIPv4Input(host: String): Boolean {
        return try {
            val address = InetAddress.getByName(host)
            isLanIPv4(address)
        } catch (_: Exception) {
            false
        }
    }

    /** Attach a socket the host side already verified (C7): the peer
     * approved us, hello_ok is sent, and the session runs exactly
     * like a dialled one. No auto-rejoin of an accepted client: the
     * host waits for the next client instead. */
    fun attachHostSession(socket: SSLSocket, peerDevice: String, peerFingerprint: String, peerHost: String) {
        if (_connection.value == ConnectionState.CONNECTED) return
        connectGeneration++
        val session = DialResult(socket, peerDevice, peerFingerprint)
        this.socket = socket
        _peerName.value = peerDevice.ifBlank { peerHost }
        _peerHost.value = peerHost
        _approval.value = null
        _approvalPhase.value = ApprovalPhase.NONE
        _connection.value = ConnectionState.CONNECTED
        DiagLog.log("session", "HOST-ACCEPTED peer=$peerDevice host=$peerHost")
        lastConnectedHost = null
        _history.value = emptyList()
        _batchMessage.value = null
        startSessionService()
        startReader(session)
        startHeartbeat(session)
    }

    /** Deliberate disconnect. The cleanup runs in a coroutine, so a
     * rejoin tapped in the same instant can start before it finishes.
     * The cleanup therefore owns only what it captured at tap time:
     * it sends bye to the captured socket, cancels the captured jobs,
     * and closes the captured socket without touching the field when a
     * rejoin has already replaced it. It resets the state to IDLE only
     * when no new connect started since the tap, so a fresh session is
     * never clobbered by the old one's cleanup. */
    fun disconnect() {
        DiagLog.log("session", "disconnect (user)")
        attemptGeneration++
        // A disconnect ends any running batch too: a late
        // acknowledgement must never start the next queued file.
        batchGeneration++
        connectJob?.cancel()
        connectJob = null
        lastConnectedHost = null
        val gen = connectGeneration
        val sock = socket
        val reader = readerJob
        val heartbeat = heartbeatJob
        scope.launch {
            try {
                sendMutex.withLock {
                    sock?.let { s ->
                        try { writeHeader(s.outputStream, JSONObject().put("type", "bye").toString().toByteArray()) } catch (_: Exception) {}
                    }
                }
            } catch (_: Exception) {}
            reader?.cancel()
            heartbeat?.cancel()
            // Close the captured socket. If a rejoin replaced the field
            // meanwhile, close the old socket directly and leave the
            // new one alone.
            if (socket === sock) {
                closeSocket()
            } else {
                try { sock?.close() } catch (_: Exception) {}
            }
            if (connectGeneration == gen) {
                stopSessionService()
                _approval.value = null
                _approvalPhase.value = ApprovalPhase.NONE
                _error.value = null
                _transferError.value = null
                _busyPhase.value = null
                _downloadState.value = DownloadState.Idle
                _connection.value = ConnectionState.IDLE
                _active.value = null
                _batch.value = null
            }
        }
    }

    /** The foreground service runs for the whole session, not just while
     * a file is moving. That is the entire fix for the background
     * disconnect: with no service running, minimising the app (or
     * opening the system file picker, which backgrounds the app the
     * same way) left an ordinary cached process holding the socket,
     * and Android reclaimed it within seconds. A running foreground
     * service is exempt from Doze network restrictions and App Standby
     * throttling while it runs, so no battery-setting change is needed.
     * The service is started only from a successful connect, which
     * always happens while the app is in the foreground, so the
     * background start restriction on Android 12+ never applies. */
    private fun startSessionService() {
        DiagLog.log("service", "startSessionService requested")
        try {
            ContextCompat.startForegroundService(
                context, Intent(context, TransferService::class.java)
            )
        } catch (e: Exception) {
            DiagLog.log("service", "startForegroundService FAILED: ${e::class.simpleName}: ${e.message}")
        }
    }

    private fun stopSessionService() {
        // While hosting, the service stays for the listener even
        // after a session ends; HostManager stops it on Stop.
        if (TransferService.hostingActive) return
        DiagLog.log("service", "stopSessionService requested")
        try { context.stopService(Intent(context, TransferService::class.java)) } catch (_: Exception) {}
    }

    /** Short caller chain for the diagnostics log, so a failSession line
     * names the path that killed the session (verifyAlive, heartbeat,
     * reader) instead of just the error string. */
    private fun callerTrace(): String =
        Throwable().stackTrace
            .filter { it.className.contains("com.riptide") }
            .take(5)
            .joinToString(" <- ") { "${it.methodName}:${it.lineNumber}" }

    /** One place where a session dies unexpectedly: set the error, drop
     * to FAILED, close the dead socket, and stop the service. Every
     * failure path goes through here so none of the four can be
     * forgotten again. */
    private fun failSession(error: String) {
        if (_connection.value != ConnectionState.CONNECTED) return
        DiagLog.log("fail", "failSession: $error | caller: ${callerTrace()}")
        _error.value = error
        _connection.value = ConnectionState.FAILED
        closeSocket()
        stopSessionService()
    }

    /** Ping the PC and wait for its pong. A ping write that succeeds
     * into a dead socket proves nothing, so the pong coming back is
     * the proof. Retries once before failing the session: a single
     * slow or lost pong (the process just came back from the
     * background, threads rescheduling, a network blip) must not kill
     * a healthy session. The one-shot check is what used to
     * disconnect a live session the moment the app was reopened.
     * Returns false and fails the session only when no pong arrives
     * on either attempt. */
    private suspend fun verifyAlive(timeoutMs: Long = 3000, attempts: Int = 2): Boolean {
        if (_connection.value != ConnectionState.CONNECTED) return false
        repeat(attempts) { attempt ->
            val before = _lastPongAt.value
            DiagLog.log("verify", "attempt ${attempt + 1}/$attempts before=$before lastPongAt=${_lastPongAt.value}")
            // The ping write is network I/O and must run off the main
            // thread. onReturnedToForeground (and the pre-send checks)
            // run on Main, and Android throws NetworkOnMainThreadException
            // for a socket write there. That used to fail a healthy
            // session instantly on reopen, because the catch-all below
            // treated it as "Connection lost".
            val wrote = try {
                withContext(Dispatchers.IO) {
                    sendMutex.withLock {
                        val s = socket ?: return@withContext false
                        writeHeader(s.outputStream, JSONObject().put("type", "ping").toString().toByteArray())
                        true
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                DiagLog.log("verify", "ping write FAILED: ${e::class.simpleName}: ${e.message}")
                failSession("Connection lost.")
                return false
            }
            if (!wrote) return false
            DiagLog.log("verify", "ping sent (attempt ${attempt + 1})")
            val ok = withTimeoutOrNull(timeoutMs) {
                _lastPongAt.first { it > before }
                true
            } ?: false
            if (ok) {
                DiagLog.log("verify", "pong received, alive (attempt ${attempt + 1})")
                return true
            }
            DiagLog.log("verify", "pong TIMEOUT ${timeoutMs}ms (attempt ${attempt + 1})")
            // No pong for this ping. A late pong still counts for the
            // next attempt, since each attempt captures a fresh
            // `before` above.
        }
        DiagLog.log("verify", "no pong on either attempt, failing session")
        failSession("Connection lost.")
        return false
    }

    /** Called when the app comes back to the foreground. If the session
     * died while the app was away, rejoin the last peer on its own
     * using the identity already in the Keystore. A deliberate
     * disconnect clears the last host, so this never reconnects after
     * the user chose to leave, and a Join that never connected never
     * rejoins either. One attempt per return: if it fails, the error
     * stays on Home for the user to act on. A rejoin of a Known peer
     * still waits for the PC's approval tap; the waiting sheet shows
     * as usual while it waits. */
    suspend fun onReturnedToForeground() {
        DiagLog.log("session", "onReturnedToForeground connection=${_connection.value}")
        if (_connection.value == ConnectionState.CONNECTED) {
            verifyAlive()
        }
        if (_connection.value == ConnectionState.FAILED) {
            val host = lastConnectedHost ?: return
            DiagLog.log("session", "rejoining $host")
            connect(host)
        }
    }

    private fun closeSocket() {
        DiagLog.log("session", "closeSocket")
        try { socket?.close() } catch (_: Exception) {}
        socket = null
    }

    /** A dialled session: the live socket, the peer device name from
     * hello_ok, and the server fingerprint. Public because a pending
     * receive (waiting for the folder choice) carries it. */
    data class DialResult(val socket: SSLSocket, val peerDevice: String, val serverFingerprint: String)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private suspend fun dial(host: String, timeoutMs: Long = JOIN_TOTAL_WINDOW_MS): DialResult {
        val capturing = object : X509TrustManager {
            var chain: Array<X509Certificate>? = null
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) { this.chain = chain }
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        val ctx = SSLContext.getInstance("TLS")
        ctx.init(null, arrayOf<TrustManager>(capturing), null)
        val factory = ctx.socketFactory
        val raw = Socket()
        raw.connect(InetSocketAddress(host, SESSION_PORT), timeoutMs.toInt())
        raw.soTimeout = 0
        raw.keepAlive = true
        val tls = factory.createSocket(raw, host, SESSION_PORT, true) as SSLSocket
        tls.soTimeout = 0
        tls.keepAlive = true
        try {
            tls.startHandshake()
        } catch (e: Exception) {
            try { tls.close() } catch (_: Exception) {}
            throw e
        }
        val serverDer = capturing.chain?.firstOrNull()?.encoded
            ?: tls.session.peerCertificates.firstOrNull()?.encoded
            ?: throw IllegalStateException("host certificate missing")
        val serverFp = certFingerprint(serverDer)
        val saved = peers.loadPeers().firstOrNull { it.host == host }
        val status: TrustStatus
        val oldFp: String
        val oldCode: String?
        when {
            saved != null && saved.fingerprint == serverFp -> { status = TrustStatus.KNOWN; oldFp = ""; oldCode = null }
            saved != null -> { status = TrustStatus.CHANGED; oldFp = saved.fingerprint; oldCode = derive6DigitCode(saved.fingerprint) }
            else -> { status = TrustStatus.NEW; oldFp = ""; oldCode = null }
        }
        if (status != TrustStatus.KNOWN) {
            val decision = kotlinx.coroutines.suspendCancellableCoroutine<Boolean> { cont ->
                approvalDecision = { allow -> if (cont.isActive) cont.resume(allow) {} }
                _approvalPhase.value = ApprovalPhase.LOCAL_TRUST
                _approval.value = ApprovalRequest(
                    host = host, code = derive6DigitCode(serverFp), fullFingerprint = serverFp,
                    trustStatus = status, oldCode = oldCode, oldFingerprint = oldFp.ifBlank { null }
                )
            }
            if (!decision) {
                _approval.value = null
                _approvalPhase.value = ApprovalPhase.NONE
                try { tls.close() } catch (_: Exception) {}
                throw IllegalStateException("client denied the fingerprint")
            }
            // Local approval given: the sheet stays up in WAITING_HOST phase
            // (set by approveConnection) showing the PHONE's code until the
            // host acknowledges with hello_ok below. Do not clear it here.
            if (status == TrustStatus.NEW || status == TrustStatus.CHANGED) {
                peers.savePeer(SavedPeerRecord(name = host, host = host, fingerprint = serverFp))
                _savedPeers.value = peers.loadPeers().sortedByDescending { it.lastSuccessfulConnection }
            }
        }
        // The hello exchange reads the identity (cert + nonce signature).
        // Hold the identity lock across it so a reset can never swap the
        // identity mid-handshake. The trust-approval wait above is outside
        // the lock, so the lock is never held across user input.
        return identityMutex.withLock {
            val myDer = identity.certDer()
            val hello = JSONObject().apply {
                put("type", "hello")
                put("device", deviceName)
                put("host", host)
                put("cert", AndroidBase64.encodeToString(myDer, AndroidBase64.NO_WRAP))
            }
            val out = tls.outputStream
            val input = tls.inputStream
            writeHeader(out, hello.toString().toByteArray())
            val reply = JSONObject(String(readHeaderBytes(input)))
            var peerDevice = ""
            when (reply.optString("type")) {
                "hello_ok" -> { peerDevice = reply.optString("device") }
                "error" -> throw IllegalStateException("host rejected: ${reply.optString("message")}")
                "hello_challenge" -> {
                    val nonce = AndroidBase64.decode(reply.optString("nonce"), AndroidBase64.DEFAULT)
                    val sig = identity.signNonce(nonce)
                    val proof = JSONObject().apply {
                        put("type", "hello_proof")
                        put("signature", AndroidBase64.encodeToString(sig, AndroidBase64.NO_WRAP))
                    }
                    writeHeader(out, proof.toString().toByteArray())
                    val ok = JSONObject(String(readHeaderBytes(input)))
                    if (ok.optString("type") == "error") throw IllegalStateException("host rejected: ${ok.optString("message")}")
                    if (ok.optString("type") != "hello_ok") throw IllegalStateException("host did not accept the session")
                    peerDevice = ok.optString("device")
                }
                else -> throw IllegalStateException("host did not accept the session")
            }
            socket = tls
            DialResult(tls, peerDevice, serverFp)
        }
    }

    /** Try the candidates in order inside one 30-second total window
     * (J7/J8). The attempt label follows the current candidate, and
     * Cancel keeps the field: dialCandidates only throws, it never
     * touches the form. When every candidate times out, the failure
     * is the J8 timeout message; when the last failure is a refusal
     * (or anything else), that failure surfaces instead. */
    private suspend fun dialCandidates(candidates: List<DialCandidate>): DialResult {
        val deadline = System.currentTimeMillis() + JOIN_TOTAL_WINDOW_MS
        var lastError: Exception? = null
        var allTimedOut = true
        for (candidate in candidates) {
            val remaining = (deadline - System.currentTimeMillis()).coerceAtLeast(1L)
            _attemptLabel.value = candidateLabel(candidate.kind)
            DiagLog.log("session", "trying ${candidate.kind} host=${candidate.host} remaining=${remaining}ms")
            try {
                val result = dial(candidate.host, remaining)
                _attemptLabel.value = null
                return result
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                lastError = e
                if (!isTimeoutFailure(e)) allTimedOut = false
                DiagLog.log("session", "candidate ${candidate.kind} failed: ${e::class.simpleName}: ${e.message}")
            }
        }
        _attemptLabel.value = null
        if (allTimedOut) throw IllegalStateException(JOIN_TIMEOUT_MESSAGE)
        throw lastError ?: IllegalStateException(JOIN_TIMEOUT_MESSAGE)
    }

    private fun isTimeoutFailure(e: Exception): Boolean {
        val m = (e.message ?: "").lowercase()
        return e is java.net.SocketTimeoutException || "timed out" in m || "timeout" in m
    }

    private fun startHeartbeat(session: DialResult) {
        heartbeatJob?.cancel()
        DiagLog.log("heartbeat", "started")
        heartbeatJob = scope.launch {
            val start = System.currentTimeMillis()
            while (true) {
                delay(10_000)
                try {
                    sendMutex.withLock {
                        session.socket.let { s ->
                            writeHeader(s.outputStream, JSONObject().put("type", "ping").toString().toByteArray())
                        }
                    }
                    _pingMs.value = System.currentTimeMillis() - start
                    DiagLog.log("heartbeat", "ping sent")
                } catch (e: Exception) {
                    // A cancelled heartbeat (deliberate disconnect) is not
                    // a failure. Any other write failure means the socket
                    // is dead, and this is what used to surface later as
                    // "send failed: Broken pipe (0 bytes sent)": the
                    // reader blocks forever on a silently dead socket, so
                    // nothing marked the session dead until the next send
                    // tried to write to it. Detect it here, within 10 s.
                    if (e is kotlinx.coroutines.CancellationException) break
                    DiagLog.log("heartbeat", "ping write FAILED: ${e::class.simpleName}: ${e.message}")
                    failSession("Connection lost.")
                    break
                }
            }
        }
    }

    private fun startReader(session: DialResult) {
        readerJob?.cancel()
        DiagLog.log("reader", "started")
        readerJob = scope.launch {
            val input = session.socket.inputStream
            try {
                while (true) {
                    val header = JSONObject(String(readHeaderBytes(input)))
                    when (header.optString("type")) {
                        "file" -> {
                            DiagLog.log("reader", "file header: ${header.optString("name")}")
                            // A file arriving answers a pending download request.
                            if (_downloadState.value is DownloadState.Waiting) _downloadState.value = DownloadState.Idle
                            receiveFile(session, header, input)
                        }
                        "download_denied" -> {
                            val name = header.optString("name")
                            DiagLog.log("reader", "download_denied: $name")
                            _downloadState.value = DownloadState.Denied(name)
                            addHistory(name, name, false, 0, TransferDirection.RECEIVE, TransferStatus.FAILED, false)
                        }
                        "ack" -> {
                            DiagLog.log("reader", "ack: ${header.optString("name")} ok=${header.optBoolean("ok")}")
                            // Completes the send that is waiting for confirmation.
                            // On success the PC sends the actual saved name, which
                            // may differ from the sent name when it was renamed.
                            pendingAck?.complete(
                                AckResult(header.optString("name"), header.optBoolean("ok"), header.optString("reason"))
                            )
                            pendingAck = null
                        }
                        "download_ask" -> {
                            DiagLog.log("reader", "download_ask: ${header.optString("name")}")
                            sendMutex.withLock {
                                val denied = JSONObject().apply {
                                    put("type", "download_denied")
                                    put("name", header.optString("name"))
                                    put("id", header.optString("id"))
                                }
                                writeHeader(session.socket.outputStream, denied.toString().toByteArray())
                            }
                        }
                        "ping" -> {
                            DiagLog.log("reader", "ping received, pong sent")
                            sendMutex.withLock {
                                writeHeader(session.socket.outputStream, JSONObject().put("type", "pong").toString().toByteArray())
                            }
                        }
                        "pong" -> {
                            _lastPongAt.value = System.currentTimeMillis()
                            DiagLog.log("reader", "pong received, lastPongAt=${_lastPongAt.value}")
                        }
                        "bye" -> {
                            DiagLog.log("reader", "bye received")
                            // The PC ended the session cleanly. This is a
                            // session error, not a join failure: close the
                            // socket and drop to FAILED so the UI shows the
                            // interrupted state instead of staying on a dead
                            // Session. Never leave a dead socket behind for
                            // a later send to trip over with Broken pipe.
                            failSession("The PC ended the session.")
                            break
                        }
                        "error" -> {
                            DiagLog.log("reader", "error: ${header.optString("message")}")
                            // Usually "no such file" answering a download
                            // request: a transfer error, not a dead session.
                            // Keep reading; if the PC really closed, the
                            // next read fails and the session path below
                            // handles it. Never break the reader here: that
                            // used to strand the UI on a dead session.
                            _transferError.value = header.optString("message")
                        }
                    }
                }
            } catch (e: Exception) {
                // A deliberate cancel/disconnect is not a failure: the
                // reader is cancelled from disconnect(), and reporting
                // "session closed" for it used to leave a stale error on
                // Home after a clean disconnect.
                if (e is kotlinx.coroutines.CancellationException) return@launch
                DiagLog.log("reader", "read FAILED: ${e::class.simpleName}: ${e.message}")
                pendingAck?.complete(null)
                pendingAck = null
                // Drop the dead socket now. A later send must see
                // "not connected" instead of writing to a closed
                // socket and reporting a misleading Broken pipe.
                failSession(friendlyError(e))
            }
        }
    }

    private suspend fun receiveFile(session: DialResult, header: JSONObject, input: java.io.InputStream) {
        val name = basenameOnly(header.optString("name"))
        val size = header.optLong("size")
        val expected = header.optString("sha256")
        _busyPhase.value = BusyPhase.TRANSFERRING
        _active.value = ActiveTransfer(filename = name, direction = TransferDirection.RECEIVE, totalBytes = size, transferredBytes = 0, speedFormatted = "", progress = 0f, etaFormatted = null)
        val part = File(context.cacheDir, "$name.part")
        val digest = MessageDigest.getInstance("SHA-256")
        var received = 0L
        var lastReport = 0L
        val start = System.currentTimeMillis()
        try {
            FileOutputStream(part).use { out ->
                var remaining = size
                val buf = ByteArray(128 * 1024)
                while (remaining > 0) {
                    val toRead = minOf(buf.size.toLong(), remaining).toInt()
                    var read = 0
                    while (read < toRead) {
                        val r = input.read(buf, read, toRead - read)
                        if (r < 0) throw java.io.EOFException("session closed during file")
                        read += r
                    }
                    out.write(buf, 0, read)
                    digest.update(buf, 0, read)
                    remaining -= read
                    received += read
                    val now = System.currentTimeMillis()
                    if (now - lastReport >= 100 || remaining == 0L) {
                        lastReport = now
                        val elapsed = ((now - start) / 1000.0).coerceAtLeast(0.1)
                        val speed = received / elapsed
                        _active.value = ActiveTransfer(name, TransferDirection.RECEIVE, size, received, formatSpeed(speed), if (size > 0) received.toFloat() / size else 0f, formatRemainingTime(size - received, speed))
                    }
                }
            }
        } catch (e: Exception) {
            clearActiveIfNoBatch()
            addHistory(name, name, false, received, TransferDirection.RECEIVE, TransferStatus.FAILED, false)
            _transferError.value = "receive failed: ${e.message}"
            return
        }
        // 100% of bytes is not success yet: verify the hash, then save.
        _busyPhase.value = BusyPhase.VERIFYING
        val ok = digest.digest().joinToString("") { "%02x".format(it) } == expected.lowercase()
        if (!ok) {
            clearActiveIfNoBatch()
            addHistory(name, name, false, size, TransferDirection.RECEIVE, TransferStatus.FAILED, false)
            sendAck(session, name, false, "SHA-256 mismatch")
            return
        }
        val saved = saveReceived(part, name)
        when (saved) {
            is SaveResult.Saved -> {
                clearActiveIfNoBatch()
                addHistory(name, saved.name, saved.name != name, size, TransferDirection.RECEIVE, TransferStatus.SUCCESS, true)
                // Durable received history: only after verification AND saving succeed.
                val record = ReceivedFileRecord(
                    id = UUID.randomUUID().toString(), uri = saved.uri, originalName = name,
                    savedName = saved.name, sourcePeer = _peerName.value.ifBlank { _peerHost.value },
                    sizeBytes = size, verified = true
                )
                receivedHistoryRepo.add(record)
                _receivedHistory.value = receivedHistoryRepo.load()
                sendAck(session, saved.name, true, "")
            }
            is SaveResult.LostAccess -> {
                // Keep the verified part file and wait for the user's
                // choice. No ack and no history yet; the recovery dialog
                // resolves it.
                _pendingReceive.value = PendingReceive(part, name, size, session)
                clearActiveIfNoBatch()
                DiagLog.log("session", "receive folder lost access, waiting for user choice: $name")
            }
            is SaveResult.Failed -> {
                // Verification passed but saving failed: this is not a success,
                // and it must not enter received history.
                clearActiveIfNoBatch()
                addHistory(name, name, false, size, TransferDirection.RECEIVE, TransferStatus.FAILED, false)
                _transferError.value = "Could not save $name to ${_receiveFolder.value.displayName}"
                sendAck(session, name, false, "save failed")
            }
        }
        return
    }

    /** Outcome of saving a verified file. LostAccess means a custom
     * folder is set but its access is gone; the caller must offer the
     * recovery choice instead of failing the transfer. */
    sealed interface SaveResult {
        data class Saved(val name: String, val uri: String) : SaveResult
        object Failed : SaveResult
        object LostAccess : SaveResult
    }

    /** Save a verified file to the current receive folder. */
    private fun saveReceived(part: File, name: String): SaveResult {
        val folder = _receiveFolder.value
        if (folder.isDefault) {
            val saved = saveToDownloads(part, name) ?: return SaveResult.Failed
            return SaveResult.Saved(saved.first, saved.second)
        }
        val treeUri = folder.treeUri ?: return SaveResult.Failed
        if (!receiveFolderRepo.hasPersistedAccess(treeUri)) return SaveResult.LostAccess
        return saveToCustomFolder(part, name, treeUri)
    }

    /** Save a verified file into a user-picked folder via the Storage
     * Access Framework. Returns LostAccess when the folder can no
     * longer be written. */
    private fun saveToCustomFolder(part: File, name: String, treeUri: String): SaveResult {
        return try {
            val dir = DocumentFile.fromTreeUri(context, Uri.parse(treeUri)) ?: return SaveResult.LostAccess
            if (!dir.exists() || !dir.canWrite()) return SaveResult.LostAccess
            val existing = mutableSetOf<String>()
            dir.listFiles().forEach { it.name?.let { n -> existing.add(n) } }
            val saved = uniqueFinalName(existing, name) ?: return SaveResult.Failed
            val file = dir.createFile("application/octet-stream", saved) ?: return SaveResult.Failed
            context.contentResolver.openOutputStream(file.uri)?.use { out ->
                FileInputStream(part).use { inp -> inp.copyTo(out) }
            } ?: return SaveResult.Failed
            part.delete()
            SaveResult.Saved(saved, file.uri.toString())
        } catch (e: SecurityException) {
            SaveResult.LostAccess
        } catch (_: Exception) {
            SaveResult.Failed
        }
    }

    /** Finish a receive that waited for the folder-recovery choice:
     * record the outcome and ack the PC. */
    private suspend fun completePendingReceive(pending: PendingReceive, saved: SaveResult) {
        when (saved) {
            is SaveResult.Saved -> {
                addHistory(pending.name, saved.name, saved.name != pending.name, pending.size, TransferDirection.RECEIVE, TransferStatus.SUCCESS, true)
                val record = ReceivedFileRecord(
                    id = UUID.randomUUID().toString(), uri = saved.uri, originalName = pending.name,
                    savedName = saved.name, sourcePeer = _peerName.value.ifBlank { _peerHost.value },
                    sizeBytes = pending.size, verified = true
                )
                receivedHistoryRepo.add(record)
                _receivedHistory.value = receivedHistoryRepo.load()
                sendAck(pending.session, saved.name, true, "")
            }
            else -> {
                try { pending.part.delete() } catch (_: Exception) {}
                addHistory(pending.name, pending.name, false, pending.size, TransferDirection.RECEIVE, TransferStatus.FAILED, false)
                _transferError.value = "Could not save ${pending.name} to ${_receiveFolder.value.displayName}"
                sendAck(pending.session, pending.name, false, "save failed")
            }
        }
    }

    /** Returns the actual saved name and its MediaStore content URI, or null
     * when saving failed. The URI is what Received history opens later. */
    private fun saveToDownloads(part: File, name: String): Pair<String, String>? {
        return try {
            val resolver: ContentResolver = context.contentResolver
            val existing = mutableSetOf<String>()
            resolver.query(MediaStore.Downloads.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Downloads.DISPLAY_NAME), "${MediaStore.Downloads.RELATIVE_PATH} = ?", arrayOf("Download/Riptide/"), null)?.use { c ->
                while (c.moveToNext()) existing.add(c.getString(0))
            }
            val saved = uniqueFinalName(existing, name) ?: return null
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, saved)
                put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
                put(MediaStore.Downloads.RELATIVE_PATH, "Download/Riptide")
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
            resolver.openOutputStream(uri)?.use { out ->
                FileInputStream(part).use { inp -> inp.copyTo(out) }
            }
            part.delete()
            saved to uri.toString()
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun sendAck(session: DialResult, name: String, ok: Boolean, reason: String) {
        sendMutex.withLock {
            val ack = JSONObject().apply { put("type", "ack"); put("name", name); put("ok", ok); put("reason", reason) }
            try { writeHeader(session.socket.outputStream, ack.toString().toByteArray()) } catch (_: Exception) {}
        }
    }

    /** Outcome of one awaitable file send. */
    sealed interface SendOutcome {
        data class Sent(val savedName: String, val size: Long) : SendOutcome
        data class NotSent(val reason: String) : SendOutcome
    }

    /** Display name of a content URI, from the provider. */
    private fun resolveUriName(uri: Uri): String {
        var name = "file"
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (ni >= 0) name = c.getString(ni) ?: name
                }
            }
        } catch (_: Exception) {}
        return basenameOnly(name)
    }

    /** Provider-reported size, or null when the provider reports
     * nothing usable (0 or absent). A zero-byte file and an unknown
     * size both read as 0 here; the spool gives the exact size, so
     * this only ever informs the display. */
    private fun resolveUriSize(uri: Uri): Long? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val si = c.getColumnIndex(OpenableColumns.SIZE)
                    if (si >= 0) c.getLong(si).takeIf { it > 0 } else null
                } else null
            }
        } catch (_: Exception) { null }
    }

    fun sendFile(uri: Uri) {
        if (_active.value != null || _busyPhase.value != null || _batch.value != null) {
            _transferError.value = "A transfer is already in progress"
            return
        }
        // Never write to a socket that is not a live session. The reader
        // closes the socket when the session dies, so a stale non-null
        // socket used to produce "send failed: Broken pipe (0 bytes sent)"
        // on Home after the session was already gone.
        if (_connection.value != ConnectionState.CONNECTED || socket == null) {
            _transferError.value = "Not connected. Join the PC and try again."
            return
        }
        _transferError.value = null
        _batchMessage.value = null
        scope.launch {
            sendSingleFile(uri, isBatch = false, preflight = true)
        }
    }

    /** One awaitable file send: spool, hash, write, wait for the PC's
     * ack. The caller holds the transfer slot (no guard here). When
     * [isBatch] is false this clears the active transfer when the file
     * finishes, as a single send always did; when true it leaves the
     * active transfer in place so the batch panel stays put between
     * files, and the batch runner clears it when the batch ends.
     * [onPrepared] fires with the exact spooled size, [onProgress]
     * with (sent, total, speed) on each progress update, [onWaitingAck]
     * when all bytes are written and the PC is confirming. Returns Sent
     * only after the PC confirms it. */
    private suspend fun sendSingleFile(
        uri: Uri,
        isBatch: Boolean,
        preflight: Boolean,
        onPrepared: ((Long) -> Unit)? = null,
        onProgress: ((Long, Long, Double) -> Unit)? = null,
        onWaitingAck: (() -> Unit)? = null
    ): SendOutcome {
        val s = socket ?: run { _transferError.value = "Not connected. Join the PC and try again."; return SendOutcome.NotSent("not connected") }
        if (preflight) {
            // The session can have died since the last heartbeat (at most
            // 10 s ago). Ping and wait for the pong before spooling a
            // whole file into a send that would fail at the first write.
            // verifyAlive already fails the session when it is dead.
            if (!verifyAlive(2000)) return SendOutcome.NotSent("connection lost")
        }
        val name = resolveUriName(uri)
        _busyPhase.value = BusyPhase.PREPARING
        // Spool the picked file to a local temp copy first. The size
        // reported by a content provider (OpenableColumns.SIZE) is
        // not trustworthy: for cloud-backed files it can be 0 or
        // stale, while the stream delivers the real bytes. Sending
        // a header whose size does not match the bytes that follow
        // makes the PC read the leftover file bytes as a header,
        // fail with "bad header length", close the session, and the
        // phone then fails with Broken pipe. Size, hash and bytes
        // all come from the same local spool, so they cannot disagree.
        val spool = File(context.cacheDir, "send-${UUID.randomUUID()}.tmp")
        try {
            val opened = context.contentResolver.openInputStream(uri)
                ?: run { _transferError.value = "send failed: could not open $name"; return SendOutcome.NotSent("could not open $name") }
            opened.use { inp -> FileOutputStream(spool).use { out -> inp.copyTo(out) } }
            val (counted, sha) = FileInputStream(spool).use { hashAndCount(it) }
            val size = counted
            onPrepared?.invoke(size)
            _busyPhase.value = BusyPhase.TRANSFERRING
            _active.value = ActiveTransfer(name, TransferDirection.SEND, size, 0, "", 0f, null)
            val header = JSONObject().apply { put("type", "file"); put("name", name); put("size", size); put("sha256", sha) }
            val start = System.currentTimeMillis()
            var sent = 0L
            var lastReport = 0L
            val ackDeferred = CompletableDeferred<AckResult?>()
            pendingAck?.complete(null)
            pendingAck = ackDeferred
            try {
                sendMutex.withLock {
                    writeHeader(s.outputStream, header.toString().toByteArray())
                    FileInputStream(spool).use { inp ->
                        val buf = ByteArray(128 * 1024)
                        while (true) {
                            val r = inp.read(buf)
                            if (r < 0) break
                            if (r == 0) continue
                            s.outputStream.write(buf, 0, r)
                            sent += r
                            val now = System.currentTimeMillis()
                            if (now - lastReport >= 100 || sent >= size) {
                                lastReport = now
                                val elapsed = ((now - start) / 1000.0).coerceAtLeast(0.1)
                                val speed = sent / elapsed
                                _active.value = ActiveTransfer(name, TransferDirection.SEND, size, sent, formatSpeed(speed), if (size > 0) sent.toFloat() / size else 0f, formatRemainingTime(size - sent, speed))
                                onProgress?.invoke(sent, size, speed)
                            }
                        }
                        s.outputStream.flush()
                    }
                }
                // Do not call it sent until the PC confirms it: the PC
                // acks only after reading every byte, verifying the
                // SHA-256 and renaming the .part file. On success the
                // ack carries the actual saved name.
                _busyPhase.value = BusyPhase.WAITING_ACK
                onWaitingAck?.invoke()
                val ack = withTimeout(60_000) { ackDeferred.await() }
                clearActiveIfSingle(isBatch)
                if (ack == null) {
                    addHistory(name, name, false, size, TransferDirection.SEND, TransferStatus.FAILED, false)
                    _transferError.value = "send failed: connection lost before PC confirmed $name ($sent of $size bytes sent)"
                    return SendOutcome.NotSent("connection lost")
                } else if (!ack.ok) {
                    addHistory(name, name, false, size, TransferDirection.SEND, TransferStatus.FAILED, false)
                    _transferError.value = "send failed: PC rejected $name: ${ack.reason.ifBlank { "unknown reason" }}"
                    return SendOutcome.NotSent("PC rejected $name")
                } else {
                    addHistory(name, ack.name, ack.name != name, size, TransferDirection.SEND, TransferStatus.SUCCESS, true)
                    return SendOutcome.Sent(ack.name, size)
                }
            } catch (e: Exception) {
                pendingAck = null
                clearActiveIfSingle(isBatch)
                addHistory(name, name, false, size, TransferDirection.SEND, TransferStatus.FAILED, false)
                _transferError.value = "send failed: ${e.message} ($sent of $size bytes sent)"
                return SendOutcome.NotSent(e.message ?: "send failed")
            }
        } catch (e: Exception) {
            clearActiveIfSingle(isBatch)
            _transferError.value = "send failed: ${e.message}"
            return SendOutcome.NotSent(e.message ?: "send failed")
        } finally {
            spool.delete()
        }
    }

    /** Clear the active transfer when this was a single (non-batch)
     * send. A batch leaves it in place so the panel stays put between
     * files; the batch runner clears it when the batch ends. */
    private fun clearActiveIfSingle(isBatch: Boolean) {
        if (!isBatch) {
            _active.value = null
            _busyPhase.value = null
        }
    }

    /** Clear the active transfer only when no batch is running. A
     * batch owns the slot, so an inbound file finishing must not
     * clear the batch's busy state and make Send file tappable
     * mid-batch. */
    private fun clearActiveIfNoBatch() {
        if (_batch.value == null) {
            _active.value = null
            _busyPhase.value = null
        }
    }

    /** Send several files as one batch, in picker result order, one at
     * a time. A single URI is just a single send. The batch holds the
     * transfer slot from the first preparation to the last
     * acknowledgement, so the panel and busy controls stay in place
     * between files. */
    fun sendBatch(uris: List<Uri>) {
        if (uris.isEmpty()) return
        if (uris.size == 1) { sendFile(uris[0]); return }
        if (_active.value != null || _busyPhase.value != null || _batch.value != null) {
            _transferError.value = "A transfer is already in progress"
            return
        }
        if (_connection.value != ConnectionState.CONNECTED || socket == null) {
            _transferError.value = "Not connected. Join the PC and try again."
            return
        }
        _transferError.value = null
        _batchMessage.value = null
        // Hold the slot synchronously, before the coroutine starts, so
        // a single send cannot slip in between the tap and the first file.
        _busyPhase.value = BusyPhase.PREPARING
        val generation = ++batchGeneration
        scope.launch {
            runBatch(uris, generation)
        }
    }

    /** Cancel the running batch. Invalidates the queue first, so a late
     * acknowledgement cannot start the next file, then disconnects:
     * the current build has no way to stop a send without closing the
     * session. Completed files stay in Session history. */
    fun cancelBatch() {
        val batch = _batch.value ?: return
        DiagLog.log("session", "cancelBatch (user) sent=${batch.sentCount} of ${batch.items.size}")
        batchGeneration++
        _batch.value = null
        _active.value = null
        _busyPhase.value = null
        _batchMessage.value = "Batch cancelled · ${batch.sentCount} of ${batch.items.size} files sent"
        disconnect()
    }

    /** The batch runner. Sends the snapshotted list in order, starting
     * the next file only after the current file is confirmed. Stops on
     * the first failure, on a disconnect, or on cancellation, and
     * leaves the remaining items unsent for explicit reselection. */
    private suspend fun runBatch(uris: List<Uri>, generation: Int) {
        // Retain access to the selected URIs for the lifetime of the
        // batch: the picker's transient permission can lapse while the
        // batch is still sending.
        uris.forEach { uri ->
            try { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
        }
        // Snapshot the selected list at the start.
        val items = uris.map { uri ->
            BatchItem(id = UUID.randomUUID().toString(), uri = uri.toString(), name = resolveUriName(uri), providerSize = resolveUriSize(uri))
        }
        val startedAt = System.currentTimeMillis()
        _batch.value = BatchState(items = items, activeIndex = 0, sentCount = 0, startedAtMs = startedAt)
        var sent = 0
        for (index in items.indices) {
            if (generation != batchGeneration) return finishBatchCancelled(sent, items.size)
            if (_connection.value != ConnectionState.CONNECTED) return finishBatchFailed(sent, items.size)
            val item = items[index]
            updateBatchItem(index) { it.copy(phase = BatchItemPhase.PREPARING) }
            _batch.update { it?.copy(activeIndex = index) }
            val outcome = sendSingleFile(
                Uri.parse(item.uri),
                isBatch = true,
                // One pre-flight ping for the batch, before the first
                // file. Later files rely on the heartbeat and on the
                // write itself failing when the socket is dead.
                preflight = index == 0,
                onPrepared = { size ->
                    updateBatchItem(index) { it.copy(spooledSize = size, phase = BatchItemPhase.SENDING) }
                },
                onProgress = { transferred, _, _ ->
                    updateBatchProgress(index, transferred, startedAt)
                },
                onWaitingAck = {
                    updateBatchItem(index) { it.copy(phase = BatchItemPhase.VERIFYING) }
                    _batch.update { it?.copy(batchEtaText = "Verifying on PC") }
                }
            )
            // A cancel (or disconnect) during this file ends the batch
            // here, before the outcome is acted on.
            if (generation != batchGeneration) return finishBatchCancelled(sent, items.size)
            when (outcome) {
                is SendOutcome.Sent -> {
                    sent++
                    updateBatchItem(index) { it.copy(phase = BatchItemPhase.SENT, savedName = outcome.savedName, transferredBytes = outcome.size) }
                    _batch.update { it?.copy(sentCount = sent) }
                }
                is SendOutcome.NotSent -> {
                    updateBatchItem(index) { it.copy(phase = BatchItemPhase.FAILED) }
                    return finishBatchFailed(sent, items.size)
                }
            }
        }
        finishBatchSent(items.size)
    }

    /** Update one item in the live batch state. */
    private fun updateBatchItem(index: Int, transform: (BatchItem) -> BatchItem) {
        _batch.update { batch ->
            batch?.copy(items = batch.items.mapIndexed { i, item -> if (i == index) transform(item) else item })
        }
    }

    /** Update the active file's progress and recompute the batch's
     * overall speed and ETA. The rate is the cumulative average since
     * the batch started, so the ETA stays steady instead of jumping
     * with each network chunk. */
    private fun updateBatchProgress(index: Int, transferred: Long, startedAt: Long) {
        _batch.update { batch ->
            val b = batch ?: return@update batch
            val items = b.items.mapIndexed { i, item -> if (i == index) item.copy(transferredBytes = transferred) else item }
            val updated = b.copy(items = items)
            val elapsed = ((System.currentTimeMillis() - startedAt) / 1000.0).coerceAtLeast(0.1)
            val rate = updated.overallTransferred / elapsed
            val total = updated.totalBytes
            val eta = when {
                total == null -> "Estimating time…"
                else -> formatRemainingTime(total - updated.overallTransferred, rate) ?: "Estimating time…"
            }
            updated.copy(overallSpeedFormatted = formatSpeed(rate), batchEtaText = eta)
        }
    }

    /** All files confirmed. The panel goes away; the outcome stays as
     * a message under it, and the confirmed transfers stay in history. */
    private fun finishBatchSent(count: Int) {
        _active.value = null
        _busyPhase.value = null
        _batch.value = null
        _batchMessage.value = "Batch sent · $count ${if (count == 1) "file" else "files"}"
    }

    /** Stopped by a file failure or a disconnect. The per-file error is
     * already in [_transferError]; keep the confirmed count visible
     * alongside it. Remaining items are unsent. */
    private fun finishBatchFailed(sent: Int, total: Int) {
        _active.value = null
        _busyPhase.value = null
        _batch.value = null
        _batchMessage.value = "$sent of $total files sent"
    }

    /** Stopped by the user cancelling. Same shape as a failure, with
     * the cancelled wording. */
    private fun finishBatchCancelled(sent: Int, total: Int) {
        _active.value = null
        _busyPhase.value = null
        _batch.value = null
        _batchMessage.value = "Batch cancelled · $sent of $total files sent"
    }

    fun requestDownload(name: String) {
        if (_downloadState.value is DownloadState.Waiting) return
        if (_connection.value != ConnectionState.CONNECTED || socket == null) {
            _transferError.value = "Not connected. Join the PC and try again."
            return
        }
        _transferError.value = null
        scope.launch {
            val s = socket ?: run { _transferError.value = "Not connected. Join the PC and try again."; return@launch }
            // Same pre-flight check as a send: do not ask for a file
            // over a session that has already died.
            if (!verifyAlive(2000)) return@launch
            val trimmed = name.trim()
            try {
                sendMutex.withLock {
                    val ask = JSONObject().apply { put("type", "download_ask"); put("name", trimmed); put("id", UUID.randomUUID().toString()) }
                    writeHeader(s.outputStream, ask.toString().toByteArray())
                }
                // The PC answers with a file (receive) or download_denied.
                _downloadState.value = DownloadState.Waiting(trimmed)
            } catch (e: Exception) {
                _downloadState.value = DownloadState.Idle
                _transferError.value = "download request failed: ${e.message}"
            }
        }
    }

    private fun addHistory(filename: String, saved: String, renamed: Boolean, size: Long, dir: TransferDirection, status: TransferStatus, verified: Boolean) {
        _history.update { listOf(TransferRecord(id = UUID.randomUUID().toString(), filename = filename, savedFilename = saved, wasRenamed = renamed, sizeBytes = size, direction = dir, status = status, sha256Verified = verified)) + it }
    }

    private fun formatSpeed(bytesPerSec: Double): String {
        return when {
            bytesPerSec >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB/s", bytesPerSec / (1024 * 1024))
            bytesPerSec >= 1024 -> String.format(Locale.US, "%.1f KB/s", bytesPerSec / 1024)
            else -> String.format(Locale.US, "%.0f B/s", bytesPerSec)
        }
    }

    private fun friendlyError(e: Exception): String {
        val m = e.message ?: e.toString()
        return when {
            m.startsWith("Couldn’t reach the server.") -> m
            "timed out" in m.lowercase() -> "Connection timed out. Check the PC is on Create Server, port 50000 is open, and the router forwards TCP 50000 to that PC."
            "refused" in m.lowercase() -> "Connection refused. The host is not listening on port 50000."
            "fingerprint changed" in m.lowercase() -> "Fingerprint changed, session aborted."
            "denied the fingerprint" in m.lowercase() -> "You denied the fingerprint."
            else -> m
        }
    }
}
