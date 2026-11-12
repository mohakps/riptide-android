package com.riptide.core

import android.content.Context
import android.content.Intent
import android.util.Base64 as AndroidBase64
import androidx.core.content.ContextCompat
import com.riptide.data.PeerRepository
import com.riptide.service.TransferService
import com.riptide.ui.model.ApprovalRequest
import com.riptide.ui.model.HostUiState
import com.riptide.ui.model.TrustStatus
import com.riptide.util.certFingerprint
import com.riptide.util.derive6DigitCode
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.security.KeyStore
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/** How long the "Network changed. Share the new address." notice stays up. */
private const val NETWORK_CHANGED_NOTICE_MS = 4000L

/**
 * The Create Server host side: a TLS listener on port 50000 that
 * accepts one client at a time, verifies it with the same signed
 * hello challenge the PC host uses (client presents its certificate
 * in hello, host sends a 32-byte nonce, client signs RSA PKCS1v15 +
 * SHA-256, host verifies against the presented certificate), shows
 * the existing verification sheet, and hands the verified socket to
 * SessionManager, which runs the session exactly like a dialled one.
 *
 * Listening is dual-stack: bind "::" first (IPv4 + IPv6 on one
 * socket where the platform allows it), fall back to 0.0.0.0. The
 * listener is retired and rebound when the network changes, and
 * Copy/Share always bind to the current addresses.
 *
 * The identity key is a SOFTWARE key (v0.9.2): the TLS context
 * offers the default protocols (TLS 1.3 and 1.2). v0.9.1 restricted
 * the listener to TLS 1.2 because the then-current AndroidKeyStore
 * key could only sign RSA-PKCS1; that restriction is gone now that
 * the key supports PSS.
 *
 * No log file is ever written here: diagnostics go through DiagLog
 * (in-memory + logcat only); the only file write in the app is
 * DiagLog.exportUri(), on the user's Share tap in Settings.
 */

/** In-memory KeyStore holding one private key + certificate pair. */
internal fun buildHostKeyStore(privateKey: PrivateKey, identityCert: X509Certificate): KeyStore {
    return KeyStore.getInstance(KeyStore.getDefaultType()).apply {
        load(null)
        setKeyEntry("riptide_identity", privateKey, null, arrayOf(identityCert))
    }
}

/**
 * Host TLS context from a private key and the identity certificate.
 * The certificate must be the IdentityManager cert (identity.crt),
 * the same one fingerprint()/code() are derived from. The private
 * key must be a SOFTWARE key (IdentityManager.privateKey()): its
 * encoded form is available, so Conscrypt can use it for the
 * handshake. An AndroidKeyStore key failed every host handshake
 * (v0.9/v0.9.1) and must never be passed here.
 */
internal fun createHostSslContext(privateKey: PrivateKey, identityCert: X509Certificate): SSLContext {
    val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
    keyManagerFactory.init(buildHostKeyStore(privateKey, identityCert), null)
    return SSLContext.getInstance("TLS").apply {
        init(keyManagerFactory.keyManagers, null, null)
    }
}

/** Full exception chain, most recent first, for the diagnostics log:
 * the top message alone hid the cause of the v0.9/v0.9.1 failures. */
internal fun exceptionChain(e: Throwable): String {
    val parts = ArrayList<String>()
    var cur: Throwable? = e
    while (cur != null && parts.size < 8) {
        parts.add("${cur::class.simpleName}: ${cur.message}")
        cur = cur.cause
    }
    return parts.joinToString(" <= ")
}

/** Negotiated session info for a failure log line, or "" when the
 * handshake never produced a session. Never throws. */
internal fun sessionInfo(tls: SSLSocket): String {
    return try {
        " session=${tls.session.protocol}/${tls.session.cipherSuite}"
    } catch (_: Exception) {
        ""
    }
}

