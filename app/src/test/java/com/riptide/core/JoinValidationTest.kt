package com.riptide.core

import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Join validation (J1-J9), deep links, candidate order, and host
 * address selection (C1-C6): the pure logic behind v0.9. Every
 * message asserted here is the exact pack copy, so a copy change
 * fails loudly instead of shipping silently.
 */
class JoinValidationTest {
    private val both = DeviceCapability(hasIPv4 = true, hasIPv6 = true)
    private val v4Only = DeviceCapability(hasIPv4 = true, hasIPv6 = false)
    private val v6Only = DeviceCapability(hasIPv4 = false, hasIPv6 = true)
    private val offline = DeviceCapability(hasIPv4 = false, hasIPv6 = false, isOffline = true)

    // J1: capability line.
    @Test
    fun capabilityLabels() {
        assertEquals("This device: IPv4 + IPv6", both.label())
        assertEquals("This device: IPv4 only", v4Only.label())
        assertEquals("This device: IPv6 only", v6Only.label())
        assertEquals("No network. Connect and retry.", offline.label())
        assertEquals("No network. Connect and retry.", DeviceCapability(false, false).label())
    }

    // Blank / whitespace: no connection attempt, blank warning owns it.
    @Test
    fun blankInput() {
        assertEquals(JoinValidation.Blank, parseJoinInput("", both))
        assertEquals(JoinValidation.Blank, parseJoinInput("   ", both))
        assertEquals(JoinValidation.Blank, parseJoinInput("user@", both))
        assertEquals(JoinValidation.Blank, parseJoinInput("  @  ", both))
    }

    // user@host strips the user part.
    @Test
    fun userAtHost() {
        val v = parseJoinInput("user@192.168.1.20", both)
        assertEquals(JoinValidation.Valid("192.168.1.20", AddressFamily.IPV4), v)
        val h = parseJoinInput("user@pc.local", both)
        assertTrue(h is JoinValidation.Valid && (h as JoinValidation.Valid).isHostname)
    }

    // IPv4 literals.
    @Test
    fun ipv4Valid() {
        assertEquals(
            JoinValidation.Valid("192.168.1.20", AddressFamily.IPV4),
            parseJoinInput("192.168.1.20", both)
        )
        assertEquals(
            JoinValidation.Valid("203.0.113.10", AddressFamily.IPV4),
            parseJoinInput("203.0.113.10:50000", both)
        )
    }

    @Test
    fun ipv4WrongPortIsMalformed() {
        assertEquals(JoinValidation.Malformed, parseJoinInput("192.168.1.20:1234", both))
        assertEquals(JoinValidation.Malformed, parseJoinInput("192.168.1.20:abc", both))
        assertEquals(JoinValidation.Malformed, parseJoinInput("192.168.1.20:", both))
    }

    // J3: unsupported IPv4.
    @Test
    fun ipv4Unsupported() {
        val v = parseJoinInput("192.168.1.20", v6Only)
        assertEquals(JoinValidation.Unsupported(AddressFamily.IPV4), v)
        assertEquals("This address is IPv4, but this device has no IPv4.", v.errorMessage())
    }

    // J4: malformed IPv4 attempts and garbage.
    @Test
    fun malformedInputs() {
        assertEquals(JoinValidation.Malformed, parseJoinInput("999.1.1.1", both))
        assertEquals(JoinValidation.Malformed, parseJoinInput("1.2.3", both))
        assertEquals(JoinValidation.Malformed, parseJoinInput("1.2.3.4.5", both))
        assertEquals(JoinValidation.Malformed, parseJoinInput("exa mple.com", both))
        assertEquals(JoinValidation.Malformed, parseJoinInput("-bad.com", both))
        assertEquals(JoinValidation.Malformed, parseJoinInput("bad..com", both))
        assertEquals(JoinValidation.Malformed, parseJoinInput("http://192.168.1.20", both))
        val v = parseJoinInput("!!!", both)
        assertEquals(JoinValidation.Malformed, v)
        assertEquals("That doesn’t look like an IP address or hostname.", v.errorMessage())
    }

