package com.dabcode.hal

/**
 * One app in the lists. [appName] is the name that HAL shows: the custom name, or else the original name.
 * [originalName] is the label of the launcher activity. [componentKey] is "package/activity".
 * [canUninstall] is true when HAL can show "Uninstall" for the app (see the canUninstall rule).
 */
data class AppInfo(
    val appName: String,
    val packageName: String,
    val isEnabled: Boolean,
    val hasNotifications: Boolean,
    val isLocked: Boolean = false,
    val componentKey: String = packageName,
    val originalName: String = appName,
    val canUninstall: Boolean = false
)

// Constants for SharedPreferences
const val PREFS_NAME = "MinimalistLauncherPrefs"
