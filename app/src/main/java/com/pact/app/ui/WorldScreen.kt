package com.pact.app.ui

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CenterFocusWeak
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pact.app.core.FarmState
import com.pact.app.ui.theme.CardBorder
import com.pact.app.ui.theme.Mint
import com.pact.app.ui.theme.Periwinkle
import com.pact.app.ui.theme.TextSecondary
import com.pact.app.ui.theme.TextTertiary
import com.pact.app.ui.theme.Violet
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sin

// ───────────────────────────────────────────────────────── world model
//
// A tiny handcrafted diorama, projected in 2.5D isometric and lit by the real
// time of day. Everything is drawn from primitives — no assets. The grid is a
// small island the world grows across as habits build their structures.

private const val N = 16                        // island is N×N tiles
private const val MIN_ZOOM = 0.7f
private const val MAX_ZOOM = 2.4f

private enum class Tile { GRASS, FLOWER, PATH, DIRT, WATER, SAND, ROCK }

/** A thing standing on the island that the user can tap for a detail sheet. */
private data class WorldObject(
    val id: String,
    val tx: Float,
    val ty: Float,
    val kind: String,
    val emoji: String,
    val title: String,
    val subtitle: String,
    val level: Int = 0,
    val next: String = "",
)

// ── terrain: deterministic curated regions + per-tile variation ──────────

private fun hash(x: Int, y: Int, s: Int = 0): Float {
    var h = x * 374761393 + y * 668265263 + s * 1442695041
    h = (h xor (h ushr 13)) * 1274126177
    return ((h xor (h ushr 16)) and 0x7fffffff) / 0x7fffffff.toFloat()
}

private val POND = setOf(3 to 12, 4 to 12, 3 to 13, 4 to 13, 5 to 13, 4 to 14, 2 to 13)

/** Central path spine plus short spurs toward the building plots. */
private val PATH: Set<Pair<Int, Int>> = buildSet {
    for (y in 3..12) add(8 to y)
    for (x in 4..11) add(x to 8)
    addAll(listOf(6 to 5, 6 to 4, 10 to 5, 10 to 4, 6 to 11, 10 to 11, 5 to 8, 11 to 8))
}

private fun tileAt(x: Int, y: Int, flowerBoost: Int): Tile {
    if (Pair(x, y) in POND) return Tile.WATER
    if (POND.any { abs(it.first - x) <= 1 && abs(it.second - y) <= 1 }) return Tile.SAND
    if (Pair(x, y) in PATH) return if (hash(x, y, 7) > 0.5f) Tile.PATH else Tile.DIRT
    val r = hash(x, y, 3)
    if (r > 0.94f) return Tile.ROCK
    if (r < 0.05f + flowerBoost * 0.02f) return Tile.FLOWER
    return Tile.GRASS
}

/** Gentle terrain: a couple of low hills and a mountain shoulder. */
private fun heightAt(x: Int, y: Int): Int {
    if (Pair(x, y) in POND) return 0
    var hgt = 0
    val d1 = hypot((x - 12).toFloat(), (y - 4).toFloat())
    if (d1 < 3.4f) hgt = maxOf(hgt, (3.4f - d1).toInt())
    val d2 = hypot((x - 5).toFloat(), (y - 3).toFloat())
    if (d2 < 2.2f) hgt = maxOf(hgt, 1)
    return hgt
}

// ── lighting: warm dawn → bright noon → gold dusk → blue night ───────────

private data class Lighting(
    val skyTop: Color, val skyBottom: Color,
    val ambient: Color, val ambientStrength: Float,
    val brightness: Float, val night: Float,
)

private val L_NIGHT = Lighting(Color(0xFF0E1430), Color(0xFF283566), Color(0xFF2A3A72), 0.54f, 0.60f, 1f)
private val L_DAWN = Lighting(Color(0xFFF6B36B), Color(0xFFFFE1B0), Color(0xFFFFB877), 0.34f, 0.92f, 0.25f)
private val L_DAY = Lighting(Color(0xFF8FD6EC), Color(0xFFD6EFF4), Color(0xFFFFFDF3), 0.10f, 1.05f, 0f)
private val L_DUSK = Lighting(Color(0xFFFF9E6B), Color(0xFFFFD79E), Color(0xFFFF9860), 0.40f, 0.86f, 0.35f)

private fun lerpL(a: Lighting, b: Lighting, f: Float) = Lighting(
    lerp(a.skyTop, b.skyTop, f), lerp(a.skyBottom, b.skyBottom, f),
    lerp(a.ambient, b.ambient, f), a.ambientStrength + (b.ambientStrength - a.ambientStrength) * f,
    a.brightness + (b.brightness - a.brightness) * f, a.night + (b.night - a.night) * f,
)

