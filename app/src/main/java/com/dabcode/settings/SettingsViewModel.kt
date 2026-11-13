package com.dabcode.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.dabcode.hal.PauseCounts
import com.dabcode.hal.dayKey

class SettingsViewModel(
    private val repository: SettingsRepository,
    initialIsDarkMode: Boolean = SettingsRepository.DEFAULT_IS_DARK_MODE,
    initialUiStyle: UiStyle = SettingsRepository.DEFAULT_UI_STYLE,
    initialFontSizeScale: String = SettingsRepository.DEFAULT_FONT_SIZE_SCALE
) : ViewModel() {

    // App List Alignment
    val appListAlignment: StateFlow<AppListAlignment> = repository.appListAlignment
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SettingsRepository.DEFAULT_APP_LIST_ALIGNMENT // Use default from repository
        )

    fun updateAppListAlignment(alignment: AppListAlignment) {
        viewModelScope.launch {
            repository.updateAppListAlignment(alignment)
        }
    }

    val pauseBeforeOpen: StateFlow<Boolean> = repository.pauseBeforeOpen
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsRepository.DEFAULT_PAUSE_BEFORE_OPEN)

    fun updatePauseBeforeOpen(enabled: Boolean) {
        viewModelScope.launch { repository.updatePauseBeforeOpen(enabled) }
    }

    // Eagerly: load the counts when the screen starts, so that the first tap gets the correct pause.
    val pauseCounts: StateFlow<PauseCounts> = repository.pauseCounts
        .stateIn(viewModelScope, SharingStarted.Eagerly, PauseCounts())

    /** Count one open of [pkg] after a pause, for today. */
    fun recordPauseOpen(pkg: String) {
        viewModelScope.launch { repository.recordPauseOpen(pkg, dayKey()) }
    }

    // Theme settings — initial values come from cached preferences to avoid dark flash
    val isDarkMode: StateFlow<Boolean> = repository.isDarkMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), initialIsDarkMode)

    fun updateIsDarkMode(isDark: Boolean) {
        viewModelScope.launch { repository.updateIsDarkMode(isDark) }
    }

    val uiStyle: StateFlow<UiStyle> = repository.uiStyle
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), initialUiStyle)

    fun updateUiStyle(style: UiStyle) {
        viewModelScope.launch { repository.updateUiStyle(style) }
    }

    val fontSizeScale: StateFlow<String> = repository.fontSizeScale
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), initialFontSizeScale)

    fun updateFontSizeScale(scale: String) {
        viewModelScope.launch { repository.updateFontSizeScale(scale) }
    }

    // The daily limit (the code names keep "Lockdown")
    val isLockdownEnabled: StateFlow<Boolean> = repository.isLockdownEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsRepository.DEFAULT_IS_LOCKDOWN_ENABLED)

    val lockdownThresholdMinutes: StateFlow<Int> = repository.lockdownThresholdMinutes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsRepository.DEFAULT_LOCKDOWN_THRESHOLD_MINUTES)

    /** Change the daily limit. The caller shows the pause before a loosen (see isLimitLoosening). */
    fun updateDailyLimit(enabled: Boolean, minutes: Int) {
        viewModelScope.launch { repository.updateDailyLimit(enabled, minutes) }
    }

    val showHomeStats: StateFlow<Boolean> = repository.showHomeStats
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsRepository.DEFAULT_SHOW_HOME_STATS)

    fun updateShowHomeStats(show: Boolean) {
        viewModelScope.launch { repository.updateShowHomeStats(show) }
    }

    val showAppScreenTime: StateFlow<Boolean> = repository.showAppScreenTime
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsRepository.DEFAULT_SHOW_APP_SCREEN_TIME)

    fun updateShowAppScreenTime(show: Boolean) {
        viewModelScope.launch { repository.updateShowAppScreenTime(show) }
    }

    val clockFormat: StateFlow<String> = repository.clockFormat
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsRepository.CLOCK_FORMAT_AUTO)

    fun updateClockFormat(format: String) {
        viewModelScope.launch { repository.updateClockFormat(format) }
    }

    val clockSize: StateFlow<String> = repository.clockSize
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsRepository.CLOCK_SIZE_BIG)

    fun updateClockSize(size: String) {
        viewModelScope.launch { repository.updateClockSize(size) }
    }

    val notificationDotsEnabled: StateFlow<Boolean> = repository.notificationDotsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsRepository.DEFAULT_NOTIFICATION_DOTS_ENABLED)

    fun updateNotificationDotsEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.updateNotificationDotsEnabled(enabled) }
    }

    val customAppNames: StateFlow<Map<String, String>> = repository.customAppNames
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** Set the custom name of an app. A null [name] gives the original name back. */
    fun setCustomAppName(componentKey: String, name: String?) {
        viewModelScope.launch { repository.setCustomAppName(componentKey, name) }
    }

    val alwaysAllowedPackages: StateFlow<Set<String>> = repository.alwaysAllowedPackages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    fun updateAlwaysAllowedPackages(packages: Set<String>) {
        viewModelScope.launch { repository.updateAlwaysAllowedPackages(packages) }
    }

    val hiddenApps: StateFlow<Set<String>> = repository.hiddenApps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    fun setAppHidden(pkg: String, hidden: Boolean) {
        viewModelScope.launch { repository.setAppHidden(pkg, hidden) }
    }

    val essentialApps: StateFlow<Set<String>> = repository.essentialApps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    fun setAppEssential(pkg: String, essential: Boolean) {
        viewModelScope.launch { repository.setAppEssential(pkg, essential) }
    }

    val openSingleMatch: StateFlow<Boolean> = repository.openSingleMatch
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsRepository.DEFAULT_OPEN_SINGLE_MATCH)

    fun updateOpenSingleMatch(enabled: Boolean) {
        viewModelScope.launch { repository.updateOpenSingleMatch(enabled) }
    }

    val showJumpBar: StateFlow<Boolean> = repository.showJumpBar
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsRepository.DEFAULT_SHOW_JUMP_BAR)

    fun updateShowJumpBar(enabled: Boolean) {
        viewModelScope.launch { repository.updateShowJumpBar(enabled) }
    }

    val quietApps: StateFlow<Set<String>> = repository.quietApps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    fun updateQuietApps(packages: Set<String>) {
        viewModelScope.launch { repository.updateQuietApps(packages) }
    }

    val showReviewPrompt: StateFlow<Boolean> = repository.showReviewPrompt
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setReviewPromptDone() {
        viewModelScope.launch { repository.setReviewPromptDone() }
    }

    val reminderSettings: StateFlow<ReminderSettings> = repository.reminderSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsRepository.DEFAULT_REMINDER_SETTINGS)

    fun updateUsageReminders(enabled: Boolean) {
        viewModelScope.launch { repository.updateUsageReminders(enabled) }
    }

    fun updateReminderMinutes(minutes: Int) {
        viewModelScope.launch { repository.updateReminderMinutes(minutes) }
    }
}

class SettingsViewModelFactory(
    private val repository: SettingsRepository,
    private val initialIsDarkMode: Boolean = SettingsRepository.DEFAULT_IS_DARK_MODE,
    private val initialUiStyle: UiStyle = SettingsRepository.DEFAULT_UI_STYLE,
    private val initialFontSizeScale: String = SettingsRepository.DEFAULT_FONT_SIZE_SCALE
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(repository, initialIsDarkMode, initialUiStyle, initialFontSizeScale) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}