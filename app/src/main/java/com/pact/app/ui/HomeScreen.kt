package com.pact.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.pact.app.R
import com.pact.app.core.Apps
import com.pact.app.core.PactState
import com.pact.app.core.TrustNetwork
import com.pact.app.service.BlockerService
import com.pact.app.ui.theme.Amber
import com.pact.app.ui.theme.CardBorder
import com.pact.app.ui.theme.Ink
import com.pact.app.ui.theme.Mint
import com.pact.app.ui.theme.PactGradient
import com.pact.app.ui.theme.Periwinkle
import com.pact.app.ui.theme.Rose
import com.pact.app.ui.theme.Surface1
import com.pact.app.ui.theme.Surface2
import com.pact.app.ui.theme.TextSecondary
import com.pact.app.ui.theme.TextTertiary
import com.pact.app.ui.theme.Violet
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    state: PactState,
    onAddApps: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenCircle: () -> Unit,
    onOpenChallenges: () -> Unit,
    onOpenReceipts: () -> Unit,
) {
    val context = LocalContext.current
    val network = remember { TrustNetwork.get(context) }
    val snapshot by state.snapshot.collectAsState()
    val netSnap by network.snapshot.collectAsState()
    val now by rememberNow()
    val serviceOn by produceState(initialValue = BlockerService.isEnabled(context)) {
        while (true) {
            value = BlockerService.isEnabled(context)
            delay(1500L)
        }
    }
    var appForAction by remember { mutableStateOf<String?>(null) }
    var changeRequested by remember { mutableStateOf(false) }
    var choosingFocus by remember { mutableStateOf(false) }
    val hasCircle = netSnap.approvers().isNotEmpty()
    val focusActive = snapshot.focusActive(now)

    // Break/shield notifications are optional; ask once on Android 13+.
    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        // header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 16.dp),
        ) {
            PactLogo(38)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
                Text(
                    if (hasCircle) stringResource(R.string.circle_members, netSnap.supporters().size)
                    else stringResource(R.string.app_name),
                    style = MaterialTheme.typography.labelMedium,
                    color = Periwinkle,
                )
            }
            IconButton(onClick = onOpenChallenges) {
                Icon(Icons.Rounded.EmojiEvents, contentDescription = stringResource(R.string.challenges_title), tint = TextSecondary)
            }
            IconButton(onClick = onOpenReceipts) {
                Icon(Icons.Rounded.ReceiptLong, contentDescription = stringResource(R.string.receipts_title), tint = TextSecondary)
            }
            IconButton(onClick = onOpenCircle) {
                Icon(Icons.Rounded.Group, contentDescription = stringResource(R.string.circle_title), tint = TextSecondary)
            }
            IconButton(onClick = onOpenStats) {
                Icon(Icons.Rounded.BarChart, contentDescription = stringResource(R.string.stats_open), tint = TextSecondary)
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.home_settings), tint = TextSecondary)
            }
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f),
        ) {
            // hero status
            item {
                HeroCard(
                    serviceOn = serviceOn,
                    lockedCount = snapshot.blocked.size,
                    screenTimeToday = snapshot.screenTimeTodayMinutes(),
                    streakDays = snapshot.streakDays(now),
                    onEnable = {
                        context.startActivity(
                            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    },
                )
            }

            // squad: the co-op tower + everyone's streaks
            if (hasCircle) {
                item {
                    val myStatus = when {
                        snapshot.focusActive(now) -> TrustNetwork.PRESENCE_FOCUS
                        snapshot.unlockUntil.any { it.value > now && it.key in snapshot.blocked } -> TrustNetwork.PRESENCE_OFFTRACK
                        else -> TrustNetwork.PRESENCE_ZONE
                    }
                    SquadTowerCard(
                        myFace = network.myAvatar,
                        myName = netSnap.myName.ifBlank { stringResource(R.string.leaderboard_you) },
                        myStreak = snapshot.streakDays(now),
                        myStatus = myStatus,
                        supporters = netSnap.supporters(),
                        peerStats = netSnap.peerStats,
                        peerPresence = netSnap.peerPresence,
                        onOpen = onOpenChallenges,
                    )
                }
            }

            // focus session: active banner, or the invitation to start one
            if (snapshot.blocked.isNotEmpty()) {
                item {
                    if (focusActive) {
                        PactCard(
                            background = Surface2,
                            modifier = Modifier.padding(top = 4.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Bolt, contentDescription = null, tint = Periwinkle)
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        stringResource(R.string.focus_active_title),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = Periwinkle,
                                    )
                                    Text(
                                        stringResource(
                                            R.string.focus_active_sub,
                                            formatCountdown(snapshot.focusUntil - now),
                                        ),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondary,
                                    )
                                }
                            }
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(Surface1)
                                .border(1.dp, CardBorder, RoundedCornerShape(20.dp))
                                .clickable { choosingFocus = true }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Surface2),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Rounded.Bolt, contentDescription = null, tint = Periwinkle, modifier = Modifier.size(22.dp))
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.focus_start), style = MaterialTheme.typography.titleSmall)
                                Text(
                                    stringResource(R.string.focus_body),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextTertiary,
                                )
                            }
                        }
                    }
                }
            }

            // circle empty → gentle nudge (red locks fall back to a pause)
            if (!hasCircle) {
                item {
                    PactCard(
                        background = Surface2,
                        modifier = Modifier.padding(top = 4.dp).clickable(onClick = onOpenCircle),
                    ) {
                        Text(
                            stringResource(R.string.circle_needs_people),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Amber,
                        )
                    }
                }
            }

            // reflection: one gentle question after a break ends
            val pending = snapshot.pendingReflection(now)
            if (pending != null) {
                item {
                    PactCard(background = Surface2) {
                        Text(
                            stringResource(R.string.reflect_title, Apps.label(context, pending.pkg)),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            stringResource(R.string.reflect_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PactButton(
                                stringResource(R.string.reflect_yes),
                                onClick = { state.setWorthIt(pending.at, true) },
                                tonal = true,
                                modifier = Modifier.weight(1f),
                            )
                            PactButton(
                                stringResource(R.string.reflect_no),
                                onClick = { state.setWorthIt(pending.at, false) },
                                tonal = true,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        TextButton(onClick = { state.setWorthIt(pending.at, null) }) {
                            Text(stringResource(R.string.reflect_skip), color = TextTertiary)
                        }
                    }
                }
            }

            // currently unlocked
            val unlockedNow = snapshot.unlockUntil.filter { it.value > now && it.key in snapshot.blocked }
            if (unlockedNow.isNotEmpty()) {
                item { SectionLabel(stringResource(R.string.section_on_break), Modifier.padding(top = 12.dp)) }
                items(unlockedNow.keys.sorted(), key = { "u_$it" }) { pkg ->
                    AppRow(
                        pkg = pkg,
                        subtitle = stringResource(
                            R.string.row_relocks_in,
                            formatCountdown((unlockedNow[pkg] ?: 0) - now),
                        ),
                        subtitleColor = Mint,
                        trailing = {
                            TextButton(onClick = { state.relock(pkg) }) {
                                Text(stringResource(R.string.action_relock), color = Periwinkle)
                            }
                        },
                    )
                }
            }

            // your apps: each with its daily allowance
            item { SectionLabel(stringResource(R.string.section_your_apps), Modifier.padding(top = 12.dp)) }
            val managed = snapshot.blocked
                .filter { (snapshot.unlockUntil[it] ?: 0L) <= now }
                .sortedBy { Apps.label(context, it).lowercase() }
            if (snapshot.blocked.isEmpty()) {
                item {
                    PactCard(modifier = Modifier.clickable(onClick = onAddApps)) {
                        Text(
                            stringResource(R.string.home_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                        )
                    }
                }
            }
            items(managed, key = { "l_$it" }) { pkg ->
                LimitedAppRow(
                    pkg = pkg,
                    limitMinutes = snapshot.limitMinutes(pkg),
                    usedMinutes = (snapshot.usedMillis(pkg) / 60_000L).toInt(),
                    remainingMinutes = snapshot.remainingMinutes(pkg),
                    tier = snapshot.tierOf(pkg),
                    onClick = { appForAction = pkg },
                )
            }
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(onClick = onAddApps)
                        .padding(vertical = 10.dp, horizontal = 6.dp),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, tint = Periwinkle, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.home_add_apps), style = MaterialTheme.typography.titleSmall, color = Periwinkle)
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }

    // action dialog for a locked app: tier control + circle-gated changes
    appForAction?.let { pkg ->
        val tier = snapshot.tierOf(pkg)
        AlertDialog(
            onDismissRequest = { appForAction = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(Apps.label(context, pkg), style = MaterialTheme.typography.headlineSmall) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    SectionLabel(stringResource(R.string.limit_label))
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.limit_help),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextTertiary,
                    )
                    Spacer(Modifier.height(10.dp))
                    LimitChips(
                        current = snapshot.limitMinutes(pkg),
                        onPick = { target ->
                            val current = snapshot.limitMinutes(pkg)
                            when {
                                target == current -> {}
                                target < current || !hasCircle -> state.setDailyLimit(pkg, target)
                                else -> {
                                    network.createRequest(
                                        kind = TrustNetwork.RequestKind.CHANGE,
                                        changeAction = TrustNetwork.CHANGE_LIMIT_UP,
                                        pkg = pkg,
                                        label = Apps.label(context, pkg),
                                        minutes = target,
                                        reason = null,
                                        usageNote = null,
                                    )
                                    changeRequested = true
                                    appForAction = null
                                }
                            }
                        },
                    )
                    Spacer(Modifier.height(20.dp))
                    SectionLabel(stringResource(R.string.tier_label))
                    Spacer(Modifier.height(8.dp))
                    TierOption(
                        selected = tier == PactState.Tier.RED,
                        dotColor = Rose,
                        title = stringResource(R.string.tier_red),
                        body = stringResource(R.string.tier_red_desc),
                        onClick = { if (tier != PactState.Tier.RED) state.setTier(pkg, PactState.Tier.RED) },
                    )
                    Spacer(Modifier.height(8.dp))
                    TierOption(
                        selected = tier == PactState.Tier.YELLOW,
                        dotColor = Amber,
                        title = stringResource(R.string.tier_yellow),
                        body = stringResource(R.string.tier_yellow_desc),
                        onClick = {
                            // relaxing a red lock is a change the circle must approve
                            if (tier == PactState.Tier.RED && hasCircle) {
                                network.createRequest(
                                    kind = TrustNetwork.RequestKind.CHANGE,
                                    changeAction = TrustNetwork.CHANGE_TIER_DOWN,
                                    pkg = pkg,
                                    label = Apps.label(context, pkg),
                                    minutes = 0,
                                    reason = null,
                                    usageNote = null,
                                )
                                changeRequested = true
                                appForAction = null
                            } else if (tier == PactState.Tier.RED && !hasCircle) {
                                state.setTier(pkg, PactState.Tier.YELLOW)
                            }
                        },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (hasCircle) {
                        network.createRequest(
                            kind = TrustNetwork.RequestKind.CHANGE,
                            changeAction = TrustNetwork.CHANGE_REMOVE_APP,
                            pkg = pkg,
                            label = Apps.label(context, pkg),
                            minutes = 0,
                            reason = null,
                            usageNote = null,
                        )
                        changeRequested = true
                    } else {
                        state.removeBlocked(pkg)
                    }
                    appForAction = null
                }) { Text(stringResource(R.string.action_remove_from_pact), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { appForAction = null }) { Text(stringResource(R.string.common_close)) }
            },
        )
    }

    if (choosingFocus) {
        AlertDialog(
            onDismissRequest = { choosingFocus = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(stringResource(R.string.focus_pick_title), style = MaterialTheme.typography.headlineSmall) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(
                        R.string.focus_25m to 25 * 60_000L,
                        R.string.focus_1h to 60 * 60_000L,
                        R.string.focus_2h to 120 * 60_000L,
                    ).forEach { (labelRes, duration) ->
                        PactButton(
                            stringResource(labelRes),
                            onClick = {
                                state.startFocus(duration)
                                choosingFocus = false
                            },
                            tonal = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.focus_confirm_note),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextTertiary,
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { choosingFocus = false }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }

    if (changeRequested) {
        AlertDialog(
            onDismissRequest = { changeRequested = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(stringResource(R.string.request_sent), style = MaterialTheme.typography.headlineSmall) },
            text = {
                Text(
                    stringResource(R.string.request_change_sent),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = { changeRequested = false }) { Text(stringResource(R.string.common_done)) }
            },
        )
    }
}

/** Daily-allowance picker: Off (hard lock) through a couple of hours. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun LimitChips(current: Int, onPick: (Int) -> Unit) {
    val options = listOf(0, 10, 20, 30, 45, 60, 90, 120)
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { m ->
            val selected = m == current
            val label = when {
                m == 0 -> stringResource(R.string.limit_off)
                m % 60 == 0 -> stringResource(R.string.limit_hours, m / 60)
                m > 60 -> stringResource(R.string.limit_h_m, m / 60, m % 60)
                else -> stringResource(R.string.limit_minutes, m)
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) PactGradient else androidx.compose.ui.graphics.SolidColor(Surface2))
                    .border(1.dp, if (selected) Periwinkle else CardBorder, RoundedCornerShape(12.dp))
                    .clickable { onPick(m) }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) Ink else TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun TierOption(
    selected: Boolean,
    dotColor: androidx.compose.ui.graphics.Color,
    title: String,
    body: String,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) Surface2 else MaterialTheme.colorScheme.surface)
            .border(1.dp, if (selected) dotColor.copy(alpha = 0.6f) else CardBorder, shape)
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Box(
            Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }
    }
}

/**
 * The Squad Hub centrepiece: a co-op tower that grows with everyone's combined
 * streak, plus a row of glossy avatar faces and their fire counts.
 */
@Composable
private fun SquadTowerCard(
    myFace: String,
    myName: String,
    myStreak: Int,
    myStatus: String,
    supporters: List<TrustNetwork.Contact>,
    peerStats: Map<String, TrustNetwork.PeerStats>,
    peerPresence: Map<String, TrustNetwork.Presence>,
    onOpen: () -> Unit,
) {
    val teamStreak = myStreak + supporters.sumOf { peerStats[it.id]?.streakDays ?: 0 }
    val level = teamStreak.coerceIn(0, 12)
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Surface1)
            .border(1.5.dp, CardBorder, shape)
            .clickable(onClick = onOpen)
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.squad_tower_label), style = MaterialTheme.typography.labelMedium, color = TextTertiary)
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("$teamStreak", style = MaterialTheme.typography.displaySmall, color = Periwinkle)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.squad_streak_unit),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
            }
            // the tower: blocks stack upward, glowing lime
            val pulse by rememberInfiniteTransition(label = "tower").animateFloat(
                initialValue = 0.45f, targetValue = 1f,
                animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                    androidx.compose.animation.core.tween(1300),
                    androidx.compose.animation.core.RepeatMode.Reverse,
                ),
                label = "pulse",
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                val blocks = level.coerceIn(1, 6)
                repeat(blocks) { i ->
                    val fromTop = i
                    val alpha = if (i == 0) pulse else 0.55f + 0.45f * (fromTop.toFloat() / blocks)
                    Box(
                        modifier = Modifier
                            .width((26 + fromTop * 5).dp)
                            .height(10.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Periwinkle.copy(alpha = alpha))
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            AvatarBubble(face = myFace, name = myName, streak = myStreak, status = myStatus, me = true)
            supporters.take(4).forEach { c ->
                AvatarBubble(
                    face = c.face,
                    name = c.name,
                    streak = peerStats[c.id]?.streakDays,
                    status = peerPresence[c.id]?.takeIf { it.fresh() }?.status,
                )
            }
        }
    }
}

