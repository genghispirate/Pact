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
import com.pact.app.ui.theme.Violet
import kotlin.math.sin

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun FarmScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val farm = remember { FarmState.get(context) }
    val snap by farm.snapshot.collectAsState()
    var adding by remember { mutableStateOf(false) }
    val builtCats = remember(snap) {
        (snap.habits.map { it.category } + snap.categoryPoints.keys).distinct()
            .map { FarmState.category(it) }
            .sortedByDescending { snap.categoryPoints[it.id] ?: 0 }
    }

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
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.farm_title), style = MaterialTheme.typography.headlineSmall)
                Text(
                    stringResource(R.string.world_stage, snap.stageName),
                    style = MaterialTheme.typography.labelMedium,
                    color = Violet,
                )
            }
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

            // Your World — every habit builds a specific structure here.
            if (builtCats.isNotEmpty()) {
                item { SectionLabel(stringResource(R.string.world_section), Modifier.padding(top = 6.dp)) }
                item {
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        builtCats.forEach { cat -> StructureChip(cat, snap.structureLevel(cat.id), snap.structureProgress(cat.id)) }
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

    if (adding) AddHabitDialog(onDismiss = { adding = false }, onAdd = { e, n, c -> farm.addHabit(e, n, c); adding = false })
}

/** One structure in your world: its icon, name, and how grown it is. */
@Composable
private fun StructureChip(cat: FarmState.Category, level: Int, progress: Float) {
    val started = level > 0 || progress > 0f
    Column(
        modifier = Modifier
            .width(104.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Surface1)
            .border(1.5.dp, if (started) Violet.copy(alpha = 0.5f) else CardBorder, RoundedCornerShape(16.dp))
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(cat.emoji, fontSize = 20.sp)
            Spacer(Modifier.width(6.dp))
            Text("Lv $level", style = MaterialTheme.typography.labelMedium, color = if (started) Violet else TextTertiary)
        }
        Spacer(Modifier.height(6.dp))
        Text(cat.structure, style = MaterialTheme.typography.titleSmall, maxLines = 1)
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(5.dp).clip(CircleShape).background(Surface2)) {
            Box(
                Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(5.dp).clip(CircleShape)
                    .background(Brush.horizontalGradient(listOf(Violet, Periwinkle)))
            )
        }
    }
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

/** A living, lush farm drawn from pixels: a farmer works, water shimmers, crops
 * sway, clouds drift — always something happening. Wilts wan when neglected. */
