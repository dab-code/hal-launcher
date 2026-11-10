package com.dabcode.hal

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = (application as HALApplication).settingsRepository

    // True when HAL is the default launcher
    private val _isDefaultLauncherFlow = MutableStateFlow(isMyAppDefaultLauncher(application))

    // Show the prompt if HAL is not the default launcher and the user was not asked before
    val shouldShowDefaultLauncherPrompt: StateFlow<Boolean> =
        combine(_isDefaultLauncherFlow, settingsRepository.askedDefaultLauncher) { isDefault, asked ->
            !isDefault && !asked
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _showNotificationPrompt = MutableStateFlow(false)
    val showNotificationPrompt: StateFlow<Boolean> = _showNotificationPrompt.asStateFlow()

    // Call this on resume, because the user can change the default launcher in the system settings
    fun checkDefaultLauncherStatus() {
        _isDefaultLauncherFlow.value = isMyAppDefaultLauncher(getApplication())
    }

    fun onSetAsDefaultLauncherClicked() {
        openDefaultLauncherSettings(getApplication())
        // Mark the prompt as shown now. Do not wait for the result of the settings screen.
        markLauncherPromptShown()
    }

    fun markLauncherPromptShown() {
        viewModelScope.launch {
            settingsRepository.setAskedDefaultLauncher(true)
        }
    }

    /**
     * Call this on resume. Show the notification prompt only when the dots are on, the access is off,
     * and the user did not select "Later" before.
     */
    fun checkNotificationAccess() {
        viewModelScope.launch {
            if (!settingsRepository.notificationDotsEnabled.first()) {
                _showNotificationPrompt.value = false
                return@launch
            }
            val enabled = isNotificationServiceEnabled(getApplication())
            val dismissed = settingsRepository.notificationPromptDismissed.first()
            _showNotificationPrompt.value = !enabled && !dismissed
        }
    }

    /** The user opens the system settings. Show the prompt again on resume if the access stays off. */
    fun onNotificationPromptConfirmed() {
        _showNotificationPrompt.value = false
    }

    /** The user selects "Later". Do not show the prompt again. */
    fun onNotificationPromptDismissed() {
        _showNotificationPrompt.value = false
        viewModelScope.launch {
            settingsRepository.setNotificationPromptDismissed(true)
        }
    }
}