/** Continuous lighting for an hour-of-day in [0,24). */
private fun lightingFor(hf: Float): Lighting = when {
    hf < 5f -> L_NIGHT
    hf < 7f -> lerpL(L_NIGHT, L_DAWN, (hf - 5f) / 2f)
    hf < 9f -> lerpL(L_DAWN, L_DAY, (hf - 7f) / 2f)
    hf < 16f -> L_DAY
    hf < 18.5f -> lerpL(L_DAY, L_DUSK, (hf - 16f) / 2.5f)
    hf < 20.5f -> lerpL(L_DUSK, L_NIGHT, (hf - 18.5f) / 2f)
    else -> L_NIGHT
}

/** Shade a base colour by the ambient light + brightness of the moment. */
private fun shade(base: Color, l: Lighting): Color {
    val tinted = lerp(base, l.ambient, l.ambientStrength)
    val b = l.brightness
    return Color(
        (tinted.red * b).coerceIn(0f, 1f),
        (tinted.green * b).coerceIn(0f, 1f),
        (tinted.blue * b).coerceIn(0f, 1f),
        tinted.alpha,
    )
}

// ── seasons ──────────────────────────────────────────────────────────────

private enum class Season { SPRING, SUMMER, AUTUMN, WINTER }

private fun seasonNow(): Season = when (Calendar.getInstance().get(Calendar.MONTH)) {
    11, 0, 1 -> Season.WINTER
    2, 3, 4 -> Season.SPRING
    5, 6, 7 -> Season.SUMMER
    else -> Season.AUTUMN
}

private fun foliage(season: Season): Color = when (season) {
    Season.SPRING -> Color(0xFF62C063)
    Season.SUMMER -> Color(0xFF3E9B44)
    Season.AUTUMN -> Color(0xFFE0862F)
    Season.WINTER -> Color(0xFF9FB7A8)
}

// ── weather: a slow, calm cycle ──────────────────────────────────────────

private enum class Weather { CLEAR, CLOUDY, RAIN, SNOW }

private fun weatherFor(timeMs: Float, season: Season): Weather {
    val phase = ((timeMs / 1000f / 70f).toInt() % 5)
    return when (season) {
        Season.WINTER -> if (phase >= 3) Weather.SNOW else if (phase == 2) Weather.CLOUDY else Weather.CLEAR
        else -> when (phase) { 0, 1 -> Weather.CLEAR; 2 -> Weather.CLOUDY; 3 -> Weather.RAIN; else -> Weather.CLEAR }
    }
}

// ─────────────────────────────────────────────────────── the diorama

/**
 * The living isometric world. Owns its own animation clock and camera. Pan with
 * drag (with inertia), pinch to zoom, double-tap to focus. Tap an object to
 * surface it through [onTapObject]; when null, a tap just gently focuses.
 */
