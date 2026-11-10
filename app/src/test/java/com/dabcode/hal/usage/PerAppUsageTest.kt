package com.dabcode.hal.usage

import android.app.usage.UsageEvents.Event.KEYGUARD_HIDDEN
import android.app.usage.UsageEvents.Event.KEYGUARD_SHOWN
import android.app.usage.UsageEvents.Event.MOVE_TO_BACKGROUND
import android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND
import android.app.usage.UsageEvents.Event.SCREEN_INTERACTIVE
import android.app.usage.UsageEvents.Event.SCREEN_NON_INTERACTIVE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PerAppUsageTest {

    private fun ev(type: Int, pkg: String?, ts: Long) = UsageEvent(type, pkg, ts)

    @Test
    fun foregroundThenBackground_countsTheInterval() {
        val events = listOf(
            ev(MOVE_TO_FOREGROUND, "a", 1_000),
            ev(MOVE_TO_BACKGROUND, "a", 4_000)
        )
        assertEquals(mapOf("a" to 3_000L), computePerAppUsage(events, end = 10_000))
    }

    @Test
    fun severalSessions_addUp() {
        val events = listOf(
            ev(MOVE_TO_FOREGROUND, "a", 0),
            ev(MOVE_TO_BACKGROUND, "a", 100),
            ev(MOVE_TO_FOREGROUND, "b", 100),
            ev(MOVE_TO_BACKGROUND, "b", 300),
            ev(MOVE_TO_FOREGROUND, "a", 300),
            ev(MOVE_TO_BACKGROUND, "a", 350)
        )
        assertEquals(mapOf("a" to 150L, "b" to 200L), computePerAppUsage(events, end = 1_000))
    }

    @Test
    fun appStillInForeground_countsUpToEnd() {
        val events = listOf(ev(MOVE_TO_FOREGROUND, "a", 1_000))
        assertEquals(mapOf("a" to 9_000L), computePerAppUsage(events, end = 10_000))
    }

    @Test
    fun keyguardShown_stopsTheCount() {
        val events = listOf(
            ev(MOVE_TO_FOREGROUND, "a", 1_000),
            ev(KEYGUARD_SHOWN, null, 2_000),
            ev(MOVE_TO_BACKGROUND, "a", 9_000)
        )
        assertEquals(mapOf("a" to 1_000L), computePerAppUsage(events, end = 10_000))
    }

    @Test
    fun screenOff_stopsTheCount_andLockedAtEnd_addsNothing() {
        val events = listOf(
            ev(MOVE_TO_FOREGROUND, "a", 1_000),
            ev(SCREEN_NON_INTERACTIVE, null, 3_000)
        )
        assertEquals(mapOf("a" to 2_000L), computePerAppUsage(events, end = 10_000))
    }

    @Test
    fun foregroundWhileLocked_isIgnored() {
        val events = listOf(
            ev(KEYGUARD_SHOWN, null, 0),
            ev(MOVE_TO_FOREGROUND, "a", 1_000),
            ev(MOVE_TO_BACKGROUND, "a", 2_000)
        )
        assertTrue(computePerAppUsage(events, end = 10_000).isEmpty())
    }

    @Test
    fun unlockThenForeground_countsAgain() {
        val events = listOf(
            ev(MOVE_TO_FOREGROUND, "a", 0),
            ev(KEYGUARD_SHOWN, null, 1_000),
            ev(SCREEN_INTERACTIVE, null, 5_000),
            ev(KEYGUARD_HIDDEN, null, 5_500),
            ev(MOVE_TO_FOREGROUND, "a", 6_000),
            ev(MOVE_TO_BACKGROUND, "a", 7_000)
        )
        assertEquals(mapOf("a" to 2_000L), computePerAppUsage(events, end = 10_000))
    }

    @Test
    fun backgroundWithoutForeground_isIgnored() {
        val events = listOf(ev(MOVE_TO_BACKGROUND, "a", 5_000))
        assertTrue(computePerAppUsage(events, end = 10_000).isEmpty())
    }

    @Test
    fun countPickups_countsKeyguardHidden() {
        val events = listOf(
            ev(KEYGUARD_HIDDEN, null, 1),
            ev(MOVE_TO_FOREGROUND, "a", 2),
            ev(KEYGUARD_SHOWN, null, 3),
            ev(KEYGUARD_HIDDEN, null, 4)
        )
        assertEquals(2, countPickups(events))
    }
}

