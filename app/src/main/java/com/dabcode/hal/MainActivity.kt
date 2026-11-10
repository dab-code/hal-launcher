package com.dabcode.hal

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlin.math.abs
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.key
import com.dabcode.hal.focus.FocusEndSheet
import com.dabcode.hal.focus.FocusModeActivity
import com.dabcode.hal.focus.FocusModeViewModel
import com.dabcode.hal.focus.FocusNowSheet
import com.dabcode.hal.usage.LockdownAppsActivity
import com.dabcode.hal.focus.focusUntilText
import com.dabcode.hal.usage.UsageStatsActivity
import com.dabcode.hal.usage.UsageStatsViewModel
import com.dabcode.hal.usage.appTimeLabel
import com.dabcode.hal.usage.formatDuration
import com.dabcode.hal.usage.formatLimit
import com.dabcode.hal.usage.rememberDurationUnits
import com.dabcode.hal.usage.underLimitDays
import com.dabcode.settings.AppListAlignment
import com.dabcode.settings.SettingsRepository
import com.dabcode.settings.UiStyle

// Main activity
class MainActivity : AppCompatActivity() {
    private val mainViewModel: MainViewModel by viewModels()
    private val settingsViewModel by settingsViewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        applyThemeBeforeCreate()
        super.onCreate(savedInstanceState)
        enableHalEdgeToEdge()

        val onBackPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Do nothing to prevent exiting the launcher.
            }
        }
        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)

        setContent {
            HalThemed(settingsViewModel) {
                val showPermissionDialog by mainViewModel.showNotificationPrompt.collectAsStateWithLifecycle()
                val currentAlignment by settingsViewModel.appListAlignment.collectAsStateWithLifecycle()
                val lockdownEnabled by settingsViewModel.isLockdownEnabled.collectAsStateWithLifecycle()
                val lockdownThreshold by settingsViewModel.lockdownThresholdMinutes.collectAsStateWithLifecycle()
                val alwaysAllowed by settingsViewModel.alwaysAllowedPackages.collectAsStateWithLifecycle()
                val showHomeStats by settingsViewModel.showHomeStats.collectAsStateWithLifecycle()
                val showAppScreenTime by settingsViewModel.showAppScreenTime.collectAsStateWithLifecycle()
                val clockSize by settingsViewModel.clockSize.collectAsStateWithLifecycle()

                HomeScreen(
                    mainViewModel = mainViewModel,
                    onRenameApp = settingsViewModel::setCustomAppName,
                    currentAlignment = currentAlignment,
                    isLockdownEnabled = lockdownEnabled,
                    lockdownThresholdMinutes = lockdownThreshold,
                    alwaysAllowed = alwaysAllowed,
                    showHomeStats = showHomeStats,
                    showAppScreenTime = showAppScreenTime,
                    smallClock = clockSize == SettingsRepository.CLOCK_SIZE_SMALL
                )

                // Dialogs overlay the pager
                if (showPermissionDialog) {
                    NotificationPermissionDialog(
                        onConfirm = {
                            requestNotificationAccess(this@MainActivity)
                            mainViewModel.onNotificationPromptConfirmed()
                        },
                        onDismiss = {
                            mainViewModel.onNotificationPromptDismissed()
                            Toast.makeText(this@MainActivity, R.string.toast_dots_need_permission, Toast.LENGTH_LONG).show()
                        }
                    )
                }
            }
        }
    }
}

/**
 * Call BEFORE super.onCreate() to set the correct XML theme (dark/light)
 * so the window background matches from the very first frame.
 */
fun android.app.Activity.applyThemeBeforeCreate() {
    val isDark = (application as? HALApplication)?.cachedIsDarkMode ?: true
    // Set the correct theme before the window decor is created
    setTheme(if (isDark) R.style.Theme_MinimalistLauncher_Dark else R.style.Theme_MinimalistLauncher_Light)
    // Set window background explicitly — overrides the transparent Child theme
    val bgColor = windowBackgroundArgb(isDark)
    window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(bgColor))
    // Kill transition animation so there's no frame showing the wrong color
    @Suppress("DEPRECATION")
    overridePendingTransition(0, 0)
}

