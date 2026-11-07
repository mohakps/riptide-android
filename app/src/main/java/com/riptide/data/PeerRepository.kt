package com.riptide.data

import android.content.Context
import android.os.Build
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore by preferencesDataStore(name = "riptide")

data class SavedPeerRecord(
    val name: String,
    val host: String,
    val fingerprint: String,
    /** Epoch ms of the last successful connection; 0 = never. Only a
     * successful hello_ok updates this, never a failed attempt. */
    val lastSuccessfulConnection: Long = 0L
)

class PeerRepository(private val context: Context) {
    private val peersKey = stringPreferencesKey("saved_peers")
    private val deviceKey = stringPreferencesKey("device_name")

    fun deviceName(): String {
        return runBlocking {
            context.dataStore.data.first()[deviceKey] ?: (Build.MODEL ?: "Android Phone")
        }
    }

    fun setDeviceName(name: String) {
        runBlocking {
            context.dataStore.edit { it[deviceKey] = name.trim() }
        }
    }

    fun loadPeers(): List<SavedPeerRecord> {
        return runBlocking {
            val raw = context.dataStore.data.first()[peersKey] ?: return@runBlocking emptyList()
            try {
                val arr = JSONArray(raw)
                List(arr.length()) { i ->
                    val o = arr.getJSONObject(i)
                    SavedPeerRecord(
                        o.optString("name"), o.optString("host"), o.optString("fingerprint"),
                        o.optLong("lastSuccessfulConnection", 0L)
                    )
                }
            } catch (_: Exception) { emptyList() }
        }
    }

    /** Record a successful connection for ordering. Failed attempts never call this. */
    fun markConnected(host: String) {
        runBlocking {
            val list = loadPeers().toMutableList()
            val idx = list.indexOfFirst { it.host == host }
            if (idx >= 0) {
                list[idx] = list[idx].copy(lastSuccessfulConnection = System.currentTimeMillis())
                persist(list)
            }
        }
    }

    fun savePeer(peer: SavedPeerRecord) {
        runBlocking {
            val list = loadPeers().toMutableList()
            val idx = list.indexOfFirst { it.host == peer.host }
            if (idx >= 0) list[idx] = peer else list.add(peer)
            persist(list)
        }
    }

    fun forgetPeer(host: String) {
        runBlocking {
            val list = loadPeers().filter { it.host != host }
            persist(list)
        }
    }

    private suspend fun persist(list: List<SavedPeerRecord>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().apply { put("name", it.name); put("host", it.host); put("fingerprint", it.fingerprint); put("lastSuccessfulConnection", it.lastSuccessfulConnection) }) }
        context.dataStore.edit { it[peersKey] = arr.toString() }
    }
}
