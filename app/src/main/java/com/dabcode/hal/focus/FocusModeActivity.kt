package com.dabcode.hal.focus

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dabcode.hal.HalThemed
import com.dabcode.hal.applyThemeBeforeCreate
import com.dabcode.hal.enableHalEdgeToEdge
import com.dabcode.hal.settingsViewModels

class FocusModeActivity : AppCompatActivity() {

    private val focusModeViewModel: FocusModeViewModel by viewModels()
    private val settingsViewModel by settingsViewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        applyThemeBeforeCreate()
        super.onCreate(savedInstanceState)
        enableHalEdgeToEdge()
        val openLimit = intent.getBooleanExtra(EXTRA_OPEN_DAILY_LIMIT, false)
        setContent {
            HalThemed(settingsViewModel) {
                val limitEnabled by settingsViewModel.isLockdownEnabled.collectAsStateWithLifecycle()
                val limitMinutes by settingsViewModel.lockdownThresholdMinutes.collectAsStateWithLifecycle()
                val alwaysAllowed by settingsViewModel.alwaysAllowedPackages.collectAsStateWithLifecycle()
                FocusModeSettingsScreen(
                    viewModel = focusModeViewModel,
                    limitEnabled = limitEnabled,
                    limitMinutes = limitMinutes,
                    alwaysAllowedCount = alwaysAllowed.size,
                    onSaveLimit = settingsViewModel::updateDailyLimit,
                    openLimitAtStart = openLimit,
                    onBack = { finish() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // The user can come back from the usage access screen.
        focusModeViewModel.refreshLimitUsage()
    }

    companion object {
        /** Open the daily limit setup at once. The stats screen card uses it. */
        const val EXTRA_OPEN_DAILY_LIMIT = "open_daily_limit"
    }
}
