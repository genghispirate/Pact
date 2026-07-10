package com.pact.app.ui.world

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pact.app.core.FarmState
import com.pact.app.ui.theme.TextLow
import com.pact.app.ui.theme.TextMid
import com.pact.app.ui.theme.Violet
import kotlin.math.sin

// ═══════════════════════════════════════════════════════════════════════
//  WorldCanvas — DESIGN.md §7-B. The dense rectangular tile village that
//  fills its card edge-to-edge. Fixed camera (tile = width/18), static
//  ground cached to a bitmap, lightweight taps. Watched, not managed.
// ═══════════════════════════════════════════════════════════════════════

enum class WorldQuality { PEEK, FULL }

data class WorldTap(
    val emoji: String, val title: String, val subtitle: String, val kind: String,
    val stage: Int = 0, val level: Int = 0, val mood: String = "", val contribution: String = "",
)

private data class TapFx(val x: Float, val y: Float, val start: Float, val type: String)

private class GroundCache { var key: Long = Long.MIN_VALUE; var bmp: ImageBitmap? = null }

private fun tappables(layout: Layout): List<Pair<Offset, WorldTap>> {
    val out = ArrayList<Pair<Offset, WorldTap>>()
    layout.buildings.forEach {
        out += Offset(it.x, it.y - 0.7f) to WorldTap(it.emoji, it.title, "Built from your ${it.title} habits", "building", it.stage, it.level)
    }
    layout.villagers.filter { it.kind != "pet" }.forEach {
        out += it.route.first() to WorldTap(
            if (it.kind == "child") "🧒" else if (it.kind == "elder") "🧓" else "🧑", it.name,
            "Currently: ${it.activity}", "villager", 0, 0, it.mood, it.contribution,
        )
    }
    layout.trees.take(8).forEach {
        out += Offset(it.x, it.y - 1f) to WorldTap("🌳", "Oak tree", "Grown by your walks", "tree", 0, 0, "", "Age ${(it.scale * 20).toInt()} rings")
    }
    return out
}

