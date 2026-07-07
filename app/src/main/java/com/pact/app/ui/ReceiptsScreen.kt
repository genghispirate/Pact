package com.pact.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pact.app.R
import com.pact.app.core.PactState
import com.pact.app.core.ShareCard
import com.pact.app.core.TrustNetwork
import com.pact.app.ui.theme.CardBorder
import com.pact.app.ui.theme.Mint
import com.pact.app.ui.theme.Periwinkle
import com.pact.app.ui.theme.Surface1
import com.pact.app.ui.theme.TextSecondary
import com.pact.app.ui.theme.TextTertiary

/** Page 5 — the Receipt Shop: this week's numbers, built to post. */
@Composable
fun ReceiptsScreen(state: PactState, onBack: () -> Unit) {
    val context = LocalContext.current
    val network = remember { TrustNetwork.get(context) }
    val snapshot by state.snapshot.collectAsState()
    val netSnap by network.snapshot.collectAsState()
    val now = System.currentTimeMillis()
    val weekAgo = now - 7 * 86_400_000L

    val last7 = snapshot.days.takeLast(7)
    val interventions = last7.sumOf { it.blocks }
    val walkaways = last7.sumOf { it.walkaways }
    val streak = snapshot.streakDays(now)
    val squadDenied = netSnap.requests.count {
        it.state == TrustNetwork.RequestState.DENIED && it.createdAt > weekAgo
    }
    val towerLevel = (streak + netSnap.supporters().sumOf { netSnap.peerStats[it.id]?.streakDays ?: 0 })
        .coerceIn(0, 12)

    val items = listOf(
        stringResource(R.string.receipt_streak) to "$streak",
        stringResource(R.string.receipt_interventions) to "$interventions",
        stringResource(R.string.receipt_walkaways) to "$walkaways",
        stringResource(R.string.receipt_denied) to "$squadDenied",
        stringResource(R.string.receipt_tower) to "LV $towerLevel",
    )

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
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back), tint = TextSecondary)
            }
            Text(stringResource(R.string.receipts_title), style = MaterialTheme.typography.headlineSmall)
        }

        // The receipt itself
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Surface1)
                .border(1.5.dp, CardBorder, RoundedCornerShape(18.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.app_name).uppercase(),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                stringResource(R.string.receipt_title),
                fontFamily = FontFamily.Monospace,
                color = Periwinkle,
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(8.dp))
            Text("- - - - - - - - - - - - - - -", color = TextTertiary, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(12.dp))
            items.forEach { (label, value) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(label.uppercase(), fontFamily = FontFamily.Monospace, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                    Text(value, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = Periwinkle, style = MaterialTheme.typography.titleMedium)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text("- - - - - - - - - - - - - - -", color = TextTertiary, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.receipt_footer), fontFamily = FontFamily.Monospace, color = TextTertiary, style = MaterialTheme.typography.labelMedium)
        }

        Spacer(Modifier.height(20.dp))
        PactButton(
            stringResource(R.string.receipt_share_button),
            onClick = {
                ShareCard.shareReceipt(
                    context,
                    ShareCard.ReceiptData(streak, interventions, walkaways, squadDenied, towerLevel),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.receipt_share_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = Mint,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(32.dp))
    }
}
