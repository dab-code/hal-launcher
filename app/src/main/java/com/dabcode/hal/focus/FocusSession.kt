package com.dabcode.hal.focus

import org.json.JSONObject
import java.util.Calendar

// The pure rules of Focus: presets, the session now, "End now", breaks and the daily limit suggestion.

/** The three presets. Each preset fills the time and the days. The name comes from the string resources. */
enum class FocusPreset(
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int,
    val days: Set<DayOfWeekCompat>
) {
    WORK(9, 0, 17, 0, WEEKDAYS),
    EVENING(20, 0, 22, 0, DayOfWeekCompat.entries.toSet()),
    SLEEP(22, 30, 7, 0, DayOfWeekCompat.entries.toSet());

    /** Return a new Focus with the values of this preset. It is on. */
    fun newProfile(): FocusProfile = FocusProfile(
        name = name,
        startHour = startHour,
        startMinute = startMinute,
        endHour = endHour,
        endMinute = endMinute,
        daysOfWeek = days,
        preset = this
    )

    companion object {
        fun fromName(name: String?): FocusPreset? = entries.firstOrNull { it.name == name }
    }
}

private val WEEKDAYS = setOf(
    DayOfWeekCompat.MONDAY, DayOfWeekCompat.TUESDAY, DayOfWeekCompat.WEDNESDAY,
    DayOfWeekCompat.THURSDAY, DayOfWeekCompat.FRIDAY
)

/** Return the Focus of [preset] from [profiles], or a new Focus with the preset values. */
fun profileForPreset(profiles: List<FocusProfile>, preset: FocusPreset): FocusProfile =
    profiles.firstOrNull { it.preset == preset } ?: preset.newProfile()

/**
 * The session state that is not a schedule.
 * [manualUntil]: the end (epoch ms) of a Focus that the user started now. 0 means none.
 * [manualPreset]: the preset of that Focus, or null for a plain "Focus".
 * [endedAt]: the time (epoch ms) of the last "End now". A scheduled session that was active then stays off.
 */
data class FocusSession(
    val manualUntil: Long = 0L,
    val manualPreset: FocusPreset? = null,
    val endedAt: Long = 0L
) {
    fun toJson(): String = JSONObject().apply {
        put("manualUntil", manualUntil)
        if (manualPreset != null) put("manualPreset", manualPreset.name)
        put("endedAt", endedAt)
    }.toString()

    companion object {
        /** Read the session from its JSON text. A damaged text gives no session. */
        fun fromJson(json: String?): FocusSession {
            if (json.isNullOrBlank()) return FocusSession()
            return try {
                val obj = JSONObject(json)
                FocusSession(
                    manualUntil = obj.optLong("manualUntil", 0L),
                    manualPreset = FocusPreset.fromName(obj.optString("manualPreset", "")),
                    endedAt = obj.optLong("endedAt", 0L)
                )
            } catch (_: Exception) {
                FocusSession()
            }
        }
    }
}

/**
 * The Focus that applies now. [name] is the name of a Focus that the user named, else null.
 * [preset] is the preset, else null. When both are null, the text is only "Focus".
 */
data class ActiveFocus(val endsAt: Long, val preset: FocusPreset? = null, val name: String? = null)

/**
 * Return the Focus that applies at [now], or null.
 * All scheduled sessions and the manual session count. When more than one applies, the lock is the same
 * (one shared list), and the one that ends last shows.
 * A scheduled session that was active at [FocusSession.endedAt] does not apply.
 */
fun activeFocus(profiles: List<FocusProfile>, session: FocusSession, now: Calendar): ActiveFocus? {
    val nowMs = now.timeInMillis
    val scheduled = profiles.mapNotNull { profile ->
        val (start, end) = profile.sessionBounds(now) ?: return@mapNotNull null
        if (session.endedAt in start until end) return@mapNotNull null
        ActiveFocus(end, profile.preset, if (profile.preset == null) profile.name else null)
    }
    val manual = if (nowMs < session.manualUntil) listOf(ActiveFocus(session.manualUntil, session.manualPreset)) else emptyList()
    return (scheduled + manual).maxByOrNull { it.endsAt }
}

/**
 * Return the session after the user starts a Focus now until [until].
 * A start never makes a running manual Focus shorter: shorter is a loosen, and "End now" does that.
 */
fun startFocusNow(session: FocusSession, until: Long, preset: FocusPreset?): FocusSession =
    if (until <= session.manualUntil) session else session.copy(manualUntil = until, manualPreset = preset)

/** Return the session after "End now" at [nowMs]: the manual Focus stops, and the active schedules stop for today. */
fun endFocusNow(session: FocusSession, nowMs: Long): FocusSession =
    session.copy(manualUntil = 0L, manualPreset = null, endedAt = nowMs)

/**
 * Return the time in ms until the active Focus can change: a schedule start or end, or the end of the manual Focus.
 * Return null when nothing can change.
 */
