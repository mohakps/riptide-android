package com.riptide.data

import com.riptide.ui.model.ReceivedFileRecord
import java.io.File
import java.util.Base64

/**
 * Durable record of successfully received files, surviving app restarts.
 *
 * Storage is one record per line in `received_history.txt` inside the app's
 * files directory. String fields are Base64-encoded so any file name, URI or
 * peer label round-trips exactly (including `|`, newlines and markup-like
 * characters); numbers and the verified flag are plain text. Fields:
 * id | uri | originalName | savedName | sourcePeer | sizeBytes | verified | timestamp
 *
 * Deliberately NOT org.json: android.jar stubs org.json in JVM unit tests,
 * so a JSON implementation could not be tested off-device. This format is
 * fully unit-testable and the file is internal, so the format is ours.
 *
 * Only add a record after SHA-256 verification AND saving succeed.
 * Removing a record never deletes the file itself.
 */
class ReceivedHistoryRepository(private val directory: File) {

    private val lock = Any()

    private val file: File get() = File(directory, "received_history.txt")

    fun load(): List<ReceivedFileRecord> {
        return try {
            synchronized(lock) {
                if (!file.exists()) return emptyList()
                file.readLines().mapNotNull { parseLine(it) }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun add(record: ReceivedFileRecord) {
        synchronized(lock) {
            val updated = listOf(record) + load()
            save(updated)
        }
    }

    fun remove(id: String) {
        synchronized(lock) {
            save(load().filter { it.id != id })
        }
    }

    private fun save(records: List<ReceivedFileRecord>) {
        directory.mkdirs()
        file.writeText(records.joinToString("\n") { encode(it) })
    }

    private fun encode(r: ReceivedFileRecord): String = listOf(
        b64(r.id),
        b64(r.uri),
        b64(r.originalName),
        b64(r.savedName),
        b64(r.sourcePeer),
        r.sizeBytes.toString(),
        r.verified.toString(),
        r.timestamp.toString()
    ).joinToString("|")

    private fun parseLine(line: String): ReceivedFileRecord? {
        val parts = line.split("|")
        if (parts.size != 8) return null
        return try {
            ReceivedFileRecord(
                id = unb64(parts[0]),
                uri = unb64(parts[1]),
                originalName = unb64(parts[2]),
                savedName = unb64(parts[3]),
                sourcePeer = unb64(parts[4]),
                sizeBytes = parts[5].toLong(),
                verified = parts[6].toBooleanStrict(),
                timestamp = parts[7].toLong()
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun b64(s: String): String =
        Base64.getEncoder().encodeToString(s.toByteArray(Charsets.UTF_8))

    private fun unb64(s: String): String =
        String(Base64.getDecoder().decode(s), Charsets.UTF_8)
}
