package com.pact.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pact.app.R
import com.pact.app.core.FarmState
import com.pact.app.ui.theme.CardBorder
import com.pact.app.ui.theme.Ink
import com.pact.app.ui.theme.Mint
import com.pact.app.ui.theme.Periwinkle
import com.pact.app.ui.theme.Rose
import com.pact.app.ui.theme.Surface1
import com.pact.app.ui.theme.Surface2
import com.pact.app.ui.theme.TextSecondary
import com.pact.app.ui.theme.TextTertiary
import kotlin.math.sin

@Composable
fun FarmScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val farm = remember { FarmState.get(context) }
    val snap by farm.snapshot.collectAsState()
    var adding by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 12.dp, bottom = 12.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back), tint = TextSecondary)
            }
            Text(stringResource(R.string.farm_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            Text("🌱 ${snap.points}", style = MaterialTheme.typography.titleMedium, color = Mint)
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { FarmScene(snap) }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FarmStat("LV ${snap.level}", stringResource(R.string.farm_level), Modifier.weight(1f))
                    FarmStat("${snap.goodStreak}🔥", stringResource(R.string.farm_streak), Modifier.weight(1f))
                    FarmStat("${snap.health}%", stringResource(R.string.farm_health), Modifier.weight(1f))
                }
            }
            item {
                // health / growth bar
                Column {
                    Text(stringResource(R.string.farm_next_level), style = MaterialTheme.typography.labelMedium, color = TextTertiary)
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(Surface2),
                    ) {
                        Box(
                            Modifier.fillMaxWidth(snap.progressInLevel.coerceIn(0f, 1f))
                                .height(10.dp).clip(CircleShape)
                                .background(Brush.horizontalGradient(listOf(Mint, Periwinkle)))
                        )
                    }
                }
            }

            item {
                SectionLabel(stringResource(R.string.farm_habits_label), Modifier.padding(top = 6.dp))
            }
            item {
                Text(
                    stringResource(R.string.farm_habits_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
            }
            items(snap.habits, key = { it.id }) { habit ->
                val done = habit.id in snap.doneToday
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (done) Mint.copy(alpha = 0.12f) else Surface1)
                        .border(1.5.dp, if (done) Mint.copy(alpha = 0.6f) else CardBorder, RoundedCornerShape(16.dp))
                        .clickable { farm.toggleHabit(habit.id) }
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                ) {
                    Text(habit.emoji, fontSize = 26.sp)
                    Spacer(Modifier.width(14.dp))
                    Text(habit.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    if (done) {
                        Text("+${FarmState.HABIT_POINTS}", style = MaterialTheme.typography.labelMedium, color = Mint)
                        Spacer(Modifier.width(8.dp))
                    }
                    Box(
                        modifier = Modifier
                            .size(28.dp).clip(CircleShape)
                            .background(if (done) Mint else Surface2)
                            .border(1.5.dp, if (done) Mint else CardBorder, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (done) Icon(Icons.Rounded.Check, contentDescription = null, tint = Ink, modifier = Modifier.size(18.dp))
                    }
                }
            }
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { adding = true }.padding(vertical = 10.dp, horizontal = 6.dp),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, tint = Periwinkle, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.farm_add_habit), style = MaterialTheme.typography.titleSmall, color = Periwinkle)
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (adding) AddHabitDialog(onDismiss = { adding = false }, onAdd = { e, n -> farm.addHabit(e, n); adding = false })
}

@Composable
private fun FarmStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(16.dp)).background(Surface1).border(1.5.dp, CardBorder, RoundedCornerShape(16.dp)).padding(vertical = 12.dp, horizontal = 12.dp),
    ) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = Mint, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelMedium, color = TextTertiary, maxLines = 1)
    }
}

// ─────────────────────────────────────────────────────── the pixel farm

/** A cozy night farm drawn entirely from pixels — lush when tended, wan when neglected. */
@Composable
private fun FarmScene(snap: FarmState.Snapshot) {
    val bob by rememberInfiniteTransition(label = "farm").animateFloat(
        0f, (2 * Math.PI).toFloat(),
        infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart), label = "bob",
    )
    val health = snap.health / 100f
    Box(
        Modifier.fillMaxWidth().height(230.dp).clip(RoundedCornerShape(20.dp)).border(1.5.dp, CardBorder, RoundedCornerShape(20.dp)),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            // sky
            drawRect(Brush.verticalGradient(listOf(Color(0xFF1B1B2E), Color(0xFF12121C))), size = size)
            // moon
            drawCircle(Color(0xFFE9F5B0), radius = h * 0.09f, center = Offset(w * 0.82f, h * 0.2f))
            // stars
            val stars = listOf(0.1f to 0.15f, 0.25f to 0.28f, 0.5f to 0.12f, 0.65f to 0.22f, 0.9f to 0.4f, 0.15f to 0.4f)
            stars.forEach { (sx, sy) -> drawRect(Color(0x88FFFFFF), Offset(w * sx, h * sy), Size(3f, 3f)) }

            // ground
            val groundTop = h * 0.62f
            val grass = lerp(Color(0xFF5A4A32), Color(0xFF3E6B2E), health)   // brown → green with health
            drawRect(grass, topLeft = Offset(0f, groundTop), size = Size(w, h - groundTop))
            drawRect(lerp(Color(0xFF4A3D28), Color(0xFF2F5222), health), topLeft = Offset(0f, groundTop), size = Size(w, 6f))

            val px = (w / 64f)                    // pixel unit
            // buildings sit at the back
            if (snap.hasBarn) sprite(BARN, barnColors(health), w * 0.04f, groundTop - 11 * px, px)
            if (snap.hasHouse) sprite(HOUSE, houseColors(health), w * 0.78f, groundTop - 9 * px, px)

            // crops in a row along the ground
            val plots = snap.plots
            val n = plots.size.coerceAtMost(FarmState.GRID)
            val startX = w * 0.06f
            val spanX = w * 0.88f
            for (i in 0 until n) {
                val cx = startX + spanX * (i + 0.5f) / n
                val sway = sin(bob + i) * 1.2f * px * (plots[i] / 3f)
                sprite(cropSprite(plots[i]), cropColors(plots[i], health, i), cx - 4 * px + sway, groundTop - 6 * px, px)
            }

            // animals wander near the front
            repeat(snap.animals) { a ->
                val cx = w * (0.15f + 0.16f * a)
                val hop = (sin(bob * 1.6f + a * 2) * 2f * px).coerceAtLeast(0f)
                sprite(CHICKEN, chickenColors(health), cx, h - 7 * px - hop, px)
            }
        }
    }
}

