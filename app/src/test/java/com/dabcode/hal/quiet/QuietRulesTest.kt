package com.dabcode.hal.quiet

import android.app.Notification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuietRulesTest {

    // The capture rule. The default values are a normal notification of a quiet app.
    private fun action(
        isQuietApp: Boolean = true,
        isOwnApp: Boolean = false,
        isOngoing: Boolean = false,
        isForegroundService: Boolean = false,
        isClearable: Boolean = true,
        isMedia: Boolean = false,
        category: String? = null,
        isGroupSummary: Boolean = false
    ) = quietAction(isQuietApp, isOwnApp, isOngoing, isForegroundService, isClearable, isMedia, category, isGroupSummary)

    @Test
    fun normalNotificationOfQuietApp_isStored() {
        assertEquals(QuietAction.STORE, action())
        assertEquals(QuietAction.STORE, action(category = Notification.CATEGORY_MESSAGE))
    }

    @Test
    fun appThatIsNotQuiet_isNotTouched() {
        assertEquals(QuietAction.IGNORE, action(isQuietApp = false))
    }

    @Test
    fun halsOwnNotification_isNotTouched() {
        assertEquals(QuietAction.IGNORE, action(isOwnApp = true))
    }

    @Test
    fun ongoingServiceUnclearableAndMedia_areNotTouched() {
        assertEquals(QuietAction.IGNORE, action(isOngoing = true))
        assertEquals(QuietAction.IGNORE, action(isForegroundService = true))
        assertEquals(QuietAction.IGNORE, action(isClearable = false))
        assertEquals(QuietAction.IGNORE, action(isMedia = true))
        assertEquals(QuietAction.IGNORE, action(category = Notification.CATEGORY_TRANSPORT))
    }

    @Test
    fun callAlarmAndNavigation_areNotTouched() {
        assertEquals(QuietAction.IGNORE, action(category = Notification.CATEGORY_CALL))
        assertEquals(QuietAction.IGNORE, action(category = Notification.CATEGORY_ALARM))
        assertEquals(QuietAction.IGNORE, action(category = Notification.CATEGORY_NAVIGATION))
    }

    @Test
    fun groupSummary_isRemovedButNotStored() {
        assertEquals(QuietAction.REMOVE, action(isGroupSummary = true))
    }

    @Test
    fun ongoingGroupSummary_isNotTouched() {
        assertEquals(QuietAction.IGNORE, action(isGroupSummary = true, isOngoing = true))
    }

    // The store

    private val day = 24 * 60 * 60_000L
    private fun entry(key: String, time: Long, title: String = "t") = QuietEntry(key, "p", "App", title, "x", time)

    @Test
    fun add_putsNewestFirst() {
        val list = addQuietEntry(listOf(entry("a", 100)), entry("b", 200), now = 300)
        assertEquals(listOf("b", "a"), list.map { it.key })
    }

    @Test
    fun add_sameKeyReplacesTheOldEntry() {
        val list = addQuietEntry(listOf(entry("a", 100, "old"), entry("b", 150)), entry("a", 200, "new"), now = 300)
        assertEquals(listOf("a", "b"), list.map { it.key })
        assertEquals("new", list.first().title)
    }

    @Test
    fun add_keepsMax100() {
        val full = (1..100).map { entry("k$it", it.toLong()) }
        val list = addQuietEntry(full, entry("new", 1_000), now = 1_000)
        assertEquals(100, list.size)
        assertEquals("new", list.first().key)
        // The oldest entry ("k1") is gone.
        assertFalse(list.any { it.key == "k1" })
    }

    @Test
    fun trim_dropsEntriesOf7DaysOrOlder() {
        val now = 10 * day
        val list = trimQuietEntries(listOf(entry("old", now - 7 * day), entry("new", now - 7 * day + 1)), now)
        assertEquals(listOf("new"), list.map { it.key })
    }

    @Test
    fun json_roundTrip() {
        val entries = listOf(entry("a", 2), entry("b", 1, "Title with \"quotes\""))
        assertEquals(entries, quietEntriesFromJson(quietEntriesToJson(entries)))
    }

    @Test
    fun json_damagedOrEmpty_givesNoEntries() {
        assertTrue(quietEntriesFromJson("not json").isEmpty())
        assertTrue(quietEntriesFromJson(null).isEmpty())
    }

    @Test
    fun section_showsOnlyWithQuietAppsOrEntries() {
        assertFalse(showQuietSection(emptySet(), emptyList()))
        assertTrue(showQuietSection(setOf("p"), emptyList()))
        assertTrue(showQuietSection(emptySet(), listOf(entry("a", 1))))
    }

    // The review rule

    @Test
    fun review_before7Days_hidden() {
        assertFalse(showReviewPrompt(firstSeenDay = 100, today = 106, done = false))
    }

    @Test
    fun review_at7DaysAndLater_shows() {
        assertTrue(showReviewPrompt(firstSeenDay = 100, today = 107, done = false))
        assertTrue(showReviewPrompt(firstSeenDay = 100, today = 300, done = false))
    }

    @Test
    fun review_afterDone_neverShows() {
        assertFalse(showReviewPrompt(firstSeenDay = 100, today = 300, done = true))
    }

    @Test
    fun review_noFirstSeenDay_hidden() {
        assertFalse(showReviewPrompt(firstSeenDay = null, today = 300, done = false))
    }
}