    // Hostnames are allowed (J5); the family is unknown until DNS.
    @Test
    fun hostnameValid() {
        val v = parseJoinInput("metalvoid.ddns.net", both)
        assertTrue(v is JoinValidation.Valid)
        assertTrue((v as JoinValidation.Valid).isHostname)
        assertNull(v.family)
        assertEquals(JoinValidation.Valid("pc.local", isHostname = true), parseJoinInput("pc.local:50000", both))
        assertEquals(JoinValidation.Malformed, parseJoinInput("pc.local:80", both))
    }

    // Bare and bracketed IPv6.
    @Test
    fun ipv6BareAndBracketed() {
        assertEquals(
            JoinValidation.Valid("2001:db8::1", AddressFamily.IPV6),
            parseJoinInput("2001:db8::1", both)
        )
        assertEquals(
            JoinValidation.Valid("2001:db8::1", AddressFamily.IPV6),
            parseJoinInput("[2001:db8::1]", both)
        )
        assertEquals(
            JoinValidation.Valid("2001:db8::1", AddressFamily.IPV6),
            parseJoinInput("[2001:db8::1]:50000", both)
        )
        assertEquals(JoinValidation.Malformed, parseJoinInput("[2001:db8::1]:80", both))
        assertEquals(JoinValidation.Malformed, parseJoinInput("[2001:db8::1", both))
        assertEquals(JoinValidation.Malformed, parseJoinInput("[2001:db8::1]x", both))
    }

    // A valid bare IPv6 literal is parsed as a whole before any port
    // suffix is considered: a valid group wins over a port reading.
    @Test
    fun bareIpv6ParsedAsWhole() {
        assertEquals(
            JoinValidation.Valid("2001:db8::1:c350", AddressFamily.IPV6),
            parseJoinInput("2001:db8::1:c350", both)
        )
        assertEquals(
            JoinValidation.Valid("2001:db8::1:80", AddressFamily.IPV6),
            parseJoinInput("2001:db8::1:80", both)
        )
        // 50000 is five hex digits, not a group: the whole string is
        // not a literal, so the trailing :50000 is a port suffix.
        assertEquals(
            JoinValidation.Valid("2001:db8::1", AddressFamily.IPV6),
            parseJoinInput("2001:db8::1:50000", both)
        )
        // 8080 is a valid four-hex-digit group, so the whole string is
        // the literal, not host + port. A five-digit suffix cannot be
        // a group, so it falls to the port reading instead.
        assertEquals(
            JoinValidation.Valid("2001:db8::1:8080", AddressFamily.IPV6),
            parseJoinInput("2001:db8::1:8080", both)
        )
        assertEquals(JoinValidation.Malformed, parseJoinInput("2001:db8::1:50000x", both))
    }

    // IPv6 compression: the expanded form InetAddress returns is
    // displayable as the compressed form everywhere it surfaces.
    @Test
    fun ipv6Compression() {
        assertEquals("2001:db8::1", compressIPv6Address("2001:db8:0:0:0:0:0:1"))
        assertEquals("::1", compressIPv6Address("0:0:0:0:0:0:0:1"))
        assertEquals("::", compressIPv6Address("0:0:0:0:0:0:0:0"))
        assertEquals("2001:db8::1", compressIPv6Address("2001:db8::1"))
        assertEquals("2001:db8:0:1::1", compressIPv6Address("2001:db8:0:1:0:0:0:1"))
        assertEquals("2001:db8::1:0:0:1", compressIPv6Address("2001:db8:0:0:1:0:0:1"))
        assertEquals("1:2:3:4:5:6:7:8", compressIPv6Address("1:2:3:4:5:6:7:8"))
        assertEquals("fe80::1", compressIPv6Address("fe80:0:0:0:0:0:0:1"))
    }

    // J2: unsupported IPv6.
    @Test
    fun ipv6Unsupported() {
        val v = parseJoinInput("2001:db8::1", v4Only)
        assertEquals(JoinValidation.Unsupported(AddressFamily.IPV6), v)
        assertEquals("This address is IPv6, but this device has no IPv6.", v.errorMessage())
        assertEquals(
            JoinValidation.Unsupported(AddressFamily.IPV6),
            parseJoinInput("[2001:db8::1]:50000", v4Only)
        )
    }

