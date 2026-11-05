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

data class SavedPeerRecord(val name: String, val host: String, val fingerprint: String)

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
                    SavedPeerRecord(o.optString("name"), o.optString("host"), o.optString("fingerprint"))
                }
            } catch (_: Exception) { emptyList() }
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
        list.forEach { arr.put(JSONObject().apply { put("name", it.name); put("host", it.host); put("fingerprint", it.fingerprint) }) }
        context.dataStore.edit { it[peersKey] = arr.toString() }
    }
}
