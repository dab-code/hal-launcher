package com.dabcode.hal.quiet

import android.app.Notification
import org.json.JSONArray
import org.json.JSONObject

/** The list keeps max this number of entries. */
const val QUIET_MAX_ENTRIES = 100

/** The list drops entries that are older than this. */
const val QUIET_MAX_AGE_MS = 7 * 24 * 60 * 60_000L

/** One notification of a quiet app. HAL keeps no PendingIntent: a tap opens the app through HAL. */
data class QuietEntry(
    val key: String,
    val pkg: String,
    val label: String,
    val title: String,
    val text: String,
    val postTime: Long
)

/** What HAL does with a notification that an app posts. */
enum class QuietAction {
    /** Do not touch the notification. */
    IGNORE,
    /** Remove the notification from the shade. Do not store it. */
    REMOVE,
    /** Store the notification in the list, then remove it from the shade. */
    STORE
}

/**
 * The capture rule. HAL touches only the notifications of a quiet app. It never touches its own
 * notifications, ongoing or foreground-service notifications, notifications that the user cannot clear,
 * media notifications, and the categories call, alarm and navigation.
 * A group summary is removed but not stored: the notifications in the group are the content.
 */
fun quietAction(
    isQuietApp: Boolean,
    isOwnApp: Boolean,
    isOngoing: Boolean,
    isForegroundService: Boolean,
    isClearable: Boolean,
    isMedia: Boolean,
    category: String?,
    isGroupSummary: Boolean
): QuietAction = when {
    !isQuietApp || isOwnApp -> QuietAction.IGNORE
    isOngoing || isForegroundService || !isClearable || isMedia -> QuietAction.IGNORE
    category == Notification.CATEGORY_CALL ||
        category == Notification.CATEGORY_ALARM ||
        category == Notification.CATEGORY_NAVIGATION ||
        category == Notification.CATEGORY_TRANSPORT -> QuietAction.IGNORE
    isGroupSummary -> QuietAction.REMOVE
    else -> QuietAction.STORE
}

/** Return [entries] without the entries older than 7 days, newest first, max 100. */
fun trimQuietEntries(entries: List<QuietEntry>, now: Long): List<QuietEntry> =
    entries
        .filter { now - it.postTime < QUIET_MAX_AGE_MS }
        .sortedByDescending { it.postTime }
        .take(QUIET_MAX_ENTRIES)

/** Add [entry]. An entry with the same key (an update of one notification) replaces the old entry. */
fun addQuietEntry(entries: List<QuietEntry>, entry: QuietEntry, now: Long): List<QuietEntry> =
    trimQuietEntries(entries.filter { it.key != entry.key } + entry, now)

/** Return true when the quiet section and the menu entry show. */
fun showQuietSection(quietApps: Set<String>, entries: List<QuietEntry>): Boolean =
    quietApps.isNotEmpty() || entries.isNotEmpty()

fun quietEntriesToJson(entries: List<QuietEntry>): String = JSONArray().apply {
    entries.forEach {
        put(
            JSONObject()
                .put("key", it.key)
                .put("pkg", it.pkg)
                .put("label", it.label)
                .put("title", it.title)
                .put("text", it.text)
                .put("time", it.postTime)
        )
    }
}.toString()

/** Read the entries from their JSON text. A damaged text gives no entries. */
fun quietEntriesFromJson(json: String?): List<QuietEntry> {
    if (json.isNullOrBlank()) return emptyList()
    return try {
        val array = JSONArray(json)
        (0 until array.length()).map {
            val o = array.getJSONObject(it)
            QuietEntry(
                key = o.getString("key"),
                pkg = o.getString("pkg"),
                label = o.optString("label"),
                title = o.optString("title"),
                text = o.optString("text"),
                postTime = o.getLong("time")
            )
        }
    } catch (_: Exception) {
        emptyList()
    }
}

// ── Review request ──────────────────────────────

/** The review line shows after this number of days. */
const val REVIEW_AFTER_DAYS = 7

/**
 * Return true when the usage screen shows the review line: [REVIEW_AFTER_DAYS] days or more after the
 * first seen day, and only when the user did not act on it before.
 */
fun showReviewPrompt(firstSeenDay: Long?, today: Long, done: Boolean): Boolean =
    !done && firstSeenDay != null && today - firstSeenDay >= REVIEW_AFTER_DAYS