    // Offline: no attempt, whatever the input.
    @Test
    fun offlineDisablesAttempts() {
        assertEquals(JoinValidation.Offline, parseJoinInput("192.168.1.20", offline))
        assertEquals(JoinValidation.Offline, parseJoinInput("2001:db8::1", offline))
        assertEquals(JoinValidation.Offline, parseJoinInput("pc.local", offline))
        assertEquals("No network. Connect and retry.", JoinValidation.Offline.errorMessage())
        assertNull(JoinValidation.Valid("192.168.1.20", AddressFamily.IPV4).errorMessage())
        assertNull(JoinValidation.Blank.errorMessage())
    }

    // IPv6 literal parsing: valid and invalid forms.
    @Test
    fun ipv6LiteralForms() {
        assertTrue(isValidIPv6("::1"))
        assertTrue(isValidIPv6("2001:db8::1"))
        assertTrue(isValidIPv6("2001:db8:0:0:0:0:0:1"))
        assertTrue(isValidIPv6("1:2:3:4:5:6:7:8"))
        assertTrue(isValidIPv6("::ffff:192.168.1.1"))
        assertTrue(isValidIPv6("fe80::1"))
        assertFalse(isValidIPv6("1:2:3:4:5:6:7:8:9"))
        assertFalse(isValidIPv6("2001:db8:::1"))
        assertFalse(isValidIPv6("2001:db8::1::2"))
        assertFalse(isValidIPv6("gggg::1"))
        assertFalse(isValidIPv6("12345::1"))
        assertFalse(isValidIPv6("2001:db8::999.1.1.1"))
        assertFalse(isValidIPv6("2001:db8"))
        assertFalse(isValidIPv6(""))
    }

    @Test
    fun ipv4LiteralForms() {
        assertTrue(isValidIPv4("192.168.1.20"))
        assertTrue(isValidIPv4("0.0.0.0"))
        assertTrue(isValidIPv4("255.255.255.255"))
        assertFalse(isValidIPv4("256.1.1.1"))
        assertFalse(isValidIPv4("1.2.3"))
        assertFalse(isValidIPv4("1.2.3.4.5"))
        assertFalse(isValidIPv4("1.2.3.x"))
        assertFalse(isValidIPv4("1234.1.1.1"))
    }

    // Global IPv6 excludes link-local, ULA, loopback, multicast.
    @Test
    fun globalIpv6Classification() {
        assertTrue(isGlobalIPv6(InetAddress.getByName("2001:db8::1")))
        assertTrue(isGlobalIPv6(InetAddress.getByName("2401:db00::1")))
        assertFalse(isGlobalIPv6(InetAddress.getByName("fe80::1")))
        assertFalse(isGlobalIPv6(InetAddress.getByName("fc00::1")))
        assertFalse(isGlobalIPv6(InetAddress.getByName("fd12:3456::1")))
        assertFalse(isGlobalIPv6(InetAddress.getByName("::1")))
        assertFalse(isGlobalIPv6(InetAddress.getByName("ff02::1")))
        assertFalse(isGlobalIPv6(InetAddress.getByName("192.168.1.20")))
    }

    @Test
    fun lanIpv4Classification() {
        assertTrue(isLanIPv4(InetAddress.getByName("192.168.1.20")))
        assertTrue(isLanIPv4(InetAddress.getByName("10.0.0.5")))
        assertTrue(isLanIPv4(InetAddress.getByName("172.16.0.1")))
        assertFalse(isLanIPv4(InetAddress.getByName("8.8.8.8")))
        assertFalse(isLanIPv4(InetAddress.getByName("127.0.0.1")))
        assertFalse(isLanIPv4(InetAddress.getByName("2001:db8::1")))
    }