@Composable
fun WorldDiorama(
    snap: FarmState.Snapshot,
    modifier: Modifier = Modifier,
    onTapObject: ((Any) -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val decay = remember { exponentialDecay<Offset>(frictionMultiplier = 0.6f) }

    // continuous frame clock (ms) — one driver for every animation
    val clock = remember { mutableFloatStateOf(0f) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        val t0 = withFrameNanos { it }
        while (true) withFrameNanos { clock.floatValue = (it - t0) / 1_000_000f }
    }

    var pan by remember { mutableStateOf(Offset.Zero) }
    var scale by remember { mutableStateOf(1f) }
    var canvas by remember { mutableStateOf(Size.Zero) }
    var flingJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    val objects = remember(snap) { worldObjects(snap) }

    // origin so the island's centre sits a little above the middle of the view
    fun origin(): Offset = Offset(canvas.width / 2f, canvas.height * 0.36f) + pan
    fun unit(): Float = (canvas.width * 0.9f / N) * scale
    fun project(tx: Float, ty: Float, tz: Float): Offset {
        val u = unit()
        val gx = (tx - ty) * u / 2f
        val gy = (tx + ty) * u / 4f - N * u / 4f - tz * u * 0.34f
        return origin() + Offset(gx, gy)
    }

    // zoom about a screen point, keeping that point fixed
    fun zoomAbout(c: Offset, factor: Float) {
        val target = (scale * factor).coerceIn(MIN_ZOOM, MAX_ZOOM)
        val real = target / scale
        pan += (c - origin()) * (1f - real)
        scale = target
    }

    Box(modifier) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(objects) {
                    awaitEachGesture {
                        flingJob?.cancel()
                        val first = awaitFirstDown(requireUnconsumed = false)
                        val tracker = VelocityTracker()
                        tracker.resetTracking()
                        var prevCentroid = first.position
                        var prevSpread = -1f
                        var moved = 0f
                        val downTime = first.uptimeMillis
                        while (true) {
                            val event = awaitPointerEvent()
                            val pts = event.changes.filter { it.pressed }
                            if (pts.isEmpty()) break
                            val centroid = pts.fold(Offset.Zero) { a, c -> a + c.position } / pts.size.toFloat()
                            if (pts.size >= 2) {
                                val spread = pts.fold(0f) { a, c -> a + (c.position - centroid).getDistance() } / pts.size
                                if (prevSpread > 0f && spread > 0f) zoomAbout(centroid, spread / prevSpread)
                                prevSpread = spread
                            } else prevSpread = -1f
                            val d = centroid - prevCentroid
                            prevCentroid = centroid
                            pan += d
                            moved += d.getDistance()
                            tracker.addPosition(event.changes.first().uptimeMillis, centroid)
                            event.changes.forEach { it.consume() }
                        }
                        val dt = first.uptimeMillis // unused placeholder to keep types simple
                        if (moved < 12f && (downTime >= 0)) {
                            // a tap — hit-test the nearest object, else focus-zoom
                            val tap = prevCentroid
                            val hit = objects.minByOrNull { (project(it.tx, it.ty, 0f) - tap).getDistance() }
                            val hitDist = hit?.let { (project(it.tx, it.ty, 0f) - tap).getDistance() } ?: Float.MAX_VALUE
                            if (onTapObject != null && hit != null && hitDist < unit() * 0.8f) {
                                onTapObject(hit)
                            } else {
                                scope.launch {
                                    val sc0 = scale; val pn0 = pan
                                    zoomAbout(tap, if (scale < 1.4f) 1.6f else 0.62f)
                                    val sc1 = scale; val pn1 = pan
                                    scale = sc0; pan = pn0
                                    animate(0f, 1f, animationSpec = tween(420)) { f, _ ->
                                        scale = sc0 + (sc1 - sc0) * f
                                        pan = Offset(pn0.x + (pn1.x - pn0.x) * f, pn0.y + (pn1.y - pn0.y) * f)
                                    }
                                }
                            }
                        } else {
                            val v = tracker.calculateVelocity()
                            flingJob = scope.launch {
                                val st = AnimationState(Offset.VectorConverter, pan, Offset(v.x, v.y))
                                st.animateDecay(decay) { pan = value }
                            }
                        }
                    }
                },
        ) {
            canvas = size
            val t = clock.floatValue
            val season = seasonNow()
            val cal = Calendar.getInstance()
            val hf = cal.get(Calendar.HOUR_OF_DAY) + cal.get(Calendar.MINUTE) / 60f
            val light = lightingFor(hf)
            val weather = weatherFor(t, season)
            val flowerBoost = snap.structureLevel("garden") + snap.structureLevel("temple")

            drawWorld(t, light, weather, season, snap, flowerBoost, objects) { tx, ty, tz -> project(tx, ty, tz) }
        }
    }
}

// ── the render, back-to-front by diagonal ────────────────────────────────

private fun DrawScope.drawWorld(
    t: Float, light: Lighting, weather: Weather, season: Season,
    snap: FarmState.Snapshot, flowerBoost: Int,
    objects: List<WorldObject>,
    P: (Float, Float, Float) -> Offset,
) {
    val w = size.width; val h = size.height
    val u = (P(1f, 0f, 0f) - P(0f, 0f, 0f)).getDistance()   // one tile width on screen

    // sky
    drawRect(Brush.verticalGradient(listOf(light.skyTop, light.skyBottom)))
    // sun or moon
    val celestialWarm = lerp(Color(0xFFFFE7A0), Color(0xFFEDEFF7), light.night)
    drawCircle(celestialWarm, radius = h * 0.05f, center = Offset(w * (0.82f - light.night * 0.6f), h * 0.13f))
    // stars at night
    if (light.night > 0.5f) {
        for (i in 0 until 40) {
            val a = (light.night - 0.5f) * 2f * (0.4f + 0.6f * sin(t / 600f + i))
            drawRect(Color.White.copy(alpha = a.coerceIn(0f, 0.9f) * 0.7f), Offset(hash(i, 1) * w, hash(i, 2) * h * 0.4f), Size(2f, 2f))
        }
    }
    // clouds
    if (weather != Weather.CLEAR || light.night < 0.5f) {
        val cloudN = if (weather == Weather.CLOUDY || weather == Weather.RAIN) 4 else 2
        for (i in 0 until cloudN) {
            val cx = ((t / 90f + i * 260f) % (w + 200f)) - 100f
            cloud(Offset(cx, h * (0.10f + 0.05f * i)), u * (0.9f + 0.3f * i), shade(Color.White, light).copy(alpha = if (weather == Weather.RAIN) 0.85f else 0.9f))
        }
    }

    // soft contact shadow under the whole island (diorama-on-a-table)
    val c0 = P(0f, N.toFloat(), 0f); val cN = P(N.toFloat(), 0f, 0f)
    val cSouth = P(N.toFloat(), N.toFloat(), 0f)
    val islandCx = (c0.x + cN.x) / 2f
    drawOval(Color.Black.copy(alpha = 0.18f), Offset(islandCx - N * u * 0.55f, cSouth.y - u * 0.3f), Size(N * u * 1.1f, u * 2.4f))

    // ground, per diagonal so nearer tiles paint over farther ones
    for (d in 0..(2 * N - 2)) {
        val xs = maxOf(0, d - (N - 1))..minOf(N - 1, d)
        for (x in xs) {
            val y = d - x
            drawTile(x, y, t, light, weather, season, flowerBoost, P)
        }
        // objects sitting on this diagonal, painted after its tiles
        objects.filter { (it.tx + it.ty).toInt() == d }
            .sortedBy { it.ty }
            .forEach { drawObject(it, t, light, season, snap, P) }
    }

    // weather + ambient particles over the whole scene
    drawParticles(t, light, weather, season, P)
    // gentle vignette for the diorama feel
    drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.22f)), center = Offset(w / 2f, h * 0.44f), radius = w * 0.9f))
}

