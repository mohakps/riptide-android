package com.riptide.core

import java.io.ByteArrayInputStream
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Test

class HashAndCountTest {
    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Test
    fun emptyStreamCountsZeroBytes() {
        val (counted, sha) = hashAndCount(ByteArrayInputStream(ByteArray(0)))
        assertEquals(0L, counted)
        assertEquals(sha256(ByteArray(0)), sha)
    }

    @Test
    fun smallStreamCountsExactBytesAndHash() {
        val bytes = "hello riptide".toByteArray()
        val (counted, sha) = hashAndCount(ByteArrayInputStream(bytes))
        assertEquals(bytes.size.toLong(), counted)
        assertEquals(sha256(bytes), sha)
    }

    @Test
    fun largeStreamCountsEveryByte() {
        val bytes = ByteArray(1024 * 1024 + 123) { (it % 251).toByte() }
        val (counted, sha) = hashAndCount(ByteArrayInputStream(bytes))
        assertEquals(bytes.size.toLong(), counted)
        assertEquals(sha256(bytes), sha)
    }
}
