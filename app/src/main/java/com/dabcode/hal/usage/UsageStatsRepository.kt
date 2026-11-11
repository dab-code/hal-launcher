package com.dabcode.hal.usage

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Process
import android.provider.Settings
import android.telecom.TelecomManager
import java.util.Calendar

/** One usage event: the event type, the package name, the time stamp and the activity class name. */
data class UsageEvent(val type: Int, val pkg: String?, val ts: Long, val cls: String? = null)

/** The usage of today, from one scan of the usage events. [perAppMs] has no home-screen apps. */
data class TodayUsage(val totalMs: Long, val pickups: Int, val perAppMs: Map<String, Long>)

/** The foreground time of one app, with its label. */
data class AppUsage(val name: String, val pkg: String, val ms: Long)

/** One day of the weekly graph: the day label, the total time and the apps with more than 1 minute. */
data class DayUsage(val label: String, val totalMs: Long, val apps: List<AppUsage>)

class UsageStatsRepository(private val context: Context) {

    private val usageStatsManager: UsageStatsManager
        get() = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    fun hasPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        // checkOpNoThrow works on all API levels. unsafeCheckOpNoThrow needs API 29.
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun openUsageAccessSettings() {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /** Read the events of today one time, and compute total, pickups and per-app time. */
    fun getTodayUsage(): TodayUsage {
        val now = System.currentTimeMillis()
        val startOfDay = startOfDay(now)
        val events = readEvents(startOfDay - LEAD_IN_MS, now)
        val perApp = withoutHomeApps(
            visibleUsage().invoke(computePerAppUsage(events, now, countFrom = startOfDay)),
            homeScreenPackages()
        )
        return TodayUsage(
            totalMs = perApp.values.sum(),
            pickups = countPickups(events, from = startOfDay),
            perAppMs = perApp
        )
    }

    /** Get the label of each app, sorted by time. Do not call this on the main thread. */
    fun toAppUsageList(perAppMs: Map<String, Long>): List<AppUsage> {
        val pm = context.packageManager
        return perAppMs.entries
            .sortedByDescending { it.value }
            .map { (pkg, ms) ->
                val name = try {
                    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                } catch (_: PackageManager.NameNotFoundException) {
                    pkg
                }
                AppUsage(name, pkg, ms)
            }
    }

    /** The packages of all home-screen apps: HAL and each app that answers the HOME intent. */
    private fun homeScreenPackages(): Set<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val found = context.packageManager
            .queryIntentActivities(intent, 0)
            .map { HomeCandidate(it.activityInfo.packageName, it.priority) }
        return homePackagesOf(found, context.packageName)
    }

