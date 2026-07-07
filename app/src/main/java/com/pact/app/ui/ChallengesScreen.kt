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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pact.app.R
import com.pact.app.core.PactState
import com.pact.app.core.ShareCard
import com.pact.app.core.TrustNetwork
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

private data class Standing(
    val name: String,
    val streak: Int,
    val screenTime: Int,
    val isMe: Boolean,
    val held: Boolean,
)

@Composable
fun ChallengesScreen(state: PactState, onBack: () -> Unit) {
    val context = LocalContext.current
    val network = remember { TrustNetwork.get(context) }
    val pactSnap by state.snapshot.collectAsState()
    val netSnap by network.snapshot.collectAsState()
    val now by rememberNow()

    val challenge = netSnap.challenge?.takeIf { !it.finished(now) }
    val myName = network.myName.ifBlank { stringResource(R.string.leaderboard_you) }

    val standings = remember(netSnap, pactSnap, now) {
        val me = Standing(
            name = myName,
            streak = pactSnap.streakDays(now),
            screenTime = pactSnap.screenTimeTodayMinutes(),
            isMe = true,
            held = challenge?.let { pactSnap.lastUnlockAt < it.startAt } ?: true,
        )
        val friends = netSnap.peerStats.mapNotNull { (id, st) ->
            val c = network.contact(id) ?: return@mapNotNull null
            // In a challenge, only its participants belong on the board.
            if (challenge != null && id !in challenge.participantIds) return@mapNotNull null
            Standing(
                name = c.name,
                streak = st.streakDays,
                screenTime = st.screenTimeMinutes,
                isMe = false,
                held = challenge?.let { st.lastUnlockAt < it.startAt } ?: true,
            )
        }
        (listOf(me) + friends).sortedWith(
            compareByDescending<Standing> { it.streak }.thenBy { it.screenTime }
        )
    }
    val myRank = standings.indexOfFirst { it.isMe } + 1

    var creating by remember { mutableStateOf(false) }

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
            Text(stringResource(R.string.challenges_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            IconButton(onClick = {
                ShareCard.share(
                    context,
                    ShareCard.Data(
                        streakDays = pactSnap.streakDays(now),
                        longestStreakDays = pactSnap.longestStreakDays,
                        screenTimeMinutes = pactSnap.screenTimeTodayMinutes(),
                        challengeName = challenge?.name,
                        rank = challenge?.let { myRank },
                        partySize = challenge?.let { standings.size },
                    ),
                )
            }) {
                Icon(Icons.Rounded.Share, contentDescription = stringResource(R.string.share_streak), tint = Periwinkle)
            }
        }

        // Pending invite → accept / decline
        val invite = netSnap.challenge?.takeIf { it.ownerId.isNotEmpty() && !it.joinedByMe && !it.finished(now) }
        if (invite != null) {
            InviteCard(
                name = network.contact(invite.ownerId)?.name ?: "",
                challengeName = invite.name,
                days = invite.days,
                onAccept = { network.respondChallenge(true) },
                onDecline = { network.respondChallenge(false) },
            )
            Spacer(Modifier.height(16.dp))
        }

        // Active challenge header
        if (challenge != null && challenge.joinedByMe) {
            ChallengeHeader(
                name = challenge.name,
                day = challenge.dayNumber(now),
                total = challenge.days,
                endsIn = challenge.endsAt - now,
                leaderName = standings.firstOrNull()?.name ?: myName,
            )
            Spacer(Modifier.height(16.dp))
        }

        // Leaderboard
        SectionLabel(
            if (challenge != null) stringResource(R.string.leaderboard_challenge)
            else stringResource(R.string.leaderboard_circle)
        )
        Spacer(Modifier.height(8.dp))
        if (standings.size == 1) {
            PactCard {
                Text(
                    stringResource(R.string.leaderboard_solo),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
            }
        } else {
            PactCard {
                standings.forEachIndexed { i, s ->
                    if (i > 0) Spacer(Modifier.height(4.dp))
                    StandingRow(rank = i + 1, standing = s, challengeActive = challenge != null)
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // Start a challenge (only when none is running)
        if (challenge == null && invite == null) {
            StartChallengeCard(
                hasFriends = netSnap.contacts.isNotEmpty(),
                onStart = { creating = true },
            )
        } else if (challenge != null && challenge.joinedByMe) {
            TextButton(
                onClick = { network.dismissChallenge() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.challenge_leave), color = TextTertiary)
            }
        }

        Spacer(Modifier.height(32.dp))
    }

    if (creating) {
        CreateChallengeDialog(
            network = network,
            onDismiss = { creating = false },
            onCreated = { creating = false },
        )
    }
}

@Composable
private fun ChallengeHeader(name: String, day: Int, total: Int, endsIn: Long, leaderName: String) {
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Surface2, Violet.copy(alpha = 0.30f))))
            .border(1.dp, CardBorder, shape)
            .padding(22.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.EmojiEvents, contentDescription = null, tint = Amber)
            Spacer(Modifier.width(10.dp))
            Text(name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(14.dp))
        Text(
            stringResource(R.string.challenge_day_of, day, total),
            style = MaterialTheme.typography.displaySmall,
            color = Periwinkle,
        )
        // progress
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape)
                .background(Surface1)
        ) {
            Box(
                Modifier
                    .fillMaxWidth((day.toFloat() / total).coerceIn(0f, 1f))
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(PactGradient)
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.challenge_leader, leaderName),
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )
        Text(
            stringResource(R.string.challenge_ends_in, formatCountdown(endsIn)),
            style = MaterialTheme.typography.labelMedium,
            color = TextTertiary,
        )
    }
}

