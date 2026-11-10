package com.dabcode.hal.focus

import android.app.TimePickerDialog
import android.content.Context
import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dabcode.hal.LocalPalette
import com.dabcode.hal.R
import com.dabcode.hal.formatClock
import com.dabcode.hal.presetNameRes
import com.dabcode.hal.usage.formatLimit
import com.dabcode.hal.usage.rememberDurationUnits
import java.util.Calendar

/** The quick lengths of "Focus now", in minutes. */
private val QUICK_MINUTES = listOf(30, 60, 120)

/** Open the system time picker. It uses the 12 h or 24 h format of the phone. */
fun showSystemTimePicker(context: Context, hour: Int, minute: Int, onPick: (hour: Int, minute: Int) -> Unit) {
    TimePickerDialog(context, { _, h, m -> onPick(h, m) }, hour, minute, DateFormat.is24HourFormat(context)).show()
}

/** The text of the home banner and the end sheet: "Work Focus until 17:00" or "Focus until 15:40". */
@Composable
fun focusUntilText(focus: ActiveFocus): String {
    val until = formatClock(focus.endsAt)
    val name = when {
        focus.preset != null -> stringResource(presetNameRes(focus.preset))
        !focus.name.isNullOrBlank() -> focus.name
        else -> return stringResource(R.string.lock_focus_until, until)
    }
    return stringResource(R.string.lock_named_focus_until, name, until)
}

/**
 * "Focus now": the sheet after a long press on the clock. One tap starts Focus at once.
 * [onStart] gets the end (epoch ms) and the preset, or null for a plain Focus.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FocusNowSheet(
    profiles: List<FocusProfile>,
    alwaysAllowedCount: Int,
    onEditAlwaysAllowed: () -> Unit,
    onStart: (until: Long, preset: FocusPreset?) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val units = rememberDurationUnits()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.focus_now_title)) },
        text = {
            Column {
                // The apps that stay open. A tap opens the picker. The count updates on return.
                Text(
                    pluralStringResource(R.plurals.focus_now_always_allowed, alwaysAllowedCount, alwaysAllowedCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalPalette.current.muted,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onEditAlwaysAllowed)
                        .padding(bottom = 12.dp)
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QUICK_MINUTES.forEach { minutes ->
                        FilterChip(
                            selected = false,
                            onClick = { onStart(System.currentTimeMillis() + minutes * 60_000L, null) },
                            label = { Text(formatLimit(minutes, units)) }
                        )
                    }
                    FilterChip(
                        selected = false,
                        onClick = {
                            val now = Calendar.getInstance()
                            showSystemTimePicker(
                                context,
                                (now.get(Calendar.HOUR_OF_DAY) + 1) % 24,
                                0
                            ) { h, m -> onStart(nextTimeOfDay(Calendar.getInstance(), h, m), null) }
                        },
                        label = { Text(stringResource(R.string.focus_until_ellipsis)) }
                    )
                }
                HorizontalDivider(Modifier.padding(vertical = 12.dp), color = LocalPalette.current.line)
                FocusPreset.entries.forEach { preset ->
                    val profile = profileForPreset(profiles, preset)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onStart(nextTimeOfDay(Calendar.getInstance(), profile.endHour, profile.endMinute), preset)
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(presetNameRes(preset)), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text(
                            stringResource(R.string.focus_until_time, profile.endTimeFormatted()),
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalPalette.current.muted
                        )
                    }
                }
                Text(
                    stringResource(R.string.focus_now_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalPalette.current.muted,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

/**
 * The sheet after a tap on the home banner: the end time, "End now" and a link to the Focus screen.
 * "End now" stops only this session. The schedule stays on.
 */
@Composable
fun FocusEndSheet(focus: ActiveFocus, onEndNow: () -> Unit, onOpenFocus: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(focusUntilText(focus)) },
        text = {
            Text(
                stringResource(R.string.focus_end_hint),
                style = MaterialTheme.typography.bodySmall,
                color = LocalPalette.current.muted
            )
        },
        confirmButton = {
            OutlinedButton(onClick = onEndNow) { Text(stringResource(R.string.focus_end_now)) }
        },
        dismissButton = {
            TextButton(onClick = onOpenFocus) { Text(stringResource(R.string.focus_settings_link)) }
        }
    )
}
