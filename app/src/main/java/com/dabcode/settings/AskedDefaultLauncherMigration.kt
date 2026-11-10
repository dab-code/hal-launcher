package com.dabcode.settings

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import java.io.File

internal val ASKED_DEFAULT_LAUNCHER_KEY = booleanPreferencesKey("asked_default_launcher")

/**
 * Move "asked_default_launcher" from the old "hal_user_preferences" DataStore file
 * into the settings DataStore. DataStore runs this before the first read of the settings.
 * After the migration, the old file is deleted, so the migration runs only one time.
 */
class AskedDefaultLauncherMigration(private val oldFile: File) : DataMigration<Preferences> {

    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        currentData[ASKED_DEFAULT_LAUNCHER_KEY] == null && oldFile.exists()

    override suspend fun migrate(currentData: Preferences): Preferences {
        val asked = readOldValue() ?: return currentData
        val updated = currentData.toMutablePreferences()
        updated[ASKED_DEFAULT_LAUNCHER_KEY] = asked
        return updated.toPreferences()
    }

    override suspend fun cleanUp() {
        oldFile.delete()
    }

    // Open the old file in a temporary DataStore, read it one time, then close the DataStore.
    private suspend fun readOldValue(): Boolean? {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        return try {
            PreferenceDataStoreFactory.create(scope = scope) { oldFile }
                .data.first()[ASKED_DEFAULT_LAUNCHER_KEY]
        } catch (_: Exception) {
            // A damaged old file must not block the settings. Keep the current data.
            null
        } finally {
            scope.cancel()
        }
    }
}