/**
 * Draw the screen behind the transparent system bars. The window background shows through the bars.
 * Call after super.onCreate() and before setContent. HalTheme calls it again when the dark mode changes.
 * [isDark] sets the bar icons: light icons on the dark theme, dark icons on the light theme.
 */
fun ComponentActivity.enableHalEdgeToEdge(
    isDark: Boolean = (application as? HALApplication)?.cachedIsDarkMode ?: true
) {
    val style = if (isDark) {
        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
    } else {
        // Android 7 cannot show dark navigation bar icons. Then a dark scrim keeps the light icons readable.
        SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.argb(0x80, 0x1b, 0x1a, 0x18))
    }
    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
}

fun mapAppListAlignmentToTextAlign(alignment: AppListAlignment): TextAlign {
    return when (alignment) {
        AppListAlignment.START -> TextAlign.Start
        AppListAlignment.CENTER -> TextAlign.Center
        AppListAlignment.END -> TextAlign.End
    }
}

fun mapAppListAlignmentToHorizontalAlignment(alignment: AppListAlignment): Alignment.Horizontal {
    return when (alignment) {
        AppListAlignment.START -> Alignment.Start
        AppListAlignment.CENTER -> Alignment.CenterHorizontally
        AppListAlignment.END -> Alignment.End
    }
}

@Composable
fun NotificationPermissionDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground) },
        title = { Text(stringResource(R.string.dialog_permission_title)) },
        text = { Text(stringResource(R.string.dialog_permission_text)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.tertiary)


            ) {
                Text(stringResource(R.string.action_open_settings))

            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.tertiary)
            ) {
                Text(stringResource(R.string.action_later))
            }
        }
    )
}

@Composable
fun DefaultLauncherPromptDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_default_launcher_title)) },
        text = { Text(stringResource(R.string.dialog_default_launcher_text)) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary) // Use theme colors
            ) {
                Text(stringResource(R.string.action_open_settings), color = MaterialTheme.colorScheme.onPrimary)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary) // Use theme colors
            ) {
                Text(stringResource(R.string.action_maybe_later))
            }
        }
    )
}

/**
 * The dialog to rename an app. Save with an empty name or the original name gives the original name back.
 * [onSave] gets the name to store, or null to remove the custom name.
 * With [onUninstall], the dialog also shows "Uninstall" (only when the app can be uninstalled).
 */
