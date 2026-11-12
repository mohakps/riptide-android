package com.riptide.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Final approved tokens (Riptide Quiet Signal, tokens.json v6).
 * Background #101010, surface #1C1C1C, surfaceRaised #282828,
 * onSurface #F2F0EB, onSurfaceVariant #B8B6B1, outline #555555,
 * primary #FF6B6B, codeContainer ivory #F2F0EB.
 */
object RiptideColors {
    val Background = Color(0xFF101010)
    val Surface = Color(0xFF1C1C1C)
    val SurfaceVariant = Color(0xFF282828)
    val SurfaceRaised = Color(0xFF282828)
    val BorderOutline = Color(0xFF555555)
    val Outline = Color(0xFF555555)
    val PrimaryCoral = Color(0xFFFF6B6B)
    val Primary = Color(0xFFFF6B6B)
    val OnPrimary = Color(0xFF101010)
    val TextPrimary = Color(0xFFF2F0EB)
    val OnSurface = Color(0xFFF2F0EB)
    val TextMuted = Color(0xFFB8B6B1)
    val OnSurfaceVariant = Color(0xFFB8B6B1)
    val SuccessMint = Color(0xFF78DC8C)
    val Success = Color(0xFF78DC8C)
    val WarningAmber = Color(0xFFFFD166)
    val Warning = Color(0xFFFFD166)
    val ErrorRed = Color(0xFFFF6B6B)
    val Error = Color(0xFFFF6B6B)
    val CodeContainer = Color(0xFFF2F0EB)
    val OnCodeContainer = Color(0xFF101010)
    /** Create Server Share cell (v0.9.4): full #E04449, white glyph.
     * Deliberately not the coral Primary (#FF6B6B). */
    val ShareRed = Color(0xFFE04449)
    /** Address-cell heading (v0.9.4): #CBC6C0, between OnSurface
     * and OnSurfaceVariant. */
    val HeadingMuted = Color(0xFFCBC6C0)
    /** Batch panel: the tonal inset behind the active filename. */
    val BatchInset = Color(0xFF272525)
    /** Batch file list: the active row's base, and the fill that
     * advances across it with that file's bytes. */
    val BatchRowBase = Color(0xFF352628)
    val BatchRowFill = Color(0xFF563438)
    /** Session history: soft blue for Received (arrow and status),
     * softer off-white for filenames, muted for the separator and
     * file size in the subtitle. */
    val ReceivedBlue = Color(0xFF8AB4F8)
    val HistoryFilename = Color(0xFFD5D2CC)
    val HistoryMuted = Color(0xFFA8A6A1)
}