@Composable
private fun StandingRow(rank: Int, standing: Standing, challengeActive: Boolean) {
    val medal = when (rank) {
        1 -> Amber
        2 -> Periwinkle
        3 -> Rose
        else -> TextTertiary
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (standing.isMe) Surface2 else Color.Transparent)
            .padding(horizontal = 10.dp, vertical = 10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(medal.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Text("$rank", style = MaterialTheme.typography.labelLarge, color = medal, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                standing.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (standing.isMe) FontWeight.Bold else FontWeight.Normal,
            )
            Text(
                if (standing.screenTime > 0)
                    stringResource(R.string.leaderboard_screen_time, standing.screenTime)
                else stringResource(R.string.leaderboard_no_screen_time),
                style = MaterialTheme.typography.labelMedium,
                color = TextTertiary,
            )
        }
        if (challengeActive && !standing.held) {
            Text(
                stringResource(R.string.leaderboard_out),
                style = MaterialTheme.typography.labelMedium,
                color = Rose,
            )
            Spacer(Modifier.width(10.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.LocalFireDepartment, contentDescription = null, tint = if (standing.streak > 0) Amber else TextTertiary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("${standing.streak}", style = MaterialTheme.typography.titleMedium, color = if (standing.streak > 0) Amber else TextTertiary)
        }
    }
}

@Composable
private fun InviteCard(
    name: String,
    challengeName: String,
    days: Int,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    PactCard(background = Surface2) {
        Text(
            stringResource(R.string.challenge_invite_title, name),
            style = MaterialTheme.typography.titleMedium,
            color = Periwinkle,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.challenge_invite_body, challengeName, days),
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PactButton(stringResource(R.string.challenge_join), onClick = onAccept, modifier = Modifier.weight(1f))
            PactButton(stringResource(R.string.challenge_no_thanks), onClick = onDecline, tonal = true, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun StartChallengeCard(hasFriends: Boolean, onStart: () -> Unit) {
    PactCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.EmojiEvents, contentDescription = null, tint = Amber)
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.challenge_start_title), style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.challenge_start_body),
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )
        Spacer(Modifier.height(16.dp))
        PactButton(
            stringResource(R.string.challenge_start_button),
            onClick = onStart,
            enabled = hasFriends,
            modifier = Modifier.fillMaxWidth(),
        )
        if (!hasFriends) {
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.challenge_need_friends),
                style = MaterialTheme.typography.labelMedium,
                color = TextTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun CreateChallengeDialog(
    network: TrustNetwork,
    onDismiss: () -> Unit,
    onCreated: () -> Unit,
) {
    val snapshot by network.snapshot.collectAsState()
    val presets = listOf(
        stringResource(R.string.challenge_preset_noscroll),
        stringResource(R.string.challenge_preset_week),
        stringResource(R.string.challenge_preset_lockin),
    )
    var name by remember { mutableStateOf(presets.first()) }
    var days by remember { mutableIntStateOf(7) }
    var picked by remember { mutableStateOf(setOf<String>()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringResource(R.string.challenge_create_title), style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                SectionLabel(stringResource(R.string.challenge_name_label))
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    presets.forEach { p ->
                        Chip(text = p, selected = name == p, onClick = { name = p })
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(24) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Periwinkle,
                        unfocusedBorderColor = Surface2,
                    ),
                )
                Spacer(Modifier.height(16.dp))
                SectionLabel(stringResource(R.string.challenge_length_label))
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(3, 7, 14, 30).forEach { d ->
                        Chip(text = stringResource(R.string.challenge_days_n, d), selected = days == d, onClick = { days = d })
                    }
                }
                Spacer(Modifier.height(16.dp))
                SectionLabel(stringResource(R.string.challenge_invite_label))
                Spacer(Modifier.height(8.dp))
                snapshot.contacts.forEach { c ->
                    val on = c.id in picked
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (on) Surface2 else MaterialTheme.colorScheme.surface)
                            .border(1.dp, if (on) Periwinkle.copy(alpha = 0.5f) else CardBorder, RoundedCornerShape(14.dp))
                            .clickable { picked = if (on) picked - c.id else picked + c.id }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        Text(c.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(if (on) Periwinkle else Surface2),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (on) Icon(Icons.Rounded.Check, contentDescription = null, tint = Ink, modifier = Modifier.size(14.dp))
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && picked.isNotEmpty(),
                onClick = {
                    network.createChallenge(name.trim(), days, picked)
                    onCreated()
                },
            ) { Text(stringResource(R.string.challenge_send_invites), color = Periwinkle) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) PactGradient else androidx.compose.ui.graphics.SolidColor(Surface2))
            .border(1.dp, if (selected) Periwinkle else CardBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) Ink else TextSecondary,
        )
    }
}
