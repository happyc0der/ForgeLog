package dev.happyc0der.forgelog.domain.workout

import java.util.Locale

/*
 * Clock readouts in plain digits whatever the device locale: String.format uses the locale's own
 * digits, which in some (Arabic, Persian) are not 0-9.
 */

fun formatElapsed(durationMs: Long): String {
    val totalSec = (durationMs / 1000L).coerceAtLeast(0L)
    val hours = totalSec / 3600L
    val minutes = (totalSec % 3600L) / 60L
    val seconds = totalSec % 60L
    return if (hours > 0L) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
    }
}

fun formatSeconds(totalSec: Int): String {
    val safe = totalSec.coerceAtLeast(0)
    val minutes = safe / 60
    val seconds = safe % 60
    return String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
}
