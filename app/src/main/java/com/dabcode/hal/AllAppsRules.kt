package com.dabcode.hal

/**
 * Return [apps] without HAL itself ([selfPkg]). All app lists load through getInstalledApps, which calls this.
 * A stored setting that has HAL (for example on the home screen) stays, but HAL does not show.
 */
fun withoutSelf(apps: List<AppInfo>, selfPkg: String): List<AppInfo> = apps.filter { it.packageName != selfPkg }

/**
 * Return the apps that All Apps can show for [query]. With an empty query, the hidden apps do not show.
 * The search sees all apps, so the user can find and open a hidden app.
 */
fun appsForAllApps(apps: List<AppInfo>, hidden: Set<String>, query: String): List<AppInfo> =
    if (query.isBlank()) apps.filter { it.packageName !in hidden } else apps

/**
 * Return the app to open at once, or null. This is the one search result, when the setting is on.
 * The caller opens it through the normal open path (lock rules and pause).
 */
fun singleMatchToOpen(enabled: Boolean, query: String, results: List<AppInfo>): AppInfo? =
    if (enabled && query.isNotBlank()) results.singleOrNull() else null
