package com.riptide.core

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URLDecoder
import java.net.URLEncoder

/** Address family of a literal address. */
enum class AddressFamily { IPV4, IPV6 }

/** What network the device is on. CELLULAR is mobile data; WIFI covers
 * Wi-Fi (and any other non-cellular network with a LAN address). */
enum class NetworkTransport { WIFI, CELLULAR, NONE }

/** Which candidate a Join attempt is currently trying (J7 labels). */
enum class CandidateKind { IPV6, LAN_IPV4, PUBLIC_IPV4 }

/** Live address-family capability of this device (J1). Global IPv6
 * excludes link-local and unique-local addresses. */
data class DeviceCapability(val hasIPv4: Boolean, val hasIPv6: Boolean, val isOffline: Boolean = false) {
    fun label(): String = when {
        isOffline -> "No network. Connect and retry."
        hasIPv4 && hasIPv6 -> "This device: IPv4 + IPv6"
        hasIPv4 -> "This device: IPv4 only"
        hasIPv6 -> "This device: IPv6 only"
        else -> "No network. Connect and retry."
    }
}

/** Result of validating what the user typed, pasted, or opened from a
 * shared link. The same function validates all three, so the rules
 * can never drift between input paths. */
sealed class JoinValidation {
    object Blank : JoinValidation()
    object Offline : JoinValidation()
    data class Valid(val host: String, val family: AddressFamily? = null, val isHostname: Boolean = false) : JoinValidation()
    data class Unsupported(val family: AddressFamily) : JoinValidation()
    object Malformed : JoinValidation()
}

/** User-facing message for a validation failure (J2/J3/J4, offline),
 * or null when the input is usable. */
fun JoinValidation.errorMessage(): String? = when (this) {
    is JoinValidation.Unsupported -> if (family == AddressFamily.IPV6)
        "This address is IPv6, but this device has no IPv6."
    else
        "This address is IPv4, but this device has no IPv4."
    JoinValidation.Malformed -> "That doesn’t look like an IP address or hostname."
    JoinValidation.Offline -> "No network. Connect and retry."
    else -> null
}

/** Validate one Join input against the device's live capability.
 * A valid bare IPv6 literal is parsed as a whole before any port
 * suffix is considered; an explicit port must match 50000. */
fun parseJoinInput(raw: String, capability: DeviceCapability): JoinValidation {
    var text = raw.trim()
    if (text.isEmpty()) return JoinValidation.Blank
    // user@host: the part after the last @ is the address.
    if ("@" in text) {
        text = text.substringAfterLast("@").trim()
        if (text.isEmpty()) return JoinValidation.Blank
    }
    if (capability.isOffline) return JoinValidation.Offline
    // Bracketed IPv6: [2001:db8::1] or [2001:db8::1]:50000.
    if (text.startsWith("[")) {
        val end = text.indexOf(']')
        if (end == -1) return JoinValidation.Malformed
        val inner = text.substring(1, end).trim()
        val rest = text.substring(end + 1)
        if (rest.isNotEmpty()) {
            if (!rest.startsWith(":")) return JoinValidation.Malformed
            val port = rest.substring(1)
            if (port.isEmpty() || !port.all { it.isDigit() } || port.toIntOrNull() != SESSION_PORT) {
                return JoinValidation.Malformed
            }
        }
        if (!isValidIPv6(inner)) return JoinValidation.Malformed
        if (!capability.hasIPv6) return JoinValidation.Unsupported(AddressFamily.IPV6)
        return JoinValidation.Valid(inner, AddressFamily.IPV6)
    }
    val colons = text.count { it == ':' }
    // Two or more colons: a bare IPv6 literal as a whole first, before
    // any port suffix is considered. When the whole string is not a
    // valid literal, a trailing :50000 may still be a port suffix on a
    // valid literal ("2001:db8::1:50000" is host 2001:db8::1, port
    // 50000, because 50000 is five hex digits, not a group).
    if (colons > 1) {
        if (isValidIPv6(text)) {
            if (!capability.hasIPv6) return JoinValidation.Unsupported(AddressFamily.IPV6)
            return JoinValidation.Valid(text, AddressFamily.IPV6)
        }
        val lastColon = text.lastIndexOf(':')
        val portPart = text.substring(lastColon + 1)
        val hostPart = text.substring(0, lastColon)
        if (portPart.all { it.isDigit() } && portPart.toIntOrNull() == SESSION_PORT && isValidIPv6(hostPart)) {
            if (!capability.hasIPv6) return JoinValidation.Unsupported(AddressFamily.IPV6)
            return JoinValidation.Valid(hostPart, AddressFamily.IPV6)
        }
        return JoinValidation.Malformed
    }
    // Exactly one colon: host:port.
    if (colons == 1) {
        val host = text.substringBefore(':').trim()
        val port = text.substringAfter(':').trim()
        if (host.isEmpty()) return JoinValidation.Malformed
        if (port.isEmpty() || !port.all { it.isDigit() } || port.toIntOrNull() != SESSION_PORT) {
            return JoinValidation.Malformed
        }
        return classifyBareHost(host, capability)
    }
    return classifyBareHost(text, capability)
}

