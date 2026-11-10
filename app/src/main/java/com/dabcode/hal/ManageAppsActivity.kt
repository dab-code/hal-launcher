package com.dabcode.hal

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import com.dabcode.hal.quiet.QuietStore
import com.dabcode.hal.quiet.showQuietSection
import com.dabcode.hal.usage.UsageStatsActivity
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


/**
 * The "All Apps" screen: the user finds and opens any app, and selects the apps that show on the home screen.
 * Swipe left on the home screen opens it.
 */
class ManageAppsActivity : AppCompatActivity() {
    private val settingsViewModel by settingsViewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        applyThemeBeforeCreate()
        super.onCreate(savedInstanceState)
        enableHalEdgeToEdge()

        setContent {
            HalThemed(settingsViewModel) {
                val pauseBeforeOpen by settingsViewModel.pauseBeforeOpen.collectAsStateWithLifecycle()
                val pauseCounts by settingsViewModel.pauseCounts.collectAsStateWithLifecycle()
                val isLockdownEnabled by settingsViewModel.isLockdownEnabled.collectAsStateWithLifecycle()
                val lockdownThresholdMinutes by settingsViewModel.lockdownThresholdMinutes.collectAsStateWithLifecycle()
                val alwaysAllowed by settingsViewModel.alwaysAllowedPackages.collectAsStateWithLifecycle()
                val customAppNames by settingsViewModel.customAppNames.collectAsStateWithLifecycle()
                val hiddenApps by settingsViewModel.hiddenApps.collectAsStateWithLifecycle()
                val openSingleMatch by settingsViewModel.openSingleMatch.collectAsStateWithLifecycle()
                val quietApps by settingsViewModel.quietApps.collectAsStateWithLifecycle()
                val quietEntries by QuietStore.get(this@ManageAppsActivity).entries.collectAsStateWithLifecycle()
                var showMenu by remember { mutableStateOf(false) }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(stringResource(R.string.title_all_apps)) },
                            colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.background,
                                titleContentColor = MaterialTheme.colorScheme.onBackground,
                                navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                                actionIconContentColor = MaterialTheme.colorScheme.onBackground
                            ),
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = stringResource(R.string.cd_back)
                                    )
                                }
                            },
                            actions = {
                                // The menu with "Quiet notifications" (no count). It shows only when
                                // an app is quiet or the list has entries.
                                if (showQuietSection(quietApps, quietEntries)) {
                                    Box {
                                        IconButton(onClick = { showMenu = true }) {
                                            Icon(Icons.Filled.MoreVert, stringResource(R.string.cd_more_options))
                                        }
                                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.quiet_notifications_title)) },
                                                onClick = {
                                                    showMenu = false
                                                    startActivity(
                                                        Intent(this@ManageAppsActivity, UsageStatsActivity::class.java)
                                                            .putExtra(UsageStatsActivity.EXTRA_SHOW_QUIET, true)
                                                    )
                                                }
                                            )
                                        }
                                    }
                                }
                                IconButton(onClick = {
                                    val intent = Intent(this@ManageAppsActivity, com.dabcode.settings.UserSettingsActivity::class.java)
                                    startActivity(intent)
                                }) {
                                    Icon(
                                        imageVector = Icons.Filled.Settings,
                                        contentDescription = stringResource(R.string.cd_open_user_settings)
                                    )
                                }
                            }
                        )
                    }
                ) { innerPadding ->
                    ManageAppsScreen(
                        // The list ends above the keyboard. The keyboard opens with the screen.
                        modifier = Modifier
                            .padding(innerPadding)
                            .consumeWindowInsets(innerPadding)
                            .imePadding(),
                        pauseBeforeOpen = pauseBeforeOpen,
                        pauseCounts = pauseCounts,
                        onPauseOpen = settingsViewModel::recordPauseOpen,
                        // "Close" in the pause dialog goes back to the home screen.
                        onCloseToHome = { finish() },
                        isLockdownEnabled = isLockdownEnabled,
                        lockdownThresholdMinutes = lockdownThresholdMinutes,
                        alwaysAllowed = alwaysAllowed,
                        customAppNames = customAppNames,
                        onRenameApp = settingsViewModel::setCustomAppName,
                        hiddenApps = hiddenApps,
                        onSetHidden = settingsViewModel::setAppHidden,
                        openSingleMatch = openSingleMatch
                    )
                }

            }
        }
    }
}

