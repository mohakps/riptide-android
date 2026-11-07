package com.riptide.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** "2.4 MB", "256 MB", "512 KB", "128 B". One decimal only when it matters. */
fun formatBytes(bytes: Long): String {
    val b = bytes.coerceAtLeast(0L)
    return when {
        b >= 1024L * 1024L -> trimDecimal(b / (1024.0 * 1024.0)) + " MB"
        b >= 1024L -> trimDecimal(b / 1024.0) + " KB"
        else -> "$b B"
    }
}

/** Unit a byte count is shown in, for pairing two values. */
private data class BytesUnit(val divisor: Double, val suffix: String)

private fun unitFor(bytes: Long): BytesUnit {
    val b = bytes.coerceAtLeast(0L)
    return when {
        b >= 1024L * 1024L -> BytesUnit(1024.0 * 1024.0, "MB")
        b >= 1024L -> BytesUnit(1024.0, "KB")
        else -> BytesUnit(1.0, "B")
    }
}

/** Transferred / total with the unit shown once when both share it:
 * "423.8 / 504.9 MB". When they differ, each keeps its own unit:
 * "512 KB / 2 MB". */
fun formatBytesPair(transferred: Long, total: Long): String {
    val t = unitFor(total)
    val f = unitFor(transferred)
    return if (t == f) {
        "${trimDecimal(transferred.toDouble() / t.divisor)} / ${trimDecimal(total.toDouble() / t.divisor)} ${t.suffix}"
    } else {
        "${formatBytes(transferred)} / ${formatBytes(total)}"
    }
}

private fun trimDecimal(value: Double): String {
    val one = String.format(Locale.US, "%.1f", value)
    return if (one.endsWith(".0")) one.dropLast(2) else one
}

/** Day group label for received history: TODAY, YESTERDAY, or "12 Oct 2026". */
fun dayLabel(timestamp: Long, now: Long = System.currentTimeMillis()): String {
    val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
    val today = Calendar.getInstance().apply { timeInMillis = now }
    val yesterday = Calendar.getInstance().apply { timeInMillis = now; add(Calendar.DAY_OF_YEAR, -1) }
    fun sameDay(a: Calendar, b: Calendar): Boolean =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    return when {
        sameDay(cal, today) -> "TODAY"
        sameDay(cal, yesterday) -> "YESTERDAY"
        else -> SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(timestamp))
    }
}

/** "14:32" local time. */
fun timeLabel(timestamp: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
