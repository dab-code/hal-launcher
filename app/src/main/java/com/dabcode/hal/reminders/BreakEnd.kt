package com.dabcode.hal.reminders

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import com.dabcode.hal.HALApplication
import com.dabcode.hal.MainActivity
import com.dabcode.hal.R
import com.dabcode.hal.focus.BREAK_MS
import com.dabcode.hal.focus.FocusModeRepository
import com.dabcode.hal.focus.activeFocus
import com.dabcode.hal.isLockdownActive
import com.dabcode.hal.lockReason
import com.dabcode.hal.usage.UsageStatsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * The pop-up at the end of a break ("Open for 5 min"): when the app is still in the foreground and still locked,
 * a pop-up tells the user that the break is over. It works also when the usage reminders are off.
 * The break end is in FocusBreaks (DataStore), and the alarm has only the package, so a process restart keeps it.
 */
object BreakEnd {
    private const val SCHEME = "hal-break"
    // Not in the backup: a new device asks one time again.
    private const val PREFS = "hal_break_end"
    private const val KEY_PERMISSION_ASKED = "notification_permission_asked"

    /**
     * Return true when HAL must ask for the notification permission before a break: Android 13+, no permission,
     * and HAL did not ask before. HAL asks only one time. When the user refuses, HAL does not ask again.
     */
    fun shouldAskPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED &&
            !prefs(context).getBoolean(KEY_PERMISSION_ASKED, false)

    /** Record that HAL asked for the notification permission. */
    fun markPermissionAsked(context: Context) {
        prefs(context).edit().putBoolean(KEY_PERMISSION_ASKED, true).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Set the alarm at [at], the end of the break of [pkg]. A new break of the same app replaces the alarm. */
    fun schedule(context: Context, pkg: String, at: Long) {
        val app = context.applicationContext
        scheduleAlarm(app, at, alarmIntent(app, pkg))
    }

    // One PendingIntent for each app: the data URI holds the package, so the match is per app.
    private fun alarmIntent(context: Context, pkg: String): PendingIntent = PendingIntent.getBroadcast(
        context, 0,
        Intent(context, BreakEndReceiver::class.java).setData(Uri.fromParts(SCHEME, pkg, null)),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    /** The alarm of [pkg] fired. */
    internal suspend fun onAlarm(context: Context, pkg: String) {
        val now = System.currentTimeMillis()
        val usageRepo = UsageStatsRepository(context)
        // Without usage access HAL cannot see the foreground app. Then there is no pop-up.
        if (!usageRepo.hasPermission()) return
        val settings = (context.applicationContext as HALApplication).settingsRepository
        val focus = FocusModeRepository(context)
        val breaks = focus.breaks.first()
        val focusActive = activeFocus(focus.profiles.first(), focus.session.first(), Calendar.getInstance()) != null
        val limitReached = isLockdownActive(
            settings.isLockdownEnabled.first(), usageRepo.getTodayUsage().totalMs, settings.lockdownThresholdMinutes.first()
        )
        val reason = lockReason(
            pkg, limitReached, focusActive, settings.alwaysAllowedPackages.first(), breaks.isOnBreak(pkg, now)
        )
        // The events from the start of the break. The app opened just after the break started.
        val breakStart = (breaks.until[pkg] ?: now) - BREAK_MS
        if (!showBreakEnd(usageRepo.readEvents(breakStart, now), pkg, now, reason)) return

        val home = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val tap = PendingIntent.getActivity(context, 0, home, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        showPopup(
            context,
            id = "$SCHEME:$pkg".hashCode(),
            title = context.getString(R.string.break_end_title, appName(context, pkg)),
            text = context.getString(R.string.break_end_text),
            tap = tap
        )
    }
}

/** Receives the alarm at the end of a break. */
class BreakEndReceiver : BroadcastReceiver() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val pkg = intent.data?.schemeSpecificPart ?: return
        val pending = goAsync()
        scope.launch {
            try {
                BreakEnd.onAlarm(context.applicationContext, pkg)
            } finally {
                pending.finish()
            }
        }
    }
}
