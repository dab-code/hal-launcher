package com.dabcode.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.dabcode.hal.AppInfo
import com.dabcode.hal.applyCustomNames
import com.dabcode.hal.getInstalledApps
import com.dabcode.hal.normalizeForSearch
import com.dabcode.hal.reminders.REMINDER_INTERVALS
import com.dabcode.hal.reminders.UsageReminders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.dabcode.hal.R
import android.content.Intent
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dabcode.hal.HalThemed
import com.dabcode.hal.settingsViewModels
import com.dabcode.hal.applyThemeBeforeCreate
import com.dabcode.hal.enableHalEdgeToEdge
import com.dabcode.hal.StyleBox
import com.dabcode.hal.focus.FocusModeActivity
import com.dabcode.hal.usage.UsageStatsActivity

@OptIn(ExperimentalMaterial3Api::class)
class UserSettingsActivity : AppCompatActivity() {

    private val settingsViewModel by settingsViewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        applyThemeBeforeCreate()
        super.onCreate(savedInstanceState)
        enableHalEdgeToEdge()
        setContent {
            HalThemed(settingsViewModel) {
                val isDarkMode by settingsViewModel.isDarkMode.collectAsStateWithLifecycle()
                val uiStyle by settingsViewModel.uiStyle.collectAsStateWithLifecycle()
                val fontSizeScale by settingsViewModel.fontSizeScale.collectAsStateWithLifecycle()
                val currentAlignment by settingsViewModel.appListAlignment.collectAsStateWithLifecycle()
                val pauseBeforeOpen by settingsViewModel.pauseBeforeOpen.collectAsStateWithLifecycle()

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(stringResource(R.string.title_user_settings)) },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.background,
                                titleContentColor = MaterialTheme.colorScheme.onBackground,
                                navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                                actionIconContentColor = MaterialTheme.colorScheme.onBackground
                            ),
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back))
                                }
                            }
                        )
                    }
                ) { innerPadding ->
                    val showHomeStats by settingsViewModel.showHomeStats.collectAsStateWithLifecycle()
                    val notificationDotsEnabled by settingsViewModel.notificationDotsEnabled.collectAsStateWithLifecycle()
                    val reminderSettings by settingsViewModel.reminderSettings.collectAsStateWithLifecycle()
                    val openSingleMatch by settingsViewModel.openSingleMatch.collectAsStateWithLifecycle()
                    val hiddenApps by settingsViewModel.hiddenApps.collectAsStateWithLifecycle()
                    val customAppNames by settingsViewModel.customAppNames.collectAsStateWithLifecycle()
                    val context = LocalContext.current

                    // Ask for the notification permission only when the user turns the reminders on.
                    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                        if (granted) settingsViewModel.updateUsageReminders(true)
                        else Toast.makeText(context, R.string.reminders_permission_denied, Toast.LENGTH_LONG).show()
                    }
                    val onUsageRemindersToggle: (Boolean) -> Unit = { on ->
                        when {
                            !on -> {
                                settingsViewModel.updateUsageReminders(false)
                                UsageReminders.stop(context)
                            }
                            Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                                context, Manifest.permission.POST_NOTIFICATIONS
                            ) != PackageManager.PERMISSION_GRANTED ->
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            else -> settingsViewModel.updateUsageReminders(true)
                        }
                    }

                    UserSettingsScreen(
                        modifier = Modifier.padding(innerPadding),
                        currentAlignment = currentAlignment,
                        onAlignmentSelected = { settingsViewModel.updateAppListAlignment(it) },
                        pauseBeforeOpen = pauseBeforeOpen,
                        onPauseBeforeOpenToggle = { settingsViewModel.updatePauseBeforeOpen(it) },
                        isDarkMode = isDarkMode,
                        onDarkModeToggle = { settingsViewModel.updateIsDarkMode(it) },
                        currentUiStyle = uiStyle,
                        onUiStyleSelected = { settingsViewModel.updateUiStyle(it) },
                        currentFontSizeScale = fontSizeScale,
                        onFontSizeScaleSelected = { settingsViewModel.updateFontSizeScale(it) },
                        showHomeStats = showHomeStats,
                        onShowHomeStatsToggle = { settingsViewModel.updateShowHomeStats(it) },
                        notificationDotsEnabled = notificationDotsEnabled,
                        onNotificationDotsToggle = { settingsViewModel.updateNotificationDotsEnabled(it) },
                        reminderSettings = reminderSettings,
                        onUsageRemindersToggle = onUsageRemindersToggle,
                        onReminderMinutesSelected = { settingsViewModel.updateReminderMinutes(it) },
                        openSingleMatch = openSingleMatch,
                        onOpenSingleMatchToggle = { settingsViewModel.updateOpenSingleMatch(it) },
                        hiddenApps = hiddenApps,
                        customAppNames = customAppNames,
                        onUnhideApp = { settingsViewModel.setAppHidden(it, false) }
                    )
                }
            }
        }
    }
}