/**
 * The Create Server host side: a TLS listener on port 50000 that
 * accepts one client at a time, verifies it with the same signed
 * hello challenge the PC host uses (client presents its certificate
 * in hello, host sends a 32-byte nonce, client signs RSA PKCS1v15 +
 * SHA-256, host verifies against the presented certificate), shows
 * the existing verification sheet, and hands the verified socket to
 * SessionManager, which runs the session exactly like a dialled one.
 *
 * Listening is dual-stack: bind "::" first (IPv4 + IPv6 on one
 * socket where the platform allows it), fall back to 0.0.0.0. The
 * listener is retired and rebound when the network changes, and
 * Copy/Share always bind to the current addresses.
 *
 * No log file is ever written here: diagnostics go through DiagLog
 * (in-memory + logcat only); the only file write in the app is
 * DiagLog.exportUri(), on the user's Share tap in Settings.
 */
class HostManager(
    private val context: Context,
    private val networkMonitor: NetworkMonitor,
    private val sessionConnection: StateFlow<ConnectionState>,
    private val onSessionAccepted: (SSLSocket, String, String, String) -> Unit
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val identity = IdentityManager(context)
    private val peers = PeerRepository(context)

    private val _uiState = MutableStateFlow(
        HostUiState(myCode = try { identity.code() } catch (_: Exception) { "------" })
    )
    val uiState: StateFlow<HostUiState> = _uiState.asStateFlow()

    private val _approval = MutableStateFlow<ApprovalRequest?>(null)
    val approval: StateFlow<ApprovalRequest?> = _approval.asStateFlow()

    private val _isHosting = MutableStateFlow(false)
    val isHosting: StateFlow<Boolean> = _isHosting.asStateFlow()

    private val listenMutex = Mutex()
    private var serverSocket: SSLServerSocket? = null
    private var acceptJob: Job? = null
    private var clientJob: Job? = null
    private var clientSocket: SSLSocket? = null
    private var networkJob: Job? = null
    private var addressesJob: Job? = null
    private var activeAddressesJob: Job? = null
    private var noticeJob: Job? = null
    private var publicFetchJob: Job? = null
    private var approvalDecision: ((Boolean) -> Unit)? = null
    @Volatile private var hosting = false

    val deviceName: String get() = peers.deviceName()
    val myCode: String get() = try { identity.code() } catch (_: Exception) { "------" }

    /** Start hosting: detect addresses, bind the listener when an
     * endpoint exists, and watch the network for changes. Safe to
     * call twice; the second call is a no-op. */
    fun startHosting() {
        if (hosting) return
        hosting = true
        _isHosting.value = true
        DiagLog.log("host", "startHosting")
        _uiState.update { it.copy(myCode = myCode) }
        TransferService.hostingActive = true
        try {
            ContextCompat.startForegroundService(
                context,
                Intent(context, TransferService::class.java)
                    .setAction(TransferService.ACTION_HOSTING)
            )
        } catch (e: Exception) {
            DiagLog.log("host", "startForegroundService FAILED: ${e::class.simpleName}: ${e.message}")
        }
        networkJob?.cancel()
        networkJob = scope.launch {
            networkMonitor.transport.collect { recompute(showNotice = true, restartListener = true) }
        }
        addressesJob?.cancel()
        addressesJob = scope.launch {
            networkMonitor.localAddresses.collect {
                if (hosting) recompute(showNotice = true, restartListener = true)
            }
        }
        activeAddressesJob?.cancel()
        activeAddressesJob = scope.launch {
            networkMonitor.activeNetworkAddresses.collect {
                if (hosting) recompute(showNotice = true, restartListener = true)
            }
        }
        scope.launch { recompute(showNotice = false, restartListener = true) }
    }

    /** Stop hosting: end listening, drop any pending approval, stop
     * the foreground service unless a session is connected (the
     * service then stays for the session). */
    fun stopHosting() {
        if (!hosting) return
        hosting = false
        _isHosting.value = false
        DiagLog.log("host", "stopHosting")
        approvalDecision?.invoke(false)
        approvalDecision = null
        _approval.value = null
        networkJob?.cancel()
        networkJob = null
        addressesJob?.cancel()
        addressesJob = null
        activeAddressesJob?.cancel()
        activeAddressesJob = null
        noticeJob?.cancel()
        publicFetchJob?.cancel()
        TransferService.hostingActive = false
        scope.launch {
            listenMutex.withLock {
                acceptJob?.cancel()
                acceptJob = null
                clientJob?.cancel()
                clientJob = null
                try { clientSocket?.close() } catch (_: Exception) {}
                clientSocket = null
                closeListenerLocked()
            }
            if (sessionConnection.value != ConnectionState.CONNECTED) {
                try { context.stopService(Intent(context, TransferService::class.java)) } catch (_: Exception) {}
            }
            _uiState.update { it.copy(isListening = false, showNetworkChanged = false) }
        }
    }

    /** Advanced open/closed. Opening on Wi-Fi starts the public IPv4
     * lookup (plain HTTPS, never STUN); closing keeps the listener
     * running. */
    fun setAdvancedOpen(open: Boolean) {
        _uiState.update { it.copy(advancedOpen = open) }
        if (open) fetchPublicIPv4()
    }

    fun backToAddresses() = setAdvancedOpen(false)

    /** The verification sheet's answer for the pending client. */
    fun approveConnection(allow: Boolean) {
        DiagLog.log("host", "approveConnection allow=$allow")
        approvalDecision?.invoke(allow)
        approvalDecision = null
    }

    /** Re-detect addresses and restart listening as needed. Shows the
     * "Network changed" notice briefly when an offered endpoint
     * actually changed. */
    private suspend fun recompute(showNotice: Boolean, restartListener: Boolean) {
        val transport = networkMonitor.transport.value
        // Displayed addresses come from the active network first;
        // the all-interfaces list is only the fallback when the
        // active network's properties are unavailable.
        val addresses = selectHostAddresses(
            networkMonitor.activeNetworkAddresses.value,
            networkMonitor.localAddresses.value,
            transport
        )
        val phase = hostPhase(transport, addresses)
        val prev = _uiState.value
        val endpointChanged = prev.globalIPv6 != addresses.globalIPv6 ||
            prev.lanIPv4 != addresses.lanIPv4 || prev.phase != phase
        _uiState.update {
            it.copy(
                phase = phase,
                globalIPv6 = addresses.globalIPv6,
                lanIPv4 = addresses.lanIPv4,
                // Advanced is Wi-Fi only: leaving Wi-Fi closes it.
                advancedOpen = if (transport == NetworkTransport.WIFI) it.advancedOpen else false,
                publicIPv4 = if (transport == NetworkTransport.WIFI) it.publicIPv4 else null
            )
        }
        if (showNotice && endpointChanged && hosting) {
            noticeJob?.cancel()
            noticeJob = scope.launch {
                _uiState.update { it.copy(showNetworkChanged = true) }
                delay(NETWORK_CHANGED_NOTICE_MS)
                _uiState.update { it.copy(showNetworkChanged = false) }
            }
        }
        listenMutex.withLock {
            if (!hosting) return
            if (phase == HostPhase.WAITING) {
                if (restartListener && (endpointChanged || serverSocket == null)) {
                    closeListenerLocked()
                    startListenerLocked()
                } else if (serverSocket == null) {
                    startListenerLocked()
                }
            } else {
                closeListenerLocked()
            }
        }
        _uiState.update { it.copy(isListening = serverSocket != null) }
    }

    private fun closeListenerLocked() {
        acceptJob?.cancel()
        acceptJob = null
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
    }

    /** Bind the dual-stack listener: "::" first, IPv4-only fallback.
     * Mirrors PC v1.3.4. */
    private fun startListenerLocked() {
        val sslContext = try {
            buildServerSslContext()
        } catch (e: Exception) {
            DiagLog.log("host", "TLS context FAILED: ${e::class.simpleName}: ${e.message}")
            return
        }
        val factory = sslContext.serverSocketFactory
        val bound: SSLServerSocket? = try {
            factory.createServerSocket(SESSION_PORT, 50, InetAddress.getByName("::")) as SSLServerSocket
        } catch (e: Exception) {
            DiagLog.log("host", "dual-stack bind FAILED (${e::class.simpleName}), trying IPv4-only")
            try {
                factory.createServerSocket(SESSION_PORT, 50, InetAddress.getByName("0.0.0.0")) as SSLServerSocket
            } catch (e2: Exception) {
                DiagLog.log("host", "IPv4 bind FAILED: ${e2::class.simpleName}: ${e2.message}")
                null
            }
        }
        val server = bound ?: return
        try {
            server.soTimeout = 1000
        } catch (_: Exception) {}
        serverSocket = server
        try {
            DiagLog.log(
                "host",
                "listening on ${server.inetAddress?.hostAddress}:$SESSION_PORT " +
                    "cert=${identity.fingerprint()} " +
                    "protocols=${server.enabledProtocols.joinToString(",")} " +
                    "ciphers=${server.enabledCipherSuites.joinToString(",")}"
            )
        } catch (_: Exception) {}
        acceptJob = scope.launch { acceptLoop(server) }
    }

    /** Accept loop. Pauses while a session is connected (one client
     * at a time); resumes when it ends. The 1s accept timeout is
     * what lets the loop notice Stop and session changes. */
    private suspend fun acceptLoop(server: SSLServerSocket) {
        while (hosting) {
            if (sessionConnection.value == ConnectionState.CONNECTED) {
                delay(500)
                continue
            }
            val accepted: SSLSocket? = try {
                server.accept() as? SSLSocket
            } catch (e: SocketTimeoutException) {
                null
            } catch (e: Exception) {
                if (hosting) DiagLog.log("host", "accept FAILED: ${e::class.simpleName}: ${e.message}")
                null
            }
            if (accepted == null) {
                if (server.isClosed) return
                continue
            }
            // One client at a time: a second client while one is
            // already being verified is turned away, never queued
            // behind the approval sheet.
            if (clientJob?.isActive == true) {
                try { accepted.close() } catch (_: Exception) {}
                continue
            }
            clientJob = scope.launch { handleClient(accepted) }
        }
    }

    /** One incoming client: hello, fingerprint, challenge, proof,
     * approval, hello_ok, handoff. An empty fingerprint or a bad
     * signature aborts before the approval sheet ever appears. */
    private suspend fun handleClient(tls: SSLSocket) {
        clientSocket = tls
        try {
            tls.soTimeout = 30_000
            tls.keepAlive = true
            tls.startHandshake()
            try {
                DiagLog.log("host", "handshake ok protocol=${tls.session.protocol} cipher=${tls.session.cipherSuite}")
            } catch (_: Exception) {}
            val input = tls.inputStream
            val out = tls.outputStream
            val hello = try {
                JSONObject(String(readHeaderBytes(input)))
            } catch (e: Exception) {
                DiagLog.log("host", "hello read FAILED: ${e::class.simpleName}")
                return
            }
            if (hello.optString("type") != "hello") {
                DiagLog.log("host", "first message was not hello")
                return
            }
            val peerName = hello.optString("device").ifBlank { "Unknown device" }
            val clientDer = try {
                AndroidBase64.decode(hello.optString("cert"), AndroidBase64.DEFAULT)
            } catch (_: Exception) {
                null
            }
            val fingerprint = clientDer?.let {
                try { certFingerprint(it) } catch (_: Exception) { "" }
            } ?: ""
            if (fingerprint.isEmpty() || clientDer == null) {
                DiagLog.log("host", "empty fingerprint, aborting before approval")
                return
            }
            // Trust, keyed by fingerprint. CHANGED means a known name
            // presented a different fingerprint.
            val trusted = peers.loadTrustedClients()
            val saved = peers.loadPeers()
            val status: TrustStatus
            var oldFp = ""
            var oldCode: String? = null
            when {
                trusted.any { it.fingerprint == fingerprint } -> { status = TrustStatus.KNOWN }
                trusted.firstOrNull { it.name == peerName }?.let { oldFp = it.fingerprint; oldCode = derive6DigitCode(it.fingerprint); true } == true -> { status = TrustStatus.CHANGED }
                saved.firstOrNull { it.name == peerName }?.let { oldFp = it.fingerprint; oldCode = derive6DigitCode(it.fingerprint); true } == true -> { status = TrustStatus.CHANGED }
                else -> { status = TrustStatus.NEW }
            }
            // Signed hello challenge: 32-byte nonce, client signs it.
            val nonce = ByteArray(32).also { SecureRandom().nextBytes(it) }
            val challenge = JSONObject().apply {
                put("type", "hello_challenge")
                put("nonce", AndroidBase64.encodeToString(nonce, AndroidBase64.NO_WRAP))
            }
            writeHeader(out, challenge.toString().toByteArray())
            val proof = try {
                JSONObject(String(readHeaderBytes(input)))
            } catch (e: Exception) {
                DiagLog.log("host", "proof read FAILED: ${e::class.simpleName}")
                return
            }
            if (proof.optString("type") != "hello_proof") {
                DiagLog.log("host", "expected hello_proof, got ${proof.optString("type")}")
                return
            }
            val signature = try {
                AndroidBase64.decode(proof.optString("signature"), AndroidBase64.DEFAULT)
            } catch (_: Exception) {
                null
            }
            if (signature == null || !identity.verifyNonceSignature(clientDer, nonce, signature)) {
                DiagLog.log("host", "bad signature, aborting before approval")
                return
            }
            // Verified: show the existing verification sheet. A
            // decline returns the host to waiting; the socket closes.
            val decision = suspendCancellableCoroutine<Boolean> { cont ->
                approvalDecision = { allow -> if (cont.isActive) cont.resume(allow) {} }
                _approval.value = ApprovalRequest(
                    host = peerName,
                    code = derive6DigitCode(fingerprint),
                    fullFingerprint = fingerprint,
                    trustStatus = status,
                    oldCode = oldCode,
                    oldFingerprint = oldFp.ifBlank { null }
                )
            }
            _approval.value = null
            if (!decision) {
                DiagLog.log("host", "client declined")
                return
            }
            peers.saveTrustedClient(fingerprint, peerName)
            val ok = JSONObject().apply {
                put("type", "hello_ok")
                put("device", deviceName)
            }
            writeHeader(out, ok.toString().toByteArray())
            val peerHost = tls.inetAddress?.hostAddress?.let { compressIPv6Address(it) } ?: ""
            DiagLog.log("host", "client verified peer=$peerName host=$peerHost status=$status")
            clientSocket = null
            onSessionAccepted(tls, peerName, fingerprint, peerHost)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            DiagLog.log("host", "handleClient FAILED: ${exceptionChain(e)}${sessionInfo(tls)}")
        } finally {
            if (clientSocket === tls) {
                try { tls.close() } catch (_: Exception) {}
                clientSocket = null
            }
        }
    }

    /** TLS server context from the phone's own identity. The client
     * certificate arrives inside hello, not via TLS client auth, so
     * no client-auth TrustManager is needed here. The presented
     * certificate is the IdentityManager cert (identity.crt), so the
     * fingerprint the laptop computes matches the phone's My code.
     * The private key is the software key from IdentityManager —
     * never an AndroidKeyStore key (see its class comment). */
    private fun buildServerSslContext(): SSLContext {
        return createHostSslContext(identity.privateKey(), identityCertificate())
    }

    /** The identity certificate, parsed from identity.crt. */
    private fun identityCertificate(): X509Certificate {
        val cf = CertificateFactory.getInstance("X.509")
        return cf.generateCertificate(identity.certDer().inputStream()) as X509Certificate
    }

    /** Public IPv4 for Advanced (Wi-Fi only): plain HTTPS IP lookup,
     * never STUN. A lookup failure leaves the card unavailable. */
    private fun fetchPublicIPv4() {
        if (_uiState.value.publicIPv4 != null || _uiState.value.publicIPv4Loading) return
        if (networkMonitor.transport.value != NetworkTransport.WIFI) return
        publicFetchJob?.cancel()
        publicFetchJob = scope.launch {
            _uiState.update { it.copy(publicIPv4Loading = true) }
            val ip = fetchPublicIPv4Address()
            _uiState.update { it.copy(publicIPv4 = ip, publicIPv4Loading = false) }
            if (ip != null) DiagLog.log("host", "public IPv4=$ip") else DiagLog.log("host", "public IPv4 lookup failed")
        }
    }

    private fun fetchPublicIPv4Address(): String? {
        for (endpoint in listOf("https://api.ipify.org", "https://ipv4.icanhazip.com")) {
            try {
                val connection = URL(endpoint).openConnection() as HttpsURLConnection
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.setRequestProperty("User-Agent", "Riptide")
                val text = connection.inputStream.bufferedReader().use { it.readText() }.trim()
                if (isValidIPv4(text)) return text
            } catch (_: Exception) {
                // Try the next endpoint.
            }
        }
        return null
    }
}
