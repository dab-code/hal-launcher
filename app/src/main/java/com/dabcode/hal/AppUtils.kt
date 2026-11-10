package com.dabcode.hal

// Make sure to import AppInfo and constants from AppModel.kt
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.Build
import android.os.UserHandle
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.widget.Toast

fun getInstalledApps(context: Context): List<AppInfo> {
    val appList = mutableListOf<AppInfo>()
    val seenPackages = mutableSetOf<String>() // Still useful if multiple activities for same app are launchable

    val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    // LauncherApps.getProfiles needs API 26. Before, use the user of this app.
    val firstProfile = if (Build.VERSION.SDK_INT >= 26) launcherApps.profiles.firstOrNull() else null
    val userHandle: UserHandle = firstProfile ?: android.os.Process.myUserHandle()

    try {
        val activities: List<LauncherActivityInfo> = launcherApps.getActivityList(null, userHandle)

        for (activityInfo in activities) {
            try {
                val appName = activityInfo.label.toString()
                val packageName = activityInfo.applicationInfo.packageName

                // The list has one entry for each package: the first launcher activity.
                if (seenPackages.add(packageName)) {
                    appList.add(
                        AppInfo(
                            appName, packageName, isEnabled = false, hasNotifications = false,
                            componentKey = activityInfo.componentName.flattenToString(),
                            canUninstall = canUninstall(packageName, context.packageName, activityInfo.applicationInfo.flags)
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e("getInstalledApps", "Error processing activity info for ${activityInfo.componentName?.packageName}: ${e.message}", e)
            }
        }
    } catch (se: SecurityException) {
        Log.e("getInstalledApps", "SecurityException while getting activity list for $userHandle: ${se.message}", se)
    } catch (e: Exception) {
        Log.e("getInstalledApps", "Error getting activity list from LauncherApps for $userHandle: ${e.message}", e)
    }

    // HAL does not show in its own lists. Sorting can happen where it's used (HomeScreen, SettingsScreen).
    return withoutSelf(appList, context.packageName)
}

fun loadEnabledApps(context: Context, allApps: List<AppInfo>): List<AppInfo> {
    val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return allApps.map { app -> app.copy(isEnabled = prefs.getBoolean(app.packageName, false)) }
}

/** Return the apps that the user enabled. Do not call this on the main thread. */
fun getEnabledApps(context: Context): List<AppInfo> =
    loadEnabledApps(context, getInstalledApps(context)).filter { it.isEnabled }

fun saveEnabledApp(context: Context, packageName: String, isEnabled: Boolean) {
    val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    with(prefs.edit()) {
        putBoolean(packageName, isEnabled)
        apply()
    }
}

fun launchApp(context: Context, packageName: String) {
    val pm = context.packageManager
    try {
        val intent = pm.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            context.startActivity(intent)
            // Each open from HAL starts or continues a usage reminder session (when the setting is on).
            com.dabcode.hal.reminders.UsageReminders.onAppOpened(context, packageName)
        } else {
            Toast.makeText(context, context.getString(R.string.toast_could_not_launch, packageName), Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        Toast.makeText(context, context.getString(R.string.toast_error_launching, packageName), Toast.LENGTH_SHORT).show()

    }
}

/**
 * Open the system dialog that asks the user to uninstall the app. The dialog does the uninstall.
 * HAL lists only the apps of its own user, so the dialog uninstalls the app for this user.
 */
fun requestUninstall(context: Context, packageName: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_DELETE, Uri.fromParts("package", packageName, null)))
    } catch (e: Exception) {
        Log.e("requestUninstall", "Could not open the uninstall dialog for $packageName: ${e.message}", e)
    }
}

/**
 * Return how many of [packages] are installed (and enabled) for this user.
 * A stored setting keeps the packages of removed apps, so that a reinstall keeps the setting.
 */
fun installedCount(context: Context, packages: Set<String>): Int {
    val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    val user = android.os.Process.myUserHandle()
    return packages.count { launcherApps.isPackageEnabled(it, user) }
}

fun isNotificationServiceEnabled(context: Context): Boolean {
    val pkgName = context.packageName
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
    if (!TextUtils.isEmpty(flat)) {
        val names = flat.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        for (name in names) {
            val cn = ComponentName.unflattenFromString(name)
            if (cn != null) {
                if (TextUtils.equals(pkgName, cn.packageName)) {
                    return true
                }
            }
        }
    }
    return false
}

fun requestNotificationAccess(context: Context) {
    val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
    context.startActivity(intent)
    Toast.makeText(context, R.string.toast_enable_notification_access, Toast.LENGTH_LONG).show()
}