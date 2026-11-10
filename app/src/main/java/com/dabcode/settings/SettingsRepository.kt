package com.dabcode.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.preferencesDataStoreFile
import com.dabcode.hal.PREFS_NAME
import com.dabcode.hal.PauseCounts
import com.dabcode.hal.customNamesToJson
import com.dabcode.hal.parseCustomNames
import com.dabcode.hal.withCustomName
import com.dabcode.hal.focus.roleDefaultPackages
import com.dabcode.hal.reminders.DEFAULT_REMINDER_MINUTES
import com.dabcode.hal.reminders.REMINDER_INTERVALS
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "hal_user_settings",
    produceMigrations = { context ->
        listOf(
            AskedDefaultLauncherMigration(context.preferencesDataStoreFile("hal_user_preferences")),
            // Run this after the other migration, so that a copied key counts as a current user.
            UiStyleMigration {
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).all.isNotEmpty()
            },
            // Run this last. It adds a key, and UiStyleMigration counts any key as a current user.
            AlwaysAllowedMigration { roleDefaultPackages(context) }
        )
    }
)

/** The usage reminder settings. */
data class ReminderSettings(val enabled: Boolean, val minutes: Int)

/** The theme values that the Activities need before the first frame. */
data class ThemeSettings(val isDarkMode: Boolean, val uiStyle: UiStyle, val fontSizeScale: String)

class SettingsRepository(private val context: Context) {

    // Access the DataStore via the extension property
    private val dataStore = context.settingsDataStore

    private object PreferencesKeys {
        val APP_LIST_ALIGNMENT = stringPreferencesKey("app_list_alignment")
        // "unlock_timer_duration" and "is_unlock_feature_enabled" (the old unlock timer) are not read anymore.
        // The keys stay in the DataStore.
        // The pause before an app opens from All Apps. The default is on, also for current users.
        val PAUSE_BEFORE_OPEN = booleanPreferencesKey("pause_before_open")
        // A JSON object: the day and the opens after a pause for each package.
        val PAUSE_COUNTS = stringPreferencesKey("pause_counts")

        val IS_DARK_MODE = booleanPreferencesKey("is_dark_mode")
        // "accent_color_hex" is not read anymore. The key stays in the DataStore.
        val UI_STYLE = UI_STYLE_KEY
        val FONT_SIZE_SCALE = stringPreferencesKey("font_size_scale")
        // The daily limit. The keys keep the old "Lockdown" names, so current users keep their setup.
        val IS_LOCKDOWN_ENABLED = booleanPreferencesKey("is_lockdown_enabled")
        val LOCKDOWN_THRESHOLD_MINUTES = intPreferencesKey("lockdown_threshold_minutes")

        // The one shared "Always allowed" list. AlwaysAllowedMigration makes it from the old lists.
        val ALWAYS_ALLOWED_PACKAGES = ALWAYS_ALLOWED_KEY
        // Show the screen time and pickups tiles on the home screen. The default is on, also for current users.
        val SHOW_HOME_STATS = booleanPreferencesKey("show_home_stats")

        val ASKED_DEFAULT_LAUNCHER = ASKED_DEFAULT_LAUNCHER_KEY
        val NOTIFICATION_PROMPT_DISMISSED = booleanPreferencesKey("notification_prompt_dismissed")
        val NOTIFICATION_DOTS_ENABLED = booleanPreferencesKey("notification_dots_enabled")
        // A JSON object: launcher component ("package/activity") to the custom name.
        val CUSTOM_APP_NAMES = stringPreferencesKey("custom_app_names")

        // New keys. A missing key gives the old behavior, so current users see no change.
        // The packages that All Apps does not show. Search still finds them.
        val HIDDEN_APPS = stringSetPreferencesKey("hidden_apps")
        // Open the app when the search in All Apps finds exactly one app.
        val OPEN_SINGLE_MATCH = booleanPreferencesKey("open_single_match")
        // Usage reminders and the interval in minutes.
        val USAGE_REMINDERS = booleanPreferencesKey("usage_reminders")
        val USAGE_REMINDER_MINUTES = intPreferencesKey("usage_reminder_minutes")
        // The packages whose notifications HAL takes out of the shade and keeps in the quiet list.
        val QUIET_APPS = stringSetPreferencesKey("quiet_apps")
        // The local epoch day of the first run with this key. The review line waits 7 days from it.
        val FIRST_SEEN_DAY = longPreferencesKey("first_seen_day")
        // True after the user taps the review line or its close button. Then it never shows again.
        val REVIEW_PROMPT_DONE = booleanPreferencesKey("review_prompt_done")
        // Show the screen time of today next to each app on the home screen.
        val SHOW_APP_SCREEN_TIME = booleanPreferencesKey("show_app_screen_time")
        // The home clock: "auto" (the format of the language), "12h" or "24h". And "big" or "small".
        val CLOCK_FORMAT = stringPreferencesKey("clock_format")
        val CLOCK_SIZE = stringPreferencesKey("clock_size")
    }

