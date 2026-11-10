package com.dabcode.hal.usage

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.material.icons.filled.Close
import com.dabcode.hal.quiet.QuietEntry
import kotlinx.coroutines.flow.first
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dabcode.hal.LocalPalette
import com.dabcode.hal.R
import com.dabcode.hal.styledAppName
import com.dabcode.hal.StyleBox
import com.dabcode.hal.focus.SUGGESTION_MIN_DAYS
import com.dabcode.hal.focus.averageDayMs
import com.dabcode.hal.focus.suggestedLimitMinutes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsageStatsScreen(
    viewModel: UsageStatsViewModel,
    isLockdownEnabled: Boolean,
    lockdownThresholdMinutes: Int,
    onOpenDailyLimit: () -> Unit = {},
    onBack: () -> Unit,
    showQuiet: Boolean = false,
    quietEntries: List<QuietEntry> = emptyList(),
    quietNames: Map<String, String> = emptyMap(),
    startQuietExpanded: Boolean = false,
    onOpenQuietEntry: (QuietEntry) -> Unit = {},
    onClearQuiet: () -> Unit = {},
    showReview: Boolean = false,
    onReview: () -> Unit = {},
    onReviewClose: () -> Unit = {}
) {
    val palette = LocalPalette.current
    val hasPermission by viewModel.hasPermission.collectAsStateWithLifecycle()
    val todayTotal by viewModel.todayScreenTime.collectAsStateWithLifecycle()
    val pickupCount by viewModel.todayPickupCount.collectAsStateWithLifecycle()
    val weeklyData by viewModel.weeklyUsage.collectAsStateWithLifecycle()
    val pastSevenDays by viewModel.pastSevenDays.collectAsStateWithLifecycle()

    // The day that the user tapped. Null means today.
    var selectedDay by rememberSaveable { mutableStateOf<Int?>(null) }
    // Show 7 days. Index 0 of the weekly data is only for the comparison.
    val chartData = if (weeklyData.size > 7) weeklyData.drop(1) else weeklyData
    val shownDay = selectedDayIndex(selectedDay, chartData.size)
    val limitMs = if (isLockdownEnabled) lockdownThresholdMinutes * 60_000L else null
    val units = rememberDurationUnits()
    val listState = rememberLazyListState()
    var quietExpanded by rememberSaveable { mutableStateOf(startQuietExpanded) }

    // From the menu: scroll the quiet section into view one time, after the data is loaded.
    val dataLoaded by rememberUpdatedState(!hasPermission || weeklyData.isNotEmpty())
    val reviewShown by rememberUpdatedState(showReview)
    LaunchedEffect(Unit) {
        if (!startQuietExpanded || !showQuiet) return@LaunchedEffect
        val total = snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it > 0 && dataLoaded }
        // The quiet section is the last item, or the item before the review line.
        listState.scrollToItem((total - 1 - if (reviewShown) 1 else 0).coerceAtLeast(0))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_screen_time)) },
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
            state = listState,
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            if (!hasPermission) {
                item {
                    // An item cannot fill the height of a list. The block shows at the top.
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            stringResource(R.string.usage_permission_needed),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        Button(onClick = { viewModel.repository.openUsageAccessSettings() }) {
                            Text(stringResource(R.string.action_grant_permission))
                        }
                    }
                }
            } else {
                // Today summary
                item {
                    Column(modifier = Modifier.padding(vertical = 16.dp)) {
                        Text(stringResource(R.string.usage_today), style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    formatDuration(todayTotal, units),
                                    style = MaterialTheme.typography.headlineLarge
                                )
                                Text(stringResource(R.string.stat_screen_time), style = MaterialTheme.typography.bodySmall, color = palette.muted)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    "$pickupCount",
                                    style = MaterialTheme.typography.headlineLarge
                                )
                                Text(stringResource(R.string.stat_pickups), style = MaterialTheme.typography.bodySmall, color = palette.muted)
                            }
                        }
                    }
                }

                // One calm card with a suggested daily limit. Show it only when the limit is off
                // and all 7 past days have data.
                val average = averageDayMs(pastSevenDays)
                if (!isLockdownEnabled && average != null && pastSevenDays.count { it > 0L } >= SUGGESTION_MIN_DAYS) {
                    item {
                        LimitSuggestionCard(
                            text = stringResource(
                                R.string.limit_suggestion_card,
                                formatDuration(average, units),
                                formatLimit(suggestedLimitMinutes(average), units)
                            ),
                            onClick = onOpenDailyLimit
                        )
                    }
                }

                // Weekly trend
                item {
                    if (chartData.isNotEmpty()) {
                        Column(modifier = Modifier.padding(vertical = 16.dp)) {
                            Text(stringResource(R.string.usage_this_week), style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(8.dp))

                            WeeklyBarChart(
                                data = chartData,
                                selectedIndex = shownDay,
                                limitMs = limitMs,
                                onSelect = { selectedDay = it }
                            )

                            // Compare today with the same weekday last week, up to the same time of day.
                            if (weeklyData.size >= 8) {
                                val change = changeVsLastWeekPercent(
                                    todayMs = weeklyData.last().totalMs,
                                    lastWeekMs = weeklyData.first().totalMs
                                )
                                if (change != null) {
                                    Text(
                                        stringResource(R.string.usage_vs_last_week, changeArrow(change), kotlin.math.abs(change)).trim(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = palette.muted,
                                        modifier = Modifier.padding(top = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Per-app breakdown of the selected day
                val day = chartData.getOrNull(shownDay)
                if (day != null) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (shownDay == chartData.lastIndex) stringResource(R.string.usage_today) else day.label,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(formatDuration(day.totalMs, units), style = MaterialTheme.typography.titleMedium)
                        }
                    }

                    items(day.apps, key = { it.pkg }) { usage ->
                        AppUsageRow(usageRowName(usage.pkg, usage.name, quietNames), usage.ms)
                    }
                }
            }

            // The quiet notifications: one item, collapsed by default. The heading has no count.
            if (showQuiet) {
                item(key = "quiet") {
                    QuietSection(
                        entries = quietEntries,
                        names = quietNames,
                        expanded = quietExpanded,
                        onToggle = { quietExpanded = !quietExpanded },
                        onOpen = onOpenQuietEntry,
                        onClearAll = onClearQuiet
                    )
                }
            }

            // The one review request: a small muted line at the end.
            if (showReview) {
                item(key = "review") {
                    ReviewLine(onReview = onReview, onClose = onReviewClose)
                }
            }
        }
    }
}

/** The card with the suggested daily limit. A tap opens the daily limit setup. */
@Composable
private fun LimitSuggestionCard(text: String, onClick: () -> Unit) {
    StyleBox(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable(onClick = onClick)
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium)
        Text(
            stringResource(R.string.limit_suggestion_action),
            style = MaterialTheme.typography.bodySmall,
            color = LocalPalette.current.muted,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

/**
 * The bars of the week. A tap on a bar selects that day.
 * When [limitMs] is not null, a dashed line shows the daily limit.
 */
@Composable
fun WeeklyBarChart(
    data: List<DayUsage>,
    selectedIndex: Int,
    limitMs: Long?,
    onSelect: (Int) -> Unit
) {
    val palette = LocalPalette.current
    val scaleMax = chartScaleMax(data.map { it.totalMs }, limitMs)
    val units = rememberDurationUnits()
    val barAreaHeight = 100.dp

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom
    ) {
        data.forEachIndexed { index, day ->
            val isSelected = index == selectedIndex
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelect(index) }
            ) {
                Text(
                    formatDurationCompact(day.totalMs, units),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                    color = palette.muted,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
                // All columns have the same bar area. The limit line segments of the columns join into one line.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(barAreaHeight)
                        .drawWithContent {
                            drawContent()
                            if (limitMs != null) {
                                val y = size.height * (1f - limitLineFraction(limitMs, scaleMax))
                                drawLine(
                                    color = palette.ink,
                                    start = Offset(0f, y),
                                    end = Offset(size.width, y),
                                    strokeWidth = 1.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
                                )
                            }
                        },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(
                        modifier = Modifier
                            .width(24.dp)
                            .height(barAreaHeight * barFraction(day.totalMs, scaleMax))
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            // The selected day is ink. The other days are muted.
                            .background(if (isSelected) palette.ink else palette.muted)
                    )
                }
                Text(
                    day.label,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    ),
                    color = if (isSelected) palette.ink else palette.muted,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
fun AppUsageRow(appName: String, timeMs: Long) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            styledAppName(appName),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Text(
            formatDuration(timeMs, rememberDurationUnits()),
            style = MaterialTheme.typography.bodyMedium,
            color = LocalPalette.current.muted
        )
    }
}

/** Read the duration units of the current language from the string resources. */
@Composable
fun rememberDurationUnits(): DurationUnits {
    val hoursMinutes = stringResource(R.string.duration_hours_minutes)
    val hours = stringResource(R.string.duration_hours)
    val minutes = stringResource(R.string.duration_minutes)
    val compact = stringResource(R.string.duration_compact)
    return remember(hoursMinutes, hours, minutes, compact) { DurationUnits(hoursMinutes, hours, minutes, compact) }
}

/**
 * The quiet notifications. A tap on the heading expands or collapses the list. Newest first.
 * A tap on a row calls [onOpen]: the caller removes the entry and opens the app through the normal open path.
 */
@Composable
private fun QuietSection(
    entries: List<QuietEntry>,
    names: Map<String, String>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpen: (QuietEntry) -> Unit,
    onClearAll: () -> Unit
) {
    val palette = LocalPalette.current
    Column(modifier = Modifier.padding(vertical = 16.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.quiet_notifications_title), style = MaterialTheme.typography.titleMedium)
            Text(if (expanded) "\u2212" else "+", style = MaterialTheme.typography.titleMedium, color = palette.muted)
        }
        if (!expanded) return@Column
        if (entries.isEmpty()) {
            Text(
                stringResource(R.string.quiet_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = palette.muted,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            return@Column
        }
        entries.forEach { entry ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(entry) }
                    .padding(vertical = 8.dp)
            ) {
                Text(
                    "${names[entry.pkg] ?: entry.label} \u00B7 ${com.dabcode.hal.formatClock(entry.postTime)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.muted
                )
                if (entry.title.isNotBlank()) {
                    Text(entry.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (entry.text.isNotBlank()) {
                    Text(
                        entry.text,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Text(
            stringResource(R.string.quiet_clear_all),
            style = MaterialTheme.typography.bodyMedium,
            color = palette.muted,
            modifier = Modifier
                .clickable(onClick = onClearAll)
                .padding(vertical = 12.dp)
        )
    }
}

/** The one review request: a small muted line with a close button. A tap opens the Play listing. */
@Composable
private fun ReviewLine(onReview: () -> Unit, onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            stringResource(R.string.review_prompt),
            style = MaterialTheme.typography.bodySmall,
            color = LocalPalette.current.muted,
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onReview)
                .padding(vertical = 8.dp)
        )
        IconButton(onClick = onClose) {
            Icon(Icons.Filled.Close, stringResource(R.string.action_close), tint = LocalPalette.current.muted)
        }
    }
}
