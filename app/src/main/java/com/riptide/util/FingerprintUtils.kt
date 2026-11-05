package com.riptide.util

import java.security.MessageDigest

fun certFingerprint(certDer: ByteArray): String {
    return MessageDigest.getInstance("SHA-256").digest(certDer)
        .joinToString("") { "%02x".format(it) }
}

fun derive6DigitCode(fingerprintHex: String): String {
    val fp = (fingerprintHex).trim().lowercase()
    if (fp.length < 6) return "------"
    return try {
        val value = fp.substring(0, 6).toInt(16)
        val code = value % 1_000_000
        val formatted = String.format("%06d", code)
        "${formatted.substring(0, 3)}-${formatted.substring(3)}"
    } catch (e: Exception) {
        "------"
    }
}
