package com.dabcode.settings

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey

/** The look of HAL. Each style has a light and a dark version. */
enum class UiStyle {
    PAPER, MONO, QUIET;

    companion object {
        /** Return the style with [name], or [PAPER] when the name is not known. */
        fun fromName(name: String?): UiStyle = entries.firstOrNull { it.name == name } ?: PAPER
    }
}

internal val UI_STYLE_KEY = stringPreferencesKey("ui_style")

/**
 * Set "ui_style" one time.
 * A current user keeps the old look (MONO). A user is current when the DataStore has other keys,
 * or when [hasEnabledAppPrefs] is true (the app list SharedPreferences has entries).
 * Other users are a new install. A new install gets PAPER.
 */
class UiStyleMigration(private val hasEnabledAppPrefs: () -> Boolean) : DataMigration<Preferences> {

    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        currentData[UI_STYLE_KEY] == null

    override suspend fun migrate(currentData: Preferences): Preferences {
        val isCurrentUser = currentData.asMap().isNotEmpty() || hasEnabledAppPrefs()
        val style = if (isCurrentUser) UiStyle.MONO else UiStyle.PAPER
        val updated = currentData.toMutablePreferences()
        updated[UI_STYLE_KEY] = style.name
        return updated.toPreferences()
    }

    override suspend fun cleanUp() = Unit
}