@Composable
fun ManageAppsScreen(
    modifier: Modifier = Modifier,
    pauseBeforeOpen: Boolean = true,
    pauseCounts: PauseCounts = PauseCounts(),
    onPauseOpen: (pkg: String) -> Unit = {},
    onCloseToHome: () -> Unit = {},
    isLockdownEnabled: Boolean = false,
    lockdownThresholdMinutes: Int = 120,
    alwaysAllowed: Set<String> = emptySet(),
    customAppNames: Map<String, String> = emptyMap(),
    onRenameApp: (componentKey: String, name: String?) -> Unit = { _, _ -> },
    hiddenApps: Set<String> = emptySet(),
    onSetHidden: (pkg: String, hidden: Boolean) -> Unit = { _, _ -> },
    openSingleMatch: Boolean = false,
    usageStatsViewModel: com.dabcode.hal.usage.UsageStatsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    focusModeViewModel: com.dabcode.hal.focus.FocusModeViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val context = LocalContext.current
    val todayScreenTime by usageStatsViewModel.todayScreenTime.collectAsStateWithLifecycle()
    val activeFocus by focusModeViewModel.activeFocus.collectAsStateWithLifecycle()
    val breaks by focusModeViewModel.breaks.collectAsStateWithLifecycle()

    val lockdownActive = isLockdownActive(isLockdownEnabled, todayScreenTime, lockdownThresholdMinutes)
    var allAppsWithState by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }

    // The HAL language: the picked app language, else the system language.
    val locale = LocalConfiguration.current.locales[0]
    // The apps with the custom names, sorted by the shown name in the order of the language.
    val namedApps = remember(allAppsWithState, customAppNames, locale) {
        sortAppLabels(applyCustomNames(allAppsWithState, customAppNames), locale) { it.appName }
    }
    // The search finds the custom name and the original name. Hidden apps show only in the search.
    val filteredApps by remember(namedApps, hiddenApps, searchQuery) {
        derivedStateOf {
            fuzzySearchNames(appsForAllApps(namedApps, hiddenApps, searchQuery), searchQuery) {
                listOf(it.appName, it.originalName)
            }
        }
    }
    // The app in the rename dialog. Null when the dialog is closed.
    var appToRename by remember { mutableStateOf<AppInfo?>(null) }
    // All opens from this screen go here: the lock rules, then the pause (see rememberGuardedOpen).
    val requestOpen = rememberGuardedOpen(
        pauseBeforeOpen = pauseBeforeOpen,
        pauseCounts = pauseCounts,
        onPauseOpen = onPauseOpen,
        lockdownActive = lockdownActive,
        alwaysAllowed = alwaysAllowed,
        focusModeViewModel = focusModeViewModel,
        onClose = onCloseToHome
    )

    // With the setting on, one search result opens at once, through the lock rules and the pause.
    // The effect runs only when the query changes, so a return to this screen does not open the app again.
    LaunchedEffect(searchQuery) {
        singleMatchToOpen(openSingleMatch, searchQuery, filteredApps)?.let(requestOpen)
    }

    // Focus the search field and show the keyboard when the screen opens.
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }
    val listState = rememberLazyListState()

    val coroutineScope = rememberCoroutineScope()

    val reloadAllAppsWithState = {
        coroutineScope.launch(Dispatchers.IO) {
            val installed = getInstalledApps(context)
            // The order comes later, after the custom names (see namedApps).
            val loadedAndSorted = loadEnabledApps(context, installed)
            withContext(Dispatchers.Main) {
                allAppsWithState = loadedAndSorted
            }
        }
        Unit
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                reloadAllAppsWithState()
                usageStatsViewModel.refresh()
                focusModeViewModel.refreshActiveProfile()
            }
        }
        // The observer also gets ON_RESUME when the screen is already resumed.
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // The uninstall can end after this screen resumes. Load the list again when an app goes away.
    DisposableEffect(context) {
        val launcherApps = context.getSystemService(android.content.Context.LAUNCHER_APPS_SERVICE) as android.content.pm.LauncherApps
        val callback = object : android.content.pm.LauncherApps.Callback() {
            override fun onPackageRemoved(packageName: String, user: android.os.UserHandle) = reloadAllAppsWithState()
            override fun onPackageAdded(packageName: String, user: android.os.UserHandle) = Unit
            override fun onPackageChanged(packageName: String, user: android.os.UserHandle) = Unit
            override fun onPackagesAvailable(packageNames: Array<String>, user: android.os.UserHandle, replacing: Boolean) = Unit
            override fun onPackagesUnavailable(packageNames: Array<String>, user: android.os.UserHandle, replacing: Boolean) = Unit
        }
        launcherApps.registerCallback(callback)
        onDispose { launcherApps.unregisterCallback(callback) }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 32.dp)
    )
    {
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            val currentApps = filteredApps

            OutlinedTextField(
                value = searchQuery,
                onValueChange = {
                    searchQuery = it
                    coroutineScope.launch { listState.scrollToItem(0) }
                },
                label = { Text(stringResource(R.string.search_apps), style = MaterialTheme.typography.bodyMedium) },
                shape = RoundedCornerShape(16.dp),
                textStyle = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .focusRequester(focusRequester),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                // Enter opens the app (through the pause dialog) when the search finds exactly one app.
                keyboardActions = KeyboardActions(onGo = {
                    currentApps.singleOrNull()?.let(requestOpen)
                }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    disabledContainerColor = MaterialTheme.colorScheme.surface,
                )
            )

            Row(modifier = Modifier.weight(1f)) {
                LazyColumn(modifier = Modifier.weight(1f), state = listState) {
                    if (currentApps.isEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.no_apps_found),
                                style = MaterialTheme.typography.bodyLarge,
                                color = LocalPalette.current.muted
                            )
                        }
                    }
                    items(currentApps, key = { app -> app.packageName }) { appInfo ->
                        val reason = lockReason(
                            appInfo.packageName, lockdownActive, activeFocus != null, alwaysAllowed,
                            breaks.isOnBreak(appInfo.packageName, System.currentTimeMillis())
                        )
                        AppToggleItem(
                            appInfo = appInfo,
                            onToggle = { newEnabledState ->
                                saveEnabledApp(context, appInfo.packageName, newEnabledState)
                                allAppsWithState = allAppsWithState.map {
                                    if (it.packageName == appInfo.packageName) {
                                        it.copy(isEnabled = newEnabledState)
                                    } else {
                                        it
                                    }
                                }
                            },
                            isLocked = reason != null,
                            onOpen = { requestOpen(appInfo) },
                            isHidden = appInfo.packageName in hiddenApps,
                            onRename = { appToRename = appInfo },
                            onSetHidden = { hidden -> onSetHidden(appInfo.packageName, hidden) },
                            onUninstall = { requestUninstall(context, appInfo.packageName) }
                        )
                    }
                }
                // The A-Z jump bar. Show it only when the search field is empty.
                if (searchQuery.isBlank() && currentApps.isNotEmpty()) {
                    JumpBar(letters = jumpLetters(locale.language), onLetter = { letter ->
                        val index = jumpIndex(currentApps.map { it.appName }, letter, locale.language)
                        if (index >= 0) coroutineScope.launch { listState.scrollToItem(index) }
                    })
                }
            }
        }

        appToRename?.let { app ->
            RenameAppDialog(
                app = app,
                onSave = { name ->
                    onRenameApp(app.componentKey, name)
                    appToRename = null
                },
                onDismiss = { appToRename = null }
            )
        }
    }
}

