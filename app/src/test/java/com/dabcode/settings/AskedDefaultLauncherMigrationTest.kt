package com.dabcode.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.preferencesOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AskedDefaultLauncherMigrationTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun oldFile() = File(tmp.root, "hal_user_preferences.preferences_pb")

    // Write the old file, then close its DataStore so that the migration can open the file.
    private fun writeOldValue(file: File, asked: Boolean) = runBlocking {
        val job = Job()
        val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job)) { file }
        store.edit { it[ASKED_DEFAULT_LAUNCHER_KEY] = asked }
        job.cancelAndJoin()
    }

    @Test
    fun copiesOldValue_andDeletesOldFile() = runBlocking {
        val file = oldFile()
        writeOldValue(file, asked = true)
        val migration = AskedDefaultLauncherMigration(file)

        assertTrue(migration.shouldMigrate(emptyPreferences()))
        val result = migration.migrate(emptyPreferences())
        migration.cleanUp()

        assertEquals(true, result[ASKED_DEFAULT_LAUNCHER_KEY])
        assertFalse(file.exists())
    }

    @Test
    fun noOldFile_noMigration() = runBlocking {
        assertFalse(AskedDefaultLauncherMigration(oldFile()).shouldMigrate(emptyPreferences()))
    }

    @Test
    fun newValuePresent_noMigration() = runBlocking {
        val file = oldFile()
        writeOldValue(file, asked = true)
        val current = preferencesOf(ASKED_DEFAULT_LAUNCHER_KEY to false)
        assertFalse(AskedDefaultLauncherMigration(file).shouldMigrate(current))
    }
}
