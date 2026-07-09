package com.pact.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pact.app.R
import com.pact.app.core.Apps
import com.pact.app.core.PactState
import com.pact.app.core.PactState.Tier
import com.pact.app.core.PactState.Trigger
import com.pact.app.core.TrustNetwork
import com.pact.app.ui.theme.Amber
import com.pact.app.ui.theme.CardBorder
import com.pact.app.ui.theme.Ink
import com.pact.app.ui.theme.Mint
import com.pact.app.ui.theme.PactGradient
import com.pact.app.ui.theme.Periwinkle
import com.pact.app.ui.theme.Surface1
import com.pact.app.ui.theme.Surface2
import com.pact.app.ui.theme.TextSecondary
import com.pact.app.ui.theme.TextTertiary
import com.pact.app.ui.theme.Violet
import kotlinx.coroutines.delay

/**
 * The wall, rendered inside the service-drawn accessibility overlay.
 *
 * Two difficulties:
 *  - RED: ask your circle. A signed, encrypted request goes to your trusted
 *    people; the wall shows a calm waiting state, and if anyone (per your
 *    rule) says yes the break starts automatically — even if you walked away.
 *  - YELLOW: a 30-second mindful pause with optional urge logging, then a
 *    short break — plus a cooldown so self-unlocks can't be chained.
 *
 * If the circle is empty, red falls back to the yellow flow — the app never
 * strands its user.
 */
@Composable
fun BlockWall(
    pkg: String,
    onGoHome: () -> Unit,
    onDismissQuietly: () -> Unit,
    onUnlocked: (durationMillis: Long, tier: Tier, trigger: Trigger?) -> Unit,
) {
    val context = LocalContext.current
    val state = remember { PactState.get(context) }
    val network = remember { TrustNetwork.get(context) }
    val pactSnap by state.snapshot.collectAsState()
    val netSnap by network.snapshot.collectAsState()
    val tier = pactSnap.tierOf(pkg)
    val label = remember(pkg) { Apps.label(context, pkg) }
    val icon = remember(pkg) { Apps.icon(context, pkg) }
    val encouragements = stringArrayResource(R.array.encouragements)
    val encouragement = remember(pkg) { encouragements.random() }
    val now by rememberNow()

    // If an approval lands (or anything else unlocks this app), step aside.
    val unlockedNow = (pactSnap.unlockUntil[pkg] ?: 0L) > now
    LaunchedEffect(unlockedNow) {
        if (unlockedNow) onDismissQuietly()
    }

    val hasApprovers = netSnap.approvers().isNotEmpty()
    val cooldownUntil = pactSnap.yellowCooldownUntil[pkg] ?: 0L
    val yellowResting = cooldownUntil > now

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Ink, Surface1))),
    ) {
        Box(
            modifier = Modifier
                .size(340.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.radialGradient(
                        listOf(Violet.copy(alpha = 0.22f), Violet.copy(alpha = 0f))
                    )
                )
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 40.dp)
                .wrapContentHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(contentAlignment = Alignment.BottomEnd) {
                AppIconImage(icon, sizeDp = 72)
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(PactGradient),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = com.pact.app.ui.theme.OnAccent,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                stringResource(R.string.wall_locked_title, label),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            val overLimit = pactSnap.hasLimit(pkg) && pactSnap.remainingMillis(pkg) <= 0L &&
                !pactSnap.focusActive(now)
            Text(
                if (overLimit) stringResource(R.string.wall_limit_reached, pactSnap.limitMinutes(pkg))
                else encouragement,
                style = MaterialTheme.typography.bodyLarge,
                color = if (overLimit) Amber else TextSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))

            val focusActive = pactSnap.focusActive(now) && pactSnap.blocked.contains(pkg)
            when {
                focusActive -> {
                    Text(
                        stringResource(R.string.focus_active_sub, formatCountdown(pactSnap.focusUntil - now)),
                        style = MaterialTheme.typography.titleSmall,
                        color = Periwinkle,
                        textAlign = TextAlign.Center,
                    )
                }
                tier == Tier.RED && hasApprovers -> AskCircleFlow(
                    network = network,
                    state = state,
                    pkg = pkg,
                    label = label,
                )
                yellowResting -> {
                    Text(
                        stringResource(
                            R.string.wall_cooldown,
                            formatCountdown(cooldownUntil - now),
                            netSnap.approvers().firstOrNull()?.name ?: "",
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Amber,
                        textAlign = TextAlign.Center,
                    )
                }
                else -> YellowFlow(
                    onUnlocked = { duration, trigger -> onUnlocked(duration, Tier.YELLOW, trigger) },
                )
            }

            Spacer(Modifier.height(24.dp))
            TextButton(onClick = onGoHome) {
                Text(stringResource(R.string.wall_go_home), color = TextTertiary)
            }
        }
    }
}

// -------------------------------------------------------- ask-circle (red)