@Composable
private fun FarmScene(snap: FarmState.Snapshot) {
    val tau = (2 * Math.PI).toFloat()
    val t by rememberInfiniteTransition(label = "farm").animateFloat(
        0f, tau, infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart), label = "t",
    )
    // Farmer sweeps back and forth across the field.
    val trip by rememberInfiniteTransition(label = "walk").animateFloat(
        0f, 1f, infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart), label = "trip",
    )
    val health = snap.health / 100f

    Box(
        Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(20.dp)).border(1.5.dp, CardBorder, RoundedCornerShape(20.dp)),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            val u = w / 80f
            val gTop = h * 0.30f

            // sky + sun + drifting clouds
            drawRect(Brush.verticalGradient(listOf(Color(0xFF8FD6EC), Color(0xFFC8ECF2))), size = Size(w, gTop + 8 * u))
            drawCircle(Color(0xFFFFE7A0), radius = h * 0.055f, center = Offset(w * 0.85f, h * 0.11f))
            cloud(w * 0.20f + w * 0.05f * sin(t), h * 0.09f, u)
            cloud(w * 0.62f + w * 0.05f * sin(t + 2f), h * 0.16f, u * 0.75f)

            // grass
            val grass = lerp(Color(0xFF9E8C58), Color(0xFF74C24E), health)
            drawRect(grass, topLeft = Offset(0f, gTop), size = Size(w, h - gTop))
            drawRect(lerp(Color(0xFF80703F), Color(0xFF5BAD3C), health), topLeft = Offset(0f, gTop), size = Size(w, u))
            // grass tufts (fixed scatter)
            val tufts = listOf(0.06f to 0.5f, 0.22f to 0.62f, 0.9f to 0.55f, 0.84f to 0.74f, 0.5f to 0.9f, 0.12f to 0.86f, 0.7f to 0.9f)
            tufts.forEach { (tx, ty) ->
                val c = lerp(Color(0xFF6E602F), Color(0xFF4E9A32), health)
                drawRect(c, Offset(w * tx, gTop + (h - gTop) * ty), Size(u, u * 1.4f))
                drawRect(c, Offset(w * tx + u * 1.4f, gTop + (h - gTop) * ty - u * 0.6f), Size(u, u * 1.4f))
            }

            // pond (left) with shimmer
            val pcx = w * 0.15f; val pcy = h * 0.80f; val prw = w * 0.13f; val prh = h * 0.10f
            drawOval(Color(0xFF2E93CE), Offset(pcx - prw, pcy - prh), Size(prw * 2, prh * 2))
            drawOval(Color(0xFF57BDEC), Offset(pcx - prw * 0.7f, pcy - prh * 0.55f), Size(prw * 1.4f, prh * 1.1f))
            listOf(Triple(-0.3f, -0.2f, 0f), Triple(0.25f, 0.15f, 2f), Triple(-0.05f, 0.35f, 4f)).forEach { (sx, sy, ph) ->
                if (sin(t * 2f + ph) > 0.3f) drawRect(Color(0xCCFFFFFF), Offset(pcx + sx * prw, pcy + sy * prh), Size(u, u))
            }

            // tilled field (centre) with crop rows
            val fx = w * 0.30f; val fy = h * 0.42f; val fw = w * 0.34f; val fh = h * 0.40f
            drawRect(lerp(Color(0xFF6E5B3A), Color(0xFF7A5230), health), Offset(fx, fy), Size(fw, fh))
            for (r in 0..3) drawRect(Color(0x22000000), Offset(fx, fy + fh * (r + 0.5f) / 4f), Size(fw, u * 0.6f))
            val plots = snap.plots
            val cols = 4
            for (i in plots.indices.take(FarmState.GRID)) {
                val col = i % cols; val row = i / cols
                val cxp = fx + fw * (col + 0.5f) / cols
                val cyp = fy + fh * (row + 0.5f) / (FarmState.GRID / cols)
                val sway = sin(t + i) * 1.1f * u * (plots[i] / 3f)
                sprite(cropSprite(plots[i]), cropColors(plots[i], health, i), cxp - 4 * u + sway, cyp - 7 * u, u)
            }

            // trees (sway)
            tree(w * 0.045f, gTop + h * 0.10f, u, health, sin(t))
            tree(w * 0.70f, gTop + h * 0.06f, u * 0.9f, health, sin(t + 1.5f))

            // a house being built, or the finished house with chimney smoke
            val hx = w * 0.74f; val hy = h * 0.50f
            if (snap.hasHouse) {
                sprite(HOUSE, houseColors(health), hx, hy, u)
                for (k in 0..2) {
                    val prog = ((t / tau) + k / 3f) % 1f
                    drawCircle(Color(0xFFDDE6EC).copy(alpha = (1f - prog) * 0.5f), u * (1f + prog), Offset(hx + 7.5f * u, hy - prog * h * 0.14f))
                }
            } else {
                sprite(SCAFFOLD, scaffoldColors(), hx, hy, u)
                // a builder hammering (bobs)
                val ham = (sin(t * 3f).coerceAtLeast(0f)) * 2f * u
                sprite(farmerFrame(0, false), farmerColors(), hx - 4 * u, hy + 2 * u - ham, u)
            }

            // chickens hop near the front
            repeat(snap.animals.coerceAtMost(3)) { a ->
                val cx = w * (0.30f + 0.12f * a)
                val hop = (sin(t * 1.8f + a * 2) * 2f * u).coerceAtLeast(0f)
                sprite(CHICKEN, chickenColors(health), cx, h - 8 * u - hop, u)
            }

            // the farmer: walks the field, watering (triangle path so we know facing)
            val pos = if (trip < 0.5f) trip * 2f else (1f - trip) * 2f
            val facingRight = trip < 0.5f
            val fxp = w * 0.34f + (w * 0.60f - w * 0.34f) * pos
            val fyp = h * 0.66f
            val step = ((t / (tau / 4f)).toInt() % 2)   // 2-frame walk cycle
            sprite(farmerFrame(step, facingRight), farmerColors(), fxp, fyp, u, flip = !facingRight)
            // watering can + drops
            val canX = if (facingRight) fxp + 6 * u else fxp - 3 * u
            sprite(CAN, canColors(), canX, fyp + 3 * u, u, flip = !facingRight)
            for (d in 0..1) {
                val dp = ((t / tau) + d * 0.5f) % 1f
                drawRect(Color(0xFF6FC3E8), Offset(canX + (if (facingRight) 3f else 0f) * u, fyp + 5 * u + dp * 4 * u), Size(u * 0.8f, u * 0.8f))
            }
        }
    }
}

