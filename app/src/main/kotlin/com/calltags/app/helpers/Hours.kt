package com.calltags.app.helpers

import android.content.Context
import android.text.format.DateFormat
import com.calltags.app.models.HourRange
import java.util.Calendar

private const val MINUTES_PER_HOUR = 60

fun minuteOfDay(now: Calendar = Calendar.getInstance()) =
    now.get(Calendar.HOUR_OF_DAY) * MINUTES_PER_HOUR + now.get(Calendar.MINUTE)

/** [minuteOfDay] as a time like "22:00" or "10:00 PM", following the system setting. */
fun Context.formatMinuteOfDay(minute: Int): String {
    val time = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, minute / MINUTES_PER_HOUR)
        set(Calendar.MINUTE, minute % MINUTES_PER_HOUR)
    }
    return DateFormat.getTimeFormat(this).format(time.time)
}

fun hourRange(fromHour: Int, toHour: Int) = HourRange(fromHour * MINUTES_PER_HOUR, toHour * MINUTES_PER_HOUR)
