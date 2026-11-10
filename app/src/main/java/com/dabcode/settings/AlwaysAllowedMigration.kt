package com.dabcode.settings

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import org.json.JSONArray

/** The one shared list of apps that Focus and the daily limit never lock. */
internal val ALWAYS_ALLOWED_KEY = stringSetPreferencesKey("always_allowed_packages")

// The old keys. HAL does not write them anymore. The values stay, so that an older version still works.
internal val LOCKDOWN_ESSENTIAL_SET_KEY = stringSetPreferencesKey("lockdown_essential_package_set")
internal val LOCKDOWN_ESSENTIAL_JSON_KEY = stringPreferencesKey("lockdown_essential_packages")
internal val FOCUS_PROFILES_KEY = stringPreferencesKey("focus_profiles")

/**
 * Make the "Always allowed" list one time. The list is the union of:
 * the essential apps of the old Lockdown Mode, the allowed apps of all focus profiles,
 * and the apps for the default roles (phone, SMS, camera, clock, maps, calendar, music).
 * A new install gets only the role apps. The daily limit keeps the Lockdown keys, so it needs no migration.
 */
class AlwaysAllowedMigration(private val roleDefaults: () -> Set<String>) : DataMigration<Preferences> {

    override suspend fun shouldMigrate(currentData: Preferences): Boolean = currentData[ALWAYS_ALLOWED_KEY] == null

    override suspend fun migrate(currentData: Preferences): Preferences {
        val updated = currentData.toMutablePreferences()
        updated[ALWAYS_ALLOWED_KEY] = oldEssentialPackages(currentData) +
            oldProfilePackages(currentData[FOCUS_PROFILES_KEY]) +
            safeRoleDefaults()
        return updated.toPreferences()
    }

    override suspend fun cleanUp() = Unit

    // A failure to read the roles must not block the settings.
    private fun safeRoleDefaults(): Set<String> = try {
        roleDefaults()
    } catch (_: Exception) {
        emptySet()
    }
}

/** Return the essential apps of Lockdown Mode. The set key wins. The old JSON key is the fallback. */
internal fun oldEssentialPackages(prefs: Preferences): Set<String> =
    prefs[LOCKDOWN_ESSENTIAL_SET_KEY] ?: parseJsonStrings(prefs[LOCKDOWN_ESSENTIAL_JSON_KEY])

/** Return the allowed apps of all focus profiles in the JSON text. A damaged text or profile gives nothing. */
internal fun oldProfilePackages(profilesJson: String?): Set<String> {
    if (profilesJson.isNullOrBlank()) return emptySet()
    return try {
        val profiles = JSONArray(profilesJson)
        (0 until profiles.length()).flatMap { i ->
            val apps = profiles.optJSONObject(i)?.optJSONArray("allowedPackages") ?: return@flatMap emptyList()
            (0 until apps.length()).map { apps.getString(it) }
        }.toSet()
    } catch (_: Exception) {
        emptySet()
    }
}

internal fun parseJsonStrings(json: String?): Set<String> {
    if (json == null) return emptySet()
    return try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { arr.getString(it) }.toSet()
    } catch (_: Exception) {
        emptySet()
    }
}
