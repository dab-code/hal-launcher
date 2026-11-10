package com.dabcode.hal

import org.json.JSONObject
import java.util.Calendar

// The pause before an app opens from All Apps. The pause grows with each open of the same app on the same day.

const val PAUSE_STEP_SECONDS = 5
const val PAUSE_MAX_SECONDS = 60

/** Return the pause for the next open: 5s for the first open of the day, then 5s more for each open, max 60s. */
fun pauseSeconds(opensToday: Int): Int =
    minOf(PAUSE_STEP_SECONDS * (opensToday.coerceAtLeast(0) + 1), PAUSE_MAX_SECONDS)

/** Return true when the app needs the pause. An app on the home screen opens at once. */
fun needsPause(pauseEnabled: Boolean, isOnHomeScreen: Boolean): Boolean = pauseEnabled && !isOnHomeScreen

/** Return a number for the day of [cal]. The number changes at midnight. */
fun dayKey(cal: Calendar = Calendar.getInstance()): Int =
    cal.get(Calendar.YEAR) * 1000 + cal.get(Calendar.DAY_OF_YEAR)

/** The opens after a pause, for each package, on the day [day]. */
data class PauseCounts(val day: Int = 0, val counts: Map<String, Int> = emptyMap()) {

    /** Return the opens of [pkg] today. The counts of an other day are 0. */
    fun opensToday(pkg: String, today: Int): Int = if (day == today) counts[pkg] ?: 0 else 0

    /** Add one open of [pkg]. On a new day, start again from 0 for all packages. */
    fun withOpen(pkg: String, today: Int): PauseCounts {
        val current = if (day == today) counts else emptyMap()
        return PauseCounts(today, current + (pkg to (current[pkg] ?: 0) + 1))
    }

    fun toJson(): String = JSONObject().apply {
        put("day", day)
        put("counts", JSONObject(counts as Map<*, *>))
    }.toString()

    companion object {
        /** Read the counts from their JSON text. A damaged text gives no counts. */
        fun fromJson(json: String?): PauseCounts {
            if (json.isNullOrBlank()) return PauseCounts()
            return try {
                val obj = JSONObject(json)
                val countsObj = obj.getJSONObject("counts")
                PauseCounts(obj.getInt("day"), countsObj.keys().asSequence().associateWith { countsObj.getInt(it) })
            } catch (_: Exception) {
                PauseCounts()
            }
        }
    }
}
