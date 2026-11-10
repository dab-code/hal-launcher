package com.dabcode.hal.usage

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UsageStatsViewModel(application: Application) : AndroidViewModel(application) {

    val repository = UsageStatsRepository(application.applicationContext)

    private val _todayScreenTime = MutableStateFlow(0L)
    val todayScreenTime: StateFlow<Long> = _todayScreenTime.asStateFlow()

    // The screen time of today for each app, without the home-screen apps.
    private val _todayPerApp = MutableStateFlow<Map<String, Long>>(emptyMap())
    val todayPerApp: StateFlow<Map<String, Long>> = _todayPerApp.asStateFlow()

    private val _todayPickupCount = MutableStateFlow(0)
    val todayPickupCount: StateFlow<Int> = _todayPickupCount.asStateFlow()

    // 8 days. See UsageStatsRepository.getWeeklyUsage().
    private val _weeklyUsage = MutableStateFlow<List<DayUsage>>(emptyList())
    val weeklyUsage: StateFlow<List<DayUsage>> = _weeklyUsage.asStateFlow()

    // The screen time of each day from Monday to today.
    private val _weekSoFar = MutableStateFlow<List<Long>>(emptyList())
    val weekSoFar: StateFlow<List<Long>> = _weekSoFar.asStateFlow()

    // The screen time of the 7 full days before today. Only the stats screen loads it.
    private val _pastSevenDays = MutableStateFlow<List<Long>>(emptyList())
    val pastSevenDays: StateFlow<List<Long>> = _pastSevenDays.asStateFlow()

    private val _hasPermission = MutableStateFlow(false)
    val hasPermission: StateFlow<Boolean> = _hasPermission.asStateFlow()

    private var refreshJob: Job? = null

    /**
     * Load the usage of today. Call this on resume.
     * Set [withDetails] to true to also load the app labels and the weekly data.
     * Set [withWeek] to true to also load the days from Monday to today.
     * A new call cancels the previous call.
     */
    fun refresh(withDetails: Boolean = false, withWeek: Boolean = false) {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val granted = withContext(Dispatchers.IO) { repository.hasPermission() }
            _hasPermission.value = granted
            if (!granted) {
                _todayScreenTime.value = 0L
                _todayPickupCount.value = 0
                _todayPerApp.value = emptyMap()
                _weeklyUsage.value = emptyList()
                _weekSoFar.value = emptyList()
                return@launch
            }

            val today = withContext(Dispatchers.IO) { repository.getTodayUsage() }
            _todayScreenTime.value = today.totalMs
            _todayPickupCount.value = today.pickups
            _todayPerApp.value = today.perAppMs

            if (withWeek) {
                _weekSoFar.value = withContext(Dispatchers.IO) { repository.getWeekSoFar(today.totalMs) }
            }
            if (withDetails) {
                _weeklyUsage.value = withContext(Dispatchers.IO) { repository.getWeeklyUsage() }
                _pastSevenDays.value = withContext(Dispatchers.IO) {
                    repository.getPastDayTotals(com.dabcode.hal.focus.SUGGESTION_MIN_DAYS)
                }
            }
        }
    }
}