@Composable
private fun AvatarBubble(face: String, name: String, streak: Int?, status: String?, me: Boolean = false) {
    val dot = statusColor(status)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.BottomEnd) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Surface2)
                    .border(if (me) 2.dp else 1.dp, if (me) Periwinkle else CardBorder, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(face, fontSize = 24.sp)
            }
            // live status dot
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(Surface1)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(dot)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            name.take(8),
            style = MaterialTheme.typography.labelMedium,
            color = if (me) Periwinkle else TextSecondary,
            maxLines = 1,
        )
        Text(
            statusLabel(status, streak),
            style = MaterialTheme.typography.labelMedium,
            color = TextTertiary,
            maxLines = 1,
        )
    }
}

private fun statusColor(status: String?): androidx.compose.ui.graphics.Color = when (status) {
    TrustNetwork.PRESENCE_FOCUS, TrustNetwork.PRESENCE_ZONE -> Mint
    TrustNetwork.PRESENCE_OFFTRACK -> Rose
    else -> TextTertiary
}

@Composable
private fun statusLabel(status: String?, streak: Int?): String = when (status) {
    TrustNetwork.PRESENCE_FOCUS -> stringResource(R.string.presence_focus)
    TrustNetwork.PRESENCE_ZONE -> stringResource(R.string.presence_zone)
    TrustNetwork.PRESENCE_OFFTRACK -> stringResource(R.string.presence_off)
    else -> if (streak != null) "🔥$streak" else stringResource(R.string.presence_idle)
}

