package org.fossify.phone.extensions

import java.util.Locale

private const val SECONDS_PER_MINUTE = 60
private const val SECONDS_PER_HOUR = 3600

/** Formats a number of seconds as mm:ss, or hh:mm:ss from one hour on. */
fun Int.getFormattedDuration(): String {
    val hours = this / SECONDS_PER_HOUR
    val minutes = this % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
    val seconds = this % SECONDS_PER_MINUTE
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}