private fun DrawScope.sprite(rows: List<String>, colors: Map<Char, Color>, x: Float, y: Float, px: Float, flip: Boolean = false) {
    for ((r, row) in rows.withIndex()) {
        val cells = if (flip) row.reversed() else row
        for ((c, ch) in cells.withIndex()) {
            val color = colors[ch] ?: continue
            drawRect(color, topLeft = Offset(x + c * px, y + r * px), size = Size(px + 0.6f, px + 0.6f))
        }
    }
}

private fun DrawScope.cloud(cx: Float, cy: Float, u: Float) {
    val c = Color(0xF2FFFFFF)
    drawOval(c, Offset(cx - 6 * u, cy - 2 * u), Size(12 * u, 4 * u))
    drawOval(c, Offset(cx - 3 * u, cy - 3.5f * u), Size(7 * u, 5 * u))
}

private fun DrawScope.tree(x: Float, baseY: Float, u: Float, health: Float, sway: Float) {
    // trunk
    drawRect(Color(0xFF6E4A2A), Offset(x - u, baseY - 4 * u), Size(2.4f * u, 6 * u))
    // canopy (three blobs, sways)
    val g1 = lerp(Color(0xFF7A7048), Color(0xFF3E8B3A), health)
    val g2 = lerp(Color(0xFF8A804F), Color(0xFF57AD45), health)
    val dx = sway * 1.2f * u
    drawOval(g1, Offset(x - 7 * u + dx, baseY - 14 * u), Size(14 * u, 11 * u))
    drawOval(g2, Offset(x - 5 * u + dx, baseY - 17 * u), Size(10 * u, 8 * u))
}

// health tint helper: fade a colour toward a wan grey-brown as the farm suffers
private fun wilt(c: Color, health: Float): Color = lerp(Color(0xFF7E7358), c, health.coerceIn(0f, 1f))

private fun cropSprite(stage: Int) = when (stage) {
    0 -> CROP0; 1 -> CROP1; 2 -> CROP2; else -> CROP3
}

private fun cropColors(stage: Int, health: Float, i: Int): Map<Char, Color> {
    val flower = listOf(Color(0xFFE86FA6), Color(0xFFFF9BB3), Color(0xFFFFD36B))[i % 3]
    return mapOf(
        's' to Color(0xFF7A5230),
        'b' to Color(0xFF4E8B32),
        'g' to wilt(Color(0xFF57C23A), health),
        'f' to wilt(flower, health),
        'y' to wilt(Color(0xFFFFE066), health),
    )
}

