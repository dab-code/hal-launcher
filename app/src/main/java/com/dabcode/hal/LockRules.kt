package com.dabcode.hal

import android.content.Context
import com.dabcode.hal.focus.ActiveFocus
import com.dabcode.hal.focus.FocusPreset
import com.dabcode.hal.focus.FocusProfile
import com.dabcode.hal.focus.formatTime
import java.util.Calendar

/** The reason why an app is locked. */
enum class LockReason { DAILY_LIMIT, FOCUS }

/** Return true when the daily screen time is at or above the daily limit. */
fun isLockdownActive(lockdownEnabled: Boolean, todayScreenTimeMs: Long, thresholdMinutes: Int): Boolean =
    lockdownEnabled && todayScreenTimeMs / 60_000 >= thresholdMinutes

/**
 * Return why [pkg] is locked, or null when it is not locked.
 * Focus and the daily limit use the one shared list [alwaysAllowed]. The daily limit has priority,
 * because it lasts longer. An app on a break ([onBreak]) is not locked.
 */
fun lockReason(
    pkg: String,
    limitReached: Boolean,
    focusActive: Boolean,
    alwaysAllowed: Set<String>,
    onBreak: Boolean = false
): LockReason? {
    if (pkg in FocusProfile.ALWAYS_ALLOWED_PACKAGES || pkg in alwaysAllowed || onBreak) return null
    if (limitReached) return LockReason.DAILY_LIMIT
    if (focusActive) return LockReason.FOCUS
    return null
}

/** Return the name of a Focus as the user sees it: the preset name, the user name, or "Focus". */
fun focusName(context: Context, preset: FocusPreset?, name: String?): String = when {
    preset != null -> context.getString(presetNameRes(preset))
    !name.isNullOrBlank() -> name
    else -> context.getString(R.string.focus_word)
}

fun presetNameRes(preset: FocusPreset): Int = when (preset) {
    FocusPreset.WORK -> R.string.focus_preset_work
    FocusPreset.EVENING -> R.string.focus_preset_evening
    FocusPreset.SLEEP -> R.string.focus_preset_sleep
}

/** Format an epoch time as "HH:MM". */
fun formatClock(epochMs: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = epochMs }
    return formatTime(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
}

/** Return the text that tells the user why the app is locked and when the lock ends. */
fun lockMessage(context: Context, reason: LockReason, focus: ActiveFocus?): String = when {
    reason == LockReason.DAILY_LIMIT -> context.getString(R.string.lock_daily_limit)
    focus == null -> context.getString(R.string.focus_word)
    focus.preset == null && focus.name.isNullOrBlank() ->
        context.getString(R.string.lock_focus_until, formatClock(focus.endsAt))
    else -> context.getString(R.string.lock_named_focus_until, focusName(context, focus.preset, focus.name), formatClock(focus.endsAt))
}