@Composable
fun RenameAppDialog(app: AppInfo, onSave: (String?) -> Unit, onDismiss: () -> Unit, onUninstall: (() -> Unit)? = null) {
    var text by remember(app.componentKey) { mutableStateOf(app.appName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_rename_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.label_name)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    stringResource(R.string.rename_original_name, app.originalName),
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalPalette.current.muted,
                    modifier = Modifier.padding(top = 8.dp)
                )
                // A quiet action under the name. The buttons row has no room for a third button.
                if (onUninstall != null && app.canUninstall) {
                    TextButton(
                        onClick = onUninstall,
                        contentPadding = PaddingValues(0.dp),
                        colors = ButtonDefaults.textButtonColors(contentColor = LocalPalette.current.muted),
                        modifier = Modifier.padding(top = 8.dp)
                    ) { Text(stringResource(R.string.action_uninstall)) }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(customNameToStore(text, app.originalName)) }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onSave(null) }) { Text(stringResource(R.string.action_reset)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    mainViewModel: MainViewModel,
    onRenameApp: (componentKey: String, name: String?) -> Unit,
    homeScreenViewModel: HomeScreenViewModel = viewModel(),
    usageStatsViewModel: UsageStatsViewModel = viewModel(),
    focusModeViewModel: FocusModeViewModel = viewModel(),
    currentAlignment: AppListAlignment,
    isLockdownEnabled: Boolean = false,
    lockdownThresholdMinutes: Int = 120,
    alwaysAllowed: Set<String> = emptySet(),
    showHomeStats: Boolean = true,
    showAppScreenTime: Boolean = false,
    smallClock: Boolean = false
) {
    val context = LocalContext.current
    val palette = LocalPalette.current
    val uiStyle = LocalUiStyle.current
    val currentTime by homeScreenViewModel.currentTime.collectAsStateWithLifecycle()
    val currentDate by homeScreenViewModel.currentDate.collectAsStateWithLifecycle()
    val todayScreenTime by usageStatsViewModel.todayScreenTime.collectAsStateWithLifecycle()
    val todayPickups by usageStatsViewModel.todayPickupCount.collectAsStateWithLifecycle()
    val todayPerApp by usageStatsViewModel.todayPerApp.collectAsStateWithLifecycle()
    val weekSoFar by usageStatsViewModel.weekSoFar.collectAsStateWithLifecycle()
    val hasUsagePermission by usageStatsViewModel.hasPermission.collectAsStateWithLifecycle()
    val activeFocus by focusModeViewModel.activeFocus.collectAsStateWithLifecycle()
    val focusProfiles by focusModeViewModel.profiles.collectAsStateWithLifecycle()
    // Collect the breaks, so that the lock marks update after a break starts.
    val breaks by focusModeViewModel.breaks.collectAsStateWithLifecycle()
    val breakOpen = rememberBreakOpen(focusModeViewModel)

    // The list of apps to display, and the loading state
    val homeUiState by homeScreenViewModel.uiState.collectAsStateWithLifecycle()
    val displayedApps = homeUiState.apps
    val isLoadingApps = homeUiState.isLoading

    val shouldShowDefaultLauncherPrompt by mainViewModel.shouldShowDefaultLauncherPrompt.collectAsStateWithLifecycle()
    // The app in the rename dialog. Null when the dialog is closed.
    var appToRename by remember { mutableStateOf<AppInfo?>(null) }
    // The locked app in the lock sheet, and the pause before its break. Null when the sheet is closed.
    var lockedApp by remember { mutableStateOf<Pair<AppInfo, LockReason>?>(null) }
    var breakSeconds by remember { mutableStateOf(PAUSE_STEP_SECONDS) }
    var showFocusNow by remember { mutableStateOf(false) }
    var showFocusEnd by remember { mutableStateOf(false) }
    var confirmEndNow by remember { mutableStateOf(false) }
    val openFocus = { context.startActivity(Intent(context, FocusModeActivity::class.java)) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, homeScreenViewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                // Update the clock only while the home screen is visible.
                Lifecycle.Event.ON_START -> homeScreenViewModel.startClock()
                Lifecycle.Event.ON_STOP -> homeScreenViewModel.stopClock()
                Lifecycle.Event.ON_RESUME -> {
                    homeScreenViewModel.updateClock()
                    homeScreenViewModel.reloadApps()
                    // The past days of the week are cached, so the week costs only the scan of today.
                    usageStatsViewModel.refresh(withWeek = true)
                    focusModeViewModel.refreshActiveProfile()
                    // The user can change these in the system settings. Check them again.
                    mainViewModel.checkNotificationAccess()
                    mainViewModel.checkDefaultLauncherStatus()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            homeScreenViewModel.stopClock()
        }
    }

    // Compute the lock state one time for all items.
    val lockdownActive = isLockdownActive(isLockdownEnabled, todayScreenTime, lockdownThresholdMinutes)
    // Without usage access the screen time stays 0, so the daily limit cannot lock. Tell the user.
    val lockdownNeedsPermission = isLockdownEnabled && !hasUsagePermission
    val nowMs = System.currentTimeMillis()
    val lockReasonOf: (String) -> LockReason? = { pkg ->
        lockReason(pkg, lockdownActive, activeFocus != null, alwaysAllowed, breaks.isOnBreak(pkg, nowMs))
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val textAlign = mapAppListAlignmentToTextAlign(currentAlignment)

    val openUsageStats = { context.startActivity(Intent(context, UsageStatsActivity::class.java)) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemGestureExclusion()
            // Keep the content out of the status bar and the navigation bar.
            .systemBarsPadding()
            .padding(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 16.dp)
            // Swipe left: open all apps. Swipe right: open the usage stats.
            .horizontalSwipe(
                onSwipeLeft = { context.startActivity(Intent(context, ManageAppsActivity::class.java)) },
                onSwipeRight = { openUsageStats() }
            )
    ) {
        // Settings Icon Button
        IconButton(
            onClick = {
                val intent = Intent(context, ManageAppsActivity::class.java)
                context.startActivity(intent)
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(horizontal = 0.dp, vertical = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Menu,
                contentDescription = stringResource(R.string.cd_menu),
                tint = MaterialTheme.colorScheme.onBackground
            )
        }

        // Main content column (time, date, focus, stats, apps)
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // A long press on the clock or the date opens "Focus now".
            // The touch area is at least 48dp high, also with the small clock.
            Column(
                modifier = Modifier
                    .padding(top = if (isLandscape) 32.dp else 68.dp, bottom = 16.dp, start = 8.dp, end = 8.dp)
                    .fillMaxWidth()
                    .combinedClickable(onClick = {}, onLongClick = { showFocusNow = true })
                    .heightIn(min = 48.dp),
                verticalArrangement = Arrangement.Center
            ) {
                val bigClock = MaterialTheme.typography.headlineLarge
                Text(
                    text = currentTime,
                    // The small clock is a little larger than the date line.
                    style = if (smallClock) bigClock.copy(fontSize = bigClock.fontSize * SMALL_CLOCK_SCALE) else bigClock,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = textAlign
                )
                Text(
                    text = currentDate,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 12.sp),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = textAlign
                )
            }

            val focus = activeFocus
            if (focus != null) {
                FocusBanner(
                    text = focusUntilText(focus),
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .padding(bottom = 12.dp)
                        .clickable { showFocusEnd = true }
                )
            }

            // Without usage access the values are not known. Show a dash, and a link to the access settings.
            val noValue = "\u2013"
            val openAccessSettings = { usageStatsViewModel.repository.openUsageAccessSettings() }
            val units = rememberDurationUnits()
            val todayNote = stringResource(R.string.stat_today)
            if (showHomeStats) StatPair(
                // "today" is in the label, so the tiles need no note line for it.
                first = Stat(
                    label = stringResource(R.string.stat_label_period, stringResource(R.string.stat_pickups), todayNote),
                    value = if (hasUsagePermission) "$todayPickups" else noValue
                ),
                second = Stat(
                    label = stringResource(R.string.stat_label_period, stringResource(R.string.stat_screen_time), todayNote),
                    value = if (hasUsagePermission) formatDuration(todayScreenTime, units) else noValue,
                    // Only the daily limit has a note line.
                    note = if (isLockdownEnabled) stringResource(R.string.stat_of_limit, formatLimit(lockdownThresholdMinutes, units)) else null,
                    // The "of 2h" note opens Focus.
                    onNoteClick = if (isLockdownEnabled) openFocus else null
                ),
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .clickable { if (hasUsagePermission) openUsageStats() else openAccessSettings() }
            )
            if (!showHomeStats && !showAppScreenTime) {
                // The user hid the tiles and the app times. Show no access link and no weekly count.
            } else if (!hasUsagePermission) {
                Text(
                    text = stringResource(if (lockdownNeedsPermission) R.string.home_limit_needs_access else R.string.home_grant_access),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = palette.ink,
                    modifier = Modifier
                        .padding(start = 8.dp, end = 8.dp, top = 12.dp)
                        .fillMaxWidth()
                        .clickable { openAccessSettings() },
                    textAlign = textAlign
                )
            } else if (showHomeStats && isLockdownEnabled && weekSoFar.isNotEmpty()) {
                val underLimit = underLimitDays(weekSoFar, lockdownThresholdMinutes)
                // A day with no usage data does not count.
                val daysWithData = underLimit.count { it != null }
                if (daysWithData > 0) {
                    Row(
                        modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (uiStyle == UiStyle.PAPER) {
                            WeekSquares(underLimit, Modifier.padding(end = 12.dp))
                        }
                        Text(
                            text = pluralStringResource(
                                R.plurals.home_under_limit_days, daysWithData, underLimit.count { it == true }, daysWithData
                            ),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = palette.muted
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(if (isLandscape) 16.dp else 24.dp))

            // App List
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalAlignment = mapAppListAlignmentToHorizontalAlignment(currentAlignment),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 8.dp)
            ) {
                if (isLoadingApps && displayedApps.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.home_loading_apps),
                            style = MaterialTheme.typography.bodyLarge,
                            color = palette.muted,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                    }
                } else if (displayedApps.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.home_no_apps),
                            style = MaterialTheme.typography.bodyLarge,
                            color = palette.muted,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                    }
                    item {
                        Button(
                            onClick = {
                                val intent = Intent(context, ManageAppsActivity::class.java)
                                context.startActivity(intent)
                            }
                        ) {
                            Text(stringResource(R.string.action_enable_apps))
                        }
                    }
                } else {
                    items(displayedApps, key = { it.packageName }) { app ->
                        AppListItem(
                            appInfo = app,
                            textAlign = textAlign,
                            timeLabel = if (showAppScreenTime) appTimeLabel(todayPerApp[app.packageName] ?: 0L, units) else null,
                            isLocked = lockReasonOf(app.packageName) != null,
                            onLongClick = { appToRename = app }
                        ) {
                            // Check again at the tap: a break can end while the screen shows.
                            val reason = lockReason(
                                app.packageName, lockdownActive, focusModeViewModel.activeFocus.value != null,
                                alwaysAllowed, focusModeViewModel.isOnBreak(app.packageName)
                            )
                            if (reason == null) {
                                launchApp(context, app.packageName)
                            } else {
                                breakSeconds = focusModeViewModel.breakPauseSeconds()
                                lockedApp = app to reason
                            }
                        }
                    }
                }
            }
        }

        lockedApp?.let { (app, reason) ->
            key(app.packageName) {
                LockSheet(
                    appName = app.appName,
                    message = lockMessage(context, reason, activeFocus),
                    seconds = breakSeconds,
                    onClose = { lockedApp = null },
                    onBreak = {
                        lockedApp = null
                        breakOpen(app.packageName)
                    }
                )
            }
        }

        if (showFocusNow) {
            FocusNowSheet(
                profiles = focusProfiles,
                // Count only installed apps. The sheet reads it again each time it opens.
                alwaysAllowedCount = remember(alwaysAllowed) { installedCount(context, alwaysAllowed) },
                // The sheet stays open. The count comes from the settings, so it updates on return.
                onEditAlwaysAllowed = { context.startActivity(Intent(context, LockdownAppsActivity::class.java)) },
                onStart = { until, preset ->
                    focusModeViewModel.startNow(until, preset)
                    showFocusNow = false
                },
                onDismiss = { showFocusNow = false }
            )
        }

        val endFocus = activeFocus
        if (showFocusEnd && endFocus != null) {
            FocusEndSheet(
                focus = endFocus,
                onEndNow = {
                    showFocusEnd = false
                    confirmEndNow = true
                },
                onOpenFocus = {
                    showFocusEnd = false
                    openFocus()
                },
                onDismiss = { showFocusEnd = false }
            )
        }

        if (confirmEndNow) {
            // "End now" is a loosen. It waits for the ring.
            LoosenDialog(
                title = stringResource(R.string.focus_end_now_title),
                message = stringResource(R.string.focus_end_hint),
                actionLabel = stringResource(R.string.focus_end_now),
                onClose = { confirmEndNow = false },
                onConfirm = {
                    focusModeViewModel.endNow()
                    confirmEndNow = false
                }
            )
        }

        appToRename?.let { app ->
            RenameAppDialog(
                app = app,
                onSave = { name ->
                    onRenameApp(app.componentKey, name)
                    appToRename = null
                },
                onDismiss = { appToRename = null },
                onUninstall = {
                    appToRename = null
                    requestUninstall(context, app.packageName)
                }
            )
        }

        // Show the Default Launcher Prompt Dialog if needed
        if (shouldShowDefaultLauncherPrompt) {
            DefaultLauncherPromptDialog(
                onConfirm = {
                    mainViewModel.onSetAsDefaultLauncherClicked()
                },
                onDismiss = {
                    mainViewModel.markLauncherPromptShown()
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppListItem(
    appInfo: AppInfo,
    textAlign: TextAlign,
    timeLabel: String? = null, // The screen time of today, for example "42m". Null shows no label.
    isLocked: Boolean = false, // A locked app shows muted, with a lock mark. A tap still calls onAppClick.
    onLongClick: () -> Unit = {}, // A long press does not open the app
    onAppClick: () -> Unit
) {
    val palette = LocalPalette.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onLongClick = onLongClick,
                onClick = onAppClick
            )
            .padding(horizontal = 8.dp, vertical = 12.dp)
    ) {
        // The time label sits next to the name: after it (START) or before it (END).
        // In the centre, the label is at the end edge. An invisible copy at the start keeps the name centred.
        // The label never changes the position of the name. A long name wraps instead.
        val isCenter = textAlign == TextAlign.Center
        val labelGap = 10.dp
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (timeLabel != null && isCenter) {
                AppTimeLabel(timeLabel, visible = false, modifier = Modifier.padding(end = 12.dp))
            }
            // A locked app shows in the muted color, with a small lock mark after the name.
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = when (textAlign) {
                    TextAlign.Start -> Arrangement.Start
                    TextAlign.End -> Arrangement.End
                    else -> Arrangement.Center
                },
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (timeLabel != null && textAlign == TextAlign.End) {
                    AppTimeLabel(timeLabel, visible = true, modifier = Modifier.padding(end = labelGap).alignByBaseline())
                }
                Text(
                    text = styledAppName(appInfo.appName),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = textAlign,
                    modifier = Modifier.weight(1f, fill = false).alignByBaseline(),
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    color = if (isLocked) palette.muted else Color.Unspecified
                )
                if (isLocked) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = stringResource(R.string.cd_locked),
                        tint = palette.muted,
                        modifier = Modifier.padding(start = 6.dp).size(14.dp)
                    )
                }
                if (timeLabel != null && textAlign == TextAlign.Start) {
                    AppTimeLabel(timeLabel, visible = true, modifier = Modifier.padding(start = labelGap).alignByBaseline())
                }
            }
            if (timeLabel != null && isCenter) {
                AppTimeLabel(timeLabel, visible = true, modifier = Modifier.padding(start = 12.dp))
            }
        }

        if (appInfo.hasNotifications && !isLocked) {
            Box(
                modifier = Modifier
                    .align(if (textAlign == TextAlign.Start) Alignment.TopEnd else Alignment.TopStart)
                    .padding(top = 4.dp, end = 4.dp)
                    .size(8.dp)
                    .background(palette.ink, CircleShape)
            )
        }
    }
}

