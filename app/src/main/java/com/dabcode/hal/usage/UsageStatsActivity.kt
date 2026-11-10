package com.dabcode.hal.usage

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dabcode.hal.AppInfo
import com.dabcode.hal.HalThemed
import com.dabcode.hal.applyCustomNames
import com.dabcode.hal.applyThemeBeforeCreate
import com.dabcode.hal.enableHalEdgeToEdge
import com.dabcode.hal.focus.FocusModeActivity
import com.dabcode.hal.focus.FocusModeViewModel
import com.dabcode.hal.getInstalledApps
import com.dabcode.hal.isLockdownActive
import com.dabcode.hal.loadEnabledApps
import com.dabcode.hal.quiet.QuietStore
import com.dabcode.hal.quiet.showQuietSection
import com.dabcode.hal.quiet.trimQuietEntries
import com.dabcode.hal.rememberGuardedOpen
import com.dabcode.hal.settingsViewModels
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UsageStatsActivity : AppCompatActivity() {

    companion object {
        /** Open the screen with the quiet section expanded and in view (the menu entry). */
        const val EXTRA_SHOW_QUIET = "show_quiet"
    }

    private val usageStatsViewModel: UsageStatsViewModel by viewModels()
    private val focusModeViewModel: FocusModeViewModel by viewModels()
    private val settingsViewModel by settingsViewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        applyThemeBeforeCreate()
        super.onCreate(savedInstanceState)
        enableHalEdgeToEdge()
        val startQuietExpanded = intent.getBooleanExtra(EXTRA_SHOW_QUIET, false)
        val quietStore = QuietStore.get(this)
        setContent {
            HalThemed(settingsViewModel) {
                val isLockdownEnabled by settingsViewModel.isLockdownEnabled.collectAsStateWithLifecycle()
                val lockdownThreshold by settingsViewModel.lockdownThresholdMinutes.collectAsStateWithLifecycle()
                val pauseBeforeOpen by settingsViewModel.pauseBeforeOpen.collectAsStateWithLifecycle()
                val pauseCounts by settingsViewModel.pauseCounts.collectAsStateWithLifecycle()
                val alwaysAllowed by settingsViewModel.alwaysAllowedPackages.collectAsStateWithLifecycle()
                val customAppNames by settingsViewModel.customAppNames.collectAsStateWithLifecycle()
                val quietApps by settingsViewModel.quietApps.collectAsStateWithLifecycle()
                val showReview by settingsViewModel.showReviewPrompt.collectAsStateWithLifecycle()
                val storedEntries by quietStore.entries.collectAsStateWithLifecycle()
                val todayTotal by usageStatsViewModel.todayScreenTime.collectAsStateWithLifecycle()
                val context = LocalContext.current

                // The installed apps with the custom names and the home screen state, by package.
                val apps by produceState(initialValue = emptyMap<String, AppInfo>(), customAppNames) {
                    value = withContext(Dispatchers.IO) {
                        applyCustomNames(loadEnabledApps(context, getInstalledApps(context)), customAppNames)
                            .associateBy { it.packageName }
                    }
                }

                // The normal open path: the lock sheet, then the pause. "Close" stays on this screen.
                val requestOpen = rememberGuardedOpen(
                    pauseBeforeOpen = pauseBeforeOpen,
                    pauseCounts = pauseCounts,
                    onPauseOpen = settingsViewModel::recordPauseOpen,
                    lockdownActive = isLockdownActive(isLockdownEnabled, todayTotal, lockdownThreshold),
                    alwaysAllowed = alwaysAllowed,
                    focusModeViewModel = focusModeViewModel,
                    onClose = {}
                )

                UsageStatsScreen(
                    viewModel = usageStatsViewModel,
                    isLockdownEnabled = isLockdownEnabled,
                    lockdownThresholdMinutes = lockdownThreshold,
                    onOpenDailyLimit = {
                        startActivity(
                            Intent(this, FocusModeActivity::class.java)
                                .putExtra(FocusModeActivity.EXTRA_OPEN_DAILY_LIMIT, true)
                        )
                    },
                    onBack = { finish() },
                    showQuiet = showQuietSection(quietApps, storedEntries),
                    quietEntries = trimQuietEntries(storedEntries, System.currentTimeMillis()),
                    quietNames = apps.mapValues { it.value.appName },
                    startQuietExpanded = startQuietExpanded,
                    onOpenQuietEntry = { entry ->
                        quietStore.remove(entry.key)
                        // An app that is not installed anymore only leaves the list.
                        apps[entry.pkg]?.let(requestOpen)
                    },
                    onClearQuiet = { quietStore.clear() },
                    showReview = showReview,
                    onReview = {
                        settingsViewModel.setReviewPromptDone()
                        openPlayListing()
                    },
                    onReviewClose = { settingsViewModel.setReviewPromptDone() }
                )
            }
        }
    }

    /** Open the HAL listing in the Play Store app, or on the web when there is no Play Store. */
    private fun openPlayListing() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.dabcode.hal")))
        } catch (_: ActivityNotFoundException) {
            try {
                startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.dabcode.hal"))
                )
            } catch (_: ActivityNotFoundException) {
                // No Play Store and no browser. Do nothing.
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Only this screen loads the app labels and the weekly data.
        usageStatsViewModel.refresh(withDetails = true)
        // The lock state of the quiet list opens.
        focusModeViewModel.refreshActiveProfile()
    }
}
