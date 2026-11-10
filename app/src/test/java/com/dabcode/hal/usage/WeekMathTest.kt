package com.dabcode.hal.usage

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class WeekMathTest {

    private val minute = 60_000L

    @Test
    fun daysSinceMonday_mondayIsZero_sundayIsSix() {
        assertEquals(0, daysSinceMonday(Calendar.MONDAY))
        assertEquals(2, daysSinceMonday(Calendar.WEDNESDAY))
        assertEquals(5, daysSinceMonday(Calendar.SATURDAY))
        assertEquals(6, daysSinceMonday(Calendar.SUNDAY))
    }

    @Test
    fun underLimitDays_atLimitIsNotUnder() {
        val totals = listOf(30 * minute, 120 * minute, 120 * minute - 1)
        assertEquals(listOf(true, false, true), underLimitDays(totals, 120))
    }

    @Test
    fun underLimitDays_noDataDayIsNull() {
        val days = underLimitDays(listOf(0L, 30 * minute, 0L, 150 * minute), 120)
        assertEquals(listOf(null, true, null, false), days)
        // X counts the days with data under the limit. Y counts the days with data.
        assertEquals(1, days.count { it == true })
        assertEquals(2, days.count { it != null })
    }

    @Test
    fun underLimitDays_emptyWeek() {
        assertEquals(emptyList<Boolean?>(), underLimitDays(emptyList(), 120))
    }

    @Test
    fun changeVsLastWeek_showsPercentChange() {
        assertEquals(121, changeVsLastWeekPercent(42 * minute, 19 * minute))
        assertEquals(-50, changeVsLastWeekPercent(30 * minute, 60 * minute))
        assertEquals(0, changeVsLastWeekPercent(60 * minute, 60 * minute))
    }

    @Test
    fun changeArrow_upOnlyForMore() {
        assertEquals("\u2191", changeArrow(121))
        assertEquals("\u2193", changeArrow(-50))
        assertEquals("", changeArrow(0))
    }

    @Test
    fun changeVsLastWeek_roundsThePercent() {
        // +10.99 % gives 11, not 10.
        assertEquals(11, changeVsLastWeekPercent(2_101_532L, 1_893_376L))
        // -0.4 % gives 0.
        assertEquals(0, changeVsLastWeekPercent(99_600L, 100_000L))
        assertEquals(-1, changeVsLastWeekPercent(99_400L, 100_000L))
    }

    @Test
    fun changeVsLastWeek_noDataLastWeek_isHidden() {
        assertEquals(null, changeVsLastWeekPercent(43 * minute, 0L))
    }

    @Test
    fun formatLimit_wholeHoursHaveNoMinutes() {
        assertEquals("2h", formatLimit(120, EnglishUnits))
        assertEquals("1h 30m", formatLimit(90, EnglishUnits))
        assertEquals("45m", formatLimit(45, EnglishUnits))
    }

    @Test
    fun formatLimit_otherLanguageUnits() {
        assertEquals("2時間", formatLimit(120, JapaneseUnits))
        assertEquals("1時間30分", formatLimit(90, JapaneseUnits))
    }

    @Test
    fun chartScaleMax_includesLimit() {
        assertEquals(120 * minute, chartScaleMax(listOf(30 * minute, 60 * minute), 120 * minute))
        assertEquals(180 * minute, chartScaleMax(listOf(180 * minute), 120 * minute))
        assertEquals(60 * minute, chartScaleMax(listOf(60 * minute), null))
    }

    @Test
    fun chartScaleMax_noData_isNotZero() {
        assertEquals(1L, chartScaleMax(emptyList(), null))
    }

    @Test
    fun limitLineFraction_isLimitOverScale() {
        assertEquals(0.5f, limitLineFraction(60 * minute, 120 * minute), 0.0001f)
        assertEquals(1f, limitLineFraction(120 * minute, chartScaleMax(listOf(10 * minute), 120 * minute)), 0.0001f)
    }

    @Test
    fun barFraction_hasMinimum() {
        assertEquals(0.05f, barFraction(0L, 100L), 0.0001f)
        assertEquals(0.5f, barFraction(50L, 100L), 0.0001f)
    }

    @Test
    fun selectedDayIndex_defaultIsToday() {
        assertEquals(6, selectedDayIndex(null, 7))
        assertEquals(2, selectedDayIndex(2, 7))
        assertEquals(6, selectedDayIndex(9, 7))
        assertEquals(-1, selectedDayIndex(null, 0))
    }
}
