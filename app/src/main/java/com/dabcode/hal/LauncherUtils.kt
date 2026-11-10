package com.dabcode.hal

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings

fun isMyAppDefaultLauncher(context: Context): Boolean {
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    val resolveInfo = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
    return resolveInfo?.activityInfo?.packageName == context.packageName
}

fun openDefaultLauncherSettings(context: Context) {
    val intent = Intent(Settings.ACTION_HOME_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) // Necessary if calling from outside an Activity
    }
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        // The settings screen can be missing on some devices.
        android.util.Log.e("LauncherUtils", "Failed to open home settings", e)
    }
}
