package com.dabcode.hal

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.ceil

/**
 * The pause before an app opens. A ring fills in [seconds]. "Open" is disabled until the ring is full.
 * "Close" and Back call [onClose] and do not open the app. A tap outside the dialog does nothing.
 */
@Composable
fun PauseDialog(appName: String, seconds: Int, onClose: () -> Unit, onOpen: () -> Unit) {
    val shownName = styledAppName(appName)
    RingDialog(
        title = shownName,
        message = stringResource(R.string.pause_take_breath),
        seconds = seconds,
        actionLabel = stringResource(R.string.pause_open_app, shownName),
        onClose = onClose,
        onAction = onOpen
    )
}

/**
 * The lock sheet: why the app is locked and until when. A large "Close" goes back.
 * "Open for 5 min" is active after the ring is full ([seconds], see FocusModeViewModel.breakPauseSeconds).
 */
@Composable
fun LockSheet(appName: String, message: String, seconds: Int, onClose: () -> Unit, onBreak: () -> Unit) {
    RingDialog(
        title = styledAppName(appName),
        message = message,
        seconds = seconds,
        actionLabel = stringResource(R.string.lock_open_for_break, com.dabcode.hal.focus.BREAK_MINUTES),
        onClose = onClose,
        onAction = onBreak
    )
}

/**
 * The pause before a change that loosens Focus or the daily limit (see LOOSEN_SECONDS).
 * [actionLabel] is active after the ring is full. "Close" keeps the current setting.
 */
@Composable
fun LoosenDialog(title: String, message: String, actionLabel: String, onClose: () -> Unit, onConfirm: () -> Unit) {
    RingDialog(
        title = title,
        message = message,
        seconds = com.dabcode.hal.focus.LOOSEN_SECONDS,
        actionLabel = actionLabel,
        onClose = onClose,
        onAction = onConfirm
    )
}

/**
 * A dialog with a ring that fills in [seconds]. [actionLabel] is disabled until the ring is full.
 * "Close" and Back call [onClose]. A tap outside the dialog does nothing.
 */
@Composable
fun RingDialog(
    title: String,
    message: String,
    seconds: Int,
    actionLabel: String,
    onClose: () -> Unit,
    onAction: () -> Unit
) {
    val p = LocalPalette.current
    val progress = remember { Animatable(0f) }
    LaunchedEffect(seconds) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMillis = seconds * 1000, easing = LinearEasing))
    }
    val isDone = progress.value >= 1f
    val remaining = ceil((1f - progress.value) * seconds).toInt()

    Dialog(onDismissRequest = onClose, properties = DialogProperties(dismissOnClickOutside = false)) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = p.bg,
            contentColor = p.ink,
            border = BorderStroke(1.dp, p.line)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = p.muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(Modifier.height(24.dp))
                Box(Modifier.size(140.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { progress.value },
                        modifier = Modifier.fillMaxSize(),
                        color = p.ink,
                        trackColor = p.empty,
                        strokeWidth = 6.dp
                    )
                    Text(
                        if (isDone) "" else "$remaining",
                        style = MaterialTheme.typography.headlineMedium,
                        color = p.muted
                    )
                }
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = onClose,
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Text(stringResource(R.string.action_close))
                }
                TextButton(
                    onClick = onAction,
                    enabled = isDone,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text(actionLabel, textAlign = TextAlign.Center)
                }
            }
        }
    }
}
