package com.dabcode.hal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class PauseTest {

    @Test
    fun pauseSeconds_growsBy5_upTo60() {
        assertEquals(5, pauseSeconds(0))
        assertEquals(10, pauseSeconds(1))
        assertEquals(15, pauseSeconds(2))
        assertEquals(55, pauseSeconds(10))
        assertEquals(60, pauseSeconds(11))
        assertEquals(60, pauseSeconds(50))
        assertEquals(5, pauseSeconds(-1))
    }

    @Test
    fun needsPause_onlyForAppsNotOnHome() {
        assertTrue(needsPause(pauseEnabled = true, isOnHomeScreen = false))
        assertFalse(needsPause(pauseEnabled = true, isOnHomeScreen = true))
        assertFalse(needsPause(pauseEnabled = false, isOnHomeScreen = false))
    }

    @Test
    fun counts_growPerApp_andResetOnNewDay() {
        val day1 = PauseCounts().withOpen("a", 100).withOpen("a", 100).withOpen("b", 100)
        assertEquals(2, day1.opensToday("a", 100))
        assertEquals(1, day1.opensToday("b", 100))
        assertEquals(0, day1.opensToday("c", 100))
        // The next day: the old counts do not count, and a new open starts from 0.
        assertEquals(0, day1.opensToday("a", 101))
        val day2 = day1.withOpen("a", 101)
        assertEquals(1, day2.opensToday("a", 101))
        assertEquals(0, day2.opensToday("b", 101))
    }

    @Test
    fun json_roundTrip_andDamagedText() {
        val counts = PauseCounts(2026313, mapOf("a" to 3, "b" to 1))
        assertEquals(counts, PauseCounts.fromJson(counts.toJson()))
        assertEquals(PauseCounts(), PauseCounts.fromJson("{bad"))
        assertEquals(PauseCounts(), PauseCounts.fromJson(null))
    }

    @Test
    fun dayKey_changesAtMidnight() {
        val cal = Calendar.getInstance().apply { set(2026, Calendar.DECEMBER, 31, 23, 59, 59) }
        val before = dayKey(cal)
        cal.add(Calendar.SECOND, 2)
        assertNotEquals(before, dayKey(cal))
        cal.add(Calendar.HOUR_OF_DAY, 1)
        assertEquals(dayKey(cal), dayKey((cal.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY, 0) }))
    }

    @Test
    fun ringProgress_followsRealTime() {
        val second = 1_000_000_000L
        assertEquals(0f, ringProgress(0, 5), 0f)
        assertEquals(0.5f, ringProgress(5 * second / 2, 5), 0.0001f)
        // The ring is not full 1 ms before the end, so "Open" stays disabled until the full time.
        assertTrue(ringProgress(5 * second - 1_000_000, 5) < 1f)
        assertEquals(1f, ringProgress(5 * second, 5), 0f)
    }

    @Test
    fun ringProgress_staysInRange() {
        assertEquals(0f, ringProgress(-1, 5), 0f)
        assertEquals(1f, ringProgress(60L * 1_000_000_000L, 5), 0f)
        assertEquals(1f, ringProgress(0, 0), 0f)
    }

    @Test
    fun ringProgress_longPause() {
        // 60 s: no overflow in the nanoseconds.
        assertEquals(0.5f, ringProgress(30L * 1_000_000_000L, 60), 0.0001f)
    }
}
