package com.dabcode.hal.usage

import org.junit.Assert.assertEquals
import org.junit.Test

/** The English units, equal to values/strings.xml. */
val EnglishUnits = DurationUnits(hoursMinutes = "%1\$dh %2\$dm", hours = "%1\$dh", minutes = "%1\$dm", compact = "%1\$d:%2\$02dh")

/** The Japanese units, equal to values-ja/strings.xml. */
val JapaneseUnits = DurationUnits(hoursMinutes = "%1\$d時間%2\$d分", hours = "%1\$d時間", minutes = "%1\$d分", compact = "%1\$d:%2\$02d")

class FormatDurationTest {

    @Test
    fun lessThanOneMinute_isZeroMinutes() {
        assertEquals("0m", formatDuration(59_999, EnglishUnits))
    }

    @Test
    fun minutesOnly() {
        assertEquals("45m", formatDuration(45 * 60_000L, EnglishUnits))
    }

    @Test
    fun hoursAndMinutes() {
        assertEquals("2h 5m", formatDuration((2 * 60 + 5) * 60_000L, EnglishUnits))
    }

    @Test
    fun exactHour_showsZeroMinutes() {
        assertEquals("1h 0m", formatDuration(60 * 60_000L, EnglishUnits))
    }

    @Test
    fun compactFormat_padsMinutes() {
        assertEquals("2:05h", formatDurationCompact((2 * 60 + 5) * 60_000L, EnglishUnits))
        assertEquals("45m", formatDurationCompact(45 * 60_000L, EnglishUnits))
    }

    @Test
    fun otherLanguageUnits() {
        assertEquals("2時間5分", formatDuration((2 * 60 + 5) * 60_000L, JapaneseUnits))
        assertEquals("2:05", formatDurationCompact((2 * 60 + 5) * 60_000L, JapaneseUnits))
    }

    @Test
    fun appTimeLabel_underOneMinute_isNull() {
        assertEquals(null, appTimeLabel(0, EnglishUnits))
        assertEquals(null, appTimeLabel(59_999, EnglishUnits))
    }

    @Test
    fun appTimeLabel_usesTheHomeTileFormat() {
        assertEquals("1m", appTimeLabel(60_000, EnglishUnits))
        assertEquals("42m", appTimeLabel(42 * 60_000L, EnglishUnits))
        assertEquals("1h 5m", appTimeLabel(65 * 60_000L, EnglishUnits))
    }
}
