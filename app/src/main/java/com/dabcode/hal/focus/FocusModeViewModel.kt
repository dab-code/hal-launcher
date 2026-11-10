package com.dabcode.hal.focus

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dabcode.hal.dayKey
import com.dabcode.hal.reminders.BreakEnd
import com.dabcode.hal.usage.UsageStatsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

/** The usage numbers for the daily limit setup. [averageMs] is null when no past day has data. */
data class LimitUsage(val hasPermission: Boolean = false, val averageMs: Long? = null, val daysWithData: Int = 0)

class FocusModeViewModel(application: Application) : AndroidViewModel(application) {

    val repository = FocusModeRepository(application.applicationContext)
    private val usageRepository = UsageStatsRepository(application.applicationContext)

    val profiles: StateFlow<List<FocusProfile>> = repository.profiles
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val session: StateFlow<FocusSession> = repository.session
        .stateIn(viewModelScope, SharingStarted.Eagerly, FocusSession())

    // Eagerly: the lock check on a tap needs the current breaks.
    val breaks: StateFlow<FocusBreaks> = repository.breaks
        .stateIn(viewModelScope, SharingStarted.Eagerly, FocusBreaks())

    private val _activeFocus = MutableStateFlow<ActiveFocus?>(null)
    val activeFocus: StateFlow<ActiveFocus?> = _activeFocus.asStateFlow()

    private val _limitUsage = MutableStateFlow(LimitUsage())
    val limitUsage: StateFlow<LimitUsage> = _limitUsage.asStateFlow()

    private var nextCheckJob: Job? = null

    init {
        // Compute the active Focus again when the profiles or the session change.
        combine(profiles, session) { _, _ -> }.onEach { refreshActiveProfile() }.launchIn(viewModelScope)
    }

    /**
     * Compute the active Focus now. Call this on resume.
     * Then schedule the next check at the next change (a schedule start or end, or the end of a manual Focus).
     */
    fun refreshActiveProfile() {
        val now = Calendar.getInstance()
        val currentProfiles = profiles.value
        val currentSession = session.value
        _activeFocus.value = activeFocus(currentProfiles, currentSession, now)

        nextCheckJob?.cancel()
        val wait = millisUntilNextFocusChange(currentProfiles, currentSession, now) ?: return
        nextCheckJob = viewModelScope.launch {
            delay(wait)
            refreshActiveProfile()
        }
    }

    /** Load the usage access state and the average of the last 7 days. Call this on resume of the Focus screen. */
    fun refreshLimitUsage() {
        viewModelScope.launch {
            _limitUsage.value = withContext(Dispatchers.IO) {
                if (!usageRepository.hasPermission()) return@withContext LimitUsage()
                val days = usageRepository.getPastDayTotals(SUGGESTION_MIN_DAYS)
                LimitUsage(true, averageDayMs(days), days.count { it > 0L })
            }
        }
    }

    fun openUsageAccessSettings() = usageRepository.openUsageAccessSettings()

    /** Add the profile, or replace the profile with the same id. */
    fun saveProfile(profile: FocusProfile) {
        viewModelScope.launch {
            if (profiles.value.any { it.id == profile.id }) repository.updateProfile(profile)
            else repository.addProfile(profile)
        }
    }

    fun deleteProfile(profileId: String) {
        viewModelScope.launch { repository.deleteProfile(profileId) }
    }

    /** Start Focus now until [until] (epoch ms). It applies at once. */
    fun startNow(until: Long, preset: FocusPreset? = null) {
        viewModelScope.launch { repository.changeSession { startFocusNow(it, until, preset) } }
    }

    /** End the Focus of now. The schedules stay on. The caller shows the pause first. */
    fun endNow() {
        val now = System.currentTimeMillis()
        viewModelScope.launch { repository.changeSession { endFocusNow(it, now) } }
    }

    /** Return true while [pkg] has a break. */
    fun isOnBreak(pkg: String): Boolean = breaks.value.isOnBreak(pkg, System.currentTimeMillis())

    /** Return the pause before the next break: 5 s, then 5 s more for each break today, max 60 s. */
    fun breakPauseSeconds(): Int = com.dabcode.hal.pauseSeconds(breaks.value.breaksToday(dayKey()))

    /** Start a break of [BREAK_MINUTES] for [pkg]. At the end, a pop-up shows when the app is still open. */
    fun startBreak(pkg: String) {
        val now = System.currentTimeMillis()
        viewModelScope.launch { repository.addBreak(pkg, now, dayKey()) }
        BreakEnd.schedule(getApplication<Application>(), pkg, now + BREAK_MS)
    }
}