@Composable
fun WorldCanvas(
    snap: FarmState.Snapshot,
    modifier: Modifier = Modifier,
    quality: WorldQuality = WorldQuality.FULL,
    onTap: ((WorldTap) -> Unit)? = null,
) {
    val clock = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(quality) {
        val t0 = withFrameNanos { it }
        var skip = false
        while (true) withFrameNanos {
            if (quality == WorldQuality.FULL || skip) clock.floatValue = (it - t0) / 1_000_000f
            skip = !skip
        }
    }

    val layout = remember(snap) { buildLayout(snap) }
    val fx = remember { mutableStateListOf<TapFx>() }
    var birdStartle by remember { mutableFloatStateOf(-9999f) }
    val cache = remember { GroundCache() }

    Canvas(
        modifier.pointerInput(layout) {
            detectTapGestures { pos ->
                val tile = size.width / MAP_COLS.toFloat()
                val oy = (size.height - MAP_ROWS * tile) / 2f
                fun proj(x: Float, y: Float) = Offset(x * tile, oy + y * tile)
                val now = clock.floatValue
                val hit = tappables(layout).minByOrNull { (proj(it.first.x, it.first.y) - pos).getDistance() }
                val hd = hit?.let { (proj(it.first.x, it.first.y) - pos).getDistance() } ?: Float.MAX_VALUE
                val pb = proj(layout.pondB.x, layout.pondB.y)
                val pa = proj(layout.pondA.x, layout.pondA.y)
                when {
                    onTap != null && hit != null && hd < tile * 1.4f -> onTap(hit.second)
                    (pos - pb).getDistance() < tile * 1.6f -> fx.add(TapFx(pb.x, pb.y, now, "fish"))
                    (pos - pa).getDistance() < tile * 1.4f -> fx.add(TapFx(pa.x, pa.y, now, "fish"))
                    pos.y < size.height * 0.12f -> birdStartle = now
                    else -> fx.add(TapFx(pos.x, pos.y, now, "ripple"))
                }
                while (fx.size > 10) fx.removeAt(0)
            }
        },
    ) {
        val t = clock.floatValue
        val season = seasonNow()
        val light = lightingFor(currentHourFloat())
        val weather = weatherFor(t, season)
        val peek = quality == WorldQuality.PEEK

        val tile = size.width / MAP_COLS
        val oy = (size.height - MAP_ROWS * tile) / 2f
        val proj: Proj = { x, y, z -> Offset(x * tile, oy + (y - z) * tile) }
        val u = tile * 1.5f   // scale for u-based sprites (villagers, campfire, landmarks)

        // 1 · static ground — cached bitmap (§7.11)
        val key = layout.staticKey.toLong() * 1_000_003L +
            size.width.toInt() * 7L + size.height.toInt() * 13L +
            paletteBucket() * 31L + season.ordinal
        if (cache.key != key || cache.bmp == null) {
            val bmp = ImageBitmap(size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1))
            CanvasDrawScope().draw(
                Density(1f), LayoutDirection.Ltr,
                androidx.compose.ui.graphics.Canvas(bmp), Size(size.width, size.height),
            ) { drawGroundStatic(layout, light, season, proj, tile) }
            cache.key = key; cache.bmp = bmp
        }
        cache.bmp?.let { drawImage(it) }

        // 2 · ground-level atmosphere + animated water + flowers
        if (!peek) drawCloudShadows(t, tile)
        drawWaterLife(layout, light, t, proj, tile)
        drawFlowers(layout, light, t, proj, tile)

        // 3 · upright objects, y-sorted
        data class D(val depth: Float, val draw: () -> Unit)
        val ds = ArrayList<D>(layout.trees.size + layout.buildings.size + layout.villagers.size + 6)
        layout.castle?.let { c -> ds += D(c.y) { drawCastle(proj(c.x, c.y, 0f), u, light) } }
        layout.windmill?.let { c -> ds += D(c.y) { drawWindmill(proj(c.x, c.y, 0f), u, t, light) } }
        layout.cottage?.let { c -> ds += D(c.y) { drawCottage(proj(c.x, c.y, 0f), u, t, light) } }
        ds += D(layout.campfire.y) { drawCampfire(proj(layout.campfire.x, layout.campfire.y, 0f), u, t, light) }
        layout.trees.forEach { tr -> ds += D(tr.y) { drawTree(proj(tr.x, tr.y, 0f), tile, t, light, season, tr.seed, tr.kind, tr.scale) } }
        layout.buildings.forEach { b -> ds += D(b.y) { drawBuilding(b, proj(b.x, b.y, 0f), tile, t, light) } }
        layout.villagers.forEach { vg ->
            val p = villagerPos(vg, t)
            ds += D(p.first.y + 0.01f) { drawVillager(proj(p.first.x, p.first.y, 0f), u, t, light, vg, p.second) }
        }
        ds.sortBy { it.depth }
        ds.forEach { it.draw() }

        // 4 · festival bunting between rooftops (level 30+)
        if (layout.festival && layout.buildings.size >= 2) {
            val tops = layout.buildings.filter { it.y < 6f }.map { proj(it.x, it.y, 0f) - Offset(0f, tile * 2.4f) }.sortedBy { it.x }
            for (i in 0 until tops.size - 1) {
                val a = tops[i]; val bb = tops[i + 1]
                for (s in 0 until 6) {
                    val f0 = s / 6f
                    val fx0 = a.x + (bb.x - a.x) * f0
                    val fy0 = a.y + (bb.y - a.y) * f0 + sin(f0 * Math.PI).toFloat() * tile * 0.3f
                    drawPath(Path().apply { moveTo(fx0, fy0); lineTo(fx0 + tile * 0.1f, fy0 + tile * 0.16f); lineTo(fx0 - tile * 0.1f, fy0 + tile * 0.16f); close() },
                        shade(listOf(Color(0xFFFF6FA6), Color(0xFFFFD24B), Color(0xFF6FA8FF))[s % 3], light))
                }
            }
        }

        // 5 · flying life + weather (FULL only), tap effects, grade
        if (!peek) drawFlyingLife(t, light, weather, season, birdStartle, proj, u)
        fx.forEach { e ->
            val age = (t - e.start) / 1000f
            if (age in 0f..1.3f) {
                for (k in 0 until 2) {
                    val rp = (age + k * 0.3f).coerceAtMost(1f)
                    drawOval(Color.White.copy(alpha = (1f - rp) * 0.5f),
                        Offset(e.x - u * 0.3f * rp, e.y - u * 0.15f * rp), Size(u * 0.6f * rp, u * 0.3f * rp), style = Stroke(2f))
                }
                if (e.type == "fish") {
                    val arc = sin(age / 0.6f * Math.PI).toFloat().coerceAtLeast(0f)
                    drawOval(shade(Color(0xFFBFD3E0), light), Offset(e.x - u * 0.05f, e.y - arc * u * 0.5f - u * 0.03f), Size(u * 0.12f, u * 0.06f))
                }
            }
        }
        grade(light)
    }
}

// ═════════════════════════════════════════════════ tap info sheet (§6)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorldInfoSheet(o: WorldTap, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(24.dp).navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(o.emoji, fontSize = 40.sp)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(o.title, style = MaterialTheme.typography.headlineSmall)
                    Text(o.subtitle, style = MaterialTheme.typography.bodyMedium, color = TextMid)
                }
            }
            Spacer(Modifier.height(16.dp))
            when (o.kind) {
                "building" -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    InfoTile("Stage", listOf("Foundation", "Framing", "Walls", "Finished")[o.stage.coerceIn(0, 3)], Modifier.weight(1f))
                    InfoTile("Level", "${o.level}", Modifier.weight(1f))
                }
                "tree" -> InfoTile("Growth", o.contribution.ifBlank { "Growing" }, Modifier.fillMaxWidth())
                else -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    InfoTile("Mood", o.mood.ifBlank { "😊" }, Modifier.weight(1f))
                    InfoTile("Today", o.contribution.ifBlank { "Helping out" }, Modifier.weight(2f))
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun InfoTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)).padding(14.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = TextLow)
        Text(value, style = MaterialTheme.typography.titleMedium, color = Violet, maxLines = 2)
    }
}
