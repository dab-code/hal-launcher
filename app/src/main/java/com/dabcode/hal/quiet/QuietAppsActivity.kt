package com.dabcode.hal.quiet

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dabcode.hal.AppInfo
import com.dabcode.hal.HalThemed
import com.dabcode.hal.LocalPalette
import com.dabcode.hal.R
import com.dabcode.hal.applyCustomNames
import com.dabcode.hal.applyThemeBeforeCreate
import com.dabcode.hal.enableHalEdgeToEdge
import com.dabcode.hal.fuzzySearch
import com.dabcode.hal.getInstalledApps
import com.dabcode.hal.isNotificationServiceEnabled
import com.dabcode.hal.requestNotificationAccess
import com.dabcode.hal.settingsViewModels
import com.dabcode.hal.usage.selectedFirst
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The picker of quiet apps. HAL takes their notifications out of the shade and keeps them in a list. */
class QuietAppsActivity : AppCompatActivity() {

    private val settingsViewModel by settingsViewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        applyThemeBeforeCreate()
        super.onCreate(savedInstanceState)
        enableHalEdgeToEdge()
        setContent {
            val quietApps by settingsViewModel.quietApps.collectAsStateWithLifecycle()
            val customAppNames by settingsViewModel.customAppNames.collectAsStateWithLifecycle()
            HalThemed(settingsViewModel) {
                QuietAppsScreen(
                    quietApps = quietApps,
                    customAppNames = customAppNames,
                    onChanged = { settingsViewModel.updateQuietApps(it) },
                    onBack = { finish() }
                )
            }
        }
    }
}

/** Open the Android notification settings of [pkg], where the user can turn off the sound. */
fun openAppNotificationSettings(context: Context, pkg: String) {
    val intent = if (Build.VERSION.SDK_INT >= 26) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pkg)
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
    }
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        // No settings screen for this intent on this device.
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuietAppsScreen(
    quietApps: Set<String>,
    customAppNames: Map<String, String>,
    onChanged: (Set<String>) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val installed by produceState(initialValue = emptyList<AppInfo>()) {
        value = withContext(Dispatchers.IO) { getInstalledApps(context) }
    }
    val allApps = remember(installed, customAppNames) { applyCustomNames(installed, customAppNames) }
    var selected by remember(quietApps) { mutableStateOf(quietApps) }
    // The selection when the list first loads. It sets the order, so an app does not jump on a tap.
    var orderSelection by remember { mutableStateOf<Set<String>?>(null) }
    LaunchedEffect(quietApps) {
        if (orderSelection == null && quietApps.isNotEmpty()) orderSelection = quietApps
    }
    var query by remember { mutableStateOf("") }
    val ordered = remember(allApps, orderSelection) { selectedFirst(allApps, orderSelection ?: emptySet()) }
    val shown = remember(ordered, query) { fuzzySearch(ordered, query) { it.appName } }

    // The access can change in the system settings. Check it again on resume.
    var hasAccess by remember { mutableStateOf(isNotificationServiceEnabled(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) hasAccess = isNotificationServiceEnabled(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val toggle: (String) -> Unit = { pkg ->
        val updated = if (pkg in selected) selected - pkg else selected + pkg
        selected = updated
        onChanged(updated)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.quiet_apps_title)) },
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
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                // The list ends above the keyboard. The search field is in the list.
                .consumeWindowInsets(innerPadding)
                .imePadding()
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            item {
                Column(modifier = Modifier.padding(vertical = 12.dp)) {
                    Text(
                        stringResource(R.string.quiet_apps_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        stringResource(R.string.quiet_sound_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    // The same access flow as the notification dots.
                    if (!hasAccess) {
                        Text(
                            stringResource(R.string.quiet_needs_access),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .padding(top = 12.dp)
                                .clickable { requestNotificationAccess(context) }
                        )
                    }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.search_apps), style = MaterialTheme.typography.bodyMedium) },
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                )
            }
            items(shown, key = { it.packageName }) { app ->
                val isQuiet = app.packageName in selected
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { toggle(app.packageName) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = isQuiet, onCheckedChange = { toggle(app.packageName) })
                    Text(
                        app.appName,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 8.dp).weight(1f)
                    )
                    if (isQuiet) {
                        TextButton(onClick = { openAppNotificationSettings(context, app.packageName) }) {
                            Text(
                                stringResource(R.string.quiet_sound_settings),
                                style = MaterialTheme.typography.bodySmall,
                                color = LocalPalette.current.muted
                            )
                        }
                    }
                }
            }
        }
    }
}