@Composable
private fun AskCircleFlow(
    network: TrustNetwork,
    state: PactState,
    pkg: String,
    label: String,
) {
    val netSnap by network.snapshot.collectAsState()
    val now by rememberNow()
    val myRequest = netSnap.requests.lastOrNull {
        it.pkg == pkg && it.kind == TrustNetwork.RequestKind.UNLOCK
    }
    val pending = myRequest?.takeIf {
        it.state == TrustNetwork.RequestState.PENDING && it.exp > now
    }

    var minutes by remember { mutableIntStateOf(15) }
    var trigger by remember { mutableStateOf<Trigger?>(null) }

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        when {
            pending != null -> {
                CircularProgressIndicator(color = Periwinkle, modifier = Modifier.size(36.dp))
                Spacer(Modifier.height(14.dp))
                Text(
                    stringResource(R.string.request_waiting),
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.request_expires_in, formatCountdown(pending.exp - now)),
                    style = MaterialTheme.typography.labelMedium,
                    color = TextTertiary,
                )
            }
            myRequest != null && myRequest.state == TrustNetwork.RequestState.DENIED &&
                now - myRequest.createdAt < Wire_REQUEST_TTL -> {
                Text(
                    stringResource(R.string.request_denied_banner),
                    style = MaterialTheme.typography.titleSmall,
                    color = Amber,
                    textAlign = TextAlign.Center,
                )
            }
            else -> {
                Text(
                    stringResource(R.string.request_ask),
                    style = MaterialTheme.typography.titleSmall,
                    color = Periwinkle,
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    stringResource(R.string.request_how_long),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextTertiary,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 15, 60).forEach { m ->
                        val selected = minutes == m
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected) PactGradient else androidx.compose.ui.graphics.SolidColor(Surface2))
                                .border(1.dp, if (selected) Periwinkle else CardBorder, RoundedCornerShape(12.dp))
                                .clickable { minutes = m }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                        ) {
                            Text(
                                stringResource(R.string.request_min_generic, m),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) Ink else TextSecondary,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.wall_whats_pulling),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextTertiary,
                )
                Spacer(Modifier.height(8.dp))
                TriggerChips(selected = trigger, onSelect = { trigger = it })
                Spacer(Modifier.height(18.dp))
                val context = LocalContext.current
                PactButton(
                    stringResource(R.string.request_send),
                    onClick = {
                        val reason = trigger?.let { context.getString(triggerLabel(it)) }
                        network.createRequest(
                            kind = TrustNetwork.RequestKind.UNLOCK,
                            pkg = pkg,
                            label = label,
                            minutes = minutes,
                            reason = reason,
                            usageNote = context.getString(
                                R.string.request_usage_note,
                                state.snapshot.value.today.blocksPerApp[pkg] ?: 0,
                            ),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private const val Wire_REQUEST_TTL = 15 * 60 * 1000L

// ------------------------------------------------------------- yellow flow

@Composable
private fun YellowFlow(
    onUnlocked: (durationMillis: Long, trigger: Trigger?) -> Unit,
) {
    var secondsLeft by remember { mutableIntStateOf(PactState.YELLOW_WAIT_SECONDS) }
    var trigger by remember { mutableStateOf<Trigger?>(null) }
    LaunchedEffect(Unit) {
        while (secondsLeft > 0) {
            delay(1000L)
            secondsLeft -= 1
        }
    }
    val waiting = secondsLeft > 0

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        if (waiting) {
            Text(
                stringResource(R.string.wall_pause_title),
                style = MaterialTheme.typography.titleSmall,
                color = Periwinkle,
            )
            Spacer(Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(Surface2)
                    .border(2.dp, Periwinkle, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "$secondsLeft",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.wall_pause_body, PactState.YELLOW_WAIT_SECONDS),
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center,
            )
        } else {
            Text(
                stringResource(R.string.wall_pause_done),
                style = MaterialTheme.typography.titleSmall,
                color = Mint,
            )
        }

        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.wall_whats_pulling),
            style = MaterialTheme.typography.bodyMedium,
            color = TextTertiary,
        )
        Spacer(Modifier.height(10.dp))
        TriggerChips(selected = trigger, onSelect = { trigger = it })

        if (!waiting) {
            Spacer(Modifier.height(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                PactButton(
                    stringResource(R.string.wall_open_5m),
                    onClick = { onUnlocked(5 * 60_000L, trigger) },
                    tonal = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                PactButton(
                    stringResource(R.string.wall_open_15m),
                    onClick = { onUnlocked(15 * 60_000L, trigger) },
                    tonal = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
fun TriggerChips(selected: Trigger?, onSelect: (Trigger?) -> Unit) {
    val labels = mapOf(
        Trigger.BORED to R.string.trigger_bored,
        Trigger.STRESS to R.string.trigger_stress,
        Trigger.HABIT to R.string.trigger_habit,
        Trigger.ANXIETY to R.string.trigger_anxiety,
        Trigger.LONELY to R.string.trigger_lonely,
        Trigger.PROCRASTINATION to R.string.trigger_procrastination,
        Trigger.NEEDED to R.string.trigger_needed,
    )
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        labels.entries.chunked(3).forEach { rowEntries ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowEntries.forEach { (value, labelRes) ->
                    val isSelected = selected == value
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) PactGradient else androidx.compose.ui.graphics.SolidColor(Surface2))
                            .border(
                                1.dp,
                                if (isSelected) Periwinkle else CardBorder,
                                RoundedCornerShape(12.dp),
                            )
                            .clickable { onSelect(if (isSelected) null else value) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text(
                            stringResource(labelRes),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isSelected) Ink else TextSecondary,
                        )
                    }
                }
            }
        }
    }
}
