package com.dabcode.hal

import androidx.compose.animation.core.Animatable
import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.keyframes
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
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
    // The ring follows the real time from the frame clock, not an animation. An animation gets shorter with
    // the system setting "Remove animations" (duration scale 0), and then the pause would end at once.
    var progress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(seconds) {
        progress = 0f
        val start = withFrameNanos { it }
        while (progress < 1f) {
            progress = withFrameNanos { ringProgress(it - start, seconds) }
        }
    }
    val isDone = progress >= 1f
    val remaining = ceil((1f - progress) * seconds).toInt()

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
                        progress = { progress },
                        modifier = Modifier.fillMaxSize(),
                        color = p.ink,
                        trackColor = p.empty,
                        strokeWidth = 6.dp
                    )
                    if (isDone) {
                        UnlockMark(color = p.ink)
                    } else {
                        Text("$remaining", style = MaterialTheme.typography.headlineMedium, color = p.muted)
                    }
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

/**
 * A small lock that opens one time. First the closed lock fades in (450 ms). Then the shackle lifts and
 * turns open (450 ms). It goes 7% past the open angle and settles back, one small bounce. Then the open lock stays.
 * With "Remove animations" in the system settings, the open lock shows at once.
 */
@Composable
fun UnlockMark(color: Color, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val alpha = remember { Animatable(0f) }
    val open = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        val scale = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        if (scale == 0f) {
            alpha.snapTo(1f)
            open.snapTo(1f)
        } else {
            alpha.animateTo(1f, tween(durationMillis = 450, easing = LinearEasing))
            open.animateTo(1f, keyframes {
                durationMillis = 450
                0f at 0 using FastOutSlowInEasing
                1.07f at 300 using FastOutSlowInEasing
            })
        }
    }
    Canvas(modifier.size(width = 24.dp, height = 30.dp).graphicsLayer { this.alpha = alpha.value }) {
        val stroke = 2.5.dp.toPx()
        val bodyTop = size.height * 0.48f
        val bodyLeft = stroke / 2
        val bodyRight = size.width - stroke / 2
        // The body: an outline with round corners.
        drawRoundRect(
            color = color,
            topLeft = Offset(bodyLeft, bodyTop),
            size = Size(bodyRight - bodyLeft, size.height - bodyTop - stroke / 2),
            cornerRadius = CornerRadius(3.dp.toPx()),
            style = Stroke(stroke)
        )
        // The shackle: a U on the body. Open lifts it and turns it on the right leg.
        val legLeft = size.width * 0.25f
        val legRight = size.width * 0.75f
        val radius = (legRight - legLeft) / 2
        val legTop = bodyTop - size.height * 0.16f
        val shackle = Path().apply {
            moveTo(legLeft, bodyTop)
            lineTo(legLeft, legTop)
            arcTo(Rect(legLeft, legTop - radius, legRight, legTop + radius), 180f, 180f, false)
            lineTo(legRight, bodyTop)
        }
        val t = open.value
        val lift = 4.dp.toPx() * t
        withTransform({
            translate(top = -lift)
            rotate(degrees = 30f * t, pivot = Offset(legRight, bodyTop))
        }) {
            drawPath(shackle, color = color, style = Stroke(stroke, cap = StrokeCap.Round))
        }
    }
}
