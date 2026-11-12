package com.riptide.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.os.Looper
import com.riptide.core.DiagLog
import java.net.InetAddress
import java.net.NetworkInterface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Live network state for Join validation (J1) and Create Server (C1-C8).
 * Capability is read from the active network's link properties, so it
 * follows the network the device is actually on. The displayed host
 * addresses come from the SAME source: the active network's link
 * properties (activeNetworkAddresses), so a cellular IPv6 never leaks
 * onto the Create screen while Wi-Fi is active. The all-interfaces
 * list (localAddresses) is only a fallback for when the active
 * network's properties are unavailable. A NetworkCallback refreshes
 * all flows when the network changes, so Join re-validates and the
 * host re-detects addresses without any polling. The callback is
 * registered once, on the main thread, and lives for the process
 * lifetime.
 */
class NetworkMonitor(private val context: Context) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _capability = MutableStateFlow(snapshotCapability())
    val capability: StateFlow<DeviceCapability> = _capability.asStateFlow()

    private val _transport = MutableStateFlow(snapshotTransport())
    val transport: StateFlow<NetworkTransport> = _transport.asStateFlow()

    private val _localAddresses = MutableStateFlow(snapshotLocalAddresses())
    val localAddresses: StateFlow<List<InetAddress>> = _localAddresses.asStateFlow()

    private val _activeNetworkAddresses = MutableStateFlow(snapshotActiveNetworkAddresses())
    /** Addresses of the ACTIVE network only (its LinkProperties),
     * or null when the active network or its properties are
     * unavailable. Unlike localAddresses this never carries the
     * cellular interface's IPv6 while Wi-Fi is active; an empty
     * list means the active network genuinely has no addresses. */
    val activeNetworkAddresses: StateFlow<List<InetAddress>?> = _activeNetworkAddresses.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = refresh()
        override fun onLost(network: Network) = refresh()
        override fun onLinkPropertiesChanged(network: Network, properties: LinkProperties) = refresh()
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = refresh()
    }

    init {
        try {
            val request = NetworkRequest.Builder().build()
            connectivityManager.registerNetworkCallback(request, callback, Handler(Looper.getMainLooper()))
        } catch (e: Exception) {
            DiagLog.log("network", "registerNetworkCallback FAILED: ${e::class.simpleName}: ${e.message}")
        }
    }

    /** Re-read all three flows now. Called by the callback on every
     * network change, and by callers that need a fresh read. */
    fun refresh() {
        try {
            _capability.value = snapshotCapability()
            _transport.value = snapshotTransport()
            _localAddresses.value = snapshotLocalAddresses()
            _activeNetworkAddresses.value = snapshotActiveNetworkAddresses()
        } catch (e: Exception) {
            DiagLog.log("network", "refresh FAILED: ${e::class.simpleName}: ${e.message}")
        }
    }

    /** Current capability, read fresh instead of from the flow. */
    fun snapshot(): DeviceCapability = snapshotCapability()

    private fun snapshotCapability(): DeviceCapability {
        val network = connectivityManager.activeNetwork ?: return DeviceCapability(false, false, true)
        val props = try {
            connectivityManager.getLinkProperties(network)
        } catch (_: Exception) {
            null
        }
        val addresses: List<InetAddress> = props?.linkAddresses?.map { it.address }
            ?: snapshotLocalAddresses()
        var hasV4 = false
        var hasV6 = false
        for (address in addresses) {
            if (address.isLoopbackAddress) continue
            when {
                address is java.net.Inet4Address -> hasV4 = true
                isGlobalIPv6(address) -> hasV6 = true
            }
        }
        return DeviceCapability(hasV4, hasV6, false)
    }

    private fun snapshotTransport(): NetworkTransport {
        val network = connectivityManager.activeNetwork ?: return NetworkTransport.NONE
        val caps = try {
            connectivityManager.getNetworkCapabilities(network)
        } catch (_: Exception) {
            null
        }
        return when {
            caps == null -> NetworkTransport.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkTransport.CELLULAR
            else -> NetworkTransport.WIFI
        }
    }

    private fun snapshotLocalAddresses(): List<InetAddress> {
        return try {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .filter { !it.isLoopbackAddress }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Addresses of the active network only, from its
     * LinkProperties. Null when there is no active network or its
     * properties are unavailable (callers fall back to the
     * all-interfaces list then); an empty list means the active
     * network has no addresses, and is trusted as such. */
    private fun snapshotActiveNetworkAddresses(): List<InetAddress>? {
        val network = connectivityManager.activeNetwork ?: return null
        val props = try {
            connectivityManager.getLinkProperties(network)
        } catch (_: Exception) {
            null
        } ?: return null
        return props.linkAddresses.map { it.address }
    }
}
