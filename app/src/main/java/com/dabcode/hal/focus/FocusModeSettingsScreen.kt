package com.dabcode.hal.focus

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dabcode.hal.LocalPalette
import com.dabcode.hal.LoosenDialog
import com.dabcode.hal.R
import com.dabcode.hal.presetNameRes
import com.dabcode.hal.usage.LockdownAppsActivity
import com.dabcode.hal.usage.formatDuration
import com.dabcode.hal.usage.formatLimit
import com.dabcode.hal.usage.rememberDurationUnits
import com.dabcode.settings.NavigationRow
import com.dabcode.settings.SectionHeader
import com.dabcode.settings.SettingsCard
import java.text.DateFormatSymbols

/** A loosen change that waits for the pause ring. [action] runs after the ring. */
private data class PendingLoosen(val title: String, val message: String, val actionLabel: String, val action: () -> Unit)

/**
 * The Focus screen: the three presets, the Focus items that the user made, the daily limit,
 * and the one shared "Always allowed" list. There is no empty state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusModeSettingsScreen(
    viewModel: FocusModeViewModel,
    limitEnabled: Boolean,
    limitMinutes: Int,
    alwaysAllowedCount: Int,
    onSaveLimit: (enabled: Boolean, minutes: Int) -> Unit,
    openLimitAtStart: Boolean,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val usage by viewModel.limitUsage.collectAsStateWithLifecycle()
    val units = rememberDurationUnits()

    // The Focus in the edit dialog. Null when the dialog is closed.
    var editing by remember { mutableStateOf<FocusProfile?>(null) }
    var showLimit by rememberSaveable { mutableStateOf(openLimitAtStart) }
    var pending by remember { mutableStateOf<PendingLoosen?>(null) }
    var confirmDelete by remember { mutableStateOf<FocusProfile?>(null) }

    val openAlwaysAllowed = { context.startActivity(Intent(context, LockdownAppsActivity::class.java)) }
    val turnOffTitle = stringResource(R.string.focus_turn_off_title)
    val turnOffMessage = stringResource(R.string.loosen_message)
    val turnOffLabel = stringResource(R.string.focus_turn_off)
    val deleteTitle = stringResource(R.string.focus_delete_title)
    val deleteLabel = stringResource(R.string.focus_delete)
    val limitLoosenTitle = stringResource(R.string.limit_loosen_title)
    val limitLoosenMessage = stringResource(R.string.loosen_message)
    val limitLoosenLabel = stringResource(R.string.limit_loosen_action)
    val saveLabel = stringResource(R.string.action_save)

    // Turn on applies at once. Turn off waits for the ring.
    val setEnabled: (FocusProfile, Boolean) -> Unit = { profile, on ->
        if (on) viewModel.saveProfile(profile.copy(isEnabled = true))
        else pending = PendingLoosen(turnOffTitle, turnOffMessage, turnOffLabel) {
            viewModel.saveProfile(profile.copy(isEnabled = false))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.focus_word)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back))
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            SectionHeader(stringResource(R.string.focus_word), stringResource(R.string.focus_screen_subtitle))
            SettingsCard {
                FocusPreset.entries.forEach { preset ->
                    val stored = profiles.firstOrNull { it.preset == preset }
                    // A preset that is not stored shows its default values, and it is off.
                    val profile = stored ?: preset.newProfile().copy(isEnabled = false)
                    FocusRow(
                        profile = profile,
                        onClick = { editing = stored ?: preset.newProfile() },
                        onToggle = { setEnabled(profile, it) }
                    )
                }
                profiles.filter { it.preset == null }.forEach { profile ->
                    FocusRow(profile = profile, onClick = { editing = profile }, onToggle = { setEnabled(profile, it) })
                }
                TextButton(onClick = {
                    editing = FocusPreset.WORK.newProfile().copy(name = "", preset = null)
                }) {
                    Text(stringResource(R.string.focus_new))
                }
            }

            Spacer(Modifier.height(28.dp))

            SettingsCard {
                val average = usage.averageMs
                NavigationRow(
                    title = stringResource(R.string.limit_title),
                    subtitle = when {
                        limitEnabled -> stringResource(R.string.limit_on_summary, formatLimit(limitMinutes, units))
                        average != null -> stringResource(R.string.limit_off_average, formatDuration(average, units))
                        else -> stringResource(R.string.limit_off)
                    },
                    onClick = { showLimit = true }
                )
                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = LocalPalette.current.line)
                NavigationRow(
                    title = stringResource(R.string.always_allowed_count, alwaysAllowedCount),
                    subtitle = stringResource(R.string.always_allowed_subtitle),
                    onClick = openAlwaysAllowed
                )
            }

            // The scope text shows here, one time, and not in each lock text.
            Text(
                stringResource(R.string.focus_scope_note),
                style = MaterialTheme.typography.bodySmall,
                color = LocalPalette.current.muted,
                modifier = Modifier.padding(top = 16.dp, bottom = 24.dp)
            )
        }
    }

    editing?.let { profile ->
        val isStored = profiles.any { it.id == profile.id }
        FocusEditDialog(
            profile = profile,
            isStored = isStored,
            alwaysAllowedCount = alwaysAllowedCount,
            onOpenAlwaysAllowed = openAlwaysAllowed,
            onSave = { updated ->
                editing = null
                // A change to a Focus that is active now can end it early. That is a loosen.
                if (isStored && profile.isActiveNow()) {
                    pending = PendingLoosen(turnOffTitle, turnOffMessage, saveLabel) { viewModel.saveProfile(updated) }
                } else {
                    viewModel.saveProfile(updated)
                }
            },
            onDelete = {
                editing = null
                if (profile.isEnabled) {
                    pending = PendingLoosen(deleteTitle, turnOffMessage, deleteLabel) { viewModel.deleteProfile(profile.id) }
                } else {
                    confirmDelete = profile
                }
            },
            onDismiss = { editing = null }
        )
    }

    confirmDelete?.let { profile ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(deleteTitle) },
            confirmButton = {
                Button(onClick = {
                    viewModel.deleteProfile(profile.id)
                    confirmDelete = null
                }) { Text(deleteLabel) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }

    if (showLimit) {
        DailyLimitDialog(
            enabled = limitEnabled,
            minutes = limitMinutes,
            usage = usage,
            onAllow = { viewModel.openUsageAccessSettings() },
            onOpenAlwaysAllowed = openAlwaysAllowed,
            onSave = { on, minutes ->
                showLimit = false
                if (isLimitLoosening(limitEnabled, limitMinutes, on, minutes)) {
                    pending = PendingLoosen(limitLoosenTitle, limitLoosenMessage, limitLoosenLabel) { onSaveLimit(on, minutes) }
                } else {
                    onSaveLimit(on, minutes)
                }
            },
            onDismiss = { showLimit = false }
        )
    }

    pending?.let { change ->
        LoosenDialog(
            title = change.title,
            message = change.message,
            actionLabel = change.actionLabel,
            onClose = { pending = null },
            onConfirm = {
                change.action()
                pending = null
            }
        )
    }
}

/** One Focus: the name, the time, the days, and the switch. A tap opens the edit dialog. */
@Composable
private fun FocusRow(profile: FocusProfile, onClick: () -> Unit, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(profileTitle(profile), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.focus_time_range, profile.startTimeFormatted(), profile.endTimeFormatted()) +
                    " · " + daysSummary(profile.daysOfWeek),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
            if (profile.isActiveNow()) {
                Text(
                    stringResource(R.string.focus_active_now),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        Switch(checked = profile.isEnabled, onCheckedChange = onToggle)
    }
}

/** The name of a Focus as the user sees it. */
@Composable
private fun profileTitle(profile: FocusProfile): String = when {
    profile.preset != null -> stringResource(presetNameRes(profile.preset))
    profile.name.isNotBlank() -> profile.name
    else -> stringResource(R.string.focus_word)
}

/** "Every day", or the short day names from Monday to Sunday. */
@Composable
private fun daysSummary(days: Set<DayOfWeekCompat>): String {
    if (days.size == DayOfWeekCompat.entries.size) return stringResource(R.string.focus_every_day)
    val names = shortDayNames()
    return days.sortedBy { it.ordinal }.joinToString(", ") { names.getValue(it) }
}

/**
 * Edit one Focus: the times (system time picker), the days, and a name for a Focus without a preset.
 * A new Focus shows "Turn on". A stored Focus shows "Save" and "Delete".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FocusEditDialog(
    profile: FocusProfile,
    isStored: Boolean,
    alwaysAllowedCount: Int,
    onOpenAlwaysAllowed: () -> Unit,
    onSave: (FocusProfile) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var draft by remember(profile.id) { mutableStateOf(profile) }
    val dayNames = shortDayNames()
    // Start = end is never active. Do not accept it.
    val sameTimes = draft.startHour == draft.endHour && draft.startMinute == draft.endMinute
    val canSave = draft.daysOfWeek.isNotEmpty() && !sameTimes

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isStored) profileTitle(profile) else stringResource(R.string.focus_new)) },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                if (draft.preset == null) {
                    OutlinedTextField(
                        value = draft.name,
                        onValueChange = { draft = draft.copy(name = it) },
                        label = { Text(stringResource(R.string.focus_name_optional)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    )
                }
                TimeRow(stringResource(R.string.focus_start_time), draft.startTimeFormatted()) {
                    showSystemTimePicker(context, draft.startHour, draft.startMinute) { h, m ->
                        draft = draft.copy(startHour = h, startMinute = m)
                    }
                }
                TimeRow(stringResource(R.string.focus_end_time), draft.endTimeFormatted()) {
                    showSystemTimePicker(context, draft.endHour, draft.endMinute) { h, m ->
                        draft = draft.copy(endHour = h, endMinute = m)
                    }
                }
                if (sameTimes) {
                    Text(
                        stringResource(R.string.focus_same_times),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    DayOfWeekCompat.entries.forEach { day ->
                        val selected = day in draft.daysOfWeek
                        FilterChip(
                            selected = selected,
                            onClick = {
                                draft = draft.copy(daysOfWeek = if (selected) draft.daysOfWeek - day else draft.daysOfWeek + day)
                            },
                            label = { Text(dayNames.getValue(day)) }
                        )
                    }
                }
                TextButton(onClick = onOpenAlwaysAllowed, modifier = Modifier.padding(top = 8.dp)) {
                    Text(stringResource(R.string.always_allowed_count, alwaysAllowedCount))
                }
                if (isStored) {
                    TextButton(onClick = onDelete) {
                        Text(stringResource(R.string.focus_delete), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(draft.copy(isEnabled = if (isStored) draft.isEnabled else true)) }, enabled = canSave) {
                Text(stringResource(if (isStored) R.string.action_save else R.string.focus_turn_on))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

@Composable
private fun TimeRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

/**
 * The daily limit setup. Without usage access, it shows only the access step.
 * With access, it shows the average of the last 7 days and the suggested limit (70%, rounded to 15 min).
 * [onSave] gets the new state. The caller decides if the change waits for the ring.
 */
@Composable
fun DailyLimitDialog(
    enabled: Boolean,
    minutes: Int,
    usage: LimitUsage,
    onAllow: () -> Unit,
    onOpenAlwaysAllowed: () -> Unit,
    onSave: (enabled: Boolean, minutes: Int) -> Unit,
    onDismiss: () -> Unit
) {
    val units = rememberDurationUnits()
    val average = usage.averageMs
    val suggestion = average?.let { suggestedLimitMinutes(it) }
    var value by rememberSaveable(enabled, minutes, suggestion) {
        mutableStateOf(if (enabled) minutes else suggestion ?: minutes)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.limit_title)) },
        text = {
            Column {
                if (!usage.hasPermission) {
                    // Do not let the user turn on a limit that cannot work.
                    Text(stringResource(R.string.limit_needs_access), style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = onAllow, modifier = Modifier.padding(top = 12.dp)) {
                        Text(stringResource(R.string.action_allow))
                    }
                } else {
                    if (average != null && suggestion != null) {
                        Text(
                            stringResource(R.string.limit_average, formatDuration(average, units)),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            stringResource(R.string.limit_suggested, formatLimit(suggestion, units)),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Button(onClick = { onSave(true, suggestion) }, modifier = Modifier.padding(vertical = 12.dp)) {
                            Text(stringResource(R.string.limit_use, formatLimit(suggestion, units)))
                        }
                        HorizontalDivider(color = LocalPalette.current.line)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(onClick = { value = stepLimit(value, up = false) }) { Text("−") }
                        Text(formatLimit(value, units), style = MaterialTheme.typography.headlineSmall)
                        TextButton(onClick = { value = stepLimit(value, up = true) }) { Text("+") }
                    }
                    TextButton(onClick = onOpenAlwaysAllowed) {
                        Text(stringResource(R.string.always_allowed_shared))
                    }
                    if (enabled) {
                        TextButton(onClick = { onSave(false, minutes) }) {
                            Text(stringResource(R.string.limit_turn_off))
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (usage.hasPermission) {
                Button(onClick = { onSave(true, value) }) {
                    Text(stringResource(if (enabled) R.string.action_save else R.string.focus_turn_on))
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

/** Return the short name of each day in the current language, for example "Mon" or "lun.". */
@Composable
fun shortDayNames(): Map<DayOfWeekCompat, String> {
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale) {
        val names = DateFormatSymbols.getInstance(locale).shortWeekdays
        DayOfWeekCompat.entries.associateWith { names[it.calendarDay] }
    }
}
