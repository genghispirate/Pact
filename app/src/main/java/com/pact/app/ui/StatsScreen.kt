package com.pact.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pact.app.R
import com.pact.app.core.Apps
import com.pact.app.core.PactState
import com.pact.app.core.PactState.Trigger
import com.pact.app.ui.theme.CardBorder
import com.pact.app.ui.theme.PactGradient
import com.pact.app.ui.theme.Periwinkle
import com.pact.app.ui.theme.Surface2
import com.pact.app.ui.theme.TextSecondary
import com.pact.app.ui.theme.TextTertiary
import java.util.Locale

/**
 * Insights: only honest, measured numbers. Streak (days since last unlock),
 * a 7-day walls-per-day bar chart, most tempting apps, when cravings hit,
 * what drives them, and how reflected breaks felt afterwards.
 */
@Composable
fun StatsScreen(state: PactState, onBack: () -> Unit) {
    val context = LocalContext.current
    val snapshot by state.snapshot.collectAsState()
    val last7 = snapshot.days.takeLast(7)
    val hasData = last7.any { it.blocks > 0 || it.unlocks > 0 || it.walkaways > 0 }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 12.dp, bottom = 16.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.common_back),
                    tint = TextSecondary,
                )
            }
            Text(stringResource(R.string.stats_title), style = MaterialTheme.typography.headlineSmall)
        }

        // streak hero
        PactCard {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.stats_streak_value, snapshot.streakDays()),
                    style = MaterialTheme.typography.displaySmall,
                    color = Periwinkle,
                )
                Text(
                    stringResource(R.string.stats_streak_label),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(
                        R.string.stats_streak_longest,
                        maxOf(snapshot.longestStreakDays, snapshot.streakDays()),
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = TextTertiary,
                )
            }
        }

        if (!hasData) {
            Spacer(Modifier.height(12.dp))
            PactCard {
                Text(
                    stringResource(R.string.stats_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
            }
            Spacer(Modifier.height(32.dp))
            return@Column
        }

        // 7-day totals
        Spacer(Modifier.height(20.dp))
        SectionLabel(stringResource(R.string.stats_week_section))
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MiniStat(last7.sumOf { it.blocks }, stringResource(R.string.stats_blocks_7d), Modifier.weight(1f))
            MiniStat(last7.sumOf { it.walkaways }, stringResource(R.string.stats_walkaways_7d), Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MiniStat(last7.sumOf { it.unlocks }, stringResource(R.string.stats_unlocks_7d), Modifier.weight(1f))
            MiniStat(last7.sumOf { it.minutesUnlocked }, stringResource(R.string.stats_minutes_7d), Modifier.weight(1f))
        }

        // walls per day bar chart
        Spacer(Modifier.height(12.dp))
        PactCard {
            Text(stringResource(R.string.stats_chart_blocks_title), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(14.dp))
            BarChart(
                values = last7.map { it.blocks },
                labels = last7.map { dayLabel(it.day) },
            )
        }

        // most tempting apps
        val tempting = remember(last7) {
            last7.flatMap { it.blocksPerApp.entries }
                .groupBy({ it.key }, { it.value })
                .mapValues { it.value.sum() }
                .entries.sortedByDescending { it.value }
                .take(3)
        }
        if (tempting.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            SectionLabel(stringResource(R.string.stats_tempting_section))
            Spacer(Modifier.height(8.dp))
            PactCard {
                val max = tempting.first().value.coerceAtLeast(1)
                tempting.forEachIndexed { index, (pkg, count) ->
                    if (index > 0) Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppIconImage(remember(pkg) { Apps.icon(context, pkg) }, sizeDp = 32)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row {
                                Text(
                                    Apps.label(context, pkg),
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    "$count",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = TextSecondary,
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(CircleShape)
                                    .background(Surface2)
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxWidth(count.toFloat() / max)
                                        .height(6.dp)
                                        .clip(CircleShape)
                                        .background(PactGradient)
                                )
                            }
                        }
                    }
                }
            }
        }

        // craving hours
        if (snapshot.hourHistogram.any { it > 0 }) {
            Spacer(Modifier.height(20.dp))
            SectionLabel(stringResource(R.string.stats_hours_section))
            Spacer(Modifier.height(8.dp))
            PactCard {
                val peakHour = snapshot.hourHistogram.indices.maxBy { snapshot.hourHistogram[it] }
                Text(
                    stringResource(R.string.stats_hours_peak, formatHour(peakHour)),
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(14.dp))
                BarChart(
                    values = snapshot.hourHistogram,
                    labels = (0 until 24).map { if (it % 6 == 0) formatHourShort(it) else "" },
                    barSpacing = 2.dp,
                    highlightIndex = peakHour,
                )
            }
        }

        // triggers
        val triggerCounts = remember(snapshot.events) {
            snapshot.events.mapNotNull { it.trigger }
                .groupingBy { it }
                .eachCount()
                .entries.sortedByDescending { it.value }
        }
        Spacer(Modifier.height(20.dp))
        SectionLabel(stringResource(R.string.stats_triggers_section))
        Spacer(Modifier.height(8.dp))
        PactCard {
            if (triggerCounts.isEmpty()) {
                Text(
                    stringResource(R.string.stats_no_triggers),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
            } else {
                triggerCounts.forEachIndexed { index, (trigger, count) ->
                    if (index > 0) Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(triggerLabel(trigger)),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "×$count",
                            style = MaterialTheme.typography.titleSmall,
                            color = Periwinkle,
                        )
                    }
                }
            }
        }

        // worth-it ratio
        val reflected = snapshot.events.filter { it.worthIt != null }
        if (reflected.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            SectionLabel(stringResource(R.string.stats_worth_section))
            Spacer(Modifier.height(8.dp))
            PactCard {
                val worthCount = reflected.count { it.worthIt == true }
                val percent = worthCount * 100 / reflected.size
                Text(
                    stringResource(R.string.stats_worth_ratio, percent, reflected.size),
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun MiniStat(value: Int, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text("$value", style = MaterialTheme.typography.headlineSmall, color = Periwinkle)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
    }
}

/**
 * Minimal single-hue bar chart: thin rounded bars, recessive labels, one
 * emphasized bar (today / peak), value labeled only on the emphasized bar.
 */
@Composable
private fun BarChart(
    values: List<Int>,
    labels: List<String>,
    barSpacing: androidx.compose.ui.unit.Dp = 6.dp,
    highlightIndex: Int = values.size - 1,
) {
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    Column {
        Row(
            horizontalArrangement = Arrangement.spacedBy(barSpacing),
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp),
        ) {
            values.forEachIndexed { i, v ->
                val fraction = v.toFloat() / max
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    if (i == highlightIndex && v > 0) {
                        Text(
                            "$v",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary,
                        )
                        Spacer(Modifier.height(2.dp))
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height((80 * fraction).dp.coerceAtLeast(3.dp))
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(
                                if (i == highlightIndex) Periwinkle
                                else Periwinkle.copy(alpha = 0.45f)
                            )
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(barSpacing), modifier = Modifier.fillMaxWidth()) {
            labels.forEach { label ->
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = TextTertiary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

fun triggerLabel(trigger: Trigger): Int = when (trigger) {
    Trigger.BORED -> R.string.trigger_bored
    Trigger.STRESS -> R.string.trigger_stress
    Trigger.HABIT -> R.string.trigger_habit
    Trigger.ANXIETY -> R.string.trigger_anxiety
    Trigger.LONELY -> R.string.trigger_lonely
    Trigger.PROCRASTINATION -> R.string.trigger_procrastination
    Trigger.NEEDED -> R.string.trigger_needed
}

private fun dayLabel(dayKey: Int): String {
    val day = dayKey % 100
    val month = (dayKey / 100) % 100
    return String.format(Locale.getDefault(), "%d/%d", day, month)
}

private fun formatHour(hour: Int): String =
    String.format(Locale.getDefault(), "%02d:00", hour)

private fun formatHourShort(hour: Int): String =
    String.format(Locale.getDefault(), "%d", hour)