/** The size of the small clock, relative to the big clock (36sp at the medium font size: about 20sp). */
private const val SMALL_CLOCK_SCALE = 0.55f

/** The quiet screen time label of an app row. An invisible label only takes its space. */
@Composable
private fun AppTimeLabel(text: String, visible: Boolean, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = LocalPalette.current.muted,
        maxLines = 1,
        modifier = if (visible) modifier else modifier.alpha(0f).clearAndSetSemantics {}
    )
}

/**
 * Detect a horizontal swipe on the whole area, also over the app list.
 * The parent sees each touch event before the list (Initial pass). The swipe takes the gesture only when
 * the horizontal movement is clearly larger than the vertical movement. Else the list scrolls as usual.
 * A swipe triggers after 32dp, or on a fast fling.
 */
private fun Modifier.horizontalSwipe(onSwipeLeft: () -> Unit, onSwipeRight: () -> Unit): Modifier =
    pointerInput(Unit) {
        val minDistance = 32.dp.toPx()
        val minVelocity = 800.dp.toPx() // per second
        val slop = viewConfiguration.touchSlop
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val tracker = VelocityTracker()
            tracker.addPosition(down.uptimeMillis, down.position)
            var isSwipe = false
            while (true) {
                val change = awaitPointerEvent(PointerEventPass.Initial).changes
                    .firstOrNull { it.id == down.id } ?: break
                tracker.addPosition(change.uptimeMillis, change.position)
                val dx = change.position.x - down.position.x
                val dy = change.position.y - down.position.y
                if (!isSwipe) {
                    // A vertical movement belongs to the list. Stop and do not touch the events.
                    if (abs(dy) > slop && abs(dy) >= abs(dx)) break
                    if (abs(dx) > slop && abs(dx) > 2 * abs(dy)) isSwipe = true
                }
                // After the swipe starts, the list and the app items do not get the events.
                if (isSwipe) change.consume()
                if (!change.pressed) {
                    if (isSwipe) {
                        val vx = tracker.calculateVelocity().x
                        when {
                            dx < 0 && (-dx >= minDistance || -vx >= minVelocity) -> onSwipeLeft()
                            dx > 0 && (dx >= minDistance || vx >= minVelocity) -> onSwipeRight()
                        }
                    }
                    break
                }
            }
        }
    }