/** Classify a host with no brackets and no port. */
private fun classifyBareHost(host: String, capability: DeviceCapability): JoinValidation {
    if (isValidIPv4(host)) {
        if (!capability.hasIPv4) return JoinValidation.Unsupported(AddressFamily.IPV4)
        return JoinValidation.Valid(host, AddressFamily.IPV4)
    }
    // All digits and dots is an IPv4 attempt, not a hostname.
    if (host.all { it.isDigit() || it == '.' }) return JoinValidation.Malformed
    if (isValidHostname(host)) return JoinValidation.Valid(host, isHostname = true)
    return JoinValidation.Malformed
}

/** Four decimal octets, 0-255. */
fun isValidIPv4(text: String): Boolean {
    val parts = text.split('.')
    if (parts.size != 4) return false
    return parts.all { part ->
        part.isNotEmpty() && part.length <= 3 && part.all { it.isDigit() } &&
            part.toIntOrNull()?.let { it in 0..255 } == true
    }
}

/** IPv6 literal, with at most one "::" compression and an optional
 * embedded IPv4 tail. No DNS, no network: pure parsing, so the result
 * is the same on and off the network. */
fun isValidIPv6(text: String): Boolean {
    val s = text.substringBefore('%').trim()
    if (s.isEmpty()) return false
    if (s.count { it == ':' } == 0) return false
    // ":::" and a second "::" are never valid.
    val compressions = s.windowed(2).count { it == "::" }
    if (compressions > 1) return false
    if (":::" in s) return false
    val halves = s.split("::")
    if (halves.size > 2) return false
    fun groups(part: String): List<String>? {
        if (part.isEmpty()) return emptyList()
        return part.split(':')
    }
    val left = groups(halves[0]) ?: return false
    val right = if (halves.size == 2) groups(halves[1]) ?: return false else emptyList()
    val all = left + right
    // An embedded IPv4 tail counts as two groups.
    val hasV4Tail = all.isNotEmpty() && isValidIPv4(all.last())
    val groupCount = all.size + if (hasV4Tail) 1 else 0
    val body = if (hasV4Tail) all.dropLast(1) else all
    if (body.any { it.isEmpty() || it.length > 4 || !it.all { c -> c.isDigit() || c.lowercaseChar() in 'a'..'f' } }) {
        return false
    }
    return if (halves.size == 2) groupCount < 8 else groupCount == 8
}

/** A plausible hostname: dot-separated labels of letters, digits and
 * hyphens, never starting or ending with a hyphen or dot. */
fun isValidHostname(text: String): Boolean {
    if (text.isEmpty() || text.length > 253) return false
    if (text.startsWith('.') || text.endsWith('.') || text.startsWith('-') || text.endsWith('-')) return false
    if (".." in text) return false
    return text.split('.').all { label ->
        label.isNotEmpty() && label.length <= 63 &&
            label.all { it.isLetterOrDigit() || it == '-' } &&
            !label.startsWith('-') && !label.endsWith('-')
    }
}

/** Compress an IPv6 literal to RFC 5952 form ("2001:db8:0:0:0:0:0:1"
 * becomes "2001:db8::1"). InetAddress.hostAddress returns the expanded
 * form on this platform, which is valid but unreadable in the UI and
 * in share links. Already-compressed input passes through unchanged. */
