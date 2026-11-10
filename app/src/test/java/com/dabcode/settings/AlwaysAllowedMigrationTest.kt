package com.dabcode.settings

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlwaysAllowedMigrationTest {

    private val roles = setOf("com.samsung.android.dialer", "com.sec.android.app.camera")
    private val migration = AlwaysAllowedMigration { roles }

    private val profilesJson = """
        [{"id":"1","name":"Work","startHour":9,"startMinute":0,"endHour":17,"endMinute":0,
          "daysOfWeek":["MONDAY"],"allowedPackages":["com.slack","com.maps"],"isEnabled":true},
         {"id":"2","name":"Night","startHour":22,"startMinute":0,"endHour":7,"endMinute":0,
          "daysOfWeek":["SUNDAY"],"allowedPackages":["com.audible"],"isEnabled":false}]
    """.trimIndent()

    @Test
    fun currentUser_getsTheUnionOfAllLists() = runBlocking {
        val current = preferencesOf(
            LOCKDOWN_ESSENTIAL_SET_KEY to setOf("com.bank", "com.maps"),
            FOCUS_PROFILES_KEY to profilesJson
        )
        assertTrue(migration.shouldMigrate(current))
        val result = migration.migrate(current)
        assertEquals(
            setOf("com.bank", "com.maps", "com.slack", "com.audible") + roles,
            result[ALWAYS_ALLOWED_KEY]
        )
    }

    @Test
    fun currentUser_keepsTheDailyLimitAndTheProfiles() = runBlocking {
        val enabledKey = booleanPreferencesKey("is_lockdown_enabled")
        val thresholdKey = intPreferencesKey("lockdown_threshold_minutes")
        val current = preferencesOf(
            enabledKey to true,
            thresholdKey to 90,
            FOCUS_PROFILES_KEY to profilesJson,
            LOCKDOWN_ESSENTIAL_SET_KEY to setOf("com.bank")
        )
        val result = migration.migrate(current)
        // The daily limit uses the same keys. The profiles stay as they are (same times, days and on/off).
        assertEquals(true, result[enabledKey])
        assertEquals(90, result[thresholdKey])
        assertEquals(profilesJson, result[FOCUS_PROFILES_KEY])
        assertEquals(setOf("com.bank"), result[LOCKDOWN_ESSENTIAL_SET_KEY])
    }

    @Test
    fun oldJsonEssentialList_isRead() = runBlocking {
        val current = preferencesOf(LOCKDOWN_ESSENTIAL_JSON_KEY to """["com.old"]""")
        assertEquals(setOf("com.old") + roles, migration.migrate(current)[ALWAYS_ALLOWED_KEY])
    }

    @Test
    fun newInstall_getsTheRoleApps() = runBlocking {
        assertEquals(roles, migration.migrate(emptyPreferences())[ALWAYS_ALLOWED_KEY])
    }

    @Test
    fun listPresent_noMigration() = runBlocking {
        assertFalse(migration.shouldMigrate(preferencesOf(ALWAYS_ALLOWED_KEY to emptySet<String>())))
    }

    @Test
    fun damagedProfiles_andRoleFailure_doNotBlock() = runBlocking {
        val failing = AlwaysAllowedMigration { throw IllegalStateException("no package manager") }
        val current = preferencesOf(
            FOCUS_PROFILES_KEY to "{damaged",
            LOCKDOWN_ESSENTIAL_SET_KEY to setOf("com.bank")
        )
        assertEquals(setOf("com.bank"), failing.migrate(current)[ALWAYS_ALLOWED_KEY])
    }

    @Test
    fun profileWithoutAllowedApps_isSkipped() {
        assertEquals(
            setOf("com.a"),
            oldProfilePackages("""[{"id":"1"},{"allowedPackages":["com.a"]}]""")
        )
    }
}