/** Big Breezy-style status hero: shield state + stat tiles. */
@Composable
private fun HeroCard(
    serviceOn: Boolean,
    lockedCount: Int,
    screenTimeToday: Int,
    streakDays: Int,
    onEnable: () -> Unit,
) {
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (serviceOn) Brush.linearGradient(
                    listOf(Surface2, Violet.copy(alpha = 0.25f))
                ) else Brush.linearGradient(listOf(Surface2, Amber.copy(alpha = 0.16f)))
            )
            .border(1.dp, CardBorder, shape)
            .padding(22.dp)
            .animateContentSize(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Surface1),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Shield,
                    contentDescription = null,
                    tint = if (serviceOn) Mint else Amber,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    stringResource(if (serviceOn) R.string.hero_active_title else R.string.hero_down_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (serviceOn) Mint else Amber,
                )
                Text(
                    stringResource(if (serviceOn) R.string.hero_active_sub else R.string.hero_down_sub),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        if (serviceOn) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(
                    value = "$lockedCount",
                    label = stringResource(R.string.stat_limited_label),
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    value = if (screenTimeToday >= 60) "${screenTimeToday / 60}h ${screenTimeToday % 60}m" else "${screenTimeToday}m",
                    label = stringResource(R.string.stat_screen_time_label),
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    value = "$streakDays",
                    label = stringResource(R.string.stat_streak_label),
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            PactButton(
                stringResource(R.string.hero_enable),
                onClick = onEnable,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Surface1.copy(alpha = 0.75f))
            .border(1.dp, CardBorder, RoundedCornerShape(18.dp))
            .padding(horizontal = 12.dp, vertical = 14.dp),
    ) {
        Text(value, style = MaterialTheme.typography.headlineMedium, color = Periwinkle, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelMedium, color = TextSecondary, maxLines = 2)
    }
}

