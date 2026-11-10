package com.dabcode.hal

import android.app.Notification
import android.content.pm.PackageManager
import com.dabcode.hal.quiet.QuietAction
import com.dabcode.hal.quiet.QuietEntry
import com.dabcode.hal.quiet.QuietStore
import com.dabcode.hal.quiet.quietAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

// Object to hold the state of packages with notifications
// This allows access from other parts of the app (like your HomeScreen/ViewModel)
object NotificationState {
    private val _packagesWithNotifications = MutableStateFlow<Set<String>>(emptySet())
    val packagesWithNotifications = _packagesWithNotifications.asStateFlow()

    /** Set the packages from the active notifications that get a dot. */
    fun setFrom(activeNotifications: Array<StatusBarNotification>?) {
        _packagesWithNotifications.value = activeNotifications
            ?.filter(::isDotWorthy)
            ?.mapNotNull { it.packageName }
            ?.toSet()
            ?: emptySet()
    }

    fun addPackage(packageName: String) {
        _packagesWithNotifications.update { it + packageName }
    }

    fun clear() {
        _packagesWithNotifications.value = emptySet()
    }
}

/** Read the values of [sbn] that the dot rule uses. */
fun isDotWorthy(sbn: StatusBarNotification): Boolean {
    val notification = sbn.notification
    return isDotWorthy(
        isOngoing = sbn.isOngoing,
        isClearable = sbn.isClearable,
        isGroupSummary = (notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0,
        category = notification.category,
        hasMediaSession = notification.extras?.containsKey(Notification.EXTRA_MEDIA_SESSION) == true
    )
}

/**
 * Return true when the notification gets a dot.
 * These do not get a dot: ongoing notifications (navigation), notifications that the user cannot clear,
 * group summaries, and media notifications (a media session, or the "transport" category).
 * A paused player keeps its media notification, so it must not show a dot.
 */
fun isDotWorthy(
    isOngoing: Boolean,
    isClearable: Boolean,
    isGroupSummary: Boolean,
    category: String?,
    hasMediaSession: Boolean
): Boolean =
    !isOngoing && isClearable && !isGroupSummary && !hasMediaSession && category != Notification.CATEGORY_TRANSPORT

/** Read the values of [sbn] that the quiet capture rule uses. */
fun quietAction(sbn: StatusBarNotification, quietApps: Set<String>, ownPackage: String): QuietAction {
    val notification = sbn.notification
    val extras = notification.extras
    val isMedia = extras?.containsKey(Notification.EXTRA_MEDIA_SESSION) == true ||
        extras?.getString(Notification.EXTRA_TEMPLATE)?.contains("MediaStyle") == true
    return quietAction(
        isQuietApp = sbn.packageName in quietApps,
        isOwnApp = sbn.packageName == ownPackage,
        isOngoing = sbn.isOngoing,
        isForegroundService = (notification.flags and Notification.FLAG_FOREGROUND_SERVICE) != 0,
        isClearable = sbn.isClearable,
        isMedia = isMedia,
        category = notification.category,
        isGroupSummary = (notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0
    )
}

class AppNotificationListenerService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // The quiet apps from the settings. Empty until the first read.
    @Volatile private var quietApps: Set<String> = emptySet()

    // True while the listener is connected. Before that, activeNotifications is not available.
    @Volatile private var connected = false

    override fun onCreate() {
        super.onCreate()
        scope.launch {
            (application as HALApplication).settingsRepository.quietApps.collect {
                quietApps = it
                sweepActive()
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        connected = true
        NotificationState.setFrom(activeNotifications)
        sweepActive()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        connected = false
        // The dots can be wrong when the listener is not connected. Remove them.
        NotificationState.clear()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn != null) handle(sbn)
    }

    /** Apply the quiet rule to the notifications that are already in the shade. */
    private fun sweepActive() {
        if (!connected) return
        try {
            activeNotifications?.forEach(::handle)
        } catch (_: SecurityException) {
            // The listener is not bound any more.
        }
    }

    /** The one place that applies the quiet rule to a notification. */
    private fun handle(sbn: StatusBarNotification) {
        when (quietAction(sbn, quietApps, packageName)) {
            QuietAction.IGNORE -> if (isDotWorthy(sbn)) NotificationState.addPackage(sbn.packageName)
            QuietAction.REMOVE -> cancelNotification(sbn.key)
            QuietAction.STORE -> {
                QuietStore.get(this).add(toQuietEntry(sbn))
                cancelNotification(sbn.key)
            }
        }
    }

    private fun toQuietEntry(sbn: StatusBarNotification): QuietEntry {
        val extras = sbn.notification.extras
        val label = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            sbn.packageName
        }
        val text = extras?.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras?.getCharSequence(Notification.EXTRA_TEXT)
        return QuietEntry(
            key = sbn.key,
            pkg = sbn.packageName,
            label = label,
            title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
            text = text?.toString().orEmpty(),
            postTime = sbn.postTime
        )
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        // Compute the set again. Another notification of the same package can keep the dot.
        NotificationState.setFrom(activeNotifications)
    }
}