private fun DrawScope.cloud(c: Offset, u: Float, col: Color) {
    drawOval(col, Offset(c.x - 3.2f * u, c.y - u * 0.8f), Size(6.4f * u, u * 1.6f))
    drawOval(col, Offset(c.x - 1.8f * u, c.y - u * 1.5f), Size(3.6f * u, u * 2.2f))
    drawOval(col, Offset(c.x + 0.2f * u, c.y - u * 1.1f), Size(3f * u, u * 1.8f))
}

// ── one iso tile: top face + front side faces for height ─────────────────

private fun DrawScope.drawTile(
    x: Int, y: Int, t: Float, light: Lighting, weather: Weather, season: Season, flowerBoost: Int,
    P: (Float, Float, Float) -> Offset,
) {
    val z = heightAt(x, y)
    val type = tileAt(x, y, flowerBoost)
    val u = (P(1f, 0f, 0f) - P(0f, 0f, 0f)).getDistance()
    val hy = z * u * 0.34f   // pixels this tile is lifted

    val top = P(x - 0.5f, y - 0.5f, z.toFloat())
    val right = P(x + 0.5f, y - 0.5f, z.toFloat())
    val bottom = P(x + 0.5f, y + 0.5f, z.toFloat())
    val left = P(x - 0.5f, y + 0.5f, z.toFloat())

    // base colour with subtle per-tile variation, seasonal & snow
    var base = when (type) {
        Tile.GRASS, Tile.FLOWER -> lerp(Color(0xFF6FBE55), Color(0xFF57A845), hash(x, y))
        Tile.PATH -> Color(0xFFB9A184)
        Tile.DIRT -> Color(0xFF9A7A54)
        Tile.SAND -> Color(0xFFE6D6A6)
        Tile.WATER -> Color(0xFF3E9BD6)
        Tile.ROCK -> Color(0xFF9AA0A6)
    }
    if (season == Season.AUTUMN && (type == Tile.GRASS || type == Tile.FLOWER)) base = lerp(base, Color(0xFFC7B25A), 0.35f)
    val snowCap = weather == Weather.SNOW || season == Season.WINTER
    if (snowCap && type != Tile.WATER) base = lerp(base, Color(0xFFEAF0F6), 0.55f)

    if (type == Tile.WATER) {
        // animated water — ripple bands + a moving highlight
        val a = 0.5f + 0.5f * sin(t / 520f + x + y)
        base = lerp(Color(0xFF2E8FD0), Color(0xFF5CC0EE), a)
    }

    // side faces (front-left darker, front-right medium) give the 3D slab
    if (z > 0) {
        val down = Offset(0f, hy)
        val leftFace = Path().apply { moveTo(left.x, left.y); lineTo(bottom.x, bottom.y); lineTo(bottom.x + down.x, bottom.y + down.y); lineTo(left.x + down.x, left.y + down.y); close() }
        val rightFace = Path().apply { moveTo(bottom.x, bottom.y); lineTo(right.x, right.y); lineTo(right.x + down.x, right.y + down.y); lineTo(bottom.x + down.x, bottom.y + down.y); close() }
        drawPath(leftFace, shade(base, light).copy().let { Color(it.red * 0.62f, it.green * 0.62f, it.blue * 0.62f) })
        drawPath(rightFace, shade(base, light).copy().let { Color(it.red * 0.8f, it.green * 0.8f, it.blue * 0.8f) })
    }

    // top face
    val face = Path().apply { moveTo(top.x, top.y); lineTo(right.x, right.y); lineTo(bottom.x, bottom.y); lineTo(left.x, left.y); close() }
    drawPath(face, shade(base, light))

    // little decorations on top
    val cx = P(x.toFloat(), y.toFloat(), z.toFloat())
    when (type) {
        Tile.GRASS -> if (hash(x, y, 9) > 0.55f) {
            // wind-blown tufts
            val sway = sin(t / 300f + x * 0.7f + y) * u * 0.06f
            val g = shade(lerp(Color(0xFF4E9A32), foliage(season), 0.4f), light)
            drawRect(g, Offset(cx.x - u * 0.05f + sway, cx.y - u * 0.16f), Size(u * 0.06f, u * 0.16f))
            drawRect(g, Offset(cx.x + u * 0.08f + sway, cx.y - u * 0.12f), Size(u * 0.06f, u * 0.12f))
        }
        Tile.FLOWER -> {
            val bounce = (sin(t / 260f + x + y) + 1f) * u * 0.03f
            val petal = shade(listOf(Color(0xFFE86FA6), Color(0xFFFFD36B), Color(0xFFB58BFF), Color(0xFFFF9BB3))[(x + y) % 4], light)
            drawRect(shade(Color(0xFF4E9A32), light), Offset(cx.x - u * 0.02f, cx.y - u * 0.16f), Size(u * 0.04f, u * 0.16f))
            drawCircle(petal, u * 0.09f, Offset(cx.x, cx.y - u * 0.18f - bounce))
            drawCircle(shade(Color(0xFFFFF0A0), light), u * 0.035f, Offset(cx.x, cx.y - u * 0.18f - bounce))
        }
        Tile.ROCK -> {
            drawCircle(shade(Color(0xFF8A9096), light), u * 0.16f, Offset(cx.x, cx.y - u * 0.08f))
            drawCircle(shade(Color(0xFF6E747A), light), u * 0.1f, Offset(cx.x + u * 0.05f, cx.y - u * 0.04f))
        }
        else -> {}
    }
}

