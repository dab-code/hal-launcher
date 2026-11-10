package com.dabcode.settings

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.preferencesOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UiStyleMigrationTest {

    // No entries in the app list SharedPreferences.
    private val newInstall = UiStyleMigration { false }

    @Test
    fun emptyDataStore_getsPaper() = runBlocking {
        assertTrue(newInstall.shouldMigrate(emptyPreferences()))
        val result = newInstall.migrate(emptyPreferences())
        assertEquals("PAPER", result[UI_STYLE_KEY])
    }

    @Test
    fun currentUser_getsMono_andKeepsOtherKeys() = runBlocking {
        val darkKey = booleanPreferencesKey("is_dark_mode")
        val current = preferencesOf(darkKey to false)
        val result = newInstall.migrate(current)
        assertEquals("MONO", result[UI_STYLE_KEY])
        assertEquals(false, result[darkKey])
    }

    @Test
    fun emptyDataStore_withEnabledAppPrefs_getsMono() = runBlocking {
        val result = UiStyleMigration { true }.migrate(emptyPreferences())
        assertEquals("MONO", result[UI_STYLE_KEY])
    }

    @Test
    fun stylePresent_noMigration() = runBlocking {
        assertFalse(newInstall.shouldMigrate(preferencesOf(UI_STYLE_KEY to "QUIET")))
    }

    @Test
    fun fromName_unknownName_givesPaper() {
        assertEquals(UiStyle.QUIET, UiStyle.fromName("QUIET"))
        assertEquals(UiStyle.PAPER, UiStyle.fromName("NEON"))
        assertEquals(UiStyle.PAPER, UiStyle.fromName(null))
    }
}
