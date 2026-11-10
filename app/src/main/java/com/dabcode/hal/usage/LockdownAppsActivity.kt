package com.dabcode.hal.usage

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dabcode.hal.AppInfo
import com.dabcode.hal.HalThemed
import com.dabcode.hal.R
import com.dabcode.hal.applyThemeBeforeCreate
import com.dabcode.hal.enableHalEdgeToEdge
import com.dabcode.hal.fuzzySearch
import com.dabcode.hal.getInstalledApps
import com.dabcode.hal.normalizeForSearch
import com.dabcode.hal.settingsViewModels
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The one shared "Always allowed" list. Focus and the daily limit never lock these apps.
 * The class keeps its old name, so the manifest does not change.
 */
@OptIn(ExperimentalMaterial3Api::class)
class LockdownAppsActivity : AppCompatActivity() {

    private val settingsViewModel by settingsViewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        applyThemeBeforeCreate()
        super.onCreate(savedInstanceState)
        enableHalEdgeToEdge()

        setContent {
            val allowedPackages by settingsViewModel.alwaysAllowedPackages.collectAsStateWithLifecycle()

            HalThemed(settingsViewModel) {
                AlwaysAllowedScreen(
                    allowedPackages = allowedPackages,
                    onPackagesChanged = { settingsViewModel.updateAlwaysAllowedPackages(it) },
                    onBack = { finish() }
                )
            }
        }
    }
}

/**
 * Return the apps with the selected apps first, then by name.
 * The order uses [selectedAtStart], so that an app does not jump when the user taps it.
 */
fun selectedFirst(apps: List<AppInfo>, selectedAtStart: Set<String>): List<AppInfo> =
    apps.sortedWith(compareBy({ it.packageName !in selectedAtStart }, { normalizeForSearch(it.appName) }))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlwaysAllowedScreen(
    allowedPackages: Set<String>,
    onPackagesChanged: (Set<String>) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    // All apps, also the apps that are not on the home screen: the lock also applies in All Apps.
    val allApps by produceState(initialValue = emptyList<AppInfo>()) {
        value = withContext(Dispatchers.IO) { getInstalledApps(context) }
    }
    var selected by remember(allowedPackages) { mutableStateOf(allowedPackages) }
    // The selection when the list first loads. It sets the order.
    var orderSelection by remember { mutableStateOf<Set<String>?>(null) }
    LaunchedEffect(allowedPackages) {
        if (orderSelection == null && allowedPackages.isNotEmpty()) orderSelection = allowedPackages
    }
    var query by remember { mutableStateOf("") }
    val ordered = remember(allApps, orderSelection) { selectedFirst(allApps, orderSelection ?: emptySet()) }
    val shown = remember(ordered, query) { fuzzySearch(ordered, query) { it.appName } }

    val toggle: (String) -> Unit = { pkg ->
        val updated = if (pkg in selected) selected - pkg else selected + pkg
        selected = updated
        onPackagesChanged(updated)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.always_allowed_title)) },
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
                Text(
                    stringResource(R.string.always_allowed_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { toggle(app.packageName) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = app.packageName in selected, onCheckedChange = { toggle(app.packageName) })
                    Text(
                        app.appName,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    }
}