    // Default values
    companion object {
        val DEFAULT_APP_LIST_ALIGNMENT = AppListAlignment.CENTER
        const val DEFAULT_PAUSE_BEFORE_OPEN = true
        const val DEFAULT_IS_DARK_MODE = true
        val DEFAULT_UI_STYLE = UiStyle.PAPER
        const val DEFAULT_FONT_SIZE_SCALE = "medium"
        const val DEFAULT_IS_LOCKDOWN_ENABLED = false
        const val DEFAULT_LOCKDOWN_THRESHOLD_MINUTES = 120
        const val DEFAULT_NOTIFICATION_DOTS_ENABLED = true
        const val DEFAULT_SHOW_HOME_STATS = true
        const val DEFAULT_SHOW_APP_SCREEN_TIME = false
        const val CLOCK_FORMAT_AUTO = "auto"
        const val CLOCK_FORMAT_12H = "12h"
        const val CLOCK_FORMAT_24H = "24h"
        const val CLOCK_SIZE_BIG = "big"
        const val CLOCK_SIZE_SMALL = "small"
        const val DEFAULT_OPEN_SINGLE_MATCH = false
        val DEFAULT_REMINDER_SETTINGS = ReminderSettings(enabled = false, minutes = DEFAULT_REMINDER_MINUTES)
    }

    // All flows read from this one source. A read error gives empty preferences (the defaults).
    private val safeData: Flow<Preferences> = dataStore.data
        .catch { exception -> if (exception is IOException) emit(emptyPreferences()) else throw exception }

    private fun <T> setting(map: (Preferences) -> T): Flow<T> = safeData.map(map)

    val appListAlignment: Flow<AppListAlignment> = setting { preferences ->
        val alignmentName = preferences[PreferencesKeys.APP_LIST_ALIGNMENT] ?: DEFAULT_APP_LIST_ALIGNMENT.name
        try {
            AppListAlignment.valueOf(alignmentName)
        } catch (e: IllegalArgumentException) {
            DEFAULT_APP_LIST_ALIGNMENT // Use the default if the stored value is not valid
        }
    }

    suspend fun updateAppListAlignment(alignment: AppListAlignment) {
        dataStore.edit { it[PreferencesKeys.APP_LIST_ALIGNMENT] = alignment.name }
    }

    val pauseBeforeOpen: Flow<Boolean> =
        setting { it[PreferencesKeys.PAUSE_BEFORE_OPEN] ?: DEFAULT_PAUSE_BEFORE_OPEN }