fun compressIPv6Address(text: String): String {
    val s = text.substringBefore('%').trim()
    if ("::" in s) return s
    if (s.count { it == ':' } != 7) return s
    val groups = s.split(':')
    if (groups.size != 8 || groups.any { it.contains('.') }) return s
    val values = groups.map { it.toIntOrNull(16) ?: return s }
    // Longest run of zero groups, at least two, wins; ties go to the first.
    var bestStart = -1
    var bestLen = 0
    var i = 0
    while (i < 8) {
        if (values[i] == 0) {
            var j = i
            while (j < 8 && values[j] == 0) j++
            if (j - i > bestLen) {
                bestLen = j - i
                bestStart = i
            }
            i = j
        } else {
            i++
        }
    }
    if (bestLen < 2) return values.joinToString(":") { it.toString(16) }
    val left = values.subList(0, bestStart).joinToString(":") { it.toString(16) }
    val right = values.subList(bestStart + bestLen, 8).joinToString(":") { it.toString(16) }
    return when {
        left.isEmpty() && right.isEmpty() -> "::"
        left.isEmpty() -> "::$right"
        right.isEmpty() -> "$left::"
        else -> "$left::$right"
    }
}

/** True for a global IPv6 address: not loopback, link-local, site-local
 * (deprecated fec0::/10), unique-local (fc00::/7), or multicast. */
fun isGlobalIPv6(address: InetAddress): Boolean {
    if (address !is Inet6Address) return false
    if (address.isLoopbackAddress || address.isLinkLocalAddress ||
        address.isSiteLocalAddress || address.isMulticastAddress
    ) return false
    val b = address.address
    // Unique-local fc00::/7: first seven bits 1111 110.
    if (b.isNotEmpty() && (b[0].toInt() and 0xFE) == 0xFC) return false
    return true
}

/** True for a LAN IPv4 address (RFC 1918 site-local). */
fun isLanIPv4(address: InetAddress): Boolean {
    return address is Inet4Address && address.isSiteLocalAddress && !address.isLoopbackAddress
}

/** One dial candidate: the literal address to try, its family, and
 * which J7 label it shows while it is being tried. */
data class DialCandidate(val host: String, val family: AddressFamily, val kind: CandidateKind)

fun candidateKindFor(address: InetAddress): CandidateKind = when {
    address is Inet6Address -> CandidateKind.IPV6
    isLanIPv4(address) -> CandidateKind.LAN_IPV4
    else -> CandidateKind.PUBLIC_IPV4
}

fun candidateLabel(kind: CandidateKind): String = when (kind) {
    CandidateKind.IPV6 -> "Trying IPv6…"
    CandidateKind.LAN_IPV4 -> "Trying LAN IPv4…"
    CandidateKind.PUBLIC_IPV4 -> "Trying public IPv4…"
}

/** Try order for several compatible candidates: global IPv6, then LAN
 * IPv4, then public IPv4. First success wins. */
fun orderCandidates(globalIPv6: String?, lanIPv4: String?, publicIPv4: String?): List<DialCandidate> {
    val out = mutableListOf<DialCandidate>()
    if (!globalIPv6.isNullOrBlank()) out += DialCandidate(globalIPv6, AddressFamily.IPV6, CandidateKind.IPV6)
    if (!lanIPv4.isNullOrBlank()) out += DialCandidate(lanIPv4, AddressFamily.IPV4, CandidateKind.LAN_IPV4)
    if (!publicIPv4.isNullOrBlank()) out += DialCandidate(publicIPv4, AddressFamily.IPV4, CandidateKind.PUBLIC_IPV4)
    return out
}

/** Outcome of resolving a hostname (J5): the supported candidates in
 * try order, or the family every resolved address belongs to when
 * none is supported on this device. */
sealed class ResolvedCandidates {
    data class Supported(val candidates: List<DialCandidate>) : ResolvedCandidates()
    data class Unsupported(val family: AddressFamily) : ResolvedCandidates()
    object Empty : ResolvedCandidates()
}