@Composable
fun SectionHeader(title: String, subtitle: String? = null) {
    Column(modifier = Modifier.padding(bottom = 12.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    // The style decides the look of the card.
    StyleBox(modifier = Modifier.fillMaxWidth(), content = content)
}

@Composable
fun NavigationRow(
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        Text(
            "\u203A",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.secondary
        )
    }
}

@Composable
fun UserSettingsScreen(
    modifier: Modifier = Modifier,
    currentAlignment: AppListAlignment,
    onAlignmentSelected: (AppListAlignment) -> Unit,
    pauseBeforeOpen: Boolean,
    onPauseBeforeOpenToggle: (Boolean) -> Unit,
    isDarkMode: Boolean,
    onDarkModeToggle: (Boolean) -> Unit,
    currentUiStyle: UiStyle,
    onUiStyleSelected: (UiStyle) -> Unit,
    currentFontSizeScale: String,
    onFontSizeScaleSelected: (String) -> Unit,
    showHomeStats: Boolean = true,
    onShowHomeStatsToggle: (Boolean) -> Unit = {},
    notificationDotsEnabled: Boolean = true,
    onNotificationDotsToggle: (Boolean) -> Unit = {},
    reminderSettings: ReminderSettings = SettingsRepository.DEFAULT_REMINDER_SETTINGS,
    onUsageRemindersToggle: (Boolean) -> Unit = {},
    onReminderMinutesSelected: (Int) -> Unit = {},
    openSingleMatch: Boolean = false,
    onOpenSingleMatchToggle: (Boolean) -> Unit = {},
    hiddenApps: Set<String> = emptySet(),
    customAppNames: Map<String, String> = emptyMap(),
    onUnhideApp: (String) -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var showHiddenApps by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // ── Focus ───────────────────────────────────────
        // The first item, so that the user finds Focus at once.
        SectionHeader(stringResource(R.string.focus_word), stringResource(R.string.section_focus_subtitle))

        SettingsCard {
            NavigationRow(
                title = stringResource(R.string.focus_word),
                subtitle = stringResource(R.string.focus_settings_subtitle),
                onClick = { context.startActivity(Intent(context, FocusModeActivity::class.java)) }
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 16.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            )

            PauseBeforeOpenToggle(
                isEnabled = pauseBeforeOpen,
                onToggle = onPauseBeforeOpenToggle
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 16.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            )

            UsageRemindersSetting(
                settings = reminderSettings,
                onToggle = onUsageRemindersToggle,
                onMinutesSelected = onReminderMinutesSelected
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        // ── Appearance ──────────────────────────────────
        SectionHeader(stringResource(R.string.section_appearance), stringResource(R.string.section_appearance_subtitle))

        SettingsCard {
            DarkModeToggle(
                isDarkMode = isDarkMode,
                onToggle = onDarkModeToggle
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                stringResource(R.string.label_style),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 10.dp)
            )
            UiStyleSelector(
                currentStyle = currentUiStyle,
                onStyleSelected = onUiStyleSelected
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                stringResource(R.string.label_font_size),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 10.dp)
            )
            FontSizeSelector(
                currentScale = currentFontSizeScale,
                onScaleSelected = onFontSizeScaleSelected
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        // ── Layout ──────────────────────────────────────
        SectionHeader(stringResource(R.string.section_layout), stringResource(R.string.section_layout_subtitle))

        SettingsCard {
            AlignmentSelectionDropdown(
                currentAlignment = currentAlignment,
                onAlignmentSelected = onAlignmentSelected
            )
            RowDescription(stringResource(R.string.align_desc), Modifier.padding(top = 4.dp))

            Spacer(modifier = Modifier.height(12.dp))

            NotificationDotsToggle(
                isEnabled = notificationDotsEnabled,
                onToggle = onNotificationDotsToggle
            )

            NavigationRow(
                title = stringResource(R.string.quiet_apps_title),
                subtitle = stringResource(R.string.quiet_apps_row_desc),
                onClick = { context.startActivity(Intent(context, com.dabcode.hal.quiet.QuietAppsActivity::class.java)) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            ShowHomeStatsToggle(
                isEnabled = showHomeStats,
                onToggle = onShowHomeStatsToggle
            )

            Spacer(modifier = Modifier.height(12.dp))

            SwitchRow(
                title = stringResource(R.string.single_match_toggle),
                description = stringResource(R.string.single_match_desc),
                checked = openSingleMatch,
                onToggle = onOpenSingleMatchToggle
            )

            NavigationRow(
                title = stringResource(R.string.hidden_apps_title),
                subtitle = stringResource(R.string.hidden_apps_desc),
                onClick = { showHiddenApps = true }
            )
        }

        if (showHiddenApps) {
            HiddenAppsDialog(
                hidden = hiddenApps,
                customNames = customAppNames,
                onUnhide = onUnhideApp,
                onDismiss = { showHiddenApps = false }
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        // ── Insights ────────────────────────────────────
        SectionHeader(stringResource(R.string.section_insights), stringResource(R.string.section_insights_subtitle))

        SettingsCard {
            NavigationRow(
                title = stringResource(R.string.title_screen_time),
                subtitle = stringResource(R.string.screen_time_subtitle),
                onClick = { context.startActivity(Intent(context, UsageStatsActivity::class.java)) }
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        // ── Language ────────────────────────────────────
        SectionHeader(stringResource(R.string.section_language), stringResource(R.string.section_language_subtitle))

        SettingsCard {
            LanguageSelectionDropdown()
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun DarkModeToggle(
    isDarkMode: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!isDarkMode) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = stringResource(if (isDarkMode) R.string.dark_mode else R.string.light_mode),
            style = MaterialTheme.typography.bodyLarge
        )
        Switch(
            checked = isDarkMode,
            onCheckedChange = onToggle
        )
    }
}

@Composable
fun UiStyleSelector(
    currentStyle: UiStyle,
    onStyleSelected: (UiStyle) -> Unit
) {
    val options = listOf(
        UiStyle.PAPER to stringResource(R.string.style_paper),
        UiStyle.MONO to stringResource(R.string.style_mono),
        UiStyle.QUIET to stringResource(R.string.style_quiet)
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { (style, label) ->
            FilterChip(
                selected = style == currentStyle,
                onClick = { onStyleSelected(style) },
                label = { Text(label) }
            )
        }
    }
}

@Composable
fun FontSizeSelector(
    currentScale: String,
    onScaleSelected: (String) -> Unit
) {
    val options = listOf(
        "small" to stringResource(R.string.font_small),
        "medium" to stringResource(R.string.font_medium),
        "large" to stringResource(R.string.font_large)
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { (value, label) ->
            val isSelected = value == currentScale
            FilterChip(
                selected = isSelected,
                onClick = { onScaleSelected(value) },
                label = { Text(label) }
            )
        }
    }
}

@Composable
fun PauseBeforeOpenToggle(
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!isEnabled) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(stringResource(R.string.pause_toggle), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = stringResource(if (isEnabled) R.string.pause_on_desc else R.string.pause_off_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }
        Switch(checked = isEnabled, onCheckedChange = onToggle)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlignmentSelectionDropdown(
    currentAlignment: AppListAlignment,
    onAlignmentSelected: (AppListAlignment) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val items = AppListAlignment.entries

    val alignmentNames = mapOf(
        AppListAlignment.START to stringResource(R.string.align_left),
        AppListAlignment.CENTER to stringResource(R.string.align_center),
        AppListAlignment.END to stringResource(R.string.align_right)
    )

    fun getDisplayAlignmentName(alignment: AppListAlignment): String = alignmentNames.getValue(alignment)

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = getDisplayAlignmentName(currentAlignment),
            onValueChange = {}, // Read-only
            readOnly = true,
            label = { Text(stringResource(R.string.align_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            items.forEach { alignment ->
                DropdownMenuItem(
                    text = { Text(getDisplayAlignmentName(alignment)) },
                    onClick = {
                        onAlignmentSelected(alignment)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun NotificationDotsToggle(
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!isEnabled) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(stringResource(R.string.dots_toggle), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = stringResource(if (isEnabled) R.string.dots_on_desc else R.string.dots_off_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }
        Switch(checked = isEnabled, onCheckedChange = onToggle)
    }
}

@Composable
fun ShowHomeStatsToggle(
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!isEnabled) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(stringResource(R.string.home_stats_toggle), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = stringResource(if (isEnabled) R.string.home_stats_on_desc else R.string.home_stats_off_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }
        Switch(checked = isEnabled, onCheckedChange = onToggle)
    }
}

/**
 * The app language. "System default" is first, then each language in its own name.
 * AppCompatDelegate stores the choice. On Android 13 and later, the system per-app language setting
 * is the same value. A change recreates the activities.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSelectionDropdown() {
    var expanded by remember { mutableStateOf(false) }
    val selected = remember { selectedAppLanguage(AppCompatDelegate.getApplicationLocales().toLanguageTags()) }
    val systemDefault = stringResource(R.string.language_system_default)
    // Null is the system default.
    val options: List<AppLanguage?> = listOf(null) + APP_LANGUAGES

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selected?.nativeName ?: systemDefault,
            onValueChange = {}, // Read-only
            readOnly = true,
            label = { Text(stringResource(R.string.label_app_language)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { language ->
                DropdownMenuItem(
                    text = { Text(language?.nativeName ?: systemDefault) },
                    onClick = {
                        expanded = false
                        if (language != selected) {
                            val locales = if (language == null) LocaleListCompat.getEmptyLocaleList()
                            else LocaleListCompat.forLanguageTags(language.tag)
                            AppCompatDelegate.setApplicationLocales(locales)
                        }
                    }
                )
            }
        }
    }
}

/** A row with a title, a description and a switch. A tap on the row also changes the switch. */
@Composable
fun SwitchRow(title: String, description: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        }
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}

/**
 * The usage reminders switch. When it is on: the interval.
 */
@Composable
fun UsageRemindersSetting(
    settings: ReminderSettings,
    onToggle: (Boolean) -> Unit,
    onMinutesSelected: (Int) -> Unit
) {
    SwitchRow(
        title = stringResource(R.string.reminders_toggle),
        description = stringResource(R.string.reminders_desc),
        checked = settings.enabled,
        onToggle = onToggle
    )
    if (!settings.enabled) return
    Text(
        stringResource(R.string.reminders_interval_label),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(top = 12.dp, bottom = 10.dp)
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        REMINDER_INTERVALS.forEach { minutes ->
            FilterChip(
                selected = minutes == settings.minutes,
                onClick = { onMinutesSelected(minutes) },
                label = { Text(stringResource(R.string.duration_minutes, minutes)) }
            )
        }
    }
    // The longer note shows only when the reminders are on.
    RowDescription(stringResource(R.string.reminders_note), Modifier.padding(top = 4.dp))
}

/** The list of hidden apps. Each app has an "Unhide" button. */
@Composable
fun HiddenAppsDialog(
    hidden: Set<String>,
    customNames: Map<String, String>,
    onUnhide: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    // Null while the apps load.
    var installed by remember { mutableStateOf<List<AppInfo>?>(null) }
    LaunchedEffect(Unit) {
        installed = withContext(Dispatchers.IO) { getInstalledApps(context) }
    }
    // An app that is not installed anymore does not show.
    val shown = remember(installed, hidden, customNames) {
        applyCustomNames(installed.orEmpty(), customNames)
            .filter { it.packageName in hidden }
            .sortedBy { normalizeForSearch(it.appName) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.hidden_apps_title)) },
        text = {
            if (installed != null && shown.isEmpty()) {
                Text(stringResource(R.string.hidden_apps_empty))
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    items(shown, key = { it.packageName }) { app ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(app.appName, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            TextButton(onClick = { onUnhide(app.packageName) }) {
                                Text(stringResource(R.string.action_unhide))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) }
        }
    )
}

/** The one-line description under the title of a settings row: small muted text. */
@Composable
fun RowDescription(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.secondary,
        modifier = modifier
    )
}
