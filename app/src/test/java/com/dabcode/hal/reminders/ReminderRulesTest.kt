package com.dabcode.hal.reminders

import android.app.usage.UsageEvents.Event.KEYGUARD_HIDDEN
import android.app.usage.UsageEvents.Event.KEYGUARD_SHOWN
import android.app.usage.UsageEvents.Event.MOVE_TO_BACKGROUND
import android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND
import android.app.usage.UsageEvents.Event.SCREEN_INTERACTIVE
import android.app.usage.UsageEvents.Event.SCREEN_NON_INTERACTIVE
import com.dabcode.hal.LockReason
import com.dabcode.hal.usage.UsageEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderRulesTest {

    private val min = 60_000L
    private fun ev(type: Int, pkg: String?, ts: Long) = UsageEvent(type, pkg, ts)

    // The session rule

    @Test
    fun breakUnderLimit_continues() {
        assertTrue(sessionContinues(lastSeen = 0, now = 5 * min - 1))
    }

    @Test
    fun breakAtLimit_ends() {
        assertFalse(sessionContinues(lastSeen = 0, now = 5 * min))
        assertFalse(sessionContinues(lastSeen = 0, now = 9 * min))
    }

    // The real session time

    @Test
    fun stillInForeground_countsUpToEnd() {
        val usage = sessionUsage(listOf(ev(MOVE_TO_FOREGROUND, "a", 0)), "a", end = 15 * min)
        assertEquals(SessionUsage(15 * min, inForeground = true, lastSeen = 15 * min), usage)
    }

    @Test
    fun screenOff_stopsTheCount_andIsNotForeground() {
        val events = listOf(
            ev(MOVE_TO_FOREGROUND, "a", 0),
            ev(SCREEN_NON_INTERACTIVE, null, 4 * min)
        )
        assertEquals(SessionUsage(4 * min, inForeground = false, lastSeen = 4 * min), sessionUsage(events, "a", 15 * min))
    }

    @Test
    fun screenOffAndBackIntoTheApp_countsBothParts() {
        val events = listOf(
            ev(MOVE_TO_FOREGROUND, "a", 0),
            ev(KEYGUARD_SHOWN, null, 4 * min),
            ev(SCREEN_INTERACTIVE, null, 6 * min),
            ev(KEYGUARD_HIDDEN, null, 6 * min),
            ev(MOVE_TO_FOREGROUND, "a", 6 * min)
        )
        assertEquals(SessionUsage(13 * min, inForeground = true, lastSeen = 15 * min), sessionUsage(events, "a", 15 * min))
    }

    @Test
    fun leaveAndReturn_countsOnlyTheForegroundTime() {
        val events = listOf(
            ev(MOVE_TO_FOREGROUND, "a", 0),
            ev(MOVE_TO_BACKGROUND, "a", 5 * min),
            ev(MOVE_TO_FOREGROUND, "a", 8 * min)
        )
        assertEquals(SessionUsage(12 * min, inForeground = true, lastSeen = 15 * min), sessionUsage(events, "a", 15 * min))
    }

    @Test
    fun switchToOtherApp_endsTheForeground_andOtherAppDoesNotCount() {
        val events = listOf(
            ev(MOVE_TO_FOREGROUND, "a", 0),
            ev(MOVE_TO_BACKGROUND, "a", 7 * min),
            ev(MOVE_TO_FOREGROUND, "b", 7 * min)
        )
        assertEquals(SessionUsage(7 * min, inForeground = false, lastSeen = 7 * min), sessionUsage(events, "a", 15 * min))
    }

    @Test
    fun appNeverInForeground_hasNoLastSeen() {
        assertEquals(SessionUsage(0, inForeground = false, lastSeen = null), sessionUsage(emptyList(), "a", 15 * min))
    }

    // The step and the next alarm

    private val session = ReminderSession("a", start = 0, lastSeen = 0)

    @Test
    fun firstInterval_remindsWithRealMinutes_andSetsNextFromRealTime() {
        // The alarm fired 40 s late. The next alarm is 15 min of use minus the real 15:40.
        val usage = SessionUsage(15 * min + 40_000, inForeground = true, lastSeen = 0)
        val step = reminderStep(session, usage, now = 15 * min + 40_000, intervalMs = 15 * min)
        assertEquals(ReminderStep.Remind(notifyMinutes = 15, reminders = 1, nextAt = 30 * min), step)
    }

    @Test
    fun underTheInterval_noReminder_alarmAtTheMissingTime() {
        // The user had a short break, so only 12 min of use after 15 min of wall time.
        val usage = SessionUsage(12 * min, inForeground = true, lastSeen = 0)
        val step = reminderStep(session, usage, now = 15 * min, intervalMs = 15 * min)
        assertEquals(ReminderStep.Remind(notifyMinutes = null, reminders = 0, nextAt = 18 * min), step)
    }

    @Test
    fun intervalAlreadyReminded_noSecondReminder() {
        val usage = SessionUsage(22 * min, inForeground = true, lastSeen = 0)
        val step = reminderStep(session.copy(reminders = 1), usage, now = 30 * min, intervalMs = 15 * min)
        assertEquals(ReminderStep.Remind(notifyMinutes = null, reminders = 1, nextAt = 38 * min), step)
    }

    @Test
    fun secondInterval_remindsAgain() {
        val usage = SessionUsage(31 * min, inForeground = true, lastSeen = 0)
        val step = reminderStep(session.copy(reminders = 1), usage, now = 40 * min, intervalMs = 15 * min)
        assertEquals(ReminderStep.Remind(notifyMinutes = 31, reminders = 2, nextAt = 54 * min), step)
    }

    @Test
    fun notInForeground_shortBreak_checksAgainAtTheBreakLimit() {
        val usage = SessionUsage(14 * min, inForeground = false, lastSeen = 14 * min)
        val step = reminderStep(session, usage, now = 15 * min, intervalMs = 15 * min)
        assertEquals(ReminderStep.CheckAgain(at = 19 * min, lastSeen = 14 * min), step)
    }

    @Test
    fun notInForeground_breakAtLimit_ends() {
        val usage = SessionUsage(10 * min, inForeground = false, lastSeen = 10 * min)
        assertEquals(ReminderStep.End, reminderStep(session, usage, now = 15 * min, intervalMs = 15 * min))
    }

    @Test
    fun notInForeground_noEvents_usesTheStoredLastSeen() {
        val usage = SessionUsage(0, inForeground = false, lastSeen = null)
        val step = reminderStep(session.copy(lastSeen = 12 * min), usage, now = 15 * min, intervalMs = 15 * min)
        assertEquals(ReminderStep.CheckAgain(at = 17 * min, lastSeen = 12 * min), step)
    }

    @Test
    fun nextAlarm_isTheMissingForegroundTime() {
        assertEquals(1_000 + 15 * min, nextAlarmAt(now = 1_000, foregroundMs = 0, intervalMs = 15 * min))
        assertEquals(100 * min + 5 * min, nextAlarmAt(now = 100 * min, foregroundMs = 25 * min, intervalMs = 10 * min))
    }

    @Test
    fun remindNullMinutes_onlyWhenNoNewInterval() {
        val usage = SessionUsage(9 * min, inForeground = true, lastSeen = 0)
        val step = reminderStep(session, usage, now = 10 * min, intervalMs = 10 * min) as ReminderStep.Remind
        assertNull(step.notifyMinutes)
    }

    // The pop-up at the end of a break

    private val openApp = listOf(ev(MOVE_TO_FOREGROUND, "a", 1_000))

    @Test
    fun breakEnd_appInForegroundAndLocked_shows() {
        assertTrue(showBreakEnd(openApp, "a", 5 * min, LockReason.FOCUS))
        assertTrue(showBreakEnd(openApp, "a", 5 * min, LockReason.DAILY_LIMIT))
    }

    @Test
    fun breakEnd_appInBackground_showsNothing() {
        val events = openApp + ev(MOVE_TO_BACKGROUND, "a", 2 * min)
        assertFalse(showBreakEnd(events, "a", 5 * min, LockReason.FOCUS))
    }

    @Test
    fun breakEnd_otherAppInForeground_showsNothing() {
        assertFalse(showBreakEnd(listOf(ev(MOVE_TO_FOREGROUND, "b", 1_000)), "a", 5 * min, LockReason.FOCUS))
    }

    @Test
    fun breakEnd_screenOff_showsNothing() {
        val events = openApp + ev(SCREEN_NON_INTERACTIVE, null, 3 * min)
        assertFalse(showBreakEnd(events, "a", 5 * min, LockReason.FOCUS))
    }

    @Test
    fun breakEnd_notLocked_showsNothing() {
        // Focus or the daily limit ended, the app is always allowed, or a new break runs.
        assertFalse(showBreakEnd(openApp, "a", 5 * min, null))
    }
}
