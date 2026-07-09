package com.pact.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pact.app.R
import com.pact.app.core.Apps
import com.pact.app.core.PactState
import com.pact.app.ui.theme.Surface1
import com.pact.app.ui.theme.Surface2
import com.pact.app.ui.theme.TextSecondary
import com.pact.app.ui.theme.TextTertiary
import com.pact.app.ui.theme.Violet

/**
 * Deep-work focus: pick a stretch, and every locked app stays shut behind a
 * calm ring counting down. Gentle by design — you can stop early, no penalty.
 */
@Composable
fun FocusScreen(state: PactState, onBack: () -> Unit) {
    val context = LocalContext.current
    val snapshot by state.snapshot.collectAsState()
    val now by rememberNowFast()
    val active = snapshot.focusActive(now)
    BackHandler { onBack() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back), tint = TextSecondary)
            }
            Text(stringResource(R.string.focus_screen_title), style = MaterialTheme.typography.headlineSmall)
        }

        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.focus_deep_work),
            style = MaterialTheme.typography.titleMedium,
            color = Violet,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(24.dp))

        // the ring
        val total = (snapshot.focusUntil - snapshot.focusStartAt).coerceAtLeast(1L)
        val remaining = (snapshot.focusUntil - now).coerceAtLeast(0L)
        val progress = if (active) (1f - remaining.toFloat() / total).coerceIn(0f, 1f) else 0f
        Box(
            modifier = Modifier.fillMaxWidth().aspectRatio(1f).padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 22f
                val d = size.minDimension - stroke
                val topLeft = Offset((size.width - d) / 2f, (size.height - d) / 2f)
                drawArc(
                    color = Surface2, startAngle = 0f, sweepAngle = 360f, useCenter = false,
                    topLeft = topLeft, size = Size(d, d), style = Stroke(stroke, cap = StrokeCap.Round),
                )
                drawArc(
                    brush = Brush.sweepGradient(listOf(Violet, com.pact.app.ui.theme.Periwinkle, Violet)),
                    startAngle = -90f, sweepAngle = 360f * progress, useCenter = false,
                    topLeft = topLeft, size = Size(d, d), style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (active) formatClock(remaining) else formatClock(0),
                    fontSize = 56.sp, fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                if (active) {
                    Text(
                        stringResource(R.string.focus_until_time, formatEndClock(snapshot.focusUntil)),
                        style = MaterialTheme.typography.bodyMedium, color = TextSecondary,
                    )
                } else {
                    Text(stringResource(R.string.focus_ready), style = MaterialTheme.typography.bodyMedium, color = TextTertiary)
                }
            }
        }

        Spacer(Modifier.height(28.dp))

        if (active) {
            PactButton(stringResource(R.string.focus_stop), onClick = { state.endFocus() }, tonal = true, modifier = Modifier.fillMaxWidth())
        } else {
            Text(stringResource(R.string.focus_pick), style = MaterialTheme.typography.labelMedium, color = TextTertiary)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(
                    R.string.focus_25m to 25 * 60_000L,
                    R.string.focus_1h to 60 * 60_000L,
                    R.string.focus_2h to 120 * 60_000L,
                ).forEach { (labelRes, ms) ->
                    PactButton(stringResource(labelRes), onClick = { state.startFocus(ms) }, modifier = Modifier.weight(1f))
                }
            }
        }

        // what's protected
        if (snapshot.blocked.isNotEmpty()) {
            Spacer(Modifier.height(28.dp))
            Text(stringResource(R.string.focus_blocking), style = MaterialTheme.typography.labelMedium, color = TextTertiary)
            Spacer(Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(snapshot.blocked.toList(), key = { it }) { pkg ->
                    AppIconImage(remember(pkg) { Apps.icon(context, pkg) }, sizeDp = 44)
                }
            }
        }
    }
}

private fun formatClock(millis: Long): String {
    val s = millis / 1000
    return "%02d:%02d".format(s / 60, s % 60)
}

private fun formatEndClock(atMillis: Long): String =
    java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(atMillis))
