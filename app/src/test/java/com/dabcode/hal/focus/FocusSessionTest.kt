package com.dabcode.hal.focus

import com.dabcode.hal.pauseSeconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class FocusSessionTest {

    // 2026-11-09 is a Monday.
    private fun at(dayOfMonth: Int, hour: Int, minute: Int): Calendar =
        Calendar.getInstance().apply {
            clear()
            set(2026, Calendar.NOVEMBER, dayOfMonth, hour, minute, 0)
        }

    private val monday = 9
    private val minute = 60_000L
    private val everyDay = DayOfWeekCompat.entries.toSet()

    private fun profile(start: Int, end: Int, preset: FocusPreset? = null, name: String = "Mine") = FocusProfile(
        name = name, startHour = start, startMinute = 0, endHour = end, endMinute = 0,
        daysOfWeek = everyDay, preset = preset
    )

    // ── Active Focus ──

    @Test
    fun noProfiles_noSession_noFocus() {
        assertNull(activeFocus(emptyList(), FocusSession(), at(monday, 12, 0)))
    }

    @Test
    fun scheduledFocus_endsAtProfileEnd() {
        val focus = activeFocus(listOf(profile(9, 17, FocusPreset.WORK)), FocusSession(), at(monday, 12, 0))!!
        assertEquals(at(monday, 17, 0).timeInMillis, focus.endsAt)
        assertEquals(FocusPreset.WORK, focus.preset)
        assertNull(focus.name)
    }

    @Test
    fun overnightFocus_endsTomorrow() {
        val focus = activeFocus(listOf(profile(22, 7)), FocusSession(), at(monday, 23, 0))!!
        assertEquals(at(monday + 1, 7, 0).timeInMillis, focus.endsAt)
        assertEquals("Mine", focus.name)
    }

    @Test
    fun overlap_showsTheOneThatEndsLast() {
        val profiles = listOf(profile(9, 17, FocusPreset.WORK), profile(12, 18, name = "Long"))
        val focus = activeFocus(profiles, FocusSession(), at(monday, 13, 0))!!
        assertEquals("Long", focus.name)
        assertEquals(at(monday, 18, 0).timeInMillis, focus.endsAt)
    }

    @Test
    fun manualFocus_appliesUntilItsEnd() {
        val until = at(monday, 15, 40).timeInMillis
        val session = FocusSession(manualUntil = until)
        assertEquals(until, activeFocus(emptyList(), session, at(monday, 15, 39))!!.endsAt)
        assertNull(activeFocus(emptyList(), session, at(monday, 15, 40)))
    }

    @Test
    fun endNow_stopsTodaysSession_butScheduleStays() {
        val profiles = listOf(profile(9, 17, FocusPreset.WORK))
        val ended = endFocusNow(FocusSession(), at(monday, 10, 0).timeInMillis)
        assertNull(activeFocus(profiles, ended, at(monday, 11, 0)))
        // The next day the schedule applies again.
        assertTrue(activeFocus(profiles, ended, at(monday + 1, 10, 0)) != null)
        // The profile is not changed: it stays on.
        assertTrue(profiles.single().isEnabled)
    }

    @Test
    fun endNow_doesNotStopALaterSessionOfTheSameDay() {
        val profiles = listOf(profile(9, 11), profile(14, 16, name = "Later"))
        val ended = endFocusNow(FocusSession(), at(monday, 10, 0).timeInMillis)
        assertEquals("Later", activeFocus(profiles, ended, at(monday, 15, 0))!!.name)
    }

    @Test
    fun endNow_stopsManualFocus() {
        val session = FocusSession(manualUntil = at(monday, 18, 0).timeInMillis, manualPreset = FocusPreset.SLEEP)
        val ended = endFocusNow(session, at(monday, 12, 0).timeInMillis)
        assertNull(activeFocus(emptyList(), ended, at(monday, 12, 1)))
        assertNull(ended.manualPreset)
    }

    @Test
    fun startNow_neverShortensAManualFocus() {
        val long = FocusSession(manualUntil = 2_000L)
        assertEquals(long, startFocusNow(long, 1_000L, null))
        assertEquals(3_000L, startFocusNow(long, 3_000L, FocusPreset.WORK).manualUntil)
        assertEquals(FocusPreset.WORK, startFocusNow(long, 3_000L, FocusPreset.WORK).manualPreset)
    }

    @Test
    fun nextTimeOfDay_laterToday_orTomorrow() {
        assertEquals(at(monday, 17, 0).timeInMillis, nextTimeOfDay(at(monday, 9, 0), 17, 0))
        assertEquals(at(monday + 1, 8, 0).timeInMillis, nextTimeOfDay(at(monday, 9, 0), 8, 0))
    }

    @Test
    fun nextChange_includesManualEnd() {
        val now = at(monday, 12, 0)
        val session = FocusSession(manualUntil = now.timeInMillis + 10 * minute)
        assertEquals(10 * minute, millisUntilNextFocusChange(emptyList(), session, now))
        assertNull(millisUntilNextFocusChange(emptyList(), FocusSession(), now))
    }

    @Test
    fun session_jsonRoundTrip_andDamagedText() {
        val session = FocusSession(123L, FocusPreset.EVENING, 456L)
        assertEquals(session, FocusSession.fromJson(session.toJson()))
        assertEquals(FocusSession(), FocusSession.fromJson("{not json"))
        assertEquals(FocusSession(), FocusSession.fromJson(null))
    }

    // ── Presets ──

    @Test
    fun presets_haveTheProposedTimes() {
        val work = FocusPreset.WORK.newProfile()
        assertEquals("09:00", work.startTimeFormatted())
        assertEquals("17:00", work.endTimeFormatted())
        assertEquals(5, work.daysOfWeek.size)
        assertEquals("22:30", FocusPreset.SLEEP.newProfile().startTimeFormatted())
        assertEquals(7, FocusPreset.EVENING.newProfile().daysOfWeek.size)
    }

    @Test
    fun profileForPreset_prefersTheStoredProfile() {
        val stored = FocusPreset.WORK.newProfile().copy(endHour = 16)
        assertEquals(stored, profileForPreset(listOf(stored), FocusPreset.WORK))
        assertEquals(FocusPreset.SLEEP, profileForPreset(listOf(stored), FocusPreset.SLEEP).preset)
    }

    // ── Breaks ──

    @Test
    fun breakPause_growsWithEachBreakToday_max60() {
        var breaks = FocusBreaks()
        val today = 2026313
        assertEquals(5, pauseSeconds(breaks.breaksToday(today)))
        breaks = breaks.withBreak("a", 0L, today)
        assertEquals(10, pauseSeconds(breaks.breaksToday(today)))
        repeat(20) { breaks = breaks.withBreak("a", 0L, today) }
        assertEquals(60, pauseSeconds(breaks.breaksToday(today)))
    }

    @Test
    fun break_lastsFiveMinutes_thenLocksAgain() {
        val breaks = FocusBreaks().withBreak("a", 1_000L, 1)
        assertTrue(breaks.isOnBreak("a", 1_000L + 5 * minute - 1))
        assertFalse(breaks.isOnBreak("a", 1_000L + 5 * minute))
        assertFalse(breaks.isOnBreak("b", 1_000L))
    }

    @Test
    fun breaks_startAgainOnANewDay() {
        val breaks = FocusBreaks().withBreak("a", 0L, 1).withBreak("a", 0L, 1)
        assertEquals(2, breaks.breaksToday(1))
        assertEquals(0, breaks.breaksToday(2))
        assertEquals(1, breaks.withBreak("a", 0L, 2).breaksToday(2))
    }

    @Test
    fun breaks_jsonRoundTrip_andDamagedText() {
        val breaks = FocusBreaks().withBreak("a", 10L, 3)
        assertEquals(breaks, FocusBreaks.fromJson(breaks.toJson()))
        assertEquals(FocusBreaks(), FocusBreaks.fromJson("[]"))
    }

    // ── Loosen ──

    @Test
    fun limitLoosening_onlyTurnOffAndRaise() {
        assertTrue(isLimitLoosening(true, 120, false, 120))
        assertTrue(isLimitLoosening(true, 120, true, 150))
        assertFalse(isLimitLoosening(true, 120, true, 90))
        assertFalse(isLimitLoosening(false, 120, true, 600))
        assertFalse(isLimitLoosening(true, 120, true, 120))
    }

    // ── Limit suggestion ──

    @Test
    fun suggestion_isSeventyPercent_roundedTo15Minutes() {
        // The example in the proposal: average 3h 10m gives 2h 15m.
        assertEquals(135, suggestedLimitMinutes((3 * 60 + 10) * minute))
        assertEquals(60, suggestedLimitMinutes(90 * minute)) // 63 -> 60
        assertEquals(75, suggestedLimitMinutes(100 * minute)) // 70 -> 75
    }

    @Test
    fun suggestion_hasAMinimum() {
        assertEquals(15, suggestedLimitMinutes(5 * minute))
    }

    @Test
    fun average_ignoresDaysWithoutData() {
        assertEquals(60 * minute, averageDayMs(listOf(0L, 30 * minute, 90 * minute)))
        assertNull(averageDayMs(listOf(0L, 0L)))
        assertNull(averageDayMs(emptyList()))
    }

    @Test
    fun stepLimit_movesTo30MinuteSteps() {
        assertEquals(150, stepLimit(135, up = true))
        assertEquals(120, stepLimit(135, up = false))
        assertEquals(150, stepLimit(120, up = true))
        assertEquals(90, stepLimit(120, up = false))
        assertEquals(LIMIT_MIN_MINUTES, stepLimit(30, up = false))
        assertEquals(LIMIT_MAX_MINUTES, stepLimit(LIMIT_MAX_MINUTES, up = true))
    }

    // ── Session bounds ──

    @Test
    fun sessionBounds_overnight_afterMidnight() {
        val (start, end) = profile(22, 7).sessionBounds(at(monday, 2, 0))!!
        assertEquals(at(monday - 1, 22, 0).timeInMillis, start)
        assertEquals(at(monday, 7, 0).timeInMillis, end)
        assertNull(profile(22, 7).sessionBounds(at(monday, 12, 0)))
    }
}
