package com.calltags.app.models

/**
 * What a number label does beyond describing the number, set on the label properties screen.
 * Only number labels have properties, call labels never reach a later call.
 *
 * [blockHours] and [ringOnlyHours] limit [block] and [ringOnly] to part of the day, null means all day.
 * [color] is an ARGB color, null for the default label color.
 */
data class LabelProperties(
    val label: String,
    val suggest: Boolean = false,
    val block: Boolean = false,
    val blockHours: HourRange? = null,
    val ringOnly: Boolean = false,
    val ringOnlyHours: HourRange? = null,
    val highlight: Boolean = false,
    val hide: Boolean = false,
    val color: Int? = null,
) {
    fun blocksAt(minuteOfDay: Int) = block && (blockHours == null || minuteOfDay in blockHours)

    fun ringsOnlyAt(minuteOfDay: Int) = ringOnly && (ringOnlyHours == null || minuteOfDay in ringOnlyHours)
}

/** From [from] up to [to], both minutes of the day. Wraps around midnight when [to] is before [from]. */
data class HourRange(val from: Int, val to: Int) {
    operator fun contains(minuteOfDay: Int) =
        if (from <= to) minuteOfDay in from until to else minuteOfDay >= from || minuteOfDay < to
}

/** A label listed on the properties screen, with how many numbers have it and when it was last given. */
data class LabelUsage(
    val properties: LabelProperties,
    val numbers: Int,
    val lastUsed: Long,
)

/** What to do with an incoming call, decided by the labels its number has. */
enum class Screening { RING, SILENCE, BLOCK }
