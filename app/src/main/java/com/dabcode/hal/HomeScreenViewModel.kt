package com.dabcode.hal

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.os.UserHandle
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private fun getCurrentTime(): String {
    val deviceLocale = Locale.getDefault()
    val timeFormat = DateFormat.getTimeInstance(DateFormat.SHORT, deviceLocale)
    return timeFormat.format(Date())
}

private fun getCurrentDate(): String {
    val deviceLocale = Locale.getDefault()
    val dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM, deviceLocale)
    return dateFormat.format(Date())
}

/** The state of the home app list. */
data class HomeUiState(val apps: List<AppInfo> = emptyList(), val isLoading: Boolean = true)

class HomeScreenViewModel(application: Application) : AndroidViewModel(application) {
    private val context: Context
        get() = getApplication<Application>().applicationContext

    // The enabled apps. Null until the first load completes.
    private val enabledApps = MutableStateFlow<List<AppInfo>?>(null)
    private var loadJob: Job? = null

    private val settingsRepository = (application as HALApplication).settingsRepository

    // Combine the cached app list with the notification set. A notification does not reload the apps.
    // When the user turns the dots off, no app has a dot, so the order is alphabetical.
    val uiState: StateFlow<HomeUiState> =
        combine(
            enabledApps,
            NotificationState.packagesWithNotifications,
            settingsRepository.notificationDotsEnabled,
            settingsRepository.customAppNames
        ) { apps, packages, dotsEnabled, customNames ->
            if (apps == null) {
                HomeUiState(isLoading = true)
            } else {
                HomeUiState(
                    // The custom names come first, so that the order uses them.
                    apps = applyCustomNames(apps, customNames)
                        .map { it.copy(hasNotifications = dotsEnabled && it.packageName in packages) }
                        .sortedWith(compareBy({ !it.hasNotifications }, { it.appName.lowercase() })),
                    isLoading = false
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    private val _currentTime = MutableStateFlow(getCurrentTime())
    val currentTime: StateFlow<String> = _currentTime

    private val _currentDate = MutableStateFlow(getCurrentDate())
    val currentDate: StateFlow<String> = _currentDate

    // The system sends ACTION_TIME_TICK at the start of each minute. The status-bar clock uses the same signal.
    private val clockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            updateClock()
        }
    }
    private var clockRegistered = false

    // Reload the app list when the user installs, removes or changes an app.
    private val launcherAppsCallback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) = reloadApps()
        override fun onPackageAdded(packageName: String, user: UserHandle) = reloadApps()
        override fun onPackageChanged(packageName: String, user: UserHandle) = reloadApps()
        override fun onPackagesAvailable(packageNames: Array<String>, user: UserHandle, replacing: Boolean) = reloadApps()
        override fun onPackagesUnavailable(packageNames: Array<String>, user: UserHandle, replacing: Boolean) = reloadApps()
    }
    private val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps

    init {
        launcherApps.registerCallback(launcherAppsCallback)
    }

    /** Load the enabled apps again. A new call cancels the previous load, so old data cannot overwrite new data. */
    fun reloadApps() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            enabledApps.value = withContext(Dispatchers.IO) { getEnabledApps(context) }
        }
    }

    fun updateClock() {
        _currentTime.value = getCurrentTime()
        _currentDate.value = getCurrentDate()
    }

    /** Call this on start. Update the clock at each minute until stopClock(). */
    fun startClock() {
        if (clockRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(context, clockReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        clockRegistered = true
        updateClock()
    }

    /** Call this on stop. */
    fun stopClock() {
        if (!clockRegistered) return
        context.unregisterReceiver(clockReceiver)
        clockRegistered = false
    }

    override fun onCleared() {
        stopClock()
        launcherApps.unregisterCallback(launcherAppsCallback)
        super.onCleared()
    }
}