    /**
     * The filter for the per-app time: keep the apps with a launcher icon (the apps of All apps).
     * HAL cannot see the other apps, so it cannot name them. Call time stays (see [withoutIconlessApps]).
     */
    private fun visibleUsage(): (Map<String, Long>) -> Map<String, Long> {
        val pm = context.packageManager
        val launcher = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        ).map { it.activityInfo.packageName }.toSet()
        val inCall = try {
            pm.queryIntentServices(Intent(IN_CALL_SERVICE_ACTION), 0).map { it.serviceInfo.packageName }.toSet()
        } catch (_: Exception) {
            emptySet()
        }
        val dialer = try {
            (context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager)?.defaultDialerPackage
        } catch (_: Exception) {
            null
        }
        return { perApp -> withoutIconlessApps(perApp, launcher, inCall, dialer) }
    }

    /**
     * Get 8 days. Index 0 is the same weekday last week, up to the same time of day as now.
     * Indices 1 to 7 are 6 days ago to today. Do not call this on the main thread.
     */
    fun getWeeklyUsage(): List<DayUsage> {
        val result = mutableListOf<DayUsage>()
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()
        val homePackages = homeScreenPackages()
        val visible = visibleUsage()

        for (daysAgo in 7 downTo 0) {
            cal.timeInMillis = now
            cal.add(Calendar.DAY_OF_YEAR, -daysAgo)
            val sameTimeThatDay = cal.timeInMillis
            val dayStart = startOfDay(sameTimeThatDay)
            cal.timeInMillis = dayStart

            val dayEnd = when (daysAgo) {
                // Today and the comparison day stop at the current time of day.
                0, 7 -> sameTimeThatDay
                else -> nextDayStart(dayStart)
            }

            val perApp = visible(
                computePerAppUsage(readEvents(dayStart - LEAD_IN_MS, dayEnd), dayEnd, countFrom = dayStart)
            )
            val dayLabel = android.text.format.DateFormat.format("EEE", cal).toString()
            // Show only the apps with more than 1 minute.
            val rows = withoutHomeApps(perApp, homePackages).filter { it.value > 60_000 }
            result.add(DayUsage(dayLabel, screenTimeMs(perApp, homePackages), toAppUsageList(rows)))
        }
        return result
    }

    // The screen time from [dayStart] to [dayEnd], without the home-screen apps.
    private fun dayScreenTime(
        dayStart: Long,
        dayEnd: Long,
        homePackages: Set<String>,
        visible: (Map<String, Long>) -> Map<String, Long>
    ): Long = screenTimeMs(
        visible(computePerAppUsage(readEvents(dayStart - LEAD_IN_MS, dayEnd), dayEnd, countFrom = dayStart)),
        homePackages
    )

    // The totals of the past days of this week, and the start of the day when they were read.
    // Past days do not change. Read them again only when the day changes.
    private var pastDaysReadOn = -1L
    private var pastDaysTotals: List<Long> = emptyList()

    /**
     * Return the screen time of each day from Monday to today. [todayMs] is the value for today.
     * Do not call this on the main thread.
     */
    @Synchronized
    fun getWeekSoFar(todayMs: Long): List<Long> {
        val todayStart = startOfDay(System.currentTimeMillis())
        if (pastDaysReadOn != todayStart) {
            val homePackages = homeScreenPackages()
            val visible = visibleUsage()
            val cal = Calendar.getInstance()
            cal.timeInMillis = todayStart
            val pastDays = daysSinceMonday(cal.get(Calendar.DAY_OF_WEEK))
            pastDaysTotals = (pastDays downTo 1).map { daysAgo ->
                cal.timeInMillis = todayStart
                cal.add(Calendar.DAY_OF_YEAR, -daysAgo)
                val dayStart = startOfDay(cal.timeInMillis)
                val dayEnd = nextDayStart(dayStart)
                dayScreenTime(dayStart, dayEnd, homePackages, visible)
            }
            pastDaysReadOn = todayStart
        }
        return pastDaysTotals + todayMs
    }

    /**
     * Return the screen time of each of the [days] full days before today. The oldest day is first.
     * Do not call this on the main thread.
     */
    fun getPastDayTotals(days: Int): List<Long> {
        val todayStart = startOfDay(System.currentTimeMillis())
        val homePackages = homeScreenPackages()
        val visible = visibleUsage()
        val cal = Calendar.getInstance()
        return (days downTo 1).map { daysAgo ->
            cal.timeInMillis = todayStart
            cal.add(Calendar.DAY_OF_YEAR, -daysAgo)
            val dayStart = startOfDay(cal.timeInMillis)
            val dayEnd = nextDayStart(dayStart)
            dayScreenTime(dayStart, dayEnd, homePackages, visible)
        }
    }

    // The start of the next day. Calendar gives the correct value on a day with a clock change.
    private fun nextDayStart(dayStart: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = dayStart
        cal.add(Calendar.DAY_OF_YEAR, 1)
        return startOfDay(cal.timeInMillis)
    }

    /** Read the usage events from [start] to [end]. Do not call this on the main thread. */
    fun readEvents(start: Long, end: Long): List<UsageEvent> {
        val events = usageStatsManager.queryEvents(start, end)
        val event = UsageEvents.Event()
        val list = mutableListOf<UsageEvent>()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            list.add(UsageEvent(event.eventType, event.packageName, event.timeStamp, event.className))
        }
        return list
    }

    private fun startOfDay(time: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = time
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}