// ── objects ──────────────────────────────────────────────────────────────

private fun DrawScope.drawObject(
    o: WorldObject, t: Float, light: Lighting, season: Season, snap: FarmState.Snapshot,
    P: (Float, Float, Float) -> Offset,
) {
    val z = heightAt(o.tx.toInt().coerceIn(0, N - 1), o.ty.toInt().coerceIn(0, N - 1))
    val p = P(o.tx, o.ty, z.toFloat())
    val u = (P(1f, 0f, 0f) - P(0f, 0f, 0f)).getDistance()
    // contact shadow
    drawOval(Color.Black.copy(alpha = 0.16f * (1f - light.night * 0.4f)), Offset(p.x - u * 0.28f, p.y - u * 0.06f), Size(u * 0.56f, u * 0.2f))

    when (o.kind) {
        "tree" -> drawTree(p, u, t, light, season, o.id.hashCode())
        "building" -> drawBuilding(p, u, t, light, o)
        "villager" -> drawVillager(p, u, t, light, o.id.hashCode())
        "well" -> {
            drawRect(shade(Color(0xFF9AA0A6), light), Offset(p.x - u * 0.22f, p.y - u * 0.5f), Size(u * 0.44f, u * 0.4f))
            drawRect(shade(Color(0xFFC85A54), light), Offset(p.x - u * 0.3f, p.y - u * 0.7f), Size(u * 0.6f, u * 0.16f))
        }
        "statue" -> {
            drawRect(shade(Color(0xFF8A8F96), light), Offset(p.x - u * 0.18f, p.y - u * 0.22f), Size(u * 0.36f, u * 0.22f))
            drawRect(shade(Color(0xFFB6BCC4), light), Offset(p.x - u * 0.1f, p.y - u * 0.7f), Size(u * 0.2f, u * 0.5f))
            drawCircle(shade(Color(0xFFC7CDD4), light), u * 0.12f, Offset(p.x, p.y - u * 0.78f))
        }
        "lantern" -> {
            drawRect(shade(Color(0xFF6E5236), light), Offset(p.x - u * 0.03f, p.y - u * 0.5f), Size(u * 0.06f, u * 0.5f))
            val glow = 0.5f + 0.5f * light.night
            drawCircle(Color(0xFFE0554E), u * 0.16f, Offset(p.x, p.y - u * 0.58f))
            drawCircle(Color(0xFFFFE08A).copy(alpha = glow), u * 0.1f, Offset(p.x, p.y - u * 0.58f))
        }
    }
}

