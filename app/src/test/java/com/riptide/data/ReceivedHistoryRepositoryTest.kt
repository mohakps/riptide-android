package com.riptide.data

import com.riptide.ui.model.ReceivedFileRecord
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceivedHistoryRepositoryTest {
    private fun newDir(): File = Files.createTempDirectory("riptide-received-test").toFile()

    private fun record(id: String, savedName: String) = ReceivedFileRecord(
        id = id,
        uri = "content://com.android.providers.downloads.documents/document/$id",
        originalName = "report.pdf",
        savedName = savedName,
        sourcePeer = "Home PC",
        sizeBytes = 2400000L,
        verified = true,
        timestamp = 1794060000000L
    )

    @Test
    fun testAddAndLoadSurvivesNewInstance() {
        val dir = newDir()
        ReceivedHistoryRepository(dir).add(record("1", "report (1).pdf"))
        // A fresh instance over the same directory sees the record: restart-safe.
        val loaded = ReceivedHistoryRepository(dir).load()
        assertEquals(1, loaded.size)
        assertEquals("report (1).pdf", loaded[0].savedName)
        assertEquals("report.pdf", loaded[0].originalName)
        assertTrue(loaded[0].verified)
    }

    @Test
    fun testRemoveDeletesRecordOnly() {
        val dir = newDir()
        val repo = ReceivedHistoryRepository(dir)
        repo.add(record("1", "a.pdf"))
        repo.add(record("2", "b.pdf"))
        repo.remove("1")
        val loaded = repo.load()
        assertEquals(1, loaded.size)
        assertEquals("b.pdf", loaded[0].savedName)
    }

    @Test
    fun testEmptyOnFreshDirectory() {
        assertTrue(ReceivedHistoryRepository(newDir()).load().isEmpty())
    }

    @Test
    fun testNewestFirst() {
        val dir = newDir()
        val repo = ReceivedHistoryRepository(dir)
        repo.add(record("1", "old.pdf"))
        repo.add(record("2", "new.pdf"))
        assertEquals("new.pdf", repo.load()[0].savedName)
    }
}
