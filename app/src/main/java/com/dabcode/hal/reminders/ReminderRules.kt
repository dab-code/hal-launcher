package com.dabcode.hal.reminders

import android.app.usage.UsageEvents
import com.dabcode.hal.LockReason
import com.dabcode.hal.usage.UsageEvent
import com.dabcode.hal.usage.computePerAppUsage

/** A break of this length or more ends a session. */
const val BREAK_LIMIT_MS = 5 * 60_000L

/** The intervals that the user can select, in minutes. */
val REMINDER_INTERVALS = listOf(10, 15, 30)
const val DEFAULT_REMINDER_MINUTES = 15

/**
 * The session of one app that the user opened from HAL. [start] and [lastSeen] are wall clock times.
 * [reminders] is the number of reminders that HAL showed in this session.
 */
data class ReminderSession(val pkg: String, val start: Long, val lastSeen: Long, val reminders: Int = 0)

/** Return true when the session continues: the break since [lastSeen] is less than [BREAK_LIMIT_MS]. */
fun sessionContinues(lastSeen: Long, now: Long): Boolean = now - lastSeen < BREAK_LIMIT_MS

/**
 * The real use of one app in a session. [foregroundMs] is the foreground time. [inForeground] is true when
 * the app is in the foreground at the end. [lastSeen] is the end of the last foreground time, or null
 * when the app was not in the foreground before the end.
 */
data class SessionUsage(val foregroundMs: Long, val inForeground: Boolean, val lastSeen: Long?)

/**
 * Compute the use of [pkg] from the events of the session, up to [end].
 * The foreground time comes from [computePerAppUsage], so screen off and the keyguard stop the count.
 */
fun sessionUsage(events: List<UsageEvent>, pkg: String, end: Long): SessionUsage {
    var foregroundSince: Long? = null
    var lastSeen: Long? = null
    var deviceLocked = false
    for (event in events) {
        when (event.type) {
            UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                if (!deviceLocked && event.pkg == pkg) foregroundSince = event.ts
            }
            UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                if (event.pkg == pkg && foregroundSince != null) {
                    lastSeen = event.ts
                    foregroundSince = null
                }
            }
            UsageEvents.Event.SCREEN_NON_INTERACTIVE,
            UsageEvents.Event.KEYGUARD_SHOWN -> {
                if (!deviceLocked && foregroundSince != null) {
                    lastSeen = event.ts
                    foregroundSince = null
                }
                deviceLocked = true
            }
            UsageEvents.Event.SCREEN_INTERACTIVE,
            UsageEvents.Event.KEYGUARD_HIDDEN -> {
                deviceLocked = false
            }
        }
    }
    val inForeground = foregroundSince != null && !deviceLocked
    return SessionUsage(
        foregroundMs = computePerAppUsage(events, end)[pkg] ?: 0L,
        inForeground = inForeground,
        lastSeen = if (inForeground) end else lastSeen
    )
}

/** What to do when the alarm of a session fires. */
sealed interface ReminderStep {
    /**
     * The app is in the foreground. Show a reminder with [notifyMinutes] when it is not null.
     * Store [reminders] and set the next alarm at [nextAt].
     */
    data class Remind(val notifyMinutes: Long?, val reminders: Int, val nextAt: Long) : ReminderStep

    /** The app is not in the foreground, and the break is short. Check again at [at]. */
    data class CheckAgain(val at: Long, val lastSeen: Long) : ReminderStep

    /** The break is at the limit or longer. End the session. */
    data object End : ReminderStep
}

/**
 * Decide the step for [session] from the real [usage] at [now].
 * The next alarm comes from the real foreground time, so a late alarm does not move the next ones.
 * A reminder shows only one time for each interval.
 */
fun reminderStep(
    session: ReminderSession,
    usage: SessionUsage,
    now: Long,
    intervalMs: Long
): ReminderStep {
    if (usage.inForeground) {
        val intervalsDone = (usage.foregroundMs / intervalMs).toInt()
        val notify = intervalsDone > session.reminders
        return ReminderStep.Remind(
            notifyMinutes = if (notify) usage.foregroundMs / 60_000 else null,
            reminders = maxOf(intervalsDone, session.reminders),
            nextAt = nextAlarmAt(now, usage.foregroundMs, intervalMs)
        )
    }
    val lastSeen = maxOf(usage.lastSeen ?: 0L, session.lastSeen)
    return if (sessionContinues(lastSeen, now)) ReminderStep.CheckAgain(lastSeen + BREAK_LIMIT_MS, lastSeen)
    else ReminderStep.End
}

/** Return the time of the next alarm: [now] plus the foreground time that is missing to the next interval. */
fun nextAlarmAt(now: Long, foregroundMs: Long, intervalMs: Long): Long {
    val nextInterval = (foregroundMs / intervalMs + 1) * intervalMs
    return now + (nextInterval - foregroundMs)
}

/**
 * Return true when the pop-up at the end of a break ("Open for 5 min") shows.
 * [pkg] must be in the foreground at [now] (see [sessionUsage]), and [lockReason] must still lock it:
 * Focus or the daily limit applies, and no new break runs.
 */
fun showBreakEnd(events: List<UsageEvent>, pkg: String, now: Long, lockReason: LockReason?): Boolean =
    lockReason != null && sessionUsage(events, pkg, now).inForeground