private fun DrawScope.drawTree(p: Offset, u: Float, t: Float, light: Lighting, season: Season, seed: Int) {
    val sway = sin(t / 700f + seed) * u * 0.05f
    drawRect(shade(Color(0xFF6E4A2A), light), Offset(p.x - u * 0.06f, p.y - u * 0.55f), Size(u * 0.12f, u * 0.55f))
    val leaf = foliage(season)
    drawCircle(shade(Color(leaf.red * 0.82f, leaf.green * 0.82f, leaf.blue * 0.82f), light), u * 0.34f, Offset(p.x - u * 0.12f + sway, p.y - u * 0.7f))
    drawCircle(shade(leaf, light), u * 0.3f, Offset(p.x + u * 0.12f + sway, p.y - u * 0.78f))
    drawCircle(shade(Color(leaf.red * 1.08f, leaf.green * 1.08f, leaf.blue * 1.08f), light).let { Color(it.red.coerceAtMost(1f), it.green.coerceAtMost(1f), it.blue.coerceAtMost(1f)) }, u * 0.24f, Offset(p.x + sway, p.y - u * 0.95f))
}

private fun DrawScope.drawBuilding(p: Offset, u: Float, t: Float, light: Lighting, o: WorldObject) {
    val stage = o.level          // 0 = foundation, 1 = frame, 2 = walls, 3+ = finished
    val bw = u * 0.7f
    val base = Offset(p.x - bw / 2f, p.y)
    // foundation
    drawRect(shade(Color(0xFF8A8F96), light), Offset(base.x, base.y - u * 0.12f), Size(bw, u * 0.12f))
    if (stage == 0) {
        // scaffolding
        val col = shade(Color(0xFF8A6A44), light)
        drawRect(col, Offset(base.x, base.y - u * 0.7f), Size(u * 0.06f, u * 0.6f))
        drawRect(col, Offset(base.x + bw - u * 0.06f, base.y - u * 0.7f), Size(u * 0.06f, u * 0.6f))
        drawRect(col, Offset(base.x, base.y - u * 0.7f), Size(bw, u * 0.06f))
        return
    }
    val wallH = u * (0.36f + 0.1f * (stage - 1).coerceAtMost(2))
    // walls
    drawRect(shade(Color(0xFFEFE2C4), light), Offset(base.x, base.y - u * 0.12f - wallH), Size(bw, wallH))
    // roof (colour by category emoji sign)
    val roof = shade(when (o.emoji) {
        "📚" -> Color(0xFF4A6BB0); "🏋️" -> Color(0xFFD0574E); "🧘" -> Color(0xFF7C5CFF)
        "💻" -> Color(0xFF35998E); "🍳" -> Color(0xFFE0956B); "🗣️" -> Color(0xFF4E9A46)
        else -> Color(0xFF8A6A44)
    }, light)
    if (stage >= 2) {
        val rp = Path().apply {
            moveTo(base.x - u * 0.06f, base.y - u * 0.12f - wallH)
            lineTo(base.x + bw / 2f, base.y - u * 0.12f - wallH - u * 0.24f)
            lineTo(base.x + bw + u * 0.06f, base.y - u * 0.12f - wallH)
            close()
        }
        drawPath(rp, roof)
    }
    // door + glowing windows (more per level)
    if (stage >= 2) {
        drawRect(shade(Color(0xFF6B4A2C), light), Offset(p.x - u * 0.07f, base.y - u * 0.12f - u * 0.22f), Size(u * 0.14f, u * 0.22f))
        val wins = o.level.coerceIn(1, 3)
        for (i in 0 until wins) {
            val wx = base.x + bw * (i + 1f) / (wins + 1f) - u * 0.05f
            drawRect(lerp(Color(0xFF9AD0EE), Color(0xFFFFE08A), light.night), Offset(wx, base.y - u * 0.12f - wallH + u * 0.08f), Size(u * 0.1f, u * 0.1f))
        }
        // chimney smoke
        for (k in 0..2) {
            val prog = ((t / 1400f) + k / 3f) % 1f
            drawCircle(Color(0xFFDDE6EC).copy(alpha = (1f - prog) * 0.4f), u * (0.05f + prog * 0.08f), Offset(base.x + bw * 0.8f, base.y - u * 0.12f - wallH - u * 0.24f - prog * u * 0.5f))
        }
    }
    // emoji sign floating above
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        textSize = u * 0.26f; textAlign = android.graphics.Paint.Align.CENTER
    }
    drawContext.canvas.nativeCanvas.drawText(o.emoji, p.x, base.y - u * 0.12f - wallH - u * (if (stage >= 2) 0.34f else 0.16f), paint)
}