/** One result of the HOME intent query: the package and the priority of the match. */
data class HomeCandidate(val pkg: String, val priority: Int)

/**
 * The home-screen packages. A result with a priority under 0 is a boot fallback
 * (for example the FallbackHome of Settings), not a launcher. Do not count it.
 */
fun homePackagesOf(candidates: List<HomeCandidate>, ownPackage: String): Set<String> =
    candidates.filter { it.priority >= 0 }.map { it.pkg }.toSet() + ownPackage

/** Remove the home-screen apps from the per-app time. Time on a home screen is not app use. */
fun withoutHomeApps(perAppMs: Map<String, Long>, homePackages: Set<String>): Map<String, Long> =
    perAppMs.filterKeys { it !in homePackages }

/** The action of an InCallService. The in-call screen of a phone has a service with this action. */
const val IN_CALL_SERVICE_ACTION = "android.telecom.InCallService"

/**
 * Keep only the apps with a launcher icon ([launcherPackages]). An app with no icon is not in All apps,
 * and HAL cannot name it. Call time stays: an in-call package ([inCallPackages]) with no icon adds its
 * time to the [dialerPackage]. With no dialer, that time is dropped.
 */
fun withoutIconlessApps(
    perAppMs: Map<String, Long>,
    launcherPackages: Set<String>,
    inCallPackages: Set<String>,
    dialerPackage: String?
): Map<String, Long> {
    val result = mutableMapOf<String, Long>()
    for ((pkg, ms) in perAppMs) {
        val target = when {
            pkg in launcherPackages -> pkg
            pkg in inCallPackages && dialerPackage != null -> dialerPackage
            else -> continue
        }
        result.merge(target, ms, Long::plus)
    }
    return result
}

/** The screen time: the sum of the per-app time without the home-screen apps. */
fun screenTimeMs(perAppMs: Map<String, Long>, homePackages: Set<String>): Long =
    withoutHomeApps(perAppMs, homePackages).values.sum()

/** The name of an app row: the name that HAL shows for [pkg] (the custom name), else the [label]. */
fun usageRowName(pkg: String, label: String, appNames: Map<String, String>): String =
    appNames[pkg] ?: label

/** Count the unlocks (KEYGUARD_HIDDEN events) from [from] on. */
fun countPickups(events: List<UsageEvent>, from: Long = Long.MIN_VALUE): Int =
    events.count { it.type == UsageEvents.Event.KEYGUARD_HIDDEN && it.ts >= from }

/** The time before the start of a day that is read, to know the open apps and the lock state at midnight. */
const val LEAD_IN_MS = 3 * 60 * 60 * 1000L

/**
 * Compute the foreground time of each app from the usage events, up to [end].
 * Do not count time while the screen is off. The count stops at SCREEN_NON_INTERACTIVE
 * or KEYGUARD_SHOWN, and starts again at SCREEN_INTERACTIVE or KEYGUARD_HIDDEN.
 * The keyguard can still show after SCREEN_INTERACTIVE. An app that shows over the
 * keyguard, for example a call screen, counts as screen time. This is by design.
 *
 * The events can start before [countFrom] (a lead-in). The lead-in gives the open apps and the lock
 * state at [countFrom]. Only the time from [countFrom] on counts.
 */