/** A managed app with its daily allowance: a usage bar and how much time is left. */
@Composable
private fun LimitedAppRow(
    pkg: String,
    limitMinutes: Int,
    usedMinutes: Int,
    remainingMinutes: Int,
    tier: PactState.Tier,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(20.dp)
    val hardLock = limitMinutes <= 0
    val reached = !hardLock && remainingMinutes <= 0
    val fraction = if (hardLock || limitMinutes == 0) 1f
    else (usedMinutes.toFloat() / limitMinutes).coerceIn(0f, 1f)
    val accent = when {
        hardLock -> Rose
        reached -> Rose
        else -> Mint
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Surface1)
            .border(1.dp, CardBorder, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIconImage(remember(pkg) { Apps.icon(context, pkg) }, sizeDp = 42)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(Apps.label(context, pkg), style = MaterialTheme.typography.titleSmall)
                Text(
                    when {
                        hardLock -> stringResource(R.string.row_hard_locked)
                        reached -> stringResource(R.string.row_limit_reached)
                        else -> stringResource(R.string.row_time_left, remainingMinutes)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = accent,
                )
            }
            if (hardLock || reached) {
                Icon(
                    Icons.Rounded.Lock,
                    contentDescription = stringResource(R.string.cd_locked),
                    tint = TextTertiary,
                    modifier = Modifier.size(20.dp),
                )
            } else {
                Text(
                    stringResource(R.string.row_limit_of, limitMinutes),
                    style = MaterialTheme.typography.labelMedium,
                    color = TextTertiary,
                )
            }
        }
        if (!hardLock) {
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(Surface2)
            ) {
                val barBrush = if (reached) androidx.compose.ui.graphics.SolidColor(Rose)
                else Brush.linearGradient(listOf(Mint, Periwinkle))
                Box(
                    Modifier
                        .fillMaxWidth(fraction)
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(barBrush)
                )
            }
        }
    }
}

@Composable
private fun AppRow(
    pkg: String,
    subtitle: String?,
    subtitleColor: androidx.compose.ui.graphics.Color,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(20.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Surface1)
            .border(1.dp, CardBorder, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        AppIconImage(remember(pkg) { Apps.icon(context, pkg) }, sizeDp = 42)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(Apps.label(context, pkg), style = MaterialTheme.typography.titleSmall)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = subtitleColor)
            }
        }
        trailing()
    }
}
