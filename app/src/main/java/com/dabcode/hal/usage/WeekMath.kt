package com.dabcode.hal.usage

import com.dabcode.hal.isLockdownActive
import java.util.Calendar
import kotlin.math.roundToInt

/** Return the number of days from Monday to the day [calendarDayOfWeek] (a Calendar.DAY_OF_WEEK value). Monday is 0. */
fun daysSinceMonday(calendarDayOfWeek: Int): Int = (calendarDayOfWeek - Calendar.MONDAY + 7) % 7

/**
 * Return true for each day where the screen time stays under the limit, and false for a day at or over the limit.
 * The rule is the same as for Lockdown. Return null for a day with no usage data (0 ms). Such a day does not count.
 */
fun underLimitDays(dayTotalsMs: List<Long>, limitMinutes: Int): List<Boolean?> =
    dayTotalsMs.map { if (it <= 0L) null else !isLockdownActive(true, it, limitMinutes) }

/**
 * Compare today with the same weekday last week. Both values stop at the same time of day.
 * Return the change in percent, for example 121 or -50. Return null when last week has no data.
 */
fun changeVsLastWeekPercent(todayMs: Long, lastWeekMs: Long): Int? {
    if (lastWeekMs <= 0L) return null
    return ((todayMs - lastWeekMs).toDouble() / lastWeekMs * 100).roundToInt()
}

/** Return the arrow for a change: down for less than 0, up for more than 0, none for 0. */
fun changeArrow(changePercent: Int): String = when {
    changePercent < 0 -> "\u2193"
    changePercent > 0 -> "\u2191"
    else -> ""
}

/** Format a limit in minutes: "2h", "1h 30m" or "45m". */
fun formatLimit(minutes: Int, units: DurationUnits): String =
    if (minutes >= 60 && minutes % 60 == 0) units.hours.format(minutes / 60)
    else formatDuration(minutes * 60_000L, units)

/** Return the top of the bar scale. The scale includes the limit, so that the limit line is always on the graph. */
fun chartScaleMax(dayTotalsMs: List<Long>, limitMs: Long?): Long =
    maxOf(dayTotalsMs.maxOrNull() ?: 0L, limitMs ?: 0L, 1L)

/** Return the bar height as a part of the full height. A small minimum keeps a bar visible for an empty day. */
fun barFraction(ms: Long, scaleMax: Long): Float = (ms.toFloat() / scaleMax).coerceIn(0.05f, 1f)

/** Return the height of the limit line as a part of the full bar height. */
fun limitLineFraction(limitMs: Long, scaleMax: Long): Float = (limitMs.toFloat() / scaleMax).coerceIn(0f, 1f)

/** Return the selected day. With no valid selection, select the last day (today). Return -1 when there are no days. */
fun selectedDayIndex(selected: Int?, dayCount: Int): Int =
    if (selected != null && selected in 0 until dayCount) selected else dayCount - 1
