package com.pact.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Grass
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pact.app.R
import com.pact.app.ui.theme.CardBorder
import com.pact.app.ui.theme.OnAccent
import com.pact.app.ui.theme.PactGradient
import com.pact.app.ui.theme.Surface1
import com.pact.app.ui.theme.TextTertiary
import com.pact.app.ui.theme.Violet

enum class BottomDest { HOME, STATS, FARM, CIRCLE, SETTINGS }

/** The bottom nav: four tabs around a raised violet Farm button. */
@Composable
fun PactBottomBar(current: BottomDest, onSelect: (BottomDest) -> Unit) {
    val haptic = LocalHapticFeedback.current
    fun go(d: BottomDest) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onSelect(d) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Surface1)
            .border(1.dp, CardBorder, RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavItem(Icons.Rounded.Home, stringResource(R.string.nav_home), current == BottomDest.HOME, Modifier.weight(1f)) { go(BottomDest.HOME) }
            NavItem(Icons.Rounded.BarChart, stringResource(R.string.nav_insights), current == BottomDest.STATS, Modifier.weight(1f)) { go(BottomDest.STATS) }
            Spacer(Modifier.weight(1f))   // room for the FAB
            NavItem(Icons.Rounded.Group, stringResource(R.string.nav_squad), current == BottomDest.CIRCLE, Modifier.weight(1f)) { go(BottomDest.CIRCLE) }
            NavItem(Icons.Rounded.Settings, stringResource(R.string.nav_more), current == BottomDest.SETTINGS, Modifier.weight(1f)) { go(BottomDest.SETTINGS) }
        }
        // raised Farm button
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-18).dp)
                .size(58.dp)
                .clip(CircleShape)
                .background(PactGradient)
                .border(4.dp, com.pact.app.ui.theme.Ink, CircleShape)
                .clickable { go(BottomDest.FARM) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Grass, contentDescription = stringResource(R.string.farm_title), tint = OnAccent, modifier = Modifier.size(26.dp))
        }
    }
}

@Composable
private fun NavItem(icon: ImageVector, label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 8.dp),
    ) {
        Icon(icon, contentDescription = label, tint = if (selected) Violet else TextTertiary, modifier = Modifier.size(24.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = if (selected) Violet else TextTertiary, maxLines = 1)
    }
}
