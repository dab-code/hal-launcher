package com.dabcode.hal.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.dabcode.hal.HALApplication
import com.dabcode.hal.R
import kotlinx.coroutines.flow.first

// The shared parts of the usage reminders and the break end: the alarm, the pop-up channel and the app name.

// A new channel ID: Android cannot raise the importance of an existing channel.
// The old channel "usage_reminders" (low importance) is deleted.
private const val CHANNEL_ID = "usage_reminders_popup"
private const val OLD_CHANNEL_ID = "usage_reminders"
private const val WINDOW_MS = 60_000L

/**
 * Set an alarm at [at] that is not exact: AlarmManager.setWindow with a window of about 60 s.
 * It needs no exact alarm permission. An alarm with the same [intent] replaces the last one.
 */
internal fun scheduleAlarm(context: Context, at: Long, intent: PendingIntent) {
    // Not a wakeup alarm: while the screen is off, the user does not use the app.
    alarmManager(context).setWindow(AlarmManager.RTC, at, WINDOW_MS, intent)
}

internal fun alarmManager(context: Context) = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

/**
 * Show a silent pop-up on the reminders channel. Create the channel when necessary.
 * Show nothing without the notification permission. The same [id] replaces the last pop-up.
 */
internal fun showPopup(context: Context, id: Int, title: String, text: String? = null, tap: PendingIntent? = null) {
    if (Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) return
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (Build.VERSION.SDK_INT >= 26) {
        manager.deleteNotificationChannel(OLD_CHANNEL_ID)
        // High importance gives the pop-up (heads-up). No sound and no vibration.
        val channel = NotificationChannel(
            CHANNEL_ID, context.getString(R.string.reminders_toggle), NotificationManager.IMPORTANCE_HIGH
        ).apply {
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }
    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_stat_reminder)
        .setContentTitle(title)
        .setContentText(text)
        .setContentIntent(tap)
        // PRIORITY_HIGH gives the pop-up before Android 8. No sound and no vibration.
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setDefaults(0)
        .setSound(null)
        .setVibrate(null)
        .setAutoCancel(true)
        .build()
    manager.notify(id, notification)
}

/** The name that HAL shows for [pkg]: the custom name, or else the label. */
internal suspend fun appName(context: Context, pkg: String): String {
    val pm = context.packageManager
    val component = pm.getLaunchIntentForPackage(pkg)?.component?.flattenToString()
    val custom = (context.applicationContext as HALApplication).settingsRepository.customAppNames.first()
    return component?.let { custom[it] } ?: try {
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        pkg
    }
}
