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

/** Transferred / total, compact and on one line: "170.8/188.6 MB".
 * The unit is shared (based on the total) and shown once, after a
 * nonbreaking space so it never wraps away from its amount. Decimals
 * use the device locale and are dropped when a value is whole, or
 * when either value reaches 1000 in the shared unit. When a positive
 * transferred amount would round to zero in the shared unit, each
 * value keeps its own unit instead ("500 B/2 MB"). */
fun formatBytesPair(transferred: Long, total: Long, locale: Locale = Locale.getDefault()): String {
    val t = transferred.coerceAtLeast(0L)
    val tot = total.coerceAtLeast(0L)
    val unit = unitFor(tot)
    val tInUnit = t.toDouble() / unit.divisor
    if (t > 0 && tInUnit < 0.05) {
        // A positive amount would render as zero in the shared unit.
        return "${formatBytes(t)}/${formatValueInUnit(tot, unit, locale)}\u00A0${unit.suffix}"
    }
    return "${formatValueInUnit(t, unit, locale)}/${formatValueInUnit(tot, unit, locale)}\u00A0${unit.suffix}"
}

/** One value in a unit: one decimal only when it matters, with the
 * given locale's decimal separator. Whole values and values at or
 * above 1000 in the unit drop the decimal. Bytes are whole numbers. */
private fun formatValueInUnit(bytes: Long, unit: BytesUnit, locale: Locale): String {
    val v = bytes.coerceAtLeast(0L).toDouble() / unit.divisor
    if (unit.divisor == 1.0) return bytes.coerceAtLeast(0L).toString()
    if (v >= 1000.0) return String.format(locale, "%.0f", v)
    val one = String.format(locale, "%.1f", v)
    val sep = java.text.DecimalFormatSymbols.getInstance(locale).decimalSeparator
    return if (one.endsWith("${sep}0")) one.dropLast(2) else one
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