/** A column of letters on the right edge. A tap on a letter calls [onLetter]. */
@Composable
fun JumpBar(letters: List<Char>, onLetter: (Char) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .padding(start = 8.dp),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        letters.forEach { letter ->
            Text(
                letter.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = LocalPalette.current.muted,
                modifier = Modifier
                    .clickable { onLetter(letter) }
                    .padding(horizontal = 6.dp)
            )
        }
    }
}

/**
 * One app in All Apps: the name and the switch for the home screen.
 * A tap on the name calls [onOpen]. A long press shows a menu (rename, hide or unhide, uninstall) and does not open the app.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppToggleItem(
    appInfo: AppInfo,
    onToggle: (Boolean) -> Unit,
    isLocked: Boolean,
    onOpen: () -> Unit,
    isHidden: Boolean = false,
    onRename: () -> Unit = {},
    onSetHidden: (Boolean) -> Unit = {},
    onUninstall: () -> Unit = {}
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .defaultMinSize(minHeight = 48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // The Box anchors the menu at the name.
        Box {
            Text(
                text = styledAppName(appInfo.appName),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .padding(end = 8.dp)
                    .combinedClickable(onLongClick = { menuOpen = true }, onClick = onOpen),
                color = if (isLocked || isHidden) LocalPalette.current.muted else MaterialTheme.colorScheme.onBackground
            )
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.dialog_rename_title)) },
                    onClick = {
                        menuOpen = false
                        onRename()
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(if (isHidden) R.string.action_unhide else R.string.action_hide)) },
                    onClick = {
                        menuOpen = false
                        onSetHidden(!isHidden)
                    }
                )
                if (appInfo.canUninstall) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_uninstall)) },
                        onClick = {
                            menuOpen = false
                            onUninstall()
                        }
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))

        Switch(checked = appInfo.isEnabled, onCheckedChange = onToggle)
    }
}