private fun DrawScope.drawVillager(p: Offset, u: Float, t: Float, light: Lighting, seed: Int) {
    val bob = abs(sin(t / 260f + seed)) * u * 0.03f
    val skin = shade(Color(0xFFF0C9A4), light)
    val shirt = shade(listOf(Color(0xFF4C79C9), Color(0xFFE0956B), Color(0xFF4E9A46), Color(0xFF7C5CFF))[(seed and 3)], light)
    drawRect(shade(Color(0xFF2E4A78), light), Offset(p.x - u * 0.05f, p.y - u * 0.18f - bob), Size(u * 0.1f, u * 0.18f))
    drawRect(shirt, Offset(p.x - u * 0.08f, p.y - u * 0.34f - bob), Size(u * 0.16f, u * 0.18f))
    drawCircle(skin, u * 0.08f, Offset(p.x, p.y - u * 0.42f - bob))
}

// ── ambient + weather particles ──────────────────────────────────────────

private fun DrawScope.drawParticles(t: Float, light: Lighting, weather: Weather, season: Season, P: (Float, Float, Float) -> Offset) {
    val w = size.width; val h = size.height
    val u = (P(1f, 0f, 0f) - P(0f, 0f, 0f)).getDistance()

    // birds by day
    if (light.night < 0.5f && weather != Weather.RAIN) {
        for (b in 0 until 2) {
            val bp = ((t / 5200f + b * 0.5f) % 1f)
            val bx = w * (0.1f + 0.8f * bp); val by = h * (0.16f + 0.05f * b) + sin(t / 300f + b) * u * 0.2f
            val flap = (sin(t / 90f + b * 2f) + 1f) / 2f
            val c = Color(0xCC33333F)
            drawRect(c, Offset(bx - u * 0.16f, by - flap * u * 0.08f), Size(u * 0.14f, u * 0.04f))
            drawRect(c, Offset(bx + u * 0.02f, by - flap * u * 0.08f), Size(u * 0.14f, u * 0.04f))
        }
    }
    // butterflies over the garden by day
    if (light.night < 0.55f) {
        for (i in 0 until 3) {
            val bx = w * (0.35f + 0.12f * i) + sin(t / 700f + i * 2f) * u * 1.2f
            val by = h * 0.62f + sin(t / 500f + i) * u * 0.8f
            val col = listOf(Color(0xFFFFB84D), Color(0xFFFF8FB3), Color(0xFFB58BFF))[i % 3]
            val flap = sin(t / 70f + i) * u * 0.06f
            drawOval(col, Offset(bx - u * 0.08f, by - abs(flap)), Size(u * 0.08f, u * 0.12f))
            drawOval(col, Offset(bx, by - abs(flap)), Size(u * 0.08f, u * 0.12f))
        }
    }
    // fireflies at night
    if (light.night > 0.6f) {
        for (i in 0 until 14) {
            val fx = w * hash(i, 5) + sin(t / 900f + i) * u
            val fy = h * (0.4f + 0.5f * hash(i, 6)) + sin(t / 700f + i * 2f) * u
            val a = (0.4f + 0.6f * sin(t / 400f + i * 3f)).coerceIn(0f, 1f)
            drawCircle(Color(0xFFFFF3A0).copy(alpha = a * 0.9f), u * 0.05f, Offset(fx, fy))
        }
    }
    // rain
    if (weather == Weather.RAIN) {
        for (i in 0 until 90) {
            val rx = (hash(i, 8) * w + t / 6f) % w
            val ry = (hash(i, 9) * h + t / 1.2f) % h
            drawLine(Color(0xFFBFD8EC).copy(alpha = 0.5f), Offset(rx, ry), Offset(rx - u * 0.06f, ry + u * 0.3f), strokeWidth = 2f)
        }
    }
    // snow
    if (weather == Weather.SNOW) {
        for (i in 0 until 70) {
            val sx = (hash(i, 8) * w + sin(t / 800f + i) * u * 2f) % w
            val sy = (hash(i, 9) * h + t / 12f) % h
            drawCircle(Color.White.copy(alpha = 0.85f), u * 0.05f, Offset(sx, sy))
        }
    }
    // falling leaves in autumn
    if (season == Season.AUTUMN && weather != Weather.SNOW) {
        for (i in 0 until 16) {
            val lx = (hash(i, 11) * w + sin(t / 600f + i) * u * 3f) % w
            val ly = (hash(i, 12) * h + t / 22f) % h
            drawOval(Color(0xFFD98A3A).copy(alpha = 0.85f), Offset(lx, ly), Size(u * 0.1f, u * 0.06f))
        }
    }
}

// ── the world's object layout, derived from the snapshot ─────────────────

private val PLOTS = mapOf(
    "library" to (6f to 4f), "gym" to (10f to 4f), "temple" to (6f to 11f),
    "workshop" to (10f to 11f), "bakery" to (5f to 8f), "school" to (11f to 8f),
)

