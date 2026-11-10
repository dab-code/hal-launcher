package com.dabcode.hal.reminders

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.util.Log
import com.dabcode.hal.HALApplication
import com.dabcode.hal.R
import com.dabcode.hal.usage.DurationUnits
import com.dabcode.hal.usage.UsageStatsRepository
import com.dabcode.hal.usage.formatDuration
import com.dabcode.settings.ReminderSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Usage reminders: a quiet notification at each interval while the user uses an app that HAL opened.
 * One session at a time. The session state is in SharedPreferences, so a process restart keeps it.
 * The alarm is not exact: AlarmManager.setWindow with a window of about 60 s.
 */
object UsageReminders {
    private const val TAG = "HALReminder"
    private const val PREFS = "hal_reminder_session"
    const val EXTRA_PLANNED = "planned"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun isDebugBuild(context: Context): Boolean =
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    private suspend fun settings(context: Context): ReminderSettings =
        (context.applicationContext as HALApplication).settingsRepository.reminderSettings.first()

    /**
     * Start or continue the session of [pkg]. HAL calls this for each open (see launchApp).
     * The same app within the break limit continues the session. Any other open starts a new session.
     */
    fun onAppOpened(context: Context, pkg: String) {
        val now = System.currentTimeMillis()
        val app = context.applicationContext
        scope.launch {
            val settings = settings(app)
            if (!settings.enabled) return@launch
            val usageRepo = UsageStatsRepository(app)
            // Without usage access HAL cannot measure the time. Then there are no reminders.
            if (!usageRepo.hasPermission()) return@launch
            val old = loadSession(app)
            if (old != null && old.pkg == pkg) {
                val usage = sessionUsage(usageRepo.readEvents(old.start, now), pkg, now)
                val lastSeen = maxOf(usage.lastSeen ?: 0L, old.lastSeen)
                if (sessionContinues(lastSeen, now)) {
                    // The alarm of the session stays. When it fires, it reads the real time.
                    saveSession(app, old.copy(lastSeen = now))
                    return@launch
                }
            }
            saveSession(app, ReminderSession(pkg, start = now, lastSeen = now))
            schedule(app, now + settings.minutes * 60_000L)
        }
    }

    /** End the session and cancel its alarm. Call this when the user turns the setting off. */
    fun stop(context: Context) {
        val app = context.applicationContext
        alarmManager(app).cancel(alarmIntent(app, 0L))
        clearSession(app)
    }

    /** The alarm fired. [planned] is the time of the alarm. */
    internal suspend fun onAlarm(context: Context, planned: Long) {
        val now = System.currentTimeMillis()
        val session = loadSession(context) ?: return
        val settings = settings(context)
        val usageRepo = UsageStatsRepository(context)
        if (!settings.enabled || !usageRepo.hasPermission()) {
            clearSession(context)
            return
        }
        val usage = sessionUsage(usageRepo.readEvents(session.start, now), session.pkg, now)
        if (isDebugBuild(context)) {
            Log.d(
                TAG, "alarm pkg=${session.pkg} planned=$planned fired=$now late=${now - planned}ms " +
                    "session=${usage.foregroundMs / 60_000}min (${usage.foregroundMs}ms) foreground=${usage.inForeground}"
            )
        }
        when (val step = reminderStep(session, usage, now, settings.minutes * 60_000L)) {
            is ReminderStep.Remind -> {
                step.notifyMinutes?.let { showReminder(context, session.pkg, it) }
                saveSession(context, session.copy(lastSeen = now, reminders = step.reminders))
                schedule(context, step.nextAt)
            }
            is ReminderStep.CheckAgain -> {
                saveSession(context, session.copy(lastSeen = step.lastSeen))
                schedule(context, step.at)
            }
            ReminderStep.End -> clearSession(context)
        }
    }

    // ── Alarm ───────────────────────────────────────

    // One PendingIntent for all alarms: a new alarm replaces the last one. The extras do not change the match.
    private fun alarmIntent(context: Context, planned: Long): PendingIntent = PendingIntent.getBroadcast(
        context, 0,
        Intent(context, ReminderReceiver::class.java).putExtra(EXTRA_PLANNED, planned),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun schedule(context: Context, at: Long) = scheduleAlarm(context, at, alarmIntent(context, at))

    // ── Session state ───────────────────────────────

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun loadSession(context: Context): ReminderSession? {
        val p = prefs(context)
        val pkg = p.getString("pkg", null) ?: return null
        return ReminderSession(pkg, p.getLong("start", 0L), p.getLong("last_seen", 0L), p.getInt("reminders", 0))
    }

    private fun saveSession(context: Context, session: ReminderSession) {
        prefs(context).edit()
            .putString("pkg", session.pkg)
            .putLong("start", session.start)
            .putLong("last_seen", session.lastSeen)
            .putInt("reminders", session.reminders)
            .commit()
    }

    private fun clearSession(context: Context) {
        prefs(context).edit().clear().commit()
    }

    // ── Notification ────────────────────────────────

    private suspend fun showReminder(context: Context, pkg: String, minutes: Long) {
        val units = DurationUnits(
            context.getString(R.string.duration_hours_minutes),
            context.getString(R.string.duration_hours),
            context.getString(R.string.duration_minutes),
            context.getString(R.string.duration_compact)
        )
        val text = context.getString(R.string.reminder_text, appName(context, pkg), formatDuration(minutes * 60_000, units))
        // One notification ID for each app: the next reminder replaces the last one.
        showPopup(context, pkg.hashCode(), text)
    }
}

/** Receives the alarm of the usage reminders. */
class ReminderReceiver : BroadcastReceiver() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val planned = intent.getLongExtra(UsageReminders.EXTRA_PLANNED, 0L)
        val pending = goAsync()
        scope.launch {
            try {
                UsageReminders.onAlarm(context.applicationContext, planned)
            } finally {
                pending.finish()
            }
        }
    }
}