class ActivityStateTest {

    private fun ev(type: Int, pkg: String?, ts: Long, cls: String? = null) = UsageEvent(type, pkg, ts, cls)
    private val stopped = 23

    @Test
    fun stoppedWithoutPaused_closesTheSession() {
        val events = listOf(
            ev(MOVE_TO_FOREGROUND, "a", 1_000, "A1"),
            ev(stopped, "a", 3_000, "A1"),
            ev(MOVE_TO_FOREGROUND, "a", 10_000, "A1"),
            ev(MOVE_TO_BACKGROUND, "a", 11_000, "A1")
        )
        assertEquals(3_000L, computePerAppUsage(events, 20_000)["a"])
    }

    @Test
    fun pausedThenStopped_countsOnce() {
        val events = listOf(
            ev(MOVE_TO_FOREGROUND, "a", 1_000, "A1"),
            ev(MOVE_TO_BACKGROUND, "a", 3_000, "A1"),
            ev(stopped, "a", 3_500, "A1")
        )
        assertEquals(2_000L, computePerAppUsage(events, 20_000)["a"])
    }

    @Test
    fun twoActivitiesOfOneApp_resumeBeforePause_countOnce() {
        // A1 resumes, A2 resumes, A1 pauses, A2 pauses: one session from 1 s to 6 s.
        val events = listOf(
            ev(MOVE_TO_FOREGROUND, "a", 1_000, "A1"),
            ev(MOVE_TO_FOREGROUND, "a", 3_000, "A2"),
            ev(MOVE_TO_BACKGROUND, "a", 3_100, "A1"),
            ev(MOVE_TO_BACKGROUND, "a", 6_000, "A2")
        )
        assertEquals(5_000L, computePerAppUsage(events, 20_000)["a"])
    }

    @Test
    fun twoActivitiesOfOneApp_overlap_isNotDoubled() {
        val events = listOf(
            ev(MOVE_TO_FOREGROUND, "a", 1_000, "A1"),
            ev(MOVE_TO_FOREGROUND, "a", 2_000, "A2"),
            ev(MOVE_TO_BACKGROUND, "a", 4_000, "A2"),
            ev(MOVE_TO_BACKGROUND, "a", 5_000, "A1")
        )
        assertEquals(4_000L, computePerAppUsage(events, 20_000)["a"])
    }

    @Test
    fun screenOffClosesAllActivities() {
        val events = listOf(
            ev(MOVE_TO_FOREGROUND, "a", 1_000, "A1"),
            ev(MOVE_TO_FOREGROUND, "a", 2_000, "A2"),
            ev(SCREEN_NON_INTERACTIVE, null, 4_000),
            ev(SCREEN_INTERACTIVE, null, 9_000),
            ev(MOVE_TO_BACKGROUND, "a", 9_500, "A1")
        )
        assertEquals(3_000L, computePerAppUsage(events, 20_000)["a"])
    }
}

class LeadInTest {

    private fun ev(type: Int, pkg: String?, ts: Long, cls: String? = null) = UsageEvent(type, pkg, ts, cls)
    private val dayStart = 100_000L

    @Test
    fun sessionAcrossMidnight_countsFromDayStart() {
        val events = listOf(
            ev(MOVE_TO_FOREGROUND, "a", 90_000, "A1"),
            ev(MOVE_TO_BACKGROUND, "a", 130_000, "A1")
        )
        assertEquals(30_000L, computePerAppUsage(events, 200_000, countFrom = dayStart)["a"])
    }

    @Test
    fun sessionOpenAtTheEnd_countsFromDayStart() {
        val events = listOf(ev(MOVE_TO_FOREGROUND, "a", 90_000, "A1"))
        assertEquals(100_000L, computePerAppUsage(events, 200_000, countFrom = dayStart)["a"])
    }

    @Test
    fun sessionEndedBeforeDayStart_countsNothing() {
        val events = listOf(
            ev(MOVE_TO_FOREGROUND, "a", 50_000, "A1"),
            ev(MOVE_TO_BACKGROUND, "a", 70_000, "A1")
        )
        assertEquals(null, computePerAppUsage(events, 200_000, countFrom = dayStart)["a"])
    }

