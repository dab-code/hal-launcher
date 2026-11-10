package com.dabcode.hal.focus

import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.Locale
import java.util.UUID

// Maps Calendar.DAY_OF_WEEK constants to readable names
enum class DayOfWeekCompat(val calendarDay: Int) {
    MONDAY(Calendar.MONDAY),
    TUESDAY(Calendar.TUESDAY),
    WEDNESDAY(Calendar.WEDNESDAY),
    THURSDAY(Calendar.THURSDAY),
    FRIDAY(Calendar.FRIDAY),
    SATURDAY(Calendar.SATURDAY),
    SUNDAY(Calendar.SUNDAY);

    companion object {
        fun fromCalendar(calendarDay: Int): DayOfWeekCompat =
            entries.first { it.calendarDay == calendarDay }
    }

    fun previous(): DayOfWeekCompat =
        fromCalendar(if (calendarDay == Calendar.SUNDAY) Calendar.SATURDAY else calendarDay - 1)
}

/** Format a time as "HH:MM" with ASCII digits in all locales. */
fun formatTime(hour: Int, minute: Int): String =
    String.format(Locale.ROOT, "%02d:%02d", hour, minute)

/**
 * Return the time in milliseconds until the next start or end minute of an enabled profile.
 * Return null when no profile is enabled.
 */
fun millisUntilNextProfileChange(profiles: List<FocusProfile>, now: Calendar): Long? {
    val nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
    val minutesAhead = profiles
        .filter { it.isEnabled }
        .flatMap { listOf(it.startHour * 60 + it.startMinute, it.endHour * 60 + it.endMinute) }
        .map { boundary ->
            val diff = (boundary - nowMinutes + MINUTES_PER_DAY) % MINUTES_PER_DAY
            if (diff == 0) MINUTES_PER_DAY else diff
        }
        .minOrNull() ?: return null
    val msIntoMinute = now.get(Calendar.SECOND) * 1000L + now.get(Calendar.MILLISECOND)
    return minutesAhead * 60_000L - msIntoMinute
}

private const val MINUTES_PER_DAY = 24 * 60

/** Return the epoch ms of [hour]:[minute] on the day [dayOffset] days after the day of [base]. */
fun timeOnDay(base: Calendar, dayOffset: Int, hour: Int, minute: Int): Long =
    (base.clone() as Calendar).apply {
        add(Calendar.DAY_OF_YEAR, dayOffset)
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

data class FocusProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int,
    val daysOfWeek: Set<DayOfWeekCompat>,
    // Not used by the lock rules. The shared "Always allowed" list replaced it.
    // HAL keeps the value in the JSON, so that an older version still has its list.
    val allowedPackages: Set<String> = emptySet(),
    val isEnabled: Boolean = true,
    // The preset that made this Focus. Null for a Focus that the user named.
    val preset: FocusPreset? = null
) {
    /**
     * Return true when the profile is active at [now].
     * The start minute is active. The end minute is not active.
     * For an overnight profile (for example 22:00 to 07:00), the part after
     * midnight belongs to the previous day, so the previous day must be selected.
     * When start and end are equal, the profile is never active.
     */
    fun isActiveNow(now: Calendar = Calendar.getInstance()): Boolean {
        if (!isEnabled) return false

        val today = DayOfWeekCompat.fromCalendar(now.get(Calendar.DAY_OF_WEEK))
        val yesterday = today.previous()
        val nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val startMinutes = startHour * 60 + startMinute
        val endMinutes = endHour * 60 + endMinute

        return if (startMinutes <= endMinutes) {
            today in daysOfWeek && nowMinutes >= startMinutes && nowMinutes < endMinutes
        } else {
            (today in daysOfWeek && nowMinutes >= startMinutes) ||
                (yesterday in daysOfWeek && nowMinutes < endMinutes)
        }
    }

    /**
     * Return the start and the end (epoch ms) of the session that is active at [now].
     * Return null when the profile is not active at [now].
     */
    fun sessionBounds(now: Calendar): Pair<Long, Long>? {
        if (!isActiveNow(now)) return null
        val nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val startMinutes = startHour * 60 + startMinute
        val endMinutes = endHour * 60 + endMinute
        // The day offsets of the start and the end, relative to the day of [now].
        val (startDay, endDay) = when {
            startMinutes <= endMinutes -> 0 to 0
            nowMinutes >= startMinutes -> 0 to 1
            else -> -1 to 0
        }
        return timeOnDay(now, startDay, startHour, startMinute) to timeOnDay(now, endDay, endHour, endMinute)
    }

    fun startTimeFormatted(): String = formatTime(startHour, startMinute)

    fun endTimeFormatted(): String = formatTime(endHour, endMinute)

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("startHour", startHour)
        put("startMinute", startMinute)
        put("endHour", endHour)
        put("endMinute", endMinute)
        put("daysOfWeek", JSONArray(daysOfWeek.map { it.name }))
        put("allowedPackages", JSONArray(allowedPackages.toList()))
        put("isEnabled", isEnabled)
        if (preset != null) put("preset", preset.name)
    }

    companion object {
        val ALWAYS_ALLOWED_PACKAGES = setOf(
            "com.android.dialer",
            "com.android.phone",
            "com.google.android.dialer",
            "com.android.messaging",
            "com.google.android.apps.messaging",
            "com.android.settings"
        )

        fun fromJson(json: JSONObject): FocusProfile {
            val daysArray = json.getJSONArray("daysOfWeek")
            val days = (0 until daysArray.length()).map { DayOfWeekCompat.valueOf(daysArray.getString(it)) }.toSet()

            val packagesArray = json.optJSONArray("allowedPackages") ?: JSONArray()
            val packages = (0 until packagesArray.length()).map { packagesArray.getString(it) }.toSet()

            return FocusProfile(
                id = json.getString("id"),
                name = json.getString("name"),
                startHour = json.getInt("startHour"),
                startMinute = json.getInt("startMinute"),
                endHour = json.getInt("endHour"),
                endMinute = json.getInt("endMinute"),
                daysOfWeek = days,
                allowedPackages = packages,
                isEnabled = json.optBoolean("isEnabled", true),
                preset = FocusPreset.fromName(json.optString("preset", ""))
            )
        }
    }
}