private fun worldObjects(snap: FarmState.Snapshot): List<WorldObject> {
    val out = ArrayList<WorldObject>()
    // buildings on their plots, at a construction stage set by the structure level
    FarmState.CATEGORIES.forEach { cat ->
        val plot = PLOTS[cat.id] ?: return@forEach
        val pts = snap.categoryPoints[cat.id] ?: 0
        if (pts <= 0) return@forEach
        val lvl = snap.structureLevel(cat.id)
        val stage = when {
            lvl >= 2 -> 3; lvl == 1 -> 2; pts >= FarmState.STRUCTURE_POINTS / 2 -> 1; else -> 0
        }
        out += WorldObject(
            "b_${cat.id}", plot.first, plot.second, "building", cat.emoji, cat.structure,
            "Built from your ${cat.structure} habits", level = stage.coerceAtLeast(lvl),
            next = if (stage < 3) "Keep at it to raise it further" else "Fully built",
        )
    }
    // a forest that thickens with the Forest structure
    val forest = 2 + snap.structureLevel("forest")
    val treeSpots = listOf(1f to 2f, 2f to 1f, 1f to 5f, 13f to 2f, 14f to 5f, 2f to 3f, 13f to 6f, 12f to 1f)
    treeSpots.take(forest.coerceAtMost(treeSpots.size)).forEachIndexed { i, s ->
        out += WorldObject("t$i", s.first, s.second, "tree", "🌳", "Oak tree", "Grown by your walks", level = snap.structureLevel("forest"))
    }
    // decorations from expeditions
    val decorSpots = mapOf(
        "well" to (3f to 10f), "statue" to (13f to 12f), "lantern" to (7f to 8f),
        "topiary" to (9f to 8f), "fountain" to (11f to 12f), "banner" to (8f to 2f),
    )
    snap.decor.forEach { id ->
        val s = decorSpots[id] ?: return@forEach
        val kind = if (id == "topiary") "tree" else id
        out += WorldObject("d_$id", s.first, s.second, kind, "✨", FarmState.decorName(id), "Brought home from an expedition")
    }
    // villagers wandering the path (count grows with the world)
    val vcount = (1 + snap.level / 2).coerceIn(1, 5)
    val waypoints = listOf(8f to 4f, 8f to 8f, 8f to 11f, 6f to 8f, 11f to 8f)
    for (i in 0 until vcount) {
        val wp = waypoints[i % waypoints.size]
        out += WorldObject("v$i", wp.first, wp.second, "villager", "🧑‍🌾", "A villager", "Tending your world")
    }
    return out
}

// ─────────────────────────────────────────────── fullscreen immersive host

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorldScreen(snap: FarmState.Snapshot, onClose: () -> Unit) {
    LocalContext.current
    var selected by remember { mutableStateOf<WorldObject?>(null) }
    val sheet = rememberModalBottomSheetState()

    Box(Modifier.fillMaxSize().background(Color(0xFF0E1430))) {
        WorldDiorama(snap, Modifier.fillMaxSize(), onTapObject = { selected = it as? WorldObject })

        // floating glass header
        Column(
            Modifier.statusBarsPadding().padding(16.dp).fillMaxWidth(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassChip {
                    Text(snap.stageName, style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text("  ·  Lv ${snap.level}", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f))
                }
                Spacer(Modifier.weight(1f))
                GlassIcon(Icons.AutoMirrored.Rounded.ArrowBack, onClose)
            }
        }

        // floating hint at the bottom
        GlassChip(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 20.dp),
        ) {
            Icon(Icons.Rounded.CenterFocusWeak, contentDescription = null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Pinch, drag, double-tap · tap anything", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.85f))
        }
    }

    selected?.let { o ->
        ModalBottomSheet(onDismissRequest = { selected = null }, sheetState = sheet, containerColor = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().padding(24.dp).navigationBarsPadding()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (o.kind == "villager") "🧑‍🌾" else if (o.kind == "tree") "🌳" else o.emoji, fontSize = 40.sp)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(o.title, style = MaterialTheme.typography.headlineSmall)
                        Text(o.subtitle, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    }
                }
                if (o.kind == "building") {
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        InfoTile("Stage", listOf("Foundation", "Framing", "Walls", "Finished")[o.level.coerceIn(0, 3)], Modifier.weight(1f))
                        InfoTile("Level", "${o.level}", Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(o.next, style = MaterialTheme.typography.bodyMedium, color = TextTertiary)
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun GlassChip(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.12f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(50))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
private fun GlassIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape).clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = Color.White) }
}

@Composable
private fun InfoTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)).padding(14.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = TextTertiary)
        Text(value, style = MaterialTheme.typography.titleMedium, color = Violet)
    }
}