    // Host address selection per network state (C1-C4, C6).
    @Test
    fun hostAddressSelection() {
        val v6 = InetAddress.getByName("2001:db8::1")
        val v4 = InetAddress.getByName("192.168.1.20")
        val pub = InetAddress.getByName("203.0.113.10")

        // C3: Wi-Fi with IPv6 offers both.
        assertEquals(HostAddresses("2001:db8::1", "192.168.1.20"), selectHostAddresses(listOf(v6, v4), NetworkTransport.WIFI))
        // C4: Wi-Fi IPv4-only offers LAN IPv4.
        assertEquals(HostAddresses(null, "192.168.1.20"), selectHostAddresses(listOf(v4), NetworkTransport.WIFI))
        // C1: mobile data offers global IPv6 only, never LAN IPv4.
        assertEquals(HostAddresses("2001:db8::1", null), selectHostAddresses(listOf(v6, v4), NetworkTransport.CELLULAR))
        // C2: mobile IPv4-only offers nothing.
        assertEquals(HostAddresses(null, null), selectHostAddresses(listOf(v4), NetworkTransport.CELLULAR))
        // A public IPv4 is never offered as a regular address.
        assertEquals(HostAddresses(null, null), selectHostAddresses(listOf(pub), NetworkTransport.WIFI))
        // Link-local and ULA are not global IPv6.
        assertEquals(
            HostAddresses(null, "192.168.1.20"),
            selectHostAddresses(listOf(InetAddress.getByName("fe80::1"), InetAddress.getByName("fd00::1"), v4), NetworkTransport.WIFI)
        )
        // C6: offline offers nothing.
        assertEquals(HostAddresses(null, null), selectHostAddresses(listOf(v6, v4), NetworkTransport.NONE))
    }

    @Test
    fun hostPhases() {
        assertEquals(HostPhase.WAITING, hostPhase(NetworkTransport.WIFI, HostAddresses("2001:db8::1", "192.168.1.20")))
        assertEquals(HostPhase.WAITING, hostPhase(NetworkTransport.WIFI, HostAddresses(null, "192.168.1.20")))
        assertEquals(HostPhase.WAITING, hostPhase(NetworkTransport.CELLULAR, HostAddresses("2001:db8::1", null)))
        assertEquals(HostPhase.MOBILE_V4_ONLY, hostPhase(NetworkTransport.CELLULAR, HostAddresses(null, null)))
        assertEquals(HostPhase.OFFLINE, hostPhase(NetworkTransport.NONE, HostAddresses(null, null)))
        assertEquals(HostPhase.OFFLINE, hostPhase(NetworkTransport.NONE, HostAddresses("2001:db8::1", "192.168.1.20")))
    }

    // Try order: global IPv6, LAN IPv4, public IPv4.
    @Test
    fun candidateOrder() {
        assertEquals(
            listOf(
                DialCandidate("2001:db8::1", AddressFamily.IPV6, CandidateKind.IPV6),
                DialCandidate("192.168.1.20", AddressFamily.IPV4, CandidateKind.LAN_IPV4),
                DialCandidate("203.0.113.10", AddressFamily.IPV4, CandidateKind.PUBLIC_IPV4)
            ),
            orderCandidates("2001:db8::1", "192.168.1.20", "203.0.113.10")
        )
        assertEquals(
            listOf(DialCandidate("192.168.1.20", AddressFamily.IPV4, CandidateKind.LAN_IPV4)),
            orderCandidates(null, "192.168.1.20", null)
        )
        assertEquals(emptyList<DialCandidate>(), orderCandidates(null, null, null))
        assertEquals("Trying IPv6…", candidateLabel(CandidateKind.IPV6))
        assertEquals("Trying LAN IPv4…", candidateLabel(CandidateKind.LAN_IPV4))
        assertEquals("Trying public IPv4…", candidateLabel(CandidateKind.PUBLIC_IPV4))
    }

