package com.dabcode.hal.focus

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Telephony
import android.telecom.TelecomManager

/** The roles of the apps that are always allowed by default. */
enum class DefaultAppRole { DIALER, SMS, CAMERA, CLOCK, MAPS, CALENDAR, MUSIC }

/**
 * The apps that a role can resolve to. [defaultPackage]: the app that the system opens for the role,
 * or null. [systemPackages]: the system apps that can do the role.
 */
data class RoleCandidates(val defaultPackage: String?, val systemPackages: List<String>)

// The package of the system chooser. When it resolves an intent, the user did not pick a default app.
private const val CHOOSER_PACKAGE = "android"

/**
 * Return the packages for one role: the default app when there is one, else all system apps for the role.
 * This finds the Samsung, OnePlus and Google apps, because it does not use package names.
 */
fun packagesForRole(candidates: RoleCandidates): Set<String> {
    val default = candidates.defaultPackage
    return if (default != null && default != CHOOSER_PACKAGE) setOf(default)
    else candidates.systemPackages.filter { it != CHOOSER_PACKAGE }.toSet()
}

/** Return the always-allowed packages for all roles. [resolve] finds the candidates of one role. */
fun roleDefaultPackages(resolve: (DefaultAppRole) -> RoleCandidates): Set<String> =
    DefaultAppRole.entries.flatMap { role -> packagesForRole(resolve(role)) }.toSet()

/** Return the always-allowed packages on this phone. A failure gives an empty set. */
fun roleDefaultPackages(context: Context): Set<String> = try {
    roleDefaultPackages { role -> resolveRole(context, role) }
} catch (_: Exception) {
    emptySet()
}

private fun resolveRole(context: Context, role: DefaultAppRole): RoleCandidates {
    val pm = context.packageManager
    // Dialer and SMS have a system role with a public getter. No permission is necessary.
    val roleHolder = when (role) {
        DefaultAppRole.DIALER ->
            (context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager)?.defaultDialerPackage
        DefaultAppRole.SMS -> Telephony.Sms.getDefaultSmsPackage(context)
        else -> null
    }
    val intent = intentForRole(role)
    val defaultPackage = roleHolder ?: pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        ?.activityInfo?.packageName
    val systemPackages = pm.queryIntentActivities(intent, 0)
        .map { it.activityInfo.applicationInfo }
        .filter { it.flags and ApplicationInfo.FLAG_SYSTEM != 0 }
        .map { it.packageName }
    return RoleCandidates(defaultPackage, systemPackages)
}

private fun intentForRole(role: DefaultAppRole): Intent = when (role) {
    DefaultAppRole.DIALER -> Intent(Intent.ACTION_DIAL)
    DefaultAppRole.SMS -> Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"))
    DefaultAppRole.CAMERA -> Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
    DefaultAppRole.CLOCK -> Intent(AlarmClock.ACTION_SHOW_ALARMS)
    DefaultAppRole.MAPS -> Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0"))
    DefaultAppRole.CALENDAR -> Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR)
    DefaultAppRole.MUSIC -> Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC)
}
