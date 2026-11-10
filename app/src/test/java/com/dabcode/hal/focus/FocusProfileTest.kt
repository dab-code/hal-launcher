package com.dabcode.hal.focus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class FocusProfileTest {

    // 2026-11-09 is a Monday.
    private fun at(dayOfMonth: Int, hour: Int, minute: Int, second: Int = 0): Calendar =
        Calendar.getInstance().apply {
            clear()
            set(2026, Calendar.NOVEMBER, dayOfMonth, hour, minute, second)
        }

    private val monday = 9
    private val tuesday = 10

    private fun profile(
        start: Pair<Int, Int>,
        end: Pair<Int, Int>,
        days: Set<DayOfWeekCompat>,
        enabled: Boolean = true
    ) = FocusProfile(
        id = "id-1",
        name = "Test",
        startHour = start.first,
        startMinute = start.second,
        endHour = end.first,
        endMinute = end.second,
        daysOfWeek = days,
        allowedPackages = setOf("com.example.allowed"),
        isEnabled = enabled
    )

    @Test
    fun calendarHelper_givesMonday() {
        assertEquals(Calendar.MONDAY, at(monday, 12, 0).get(Calendar.DAY_OF_WEEK))
    }

    @Test
    fun daytime_activeInsideRange() {
        val p = profile(9 to 0, 17 to 0, setOf(DayOfWeekCompat.MONDAY))
        assertTrue(p.isActiveNow(at(monday, 9, 0)))
        assertTrue(p.isActiveNow(at(monday, 16, 59)))
        assertFalse(p.isActiveNow(at(monday, 8, 59)))
    }

    @Test
    fun endMinute_isNotActive() {
        val p = profile(9 to 0, 17 to 0, setOf(DayOfWeekCompat.MONDAY))
        assertFalse(p.isActiveNow(at(monday, 17, 0)))
    }

    @Test
    fun daytime_otherDay_notActive() {
        val p = profile(9 to 0, 17 to 0, setOf(DayOfWeekCompat.MONDAY))
        assertFalse(p.isActiveNow(at(tuesday, 12, 0)))
    }

    @Test
    fun overnight_activeOnSelectedEvening() {
        val p = profile(22 to 0, 7 to 0, setOf(DayOfWeekCompat.MONDAY))
        assertTrue(p.isActiveNow(at(monday, 22, 0)))
        assertTrue(p.isActiveNow(at(monday, 23, 59)))
    }

    @Test
    fun overnight_activeAfterMidnight_whenNextDayNotSelected() {
        val p = profile(22 to 0, 7 to 0, setOf(DayOfWeekCompat.MONDAY))
        assertTrue(p.isActiveNow(at(tuesday, 2, 0)))
        assertTrue(p.isActiveNow(at(tuesday, 6, 59)))
        assertFalse(p.isActiveNow(at(tuesday, 7, 0)))
    }

    @Test
    fun overnight_notActiveAfterMidnight_whenPreviousDayNotSelected() {
        val p = profile(22 to 0, 7 to 0, setOf(DayOfWeekCompat.TUESDAY))
        assertFalse(p.isActiveNow(at(tuesday, 2, 0)))
        assertTrue(p.isActiveNow(at(tuesday, 22, 30)))
    }

    @Test
    fun overnight_sundayToMonday() {
        val p = profile(22 to 0, 7 to 0, setOf(DayOfWeekCompat.SUNDAY))
        assertTrue(p.isActiveNow(at(monday, 3, 0)))
    }

    @Test
    fun disabledProfile_neverActive() {
        val p = profile(0 to 0, 23 to 59, DayOfWeekCompat.entries.toSet(), enabled = false)
        assertFalse(p.isActiveNow(at(monday, 12, 0)))
    }

    @Test
    fun endTimeFormatted_usesTwoDigits() {
        assertEquals("07:05", profile(22 to 0, 7 to 5, emptySet()).endTimeFormatted())
    }

    @Test
    fun json_roundTrip_keepsAllFields() {
        val p = profile(22 to 15, 7 to 5, setOf(DayOfWeekCompat.MONDAY, DayOfWeekCompat.SUNDAY), enabled = false)
        assertEquals(p, FocusProfile.fromJson(p.toJson()))
    }

    @Test
    fun json_missingIsEnabled_defaultsToTrue() {
        val json = profile(9 to 0, 17 to 0, setOf(DayOfWeekCompat.FRIDAY)).toJson()
        json.remove("isEnabled")
        assertTrue(FocusProfile.fromJson(json).isEnabled)
    }

    @Test
    fun json_oldProfile_withoutPreset_parses() {
        val json = profile(9 to 0, 17 to 0, setOf(DayOfWeekCompat.FRIDAY)).toJson()
        assertNull(FocusProfile.fromJson(json).preset)
    }

    @Test
    fun json_preset_roundTrip() {
        val p = FocusPreset.SLEEP.newProfile()
        assertEquals(p, FocusProfile.fromJson(p.toJson()))
    }

    @Test
    fun json_missingAllowedPackages_givesEmptySet() {
        val json = profile(9 to 0, 17 to 0, setOf(DayOfWeekCompat.FRIDAY)).toJson()
        json.remove("allowedPackages")
        assertTrue(FocusProfile.fromJson(json).allowedPackages.isEmpty())
    }

    @Test
    fun nextChange_isNextStartOrEnd() {
        val profiles = listOf(profile(9 to 0, 17 to 0, setOf(DayOfWeekCompat.MONDAY)))
        // 08:59:30 -> 30 s to 09:00.
        assertEquals(30_000L, millisUntilNextProfileChange(profiles, at(monday, 8, 59, 30)))
        // 09:00:00 -> next change is 17:00.
        assertEquals(8 * 60 * 60_000L, millisUntilNextProfileChange(profiles, at(monday, 9, 0)))
    }

    @Test
    fun nextChange_nullWithoutEnabledProfiles() {
        val profiles = listOf(profile(9 to 0, 17 to 0, setOf(DayOfWeekCompat.MONDAY), enabled = false))
        assertNull(millisUntilNextProfileChange(profiles, at(monday, 8, 0)))
    }
}