fun computePerAppUsage(events: List<UsageEvent>, end: Long, countFrom: Long = Long.MIN_VALUE): Map<String, Long> {
    // The open activities of each app, and the time when the app became open.
    // One app counts one time, also when two of its activities are open together.
    val openActivities = mutableMapOf<String, MutableSet<String>>()
    val openSince = mutableMapOf<String, Long>()
    // The PAUSED events of each activity (package/class) that wait for their STOPPED event.
    val pendingStop = mutableMapOf<String, Int>()
    val totals = mutableMapOf<String, Long>()
    var deviceLocked = false

    fun addTime(pkg: String, since: Long, until: Long) {
        val ms = until - maxOf(since, countFrom)
        if (ms > 0) totals[pkg] = (totals[pkg] ?: 0L) + ms
    }

    fun closeApp(pkg: String, ts: Long) {
        val since = openSince.remove(pkg) ?: return
        openActivities.remove(pkg)
        addTime(pkg, since, ts)
    }

    for (event in events) {
        val pkg = event.pkg
        val ts = event.ts
        when (event.type) {
            UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                if (!deviceLocked && pkg != null) {
                    openActivities.getOrPut(pkg) { mutableSetOf() }.add(event.cls.orEmpty())
                    openSince.putIfAbsent(pkg, ts)
                }
            }
            // The public API gives no instance id, so the activity is the package and the class name.
            // Android sends the STOPPED of an old activity after the RESUMED of the next one. That next
            // one can have the same class. So a STOPPED that follows a PAUSED must not close anything.
            UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                if (pkg == null) continue
                val cls = event.cls.orEmpty()
                pendingStop.merge("$pkg/$cls", 1, Int::plus)
                val open = openActivities[pkg] ?: continue
                if (open.remove(cls) && open.isEmpty()) closeApp(pkg, ts)
            }
            // ACTIVITY_STOPPED (API 29) with no PAUSED before it: the process ended. Close the activity.
            ACTIVITY_STOPPED -> {
                if (pkg == null) continue
                val cls = event.cls.orEmpty()
                val pending = pendingStop["$pkg/$cls"] ?: 0
                if (pending > 0) {
                    pendingStop["$pkg/$cls"] = pending - 1
                    continue
                }
                val open = openActivities[pkg] ?: continue
                if (open.remove(cls) && open.isEmpty()) closeApp(pkg, ts)
            }
            UsageEvents.Event.SCREEN_NON_INTERACTIVE,
            UsageEvents.Event.KEYGUARD_SHOWN -> {
                if (!deviceLocked) {
                    for (p in openSince.keys.toList()) closeApp(p, ts)
                    pendingStop.clear()
                    deviceLocked = true
                }
            }
            UsageEvents.Event.SCREEN_INTERACTIVE,
            UsageEvents.Event.KEYGUARD_HIDDEN -> {
                deviceLocked = false
            }
        }
    }

    // An app is still in the foreground at the end. Count its time up to the end,
    // but only if the device is not locked.
    if (!deviceLocked) {
        for ((pkg, since) in openSince) addTime(pkg, since, end)
    }

    return totals
}

/** The event type ACTIVITY_STOPPED. The constant exists from API 29. Older devices never send it. */
private const val ACTIVITY_STOPPED = 23

/**
 * The format patterns of a short duration. The screens get them from the string resources
 * (see rememberDurationUnits). [hoursMinutes] and [compact] take the hours and then the minutes.
 */
data class DurationUnits(
    val hoursMinutes: String,
    val hours: String,
    val minutes: String,
    val compact: String
)

fun formatDuration(millis: Long, units: DurationUnits): String {
    val totalMinutes = millis / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) units.hoursMinutes.format(hours, minutes) else units.minutes.format(minutes)
}

/** The label next to an app on the home screen: the same format as the home tile. Under 1 minute: null. */
fun appTimeLabel(millis: Long, units: DurationUnits): String? =
    if (millis < 60_000) null else formatDuration(millis, units)

/** Compact format for bar chart labels: "2:30h" or "45m" */
fun formatDurationCompact(millis: Long, units: DurationUnits): String {
    val totalMinutes = millis / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) units.compact.format(hours, minutes) else units.minutes.format(minutes)
}
