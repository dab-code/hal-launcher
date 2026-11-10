package com.dabcode.hal.focus

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.dabcode.settings.settingsDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import java.io.IOException

class FocusModeRepository(private val context: Context) {

    private val dataStore = context.settingsDataStore

    private object Keys {
        val FOCUS_PROFILES = stringPreferencesKey("focus_profiles")
        // A JSON object: see FocusSession.
        val FOCUS_SESSION = stringPreferencesKey("focus_session")
        // A JSON object: see FocusBreaks.
        val FOCUS_BREAKS = stringPreferencesKey("focus_breaks")
    }

    private val safeData = dataStore.data
        .catch { exception -> if (exception is IOException) emit(emptyPreferences()) else throw exception }

    val profiles: Flow<List<FocusProfile>> = safeData.map { prefs -> parseProfiles(prefs[Keys.FOCUS_PROFILES] ?: "[]") }

    val session: Flow<FocusSession> = safeData.map { FocusSession.fromJson(it[Keys.FOCUS_SESSION]) }

    val breaks: Flow<FocusBreaks> = safeData.map { FocusBreaks.fromJson(it[Keys.FOCUS_BREAKS]) }

    /** Change the session. Read and write in one transaction. */
    suspend fun changeSession(change: (FocusSession) -> FocusSession) {
        dataStore.edit { prefs ->
            prefs[Keys.FOCUS_SESSION] = change(FocusSession.fromJson(prefs[Keys.FOCUS_SESSION])).toJson()
        }
    }

    /** Start a break of [pkg]. Read and write in one transaction. */
    suspend fun addBreak(pkg: String, nowMs: Long, today: Int) {
        dataStore.edit { prefs ->
            prefs[Keys.FOCUS_BREAKS] = FocusBreaks.fromJson(prefs[Keys.FOCUS_BREAKS]).withBreak(pkg, nowMs, today).toJson()
        }
    }

    suspend fun addProfile(profile: FocusProfile) = changeProfiles { it + profile }

    suspend fun updateProfile(profile: FocusProfile) =
        changeProfiles { list -> list.map { if (it.id == profile.id) profile else it } }

    suspend fun deleteProfile(profileId: String) = changeProfiles { list -> list.filter { it.id != profileId } }

    // Read and write in one edit transaction, so that two fast changes cannot overwrite each other.
    private suspend fun changeProfiles(change: (List<FocusProfile>) -> List<FocusProfile>) {
        dataStore.edit { prefs: MutablePreferences ->
            val current = parseProfiles(prefs[Keys.FOCUS_PROFILES] ?: "[]")
            prefs[Keys.FOCUS_PROFILES] = JSONArray(change(current).map { it.toJson() }).toString()
        }
    }

    private fun parseProfiles(json: String): List<FocusProfile> {
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { FocusProfile.fromJson(array.getJSONObject(it)) }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
