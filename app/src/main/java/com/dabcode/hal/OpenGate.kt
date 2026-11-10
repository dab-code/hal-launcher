package com.dabcode.hal

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dabcode.hal.focus.FocusModeViewModel
import com.dabcode.hal.reminders.BreakEnd

/**
 * The normal open path of All Apps and the quiet list. Return the function that opens an app,
 * and show the lock sheet or the pause dialog when one is needed.
 * The lock rules win: a locked app shows the lock sheet. An app on the home screen opens at once.
 * Other apps open after the pause dialog. "Close" in a dialog calls [onClose].
 */
@Composable
fun rememberGuardedOpen(
    pauseBeforeOpen: Boolean,
    pauseCounts: PauseCounts,
    onPauseOpen: (pkg: String) -> Unit,
    lockdownActive: Boolean,
    alwaysAllowed: Set<String>,
    focusModeViewModel: FocusModeViewModel,
    onClose: () -> Unit
): (AppInfo) -> Unit {
    val context = LocalContext.current
    val activeFocus by focusModeViewModel.activeFocus.collectAsStateWithLifecycle()
    val breakOpen = rememberBreakOpen(focusModeViewModel)
    // The app in the pause dialog, and its pause. Null when the dialog is closed.
    var appToPause by remember { mutableStateOf<AppInfo?>(null) }
    var pauseForApp by remember { mutableStateOf(PAUSE_STEP_SECONDS) }
    // The locked app in the lock sheet. Null when the sheet is closed.
    var lockedApp by remember { mutableStateOf<Pair<AppInfo, LockReason>?>(null) }
    var breakSeconds by remember { mutableStateOf(PAUSE_STEP_SECONDS) }

    lockedApp?.let { (app, reason) ->
        key(app.packageName) {
            LockSheet(
                appName = app.appName,
                message = lockMessage(context, reason, activeFocus),
                seconds = breakSeconds,
                onClose = {
                    lockedApp = null
                    onClose()
                },
                onBreak = {
                    // The ring of the lock sheet was the pause. The app opens at once.
                    lockedApp = null
                    breakOpen(app.packageName)
                }
            )
        }
    }

    appToPause?.let { app ->
        key(app.packageName) {
            PauseDialog(
                appName = app.appName,
                seconds = pauseForApp,
                onClose = {
                    appToPause = null
                    onClose()
                },
                onOpen = {
                    // Count the open only here, when the user taps "Open".
                    onPauseOpen(app.packageName)
                    appToPause = null
                    launchApp(context, app.packageName)
                }
            )
        }
    }

    return { app ->
        // Check again at the tap: a Focus or a break can change while the screen shows.
        val reason = lockReason(
            app.packageName, lockdownActive, focusModeViewModel.activeFocus.value != null,
            alwaysAllowed, focusModeViewModel.isOnBreak(app.packageName)
        )
        when {
            reason != null -> {
                breakSeconds = focusModeViewModel.breakPauseSeconds()
                lockedApp = app to reason
            }
            needsPause(pauseBeforeOpen, isOnHomeScreen = app.isEnabled) -> {
                pauseForApp = pauseSeconds(pauseCounts.opensToday(app.packageName, dayKey()))
                appToPause = app
            }
            else -> launchApp(context, app.packageName)
        }
    }
}

/**
 * Return the function for "Open for 5 min": it starts the break and opens the app.
 * On Android 13+ without the notification permission, it first asks for the permission one time,
 * because the pop-up at the end of the break needs it. The break starts in all cases.
 * The app opens after the answer, so the app does not hide the permission dialog.
 */
@Composable
fun rememberBreakOpen(focusModeViewModel: FocusModeViewModel): (pkg: String) -> Unit {
    val context = LocalContext.current
    // The app that opens after the permission dialog. Null when no dialog shows.
    var pendingPkg by rememberSaveable { mutableStateOf<String?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        pendingPkg?.let { launchApp(context, it) }
        pendingPkg = null
    }
    return { pkg ->
        focusModeViewModel.startBreak(pkg)
        if (BreakEnd.shouldAskPermission(context)) {
            BreakEnd.markPermissionAsked(context)
            pendingPkg = pkg
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            launchApp(context, pkg)
        }
    }
}