private fun farmerFrame(step: Int, facingRight: Boolean) = if (step == 0) FARMER_A else FARMER_B
private fun farmerColors() = mapOf(
    'h' to Color(0xFFE7C868),   // straw hat
    's' to Color(0xFFF0C9A4),   // skin
    'b' to Color(0xFF4C79C9),   // overalls
    'l' to Color(0xFF2E4A78),   // legs
)
private fun canColors() = mapOf('c' to Color(0xFFAEB6C0), 'm' to Color(0xFF8A929C))
private fun scaffoldColors() = mapOf('w' to Color(0xFF8A6A44), 'p' to Color(0xFF6E5236))

private fun chickenColors(health: Float) = mapOf(
    'w' to wilt(Color(0xFFF6F4EA), health), 'y' to Color(0xFFFFB84D), 'l' to Color(0xFFCC8844),
)
private fun houseColors(health: Float) = mapOf(
    'r' to Color(0xFFC85A54), 'h' to Color(0xFFE7B77A), 'p' to Color(0xFFCBA06A),
    'd' to Color(0xFF5A3A22), 'w' to Color(0xFF7FC7E8),
)

// sprites — '.' is transparent
private val CROP0 = listOf("........", "........", "........", "........", "........", "........", ".ssssss.", "ssssssss")
private val CROP1 = listOf("........", "........", "........", "...g....", "..ggg...", "...g....", ".ssssss.", "ssssssss")
private val CROP2 = listOf("........", "...g....", "..ggg...", ".ggggg..", "..ggg...", "...b....", ".ssssss.", "ssssssss")
private val CROP3 = listOf("...f....", "..fyf...", ".ffyff..", "..fgf...", "...g....", "...b....", ".ssssss.", "ssssssss")
private val CHICKEN = listOf(".ww...", "wwww..", "wwwwy.", "wwww..", ".l.l..")
private val FARMER_A = listOf("..hhh..", ".hhhhh.", "..sss..", ".bbbbb.", "..bbb..", "..l.l..")
private val FARMER_B = listOf("..hhh..", ".hhhhh.", "..sss..", ".bbbbb.", "..bbb..", "...ll..")
private val CAN = listOf("cccm", "cccc", "cccc")
private val SCAFFOLD = listOf(
    "w.....w", "wwwwwww", "w.....w", "w.....w", "wwwwwww", "p.....p", "p.....p",
)
private val HOUSE = listOf(
    "..rrrrr..", ".rrrrrrr.", "rrrrrrrrr", "hhhhhhhhh", "hh.www.hh", "hh.www.hh", "hhh.d.hhh", "hhh.d.hhh",
)

@Composable
private fun AddHabitDialog(onDismiss: () -> Unit, onAdd: (String, String, String) -> Unit) {
    val emojis = listOf("💧", "🏃", "📖", "😴", "🧘", "🥗", "☀️", "✍️", "🎧", "🚿", "🙏", "🎯")
    var emoji by remember { mutableStateOf(emojis.first()) }
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(FarmState.CATEGORIES.first().id) }
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
                Spacer(Modifier.height(14.dp))
                Text(stringResource(R.string.world_builds), style = MaterialTheme.typography.labelMedium, color = TextTertiary)
                Spacer(Modifier.height(8.dp))
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(FarmState.CATEGORIES) { c ->
                        val on = c.id == category
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.width(66.dp).clip(RoundedCornerShape(12.dp))
                                .background(if (on) Violet.copy(alpha = 0.18f) else Surface2)
                                .border(if (on) 2.dp else 1.dp, if (on) Violet else CardBorder, RoundedCornerShape(12.dp))
                                .clickable { category = c.id }
                                .padding(vertical = 8.dp),
                        ) {
                            Text(c.emoji, fontSize = 20.sp)
                            Text(c.structure, style = MaterialTheme.typography.labelMedium, color = if (on) Violet else TextTertiary, maxLines = 1)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onAdd(emoji, name.trim(), category) }) {
                Text(stringResource(R.string.farm_add), color = Periwinkle)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}