    suspend fun updatePauseBeforeOpen(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.PAUSE_BEFORE_OPEN] = enabled }
    }

    val pauseCounts: Flow<PauseCounts> = setting { PauseCounts.fromJson(it[PreferencesKeys.PAUSE_COUNTS]) }

    /** Add one open of [pkg] on the day [today]. Read and write in one transaction. */
    suspend fun recordPauseOpen(pkg: String, today: Int) {
        dataStore.edit {
            val counts = PauseCounts.fromJson(it[PreferencesKeys.PAUSE_COUNTS]).withOpen(pkg, today)
            it[PreferencesKeys.PAUSE_COUNTS] = counts.toJson()
        }
    }

    val isDarkMode: Flow<Boolean> = setting { it[PreferencesKeys.IS_DARK_MODE] ?: DEFAULT_IS_DARK_MODE }

    suspend fun updateIsDarkMode(isDark: Boolean) {
        dataStore.edit { it[PreferencesKeys.IS_DARK_MODE] = isDark }
    }

    val uiStyle: Flow<UiStyle> = setting { readUiStyle(it) }

    suspend fun updateUiStyle(style: UiStyle) {
        dataStore.edit { it[PreferencesKeys.UI_STYLE] = style.name }
    }

    val fontSizeScale: Flow<String> = setting { it[PreferencesKeys.FONT_SIZE_SCALE] ?: DEFAULT_FONT_SIZE_SCALE }

    suspend fun updateFontSizeScale(scale: String) {
        dataStore.edit { it[PreferencesKeys.FONT_SIZE_SCALE] = scale }
    }

    /** The three theme values from one read. */
    val themeSettings: Flow<ThemeSettings> = setting {
        ThemeSettings(
            isDarkMode = it[PreferencesKeys.IS_DARK_MODE] ?: DEFAULT_IS_DARK_MODE,
            uiStyle = readUiStyle(it),
            fontSizeScale = it[PreferencesKeys.FONT_SIZE_SCALE] ?: DEFAULT_FONT_SIZE_SCALE
        )
    }

    // The daily limit (the code names keep "Lockdown")
    val isLockdownEnabled: Flow<Boolean> =
        setting { it[PreferencesKeys.IS_LOCKDOWN_ENABLED] ?: DEFAULT_IS_LOCKDOWN_ENABLED }

    val lockdownThresholdMinutes: Flow<Int> =
        setting { it[PreferencesKeys.LOCKDOWN_THRESHOLD_MINUTES] ?: DEFAULT_LOCKDOWN_THRESHOLD_MINUTES }

    /** Change the daily limit in one transaction. */
    suspend fun updateDailyLimit(enabled: Boolean, minutes: Int) {
        dataStore.edit {
            it[PreferencesKeys.IS_LOCKDOWN_ENABLED] = enabled
            it[PreferencesKeys.LOCKDOWN_THRESHOLD_MINUTES] = minutes
        }
    }

    val alwaysAllowedPackages: Flow<Set<String>> = setting { it[PreferencesKeys.ALWAYS_ALLOWED_PACKAGES] ?: emptySet() }

    suspend fun updateAlwaysAllowedPackages(packages: Set<String>) {
        dataStore.edit { it[PreferencesKeys.ALWAYS_ALLOWED_PACKAGES] = packages }
    }

    val showHomeStats: Flow<Boolean> = setting { it[PreferencesKeys.SHOW_HOME_STATS] ?: DEFAULT_SHOW_HOME_STATS }

    suspend fun updateShowHomeStats(show: Boolean) {
        dataStore.edit { it[PreferencesKeys.SHOW_HOME_STATS] = show }
    }

    val showAppScreenTime: Flow<Boolean> =
        setting { it[PreferencesKeys.SHOW_APP_SCREEN_TIME] ?: DEFAULT_SHOW_APP_SCREEN_TIME }

    suspend fun updateShowAppScreenTime(show: Boolean) {
        dataStore.edit { it[PreferencesKeys.SHOW_APP_SCREEN_TIME] = show }
    }

    val clockFormat: Flow<String> = setting { it[PreferencesKeys.CLOCK_FORMAT] ?: CLOCK_FORMAT_AUTO }

    suspend fun updateClockFormat(format: String) {
        dataStore.edit { it[PreferencesKeys.CLOCK_FORMAT] = format }
    }

    val clockSize: Flow<String> = setting { it[PreferencesKeys.CLOCK_SIZE] ?: CLOCK_SIZE_BIG }

    suspend fun updateClockSize(size: String) {
        dataStore.edit { it[PreferencesKeys.CLOCK_SIZE] = size }
    }

    // Default launcher prompt
    val askedDefaultLauncher: Flow<Boolean> = setting { it[PreferencesKeys.ASKED_DEFAULT_LAUNCHER] ?: false }

    suspend fun setAskedDefaultLauncher(asked: Boolean) {
        dataStore.edit { it[PreferencesKeys.ASKED_DEFAULT_LAUNCHER] = asked }
    }

    // Notification access prompt. True after the user selects "Later".
    val notificationPromptDismissed: Flow<Boolean> =
        setting { it[PreferencesKeys.NOTIFICATION_PROMPT_DISMISSED] ?: false }

    suspend fun setNotificationPromptDismissed(dismissed: Boolean) {
        dataStore.edit { it[PreferencesKeys.NOTIFICATION_PROMPT_DISMISSED] = dismissed }
    }

    // Notification dots. When false, HAL shows no dots and does not ask for notification access.
    val notificationDotsEnabled: Flow<Boolean> =
        setting { it[PreferencesKeys.NOTIFICATION_DOTS_ENABLED] ?: DEFAULT_NOTIFICATION_DOTS_ENABLED }

    suspend fun updateNotificationDotsEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.NOTIFICATION_DOTS_ENABLED] = enabled }
    }

    // Custom app names, keyed by the launcher component.
    val customAppNames: Flow<Map<String, String>> = setting { parseCustomNames(it[PreferencesKeys.CUSTOM_APP_NAMES]) }

    /** Set the custom name of [componentKey]. A null [name] removes it. Read and write in one transaction. */
    suspend fun setCustomAppName(componentKey: String, name: String?) {
        dataStore.edit {
            val names = withCustomName(parseCustomNames(it[PreferencesKeys.CUSTOM_APP_NAMES]), componentKey, name)
            if (names.isEmpty()) it.remove(PreferencesKeys.CUSTOM_APP_NAMES)
            else it[PreferencesKeys.CUSTOM_APP_NAMES] = customNamesToJson(names)
        }
    }

    val hiddenApps: Flow<Set<String>> = setting { it[PreferencesKeys.HIDDEN_APPS] ?: emptySet() }

    /** Hide or show [pkg] in All Apps. Read and write in one transaction. */
    suspend fun setAppHidden(pkg: String, hidden: Boolean) {
        dataStore.edit {
            val current = it[PreferencesKeys.HIDDEN_APPS] ?: emptySet()
            it[PreferencesKeys.HIDDEN_APPS] = if (hidden) current + pkg else current - pkg
        }
    }

    val openSingleMatch: Flow<Boolean> = setting { it[PreferencesKeys.OPEN_SINGLE_MATCH] ?: DEFAULT_OPEN_SINGLE_MATCH }

    suspend fun updateOpenSingleMatch(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.OPEN_SINGLE_MATCH] = enabled }
    }

    /** The reminder settings from one read. An unknown interval gives the default interval. */
    val reminderSettings: Flow<ReminderSettings> = setting {
        val minutes = it[PreferencesKeys.USAGE_REMINDER_MINUTES]?.takeIf { m -> m in REMINDER_INTERVALS }
        ReminderSettings(
            enabled = it[PreferencesKeys.USAGE_REMINDERS] ?: DEFAULT_REMINDER_SETTINGS.enabled,
            minutes = minutes ?: DEFAULT_REMINDER_MINUTES
        )
    }

    suspend fun updateUsageReminders(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.USAGE_REMINDERS] = enabled }
    }

    suspend fun updateReminderMinutes(minutes: Int) {
        dataStore.edit { it[PreferencesKeys.USAGE_REMINDER_MINUTES] = minutes }
    }


    val quietApps: Flow<Set<String>> = setting { it[PreferencesKeys.QUIET_APPS] ?: emptySet() }

    suspend fun updateQuietApps(packages: Set<String>) {
        dataStore.edit { it[PreferencesKeys.QUIET_APPS] = packages }
    }

    /** Store [today] as the first seen day, only if the key is missing. Current users also wait 7 days. */
    suspend fun markFirstSeenDay(today: Long) {
        dataStore.edit { if (it[PreferencesKeys.FIRST_SEEN_DAY] == null) it[PreferencesKeys.FIRST_SEEN_DAY] = today }
    }

    /** The review line shows from 7 days after the first seen day, until the user acts on it. */
    val showReviewPrompt: Flow<Boolean> = setting {
        com.dabcode.hal.quiet.showReviewPrompt(
            firstSeenDay = it[PreferencesKeys.FIRST_SEEN_DAY],
            today = localEpochDay(),
            done = it[PreferencesKeys.REVIEW_PROMPT_DONE] ?: false
        )
    }

    suspend fun setReviewPromptDone() {
        dataStore.edit { it[PreferencesKeys.REVIEW_PROMPT_DONE] = true }
    }

    // A missing or unknown value gives PAPER (DEFAULT_UI_STYLE).
    private fun readUiStyle(preferences: Preferences): UiStyle = UiStyle.fromName(preferences[PreferencesKeys.UI_STYLE])
}

/** The number of the local day since 1970-01-01. It changes at local midnight. */
fun localEpochDay(now: Long = System.currentTimeMillis()): Long =
    (now + java.util.TimeZone.getDefault().getOffset(now)) / (24 * 60 * 60_000L)