fun classifyResolved(addresses: List<InetAddress>, capability: DeviceCapability): ResolvedCandidates {
    if (addresses.isEmpty()) return ResolvedCandidates.Empty
    val supported = addresses
        .filter {
            (it is Inet6Address && capability.hasIPv6 && isGlobalIPv6(it)) ||
                (it is Inet4Address && capability.hasIPv4)
        }
        .sortedWith(
            compareByDescending<InetAddress> { it is Inet6Address }
                .thenByDescending { isLanIPv4(it) }
        )
        .mapNotNull { addr ->
            val host = addr.hostAddress?.let { compressIPv6Address(it) } ?: return@mapNotNull null
            DialCandidate(host, if (addr is Inet6Address) AddressFamily.IPV6 else AddressFamily.IPV4, candidateKindFor(addr))
        }
        .distinctBy { it.host }
    if (supported.isNotEmpty()) return ResolvedCandidates.Supported(supported)
    val family = if (addresses.any { it is Inet6Address }) AddressFamily.IPV6 else AddressFamily.IPV4
    return ResolvedCandidates.Unsupported(family)
}

/** A shared link: riptide://join?host=<address>&port=50000, with an
 * optional percent-encoded device name. Never carries a fingerprint
 * or a code. */
data class JoinLink(val host: String, val port: Int = SESSION_PORT, val name: String? = null)

/** Parse a deep link. Returns null for anything that is not a
 * riptide://join link, or for an explicit port other than 50000.
 * Parsed by hand, not with java.net.URI: the bracketed IPv6 form
 * is illegal in a URI query string, so URI would reject it. */
fun parseJoinLink(text: String): JoinLink? {
    val t = text.trim()
    val prefix = "riptide://join?"
    if (!t.startsWith(prefix, ignoreCase = true)) return null
    val query = t.substring(prefix.length)
    if (query.isEmpty()) return null
    val params = mutableMapOf<String, String>()
    for (part in query.split('&')) {
        val eq = part.indexOf('=')
        if (eq < 0) continue
        val key = urlDecode(part.substring(0, eq)) ?: continue
        val value = urlDecode(part.substring(eq + 1)) ?: continue
        params[key] = value
    }
    val host = params["host"]?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    // A bracketed IPv6 in the link becomes the bare address: the Join
    // field and the validator take it from there.
    val bareHost = if (host.startsWith("[") && host.endsWith("]")) host.substring(1, host.length - 1) else host
    val port = params["port"]?.toIntOrNull() ?: SESSION_PORT
    if (port != SESSION_PORT) return null
    return JoinLink(bareHost, port, params["name"]?.takeIf { it.isNotEmpty() })
}

private fun urlDecode(s: String): String? = try {
    URLDecoder.decode(s, "UTF-8")
} catch (_: Exception) {
    null
}

/** Build the share link for one address row. IPv6 is bracketed. */
fun buildJoinLink(address: String, family: AddressFamily, deviceName: String? = null): String {
    val hostPart = if (family == AddressFamily.IPV6) "[$address]" else address
    val namePart = deviceName?.trim()?.takeIf { it.isNotEmpty() }
        ?.let { "&name=" + URLEncoder.encode(it, "UTF-8") } ?: ""
    return "riptide://join?host=$hostPart&port=$SESSION_PORT$namePart"
}

/** Copy writes the bare address: brackets stripped, port omitted. */
fun copyTextFor(address: String): String =
    address.trim().removePrefix("[").removeSuffix("]")

/** Addresses this phone can offer as a host (C1-C4). Mobile data
 * offers global IPv6 only; Wi-Fi offers global IPv6 plus LAN IPv4.
 * Public IPv4 is a separate Advanced lookup, never part of this. */
data class HostAddresses(val globalIPv6: String?, val lanIPv4: String?)

fun selectHostAddresses(addresses: List<InetAddress>, transport: NetworkTransport): HostAddresses {
    if (transport == NetworkTransport.NONE) return HostAddresses(null, null)
    val v6 = addresses.firstOrNull { isGlobalIPv6(it) }?.hostAddress?.let { compressIPv6Address(it) }
    val v4 = if (transport == NetworkTransport.WIFI) {
        addresses.firstOrNull { isLanIPv4(it) }?.hostAddress
    } else {
        null
    }
    return HostAddresses(v6, v4)
}

/** Which host state the current network is in (C1/C2/C3/C4/C6). */
enum class HostPhase { WAITING, MOBILE_V4_ONLY, OFFLINE }

fun hostPhase(transport: NetworkTransport, addresses: HostAddresses): HostPhase = when {
    transport == NetworkTransport.NONE -> HostPhase.OFFLINE
    transport == NetworkTransport.CELLULAR && addresses.globalIPv6 == null -> HostPhase.MOBILE_V4_ONLY
    else -> HostPhase.WAITING
}