    // J5: resolved hostnames keep supported families in try order.
    @Test
    fun resolvedClassification() {
        val v6 = InetAddress.getByName("2001:db8::1")
        val lan = InetAddress.getByName("192.168.1.20")
        val pub = InetAddress.getByName("203.0.113.10")
        val supported = classifyResolved(listOf(pub, lan, v6), both)
        assertTrue(supported is ResolvedCandidates.Supported)
        assertEquals(
            listOf("2001:db8::1", "192.168.1.20", "203.0.113.10"),
            (supported as ResolvedCandidates.Supported).candidates.map { it.host }
        )
        // All resolved IPv6 on an IPv4-only device: J2, before dialing.
        assertEquals(
            ResolvedCandidates.Unsupported(AddressFamily.IPV6),
            classifyResolved(listOf(v6), v4Only)
        )
        // All resolved IPv4 on an IPv6-only device: J3.
        assertEquals(
            ResolvedCandidates.Unsupported(AddressFamily.IPV4),
            classifyResolved(listOf(lan, pub), v6Only)
        )
        // Link-local v6 does not count as a supported candidate.
        assertEquals(
            ResolvedCandidates.Unsupported(AddressFamily.IPV6),
            classifyResolved(listOf(InetAddress.getByName("fe80::1")), both)
        )
        assertEquals(ResolvedCandidates.Empty, classifyResolved(emptyList(), both))
    }

    // Deep links: the three pack examples, plus name and bad ports.
    @Test
    fun deepLinkParsing() {
        assertEquals(
            JoinLink("2001:db8::1", 50000, null),
            parseJoinLink("riptide://join?host=[2001:db8::1]&port=50000")
        )
        assertEquals(
            JoinLink("192.168.1.20", 50000, null),
            parseJoinLink("riptide://join?host=192.168.1.20&port=50000")
        )
        assertEquals(
            JoinLink("203.0.113.10", 50000, null),
            parseJoinLink("riptide://join?host=203.0.113.10&port=50000")
        )
        assertEquals(
            JoinLink("192.168.1.20", 50000, "Mohak's Phone"),
            parseJoinLink("riptide://join?host=192.168.1.20&port=50000&name=Mohak%27s+Phone")
        )
        // Port defaults to 50000 when omitted.
        assertEquals(
            JoinLink("192.168.1.20", 50000, null),
            parseJoinLink("riptide://join?host=192.168.1.20")
        )
        // An explicit port other than 50000 is rejected.
        assertNull(parseJoinLink("riptide://join?host=192.168.1.20&port=80"))
        // Anything else is not a join link.
        assertNull(parseJoinLink("https://join?host=192.168.1.20&port=50000"))
        assertNull(parseJoinLink("riptide://create?host=192.168.1.20"))
        assertNull(parseJoinLink("riptide://join?port=50000"))
        assertNull(parseJoinLink("riptide://join?host="))
        assertNull(parseJoinLink(""))
    }

    // Share links: bracketed IPv6, plain IPv4, optional device name.
    @Test
    fun shareLinkBuilding() {
        assertEquals(
            "riptide://join?host=[2001:db8::1]&port=50000",
            buildJoinLink("2001:db8::1", AddressFamily.IPV6)
        )
        assertEquals(
            "riptide://join?host=192.168.1.20&port=50000",
            buildJoinLink("192.168.1.20", AddressFamily.IPV4)
        )
        assertEquals(
            "riptide://join?host=203.0.113.10&port=50000",
            buildJoinLink("203.0.113.10", AddressFamily.IPV4)
        )
        val withName = buildJoinLink("192.168.1.20", AddressFamily.IPV4, "Mohak's Phone")
        assertEquals(JoinLink("192.168.1.20", 50000, "Mohak's Phone"), parseJoinLink(withName))
        // Round trip: a built link parses back to the same host.
        assertEquals(
            JoinLink("2001:db8::1", 50000, null),
            parseJoinLink(buildJoinLink("2001:db8::1", AddressFamily.IPV6, "  "))
        )
    }

    // Copy: bare address, brackets stripped, port omitted.
    @Test
    fun copyText() {
        assertEquals("2001:db8::1", copyTextFor("[2001:db8::1]"))
        assertEquals("192.168.1.20", copyTextFor("192.168.1.20"))
        assertEquals("203.0.113.10", copyTextFor(" 203.0.113.10 "))
    }
}