    @Test
    fun lockedAtDayStart_countsNothingUntilUnlock() {
        // The screen goes off before the day start and the app is not open when it comes on.
        val events = listOf(
            ev(MOVE_TO_FOREGROUND, "a", 80_000, "A1"),
            ev(SCREEN_NON_INTERACTIVE, null, 90_000),
            ev(SCREEN_INTERACTIVE, null, 150_000),
            ev(MOVE_TO_FOREGROUND, "b", 160_000, "B1"),
            ev(MOVE_TO_BACKGROUND, "b", 170_000, "B1")
        )
        val result = computePerAppUsage(events, 200_000, countFrom = dayStart)
        assertEquals(null, result["a"])
        assertEquals(10_000L, result["b"])
    }

    @Test
    fun lockedAtDayStartAndAtTheEnd_countsNothing() {
        val events = listOf(
            ev(MOVE_TO_FOREGROUND, "a", 80_000, "A1"),
            ev(KEYGUARD_SHOWN, null, 90_000)
        )
        assertEquals(emptyMap<String, Long>(), computePerAppUsage(events, 200_000, countFrom = dayStart))
    }

    @Test
    fun pickupsBeforeFrom_doNotCount() {
        val events = listOf(
            ev(KEYGUARD_HIDDEN, null, 90_000),
            ev(KEYGUARD_HIDDEN, null, 110_000)
        )
        assertEquals(1, countPickups(events, from = dayStart))
        assertEquals(2, countPickups(events))
    }
}

class LateStoppedTest {

    private fun ev(type: Int, pkg: String?, ts: Long, cls: String? = null) = UsageEvent(type, pkg, ts, cls)
    private val stopped = 23

    @Test
    fun lateStoppedOfOldActivity_doesNotCloseTheNewOne() {
        val events = listOf(
            ev(MOVE_TO_FOREGROUND, "a", 1_000, "A1"),
            ev(MOVE_TO_BACKGROUND, "a", 2_000, "A1"),
            ev(MOVE_TO_FOREGROUND, "a", 2_100, "A2"),
            ev(stopped, "a", 2_200, "A1"),
            ev(MOVE_TO_BACKGROUND, "a", 9_000, "A2")
        )
        assertEquals(1_000L + 6_900L, computePerAppUsage(events, 20_000)["a"])
    }

    @Test
    fun lateStoppedOfSameClass_doesNotCloseTheNewInstance() {
        val events = listOf(
            ev(MOVE_TO_FOREGROUND, "a", 1_000, "A1"),
            ev(MOVE_TO_BACKGROUND, "a", 2_000, "A1"),
            ev(MOVE_TO_FOREGROUND, "a", 2_100, "A1"),
            ev(stopped, "a", 2_200, "A1"),
            ev(MOVE_TO_BACKGROUND, "a", 9_000, "A1")
        )
        assertEquals(1_000L + 6_900L, computePerAppUsage(events, 20_000)["a"])
    }

    @Test
    fun stoppedWithoutPaused_stillCloses() {
        val events = listOf(
            ev(MOVE_TO_FOREGROUND, "a", 1_000, "A1"),
            ev(stopped, "a", 4_000, "A1")
        )
        assertEquals(3_000L, computePerAppUsage(events, 20_000)["a"])
    }

    @Test
    fun pendingStopIsClearedAtScreenOff() {
        // The PAUSED has no STOPPED before the screen goes off. A later STOPPED of a new session closes it.
        val events = listOf(
            ev(MOVE_TO_FOREGROUND, "a", 1_000, "A1"),
            ev(MOVE_TO_BACKGROUND, "a", 2_000, "A1"),
            ev(SCREEN_NON_INTERACTIVE, null, 3_000),
            ev(SCREEN_INTERACTIVE, null, 4_000),
            ev(MOVE_TO_FOREGROUND, "a", 5_000, "A1"),
            ev(stopped, "a", 8_000, "A1")
        )
        assertEquals(1_000L + 3_000L, computePerAppUsage(events, 20_000)["a"])
    }
}