private fun DrawScope.sprite(rows: List<String>, colors: Map<Char, Color>, x: Float, y: Float, px: Float) {
    for ((r, row) in rows.withIndex()) {
        for ((c, ch) in row.withIndex()) {
            val color = colors[ch] ?: continue
            drawRect(color, topLeft = Offset(x + c * px, y + r * px), size = Size(px + 0.5f, px + 0.5f))
        }
    }
}

// health tint helper: fade a colour toward a wan grey-brown as the farm suffers
private fun wilt(c: Color, health: Float): Color = lerp(Color(0xFF6E6455), c, health.coerceIn(0f, 1f))

private fun cropSprite(stage: Int) = when (stage) {
    0 -> CROP0; 1 -> CROP1; 2 -> CROP2; else -> CROP3
}

private fun cropColors(stage: Int, health: Float, i: Int): Map<Char, Color> {
    val flower = listOf(Color(0xFFB19CD9), Color(0xFFFF9BB3), Color(0xFFFFD36B))[i % 3]
    return mapOf(
        's' to Color(0xFF6B4A2E),
        'b' to Color(0xFF5B7A3A),
        'g' to wilt(Color(0xFF8BD64B), health),
        'f' to wilt(flower, health),
        'y' to wilt(Color(0xFFFFE066), health),
    )
}

private fun chickenColors(health: Float) = mapOf(
    'w' to wilt(Color(0xFFF2F2E8), health),
    'y' to Color(0xFFFFB84D),
    'l' to Color(0xFFCC8844),
)

private fun barnColors(health: Float) = mapOf(
    'r' to wilt(Color(0xFFC85A54), health),
    'd' to Color(0xFF3A2A22),
)

private fun houseColors(health: Float) = mapOf(
    'h' to wilt(Color(0xFF9C7BD9), health),
    'p' to Color(0xFF4A4458),
    'd' to Color(0xFF2A2436),
)

// sprites — '.' is transparent
private val CROP0 = listOf(
    "........", "........", "........", "........",
    "........", "........", ".ssssss.", "ssssssss",
)
private val CROP1 = listOf(
    "........", "........", "........", "...g....",
    "..ggg...", "...g....", ".ssssss.", "ssssssss",
)
private val CROP2 = listOf(
    "........", "...g....", "..ggg...", ".ggggg..",
    "..ggg...", "...b....", ".ssssss.", "ssssssss",
)
private val CROP3 = listOf(
    "...f....", "..fyf...", ".ffyff..", "..fgf...",
    "...g....", "...b....", ".ssssss.", "ssssssss",
)
private val CHICKEN = listOf(
    ".ww...", "wwww..", "wwwwy.", "wwww..", ".l.l..",
)
private val BARN = listOf(
    "....rr....", "...rrrr...", "..rrrrrr..", ".rrrrrrrr.",
    "rrrrrrrrrr", "rr.dddd.rr", "rr.dddd.rr", "rr.dddd.rr",
    "rrrrrrrrrr",
)
private val HOUSE = listOf(
    "...hh...", "..hhhh..", ".hhhhhh.", "hhhhhhhh",
    "pp.dd.pp", "pp.dd.pp", "pp.dd.pp", "pppppppp",
)

@Composable
private fun AddHabitDialog(onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    val emojis = listOf("💧", "🏃", "📖", "😴", "🧘", "🥗", "☀️", "✍️", "🎧", "🚿", "🙏", "🎯")
    var emoji by remember { mutableStateOf(emojis.first()) }
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringResource(R.string.farm_add_habit), style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column {
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(emojis) { e ->
                        Box(
                            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                                .background(if (e == emoji) Periwinkle.copy(alpha = 0.18f) else Surface2)
                                .border(if (e == emoji) 2.dp else 1.dp, if (e == emoji) Periwinkle else CardBorder, RoundedCornerShape(12.dp))
                                .clickable { emoji = e },
                            contentAlignment = Alignment.Center,
                        ) { Text(e, fontSize = 22.sp) }
                    }
                }
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(28) },
                    label = { Text(stringResource(R.string.farm_habit_name)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Periwinkle, unfocusedBorderColor = Surface2),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onAdd(emoji, name.trim()) }) {
                Text(stringResource(R.string.farm_add), color = Periwinkle)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}