fun millisUntilNextFocusChange(profiles: List<FocusProfile>, session: FocusSession, now: Calendar): Long? {
    val manual = (session.manualUntil - now.timeInMillis).takeIf { it > 0 }
    return listOfNotNull(millisUntilNextProfileChange(profiles, now), manual).minOrNull()
}

/** Return the end (epoch ms) of the next time [hour]:[minute] after [now]. "Until 08:00" at 09:00 means tomorrow. */
fun nextTimeOfDay(now: Calendar, hour: Int, minute: Int): Long {
    val today = timeOnDay(now, 0, hour, minute)
    return if (today > now.timeInMillis) today else timeOnDay(now, 1, hour, minute)
}

// ── Breaks ("Open for 5 min") ──────────────────────────

const val BREAK_MINUTES = 5
const val BREAK_MS = BREAK_MINUTES * 60_000L

/**
 * The breaks of today. [count]: the breaks on the day [day]. [until]: the end (epoch ms) of the break of each package.
 * The pause before a break uses the same rule as the pause before an app opens (see pauseSeconds).
 */
data class FocusBreaks(val day: Int = 0, val count: Int = 0, val until: Map<String, Long> = emptyMap()) {

    fun breaksToday(today: Int): Int = if (day == today) count else 0

    /** Return true while [pkg] has a break at [nowMs]. After the break, the app locks again on the next open. */
    fun isOnBreak(pkg: String, nowMs: Long): Boolean = nowMs < (until[pkg] ?: 0L)

    /** Add a break of [BREAK_MINUTES] for [pkg]. On a new day, the count starts again from 0. Old breaks go away. */
    fun withBreak(pkg: String, nowMs: Long, today: Int): FocusBreaks {
        val running = until.filterValues { it > nowMs }
        return FocusBreaks(today, breaksToday(today) + 1, running + (pkg to nowMs + BREAK_MS))
    }

    fun toJson(): String = JSONObject().apply {
        put("day", day)
        put("count", count)
        put("until", JSONObject(until as Map<*, *>))
    }.toString()

    companion object {
        /** Read the breaks from their JSON text. A damaged text gives no breaks. */
        fun fromJson(json: String?): FocusBreaks {
            if (json.isNullOrBlank()) return FocusBreaks()
            return try {
                val obj = JSONObject(json)
                val untilObj = obj.getJSONObject("until")
                FocusBreaks(
                    obj.getInt("day"),
                    obj.getInt("count"),
                    untilObj.keys().asSequence().associateWith { untilObj.getLong(it) }
                )
            } catch (_: Exception) {
                FocusBreaks()
            }
        }
    }
}

// ── Loosen and tighten ─────────────────────────────────

/** The pause before a change that loosens Focus or the daily limit. */
const val LOOSEN_SECONDS = 30

/**
 * Return true when the change of the daily limit loosens it: turn it off, or raise it.
 * Turn it on and lower it apply at once.
 */
fun isLimitLoosening(oldEnabled: Boolean, oldMinutes: Int, newEnabled: Boolean, newMinutes: Int): Boolean =
    oldEnabled && (!newEnabled || newMinutes > oldMinutes)

// ── Daily limit suggestion ─────────────────────────────

const val LIMIT_STEP_MINUTES = 30
const val LIMIT_MIN_MINUTES = 15
const val LIMIT_MAX_MINUTES = 12 * 60

/** The days of data before the stats screen suggests a limit. */
const val SUGGESTION_MIN_DAYS = 7

/** Return the average of the days with data (more than 0 ms). Return null when no day has data. */
fun averageDayMs(dayTotalsMs: List<Long>): Long? {
    val days = dayTotalsMs.filter { it > 0L }
    return if (days.isEmpty()) null else days.sum() / days.size
}

/** Return the suggested limit: 70% of the average, rounded to 15 min. Min 15 min. */
fun suggestedLimitMinutes(averageMs: Long): Int {
    val minutes = averageMs / 60_000.0 * 0.7
    val rounded = Math.round(minutes / LIMIT_MIN_MINUTES).toInt() * LIMIT_MIN_MINUTES
    return rounded.coerceIn(LIMIT_MIN_MINUTES, LIMIT_MAX_MINUTES)
}

/**
 * Return the limit after one step of the time picker: up or down to the next multiple of 30 min.
 * 135 goes up to 150 and down to 120. The result stays from 15 min to 12 h.
 */
fun stepLimit(minutes: Int, up: Boolean): Int {
    val next = if (up) (minutes / LIMIT_STEP_MINUTES + 1) * LIMIT_STEP_MINUTES
    else ((minutes - 1) / LIMIT_STEP_MINUTES) * LIMIT_STEP_MINUTES
    return next.coerceIn(LIMIT_MIN_MINUTES, LIMIT_MAX_MINUTES)
}
