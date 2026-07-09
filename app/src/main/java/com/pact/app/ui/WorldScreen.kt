package com.pact.app.ui

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDecay
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pact.app.core.FarmState
import com.pact.app.ui.theme.TextSecondary
import com.pact.app.ui.theme.TextTertiary
import com.pact.app.ui.theme.Violet
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

// ════════════════════════════════════════════════════════════════════════
//  A tiny handcrafted low-poly village, rendered as flat-shaded vector
//  geometry in Compose (no assets, no 3D engine). The village sits in the
//  centre, ringed by forest, a river and hills. The camera auto-frames the
//  populated part and zooms out as the world grows. Everything breathes.
// ════════════════════════════════════════════════════════════════════════

private fun rnd(i: Int, s: Int = 0): Float {
    var h = i * 374761393 + s * 668265263 + 1274126177
    h = (h xor (h ushr 13)) * 1274126177
    return ((h xor (h ushr 16)) and 0x7fffffff) / 0x7fffffff.toFloat()
}

// ── isometric projection in continuous "world units" ─────────────────────
// nProj is camera-independent; screen = origin + u * nProj.

private fun nx(tx: Float, ty: Float) = (tx - ty) * 0.5f
private fun ny(tx: Float, ty: Float, tz: Float) = (tx + ty) * 0.25f - tz * 0.42f

// ── lighting ──────────────────────────────────────────────────────────────

private data class Lighting(
    val skyTop: Color, val skyBottom: Color,
    val ambient: Color, val ambientStrength: Float,
    val brightness: Float, val night: Float, val warmth: Float,
)

private val L_NIGHT = Lighting(Color(0xFF10193C), Color(0xFF2B3A6B), Color(0xFF33447E), 0.50f, 0.66f, 1f, 0f)
private val L_DAWN = Lighting(Color(0xFFF3B26A), Color(0xFFFFE4B8), Color(0xFFFFC08A), 0.26f, 0.98f, 0.22f, 0.7f)
private val L_DAY = Lighting(Color(0xFF7FCBEA), Color(0xFFCDEFF3), Color(0xFFFFFBEF), 0.07f, 1.08f, 0f, 0.25f)
private val L_DUSK = Lighting(Color(0xFFFF9A5E), Color(0xFFFFD59A), Color(0xFFFFA268), 0.32f, 0.92f, 0.32f, 0.85f)

private fun lerpL(a: Lighting, b: Lighting, f: Float) = Lighting(
    lerp(a.skyTop, b.skyTop, f), lerp(a.skyBottom, b.skyBottom, f),
    lerp(a.ambient, b.ambient, f), a.ambientStrength + (b.ambientStrength - a.ambientStrength) * f,
    a.brightness + (b.brightness - a.brightness) * f, a.night + (b.night - a.night) * f,
    a.warmth + (b.warmth - a.warmth) * f,
)

private fun lightingFor(hf: Float): Lighting = when {
    hf < 5f -> L_NIGHT
    hf < 7f -> lerpL(L_NIGHT, L_DAWN, (hf - 5f) / 2f)
    hf < 9f -> lerpL(L_DAWN, L_DAY, (hf - 7f) / 2f)
    hf < 16f -> L_DAY
    hf < 18.5f -> lerpL(L_DAY, L_DUSK, (hf - 16f) / 2.5f)
    hf < 20.5f -> lerpL(L_DUSK, L_NIGHT, (hf - 18.5f) / 2f)
    else -> L_NIGHT
}

private fun shade(base: Color, l: Lighting): Color {
    val warm = lerp(base, Color(0xFFFFDCA8), l.warmth * 0.12f)
    val tinted = lerp(warm, l.ambient, l.ambientStrength)
    val b = l.brightness
    return Color(
        (tinted.red * b).coerceIn(0f, 1f),
        (tinted.green * b).coerceIn(0f, 1f),
        (tinted.blue * b).coerceIn(0f, 1f),
        base.alpha,
    )
}

private fun darker(c: Color, f: Float) = Color(c.red * f, c.green * f, c.blue * f, c.alpha)

// ── seasons & weather ─────────────────────────────────────────────────────

private enum class Season { SPRING, SUMMER, AUTUMN, WINTER }

private fun seasonNow(): Season = when (Calendar.getInstance().get(Calendar.MONTH)) {
    11, 0, 1 -> Season.WINTER
    2, 3, 4 -> Season.SPRING
    5, 6, 7 -> Season.SUMMER
    else -> Season.AUTUMN
}

private enum class Weather { CLEAR, CLOUDY, RAIN, SNOW }

private fun weatherFor(t: Float, season: Season): Weather {
    val phase = (t / 1000f / 80f).toInt() % 5
    return when (season) {
        Season.WINTER -> if (phase >= 3) Weather.SNOW else if (phase == 2) Weather.CLOUDY else Weather.CLEAR
        else -> when (phase) { 2 -> Weather.CLOUDY; 3 -> Weather.RAIN; else -> Weather.CLEAR }
    }
}

// ── world layout ────────────────────────────────────────────────────────

private data class Building(
    val id: String, val x: Float, val y: Float, val kind: String,
    val emoji: String, val title: String, val stage: Int, val level: Int,
)
private data class Tree(val x: Float, val y: Float, val seed: Int, val pine: Boolean, val scale: Float)
private data class Deco(val x: Float, val y: Float, val type: String, val seed: Int)
private data class Villager(
    val id: String, val route: List<Offset>, val speed: Float, val phase: Float,
    val kind: String, val hair: Color, val shirt: Color,
    val name: String, val activity: String, val mood: String, val contribution: String,
)
private data class Layout(
    val landR: Float, val contentR: Float,
    val patches: List<Deco>, val flowers: List<Deco>, val rocks: List<Deco>,
    val trees: List<Tree>, val hills: List<Deco>,
    val buildings: List<Building>, val villagers: List<Villager>,
    val river: List<Offset>, val pond: Offset, val pondR: Float,
    val paths: List<List<Offset>>,
)

private val VNAMES = listOf("Emma", "Noah", "Mila", "Theo", "Ava", "Leo", "Ivy", "Finn", "Rosa", "Sol", "Nina", "Kai")
private val ACTS = listOf("Reading" to "Read by the library", "Fishing" to "Caught two fish", "Planting" to "Planted flowers",
    "Baking" to "Baked fresh bread", "Walking" to "Took a long walk", "Building" to "Raised a new wall",
    "Watering" to "Watered the crops", "Stargazing" to "Named a new star")

/** Village sites, keyed to habit categories, arranged in a ring around the plaza. */
private val SITES = listOf(
    Triple("library", 150f, 3.3f), Triple("gym", 95f, 3.2f), Triple("temple", 40f, 3.4f),
    Triple("workshop", -35f, 3.3f), Triple("bakery", -90f, 3.2f), Triple("school", -145f, 3.4f),
    Triple("garden", 190f, 3.5f), Triple("moon", 60f, 3.7f),
)

private fun polar(angleDeg: Float, r: Float): Offset {
    val a = Math.toRadians(angleDeg.toDouble())
    return Offset((cos(a) * r).toFloat(), (sin(a) * r).toFloat())
}

private fun buildLayout(snap: FarmState.Snapshot): Layout {
    val buildings = ArrayList<Building>()
    val paths = ArrayList<List<Offset>>()
    SITES.forEach { (id, ang, r) ->
        val cat = FarmState.category(id)
        val pts = snap.categoryPoints[id] ?: 0
        val p = polar(ang, r)
        // buildings only appear once the habit has some progress; farm/moon show early
        val show = pts > 0 || id == "garden"
        if (!show) return@forEach
        val lvl = snap.structureLevel(id)
        val stage = when {
            lvl >= 2 -> 3; lvl == 1 -> 2; pts >= FarmState.STRUCTURE_POINTS / 2 -> 1; else -> 0
        }
        buildings += Building("b_$id", p.x, p.y, id, cat.emoji, cat.structure, stage, lvl)
        // a gently curved path from the plaza to the door
        val mid = Offset(p.x * 0.5f + p.y * 0.12f, p.y * 0.5f - p.x * 0.12f)
        paths += listOf(Offset(0f, 0f), mid, p)
    }

    // forest: clumps in the gaps beyond the ring — always a full ring so it's never empty
    val trees = ArrayList<Tree>()
    val forestLvl = snap.structureLevel("forest")
    val clumps = 9 + forestLvl * 2
    for (c in 0 until clumps) {
        val ang = c * (360f / clumps) + rnd(c, 1) * 22f - 11f
        val baseR = 5.6f + rnd(c, 2) * 1.9f
        val n = 3 + (rnd(c, 3) * 3).toInt()
        for (k in 0 until n) {
            val jr = baseR + rnd(c * 7 + k, 4) * 1.5f - 0.5f
            val ja = ang + rnd(c * 7 + k, 5) * 26f - 13f
            val p = polar(ja, jr)
            trees += Tree(p.x, p.y, c * 31 + k, rnd(c * 7 + k, 6) > 0.6f, 0.8f + rnd(c * 7 + k, 8) * 0.5f)
        }
    }
    // a few lone trees dotted inside for depth
    for (i in 0 until 5) {
        val p = polar(rnd(i, 11) * 360f, 2.0f + rnd(i, 12) * 1.4f)
        if (hypot(p.x, p.y) > 1.4f) trees += Tree(p.x, p.y, 900 + i, false, 0.7f + rnd(i, 13) * 0.3f)
    }

    // darker grass patches, flower clusters, rocks — clumped, never a checkerboard
    val patches = ArrayList<Deco>(); val flowers = ArrayList<Deco>(); val rocks = ArrayList<Deco>()
    for (i in 0 until 26) {
        val p = polar(rnd(i, 21) * 360f, rnd(i, 22) * 7.5f)
        patches += Deco(p.x, p.y, "patch", i)
    }
    val flowerBoost = 3 + snap.structureLevel("garden") + snap.structureLevel("temple")
    for (cl in 0 until flowerBoost) {
        val cc = polar(rnd(cl, 31) * 360f, 2.2f + rnd(cl, 32) * 5f)
        val n = 4 + (rnd(cl, 33) * 5).toInt()
        for (k in 0 until n) flowers += Deco(cc.x + (rnd(cl * 9 + k, 34) - 0.5f) * 1.1f, cc.y + (rnd(cl * 9 + k, 35) - 0.5f) * 1.1f, "flower", cl * 9 + k)
    }
    for (i in 0 until 10) {
        val p = polar(rnd(i, 41) * 360f, 1.8f + rnd(i, 42) * 6f)
        rocks += Deco(p.x, p.y, if (rnd(i, 43) > 0.6f) "log" else "rock", i)
    }

    // hills at the back (small x+y projects up/behind the village)
    val hills = listOf(Deco(-5.5f, -3.5f, "hill", 1), Deco(-3.2f, -6f, "hill", 2), Deco(-7f, -6.5f, "hill", 3))

    // a winding river crossing behind the village, and a pond off to one side
    val river = listOf(Offset(-8f, -2f), Offset(-4.5f, -0.5f), Offset(-1.5f, 1.5f), Offset(1.5f, 3.5f), Offset(4.5f, 6f), Offset(7.5f, 8f))
    val pond = polar(250f, 4.6f); val pondR = 1.7f

    // villagers — a lively, story-filled baseline in and around the plaza
    val villagers = ArrayList<Villager>()
    fun v(id: String, route: List<Offset>, kind: String, hairI: Int, shirtI: Int, actI: Int, speed: Float, phase: Float): Villager {
        val hairs = listOf(Color(0xFF3A2A1C), Color(0xFF6B4A2A), Color(0xFFC9A24B), Color(0xFF9AA0A6), Color(0xFF2A2A2E))
        val shirts = listOf(Color(0xFF4C79C9), Color(0xFFE0774A), Color(0xFF4E9A46), Color(0xFF8A6BEE), Color(0xFFD95E86), Color(0xFF3E9B8E))
        val act = ACTS[actI % ACTS.size]
        val moods = listOf("😊", "🙂", "😌", "🥰")
        return Villager(id, route, speed, phase, kind, hairs[hairI % hairs.size], shirts[shirtI % shirts.size],
            VNAMES[(id.hashCode() and 0xffff) % VNAMES.size], act.first, moods[(id.hashCode() ushr 3) and 3], act.second)
    }
    // couple strolling the plaza
    val ring = (0..7).map { polar(it * 45f, 1.5f) }
    villagers += v("couple_a", ring, "adult", 0, 0, 4, 0.05f, 0f)
    villagers += v("couple_b", ring, "adult", 2, 4, 4, 0.05f, 0.5f)
    // child darting about
    villagers += v("child", listOf(Offset(0.3f, 0.3f), Offset(1.2f, -0.6f), Offset(-0.8f, -0.9f), Offset(-0.5f, 0.9f)), "child", 1, 1, 7, 0.12f, 0.2f)
    // old villager feeding birds (mostly still)
    villagers += v("elder", listOf(Offset(-1.2f, 0.6f), Offset(-1.0f, 0.6f)), "elder", 3, 3, 6, 0.02f, 0f)
    // a fisher at the pond
    villagers += v("fisher", listOf(pond + Offset(1.2f, -0.2f), pond + Offset(1.25f, -0.15f)), "adult", 4, 2, 1, 0.02f, 0f)
    // a wandering pet
    villagers += v("pet", ring.map { it * 0.9f }, "pet", 1, 1, 4, 0.09f, 0.7f)
    // a builder at any unfinished building
    buildings.firstOrNull { it.stage < 3 }?.let { b ->
        villagers += v("builder", listOf(Offset(b.x - 0.6f, b.y), Offset(b.x - 0.55f, b.y)), "adult", 0, 5, 5, 0f, 0f)
    }
    // more villagers as the world levels up
    val extra = (snap.level).coerceIn(0, 4)
    for (i in 0 until extra) {
        val a = polar(i * 80f + 20f, 2.4f); val b = polar(i * 80f + 60f, 3.0f)
        villagers += v("wander$i", listOf(a, b, a), "adult", i + 1, i + 2, i + 2, 0.04f + rnd(i, 51) * 0.03f, rnd(i, 52))
    }

    val contentR = 8.2f + forestLvl * 0.5f
    return Layout(contentR + 1.4f, contentR, patches, flowers, rocks, trees, hills, buildings, villagers, river, pond, pondR, paths)
}

// ── smooth vector path helpers (Catmull-Rom → cubic) ─────────────────────

private fun smoothPath(pts: List<Offset>, closed: Boolean): Path {
    val p = Path()
    if (pts.size < 2) return p
    val n = pts.size
    fun pt(i: Int) = if (closed) pts[(i % n + n) % n] else pts[i.coerceIn(0, n - 1)]
    p.moveTo(pts[0].x, pts[0].y)
    val last = if (closed) n else n - 1
    for (i in 0 until last) {
        val p0 = pt(i - 1); val p1 = pt(i); val p2 = pt(i + 1); val p3 = pt(i + 2)
        val c1 = Offset(p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f)
        val c2 = Offset(p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f)
        p.cubicTo(c1.x, c1.y, c2.x, c2.y, p2.x, p2.y)
    }
    if (closed) p.close()
    return p
}

/** An organic closed blob around a centre, flattened into the ground plane. */
private fun groundBlob(center: Offset, rx: Float, ry: Float, seed: Int, lobes: Int = 9): Path {
    val pts = ArrayList<Offset>(lobes)
    for (i in 0 until lobes) {
        val a = i * (2 * Math.PI / lobes)
        val jitter = 0.72f + rnd(seed * 13 + i, 61) * 0.5f
        pts += Offset(center.x + (cos(a) * rx * jitter).toFloat(), center.y + (sin(a) * ry * jitter).toFloat())
    }
    return smoothPath(pts, true)
}

// ═══════════════════════════════════════════════════════════ the diorama

@Composable
fun WorldDiorama(
    snap: FarmState.Snapshot,
    modifier: Modifier = Modifier,
    onTapObject: ((Any) -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val decay = remember { exponentialDecay<Offset>(frictionMultiplier = 0.7f) }
    val clock = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val t0 = withFrameNanos { it }
        while (true) withFrameNanos { clock.floatValue = (it - t0) / 1_000_000f }
    }

    val layout = remember(snap) { buildLayout(snap) }

    var u by remember { mutableStateOf(0f) }          // pixels per world unit (zoom)
    var origin by remember { mutableStateOf(Offset.Zero) }   // screen pos of world (0,0,0)
    var canvas by remember { mutableStateOf(Size.Zero) }
    var framedFor by remember { mutableStateOf(-1f) }
    var flingJob by remember { mutableStateOf<Job?>(null) }

    fun project(x: Float, y: Float, z: Float) = origin + Offset(nx(x, y) * u, ny(x, y, z) * u)

    // frame the populated part of the world; zoom out as it grows
    fun targetFrame(size: Size, radius: Float): Pair<Float, Offset> {
        var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        for (i in 0 until 12) {
            val a = i * 30f
            val p = polar(a, radius)
            for (zz in listOf(0f, 1.4f)) {
                val gx = nx(p.x, p.y); val gy = ny(p.x, p.y, zz)
                minX = minOf(minX, gx); maxX = maxOf(maxX, gx)
                minY = minOf(minY, gy); maxY = maxOf(maxY, gy)
            }
        }
        val wN = (maxX - minX).coerceAtLeast(0.01f); val hN = (maxY - minY).coerceAtLeast(0.01f)
        val uu = minOf(size.width * 0.96f / wN, size.height * 0.82f / hN)
        val cN = Offset((minX + maxX) / 2f, (minY + maxY) / 2f)
        val org = Offset(size.width / 2f, size.height * 0.5f) - cN * uu
        return uu to org
    }

    fun zoomAbout(c: Offset, factor: Float) {
        val minU = canvas.width * 0.03f; val maxU = canvas.width * 0.2f
        val nu = (u * factor).coerceIn(minU, maxU)
        origin = c - (c - origin) * (nu / u)
        u = nu
    }

    Box(modifier) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(layout) {
                    awaitEachGesture {
                        flingJob?.cancel()
                        val first = awaitFirstDown(requireUnconsumed = false)
                        val tracker = VelocityTracker(); tracker.resetTracking()
                        var prevC = first.position; var prevSpread = -1f; var moved = 0f
                        while (true) {
                            val e = awaitPointerEvent()
                            val pts = e.changes.filter { it.pressed }
                            if (pts.isEmpty()) break
                            val c = pts.fold(Offset.Zero) { a, p -> a + p.position } / pts.size.toFloat()
                            if (pts.size >= 2) {
                                val spread = pts.fold(0f) { a, p -> a + (p.position - c).getDistance() } / pts.size
                                if (prevSpread > 0f && spread > 0f) zoomAbout(c, spread / prevSpread)
                                prevSpread = spread
                            } else prevSpread = -1f
                            val d = c - prevC; prevC = c
                            origin += d; moved += d.getDistance()
                            tracker.addPosition(e.changes.first().uptimeMillis, c)
                            e.changes.forEach { it.consume() }
                        }
                        if (moved < 14f) {
                            val tap = prevC
                            val hit = tappables(layout).minByOrNull { (project(it.first.x, it.first.y, 0f) - tap).getDistance() }
                            val dist = hit?.let { (project(it.first.x, it.first.y, 0f) - tap).getDistance() } ?: Float.MAX_VALUE
                            if (onTapObject != null && hit != null && dist < u * 0.9f) onTapObject(hit.second)
                            else scope.launch {
                                val u0 = u; val o0 = origin
                                zoomAbout(tap, if (u < canvas.width * 0.09f) 1.7f else 0.62f)
                                val u1 = u; val o1 = origin; u = u0; origin = o0
                                animate(0f, 1f, animationSpec = tween(440)) { f, _ ->
                                    u = u0 + (u1 - u0) * f
                                    origin = Offset(o0.x + (o1.x - o0.x) * f, o0.y + (o1.y - o0.y) * f)
                                }
                            }
                        } else {
                            val v = tracker.calculateVelocity()
                            flingJob = scope.launch {
                                val st = AnimationState(Offset.VectorConverter, origin, Offset(v.x, v.y))
                                st.animateDecay(decay) { origin = value }
                            }
                        }
                    }
                },
        ) {
            canvas = size
            if (u <= 0f) { val (uu, oo) = targetFrame(size, layout.contentR); u = uu; origin = oo; framedFor = layout.contentR }
            val t = clock.floatValue
            val season = seasonNow()
            val cal = Calendar.getInstance()
            val hf = cal.get(Calendar.HOUR_OF_DAY) + cal.get(Calendar.MINUTE) / 60f
            val light = lightingFor(hf)
            val weather = weatherFor(t, season)
            drawScene(t, light, weather, season, layout) { x, y, z -> project(x, y, z) }
        }
    }

    // smoothly re-frame (zoom out) whenever the world grows
    LaunchedEffect(layout.contentR, canvas) {
        if (canvas == Size.Zero || u <= 0f) return@LaunchedEffect
        if (layout.contentR > framedFor + 0.01f) {
            framedFor = layout.contentR
            val (uu, oo) = targetFrame(canvas, layout.contentR)
            val u0 = u; val o0 = origin
            animate(0f, 1f, animationSpec = tween(700)) { f, _ ->
                u = u0 + (uu - u0) * f
                origin = Offset(o0.x + (oo.x - o0.x) * f, o0.y + (oo.y - o0.y) * f)
            }
        }
    }
}

// list of tappable (worldPos, info) for hit-testing
private fun tappables(layout: Layout): List<Pair<Offset, WorldTap>> {
    val out = ArrayList<Pair<Offset, WorldTap>>()
    layout.buildings.forEach {
        out += Offset(it.x, it.y) to WorldTap(it.emoji, it.title, "Built from your ${it.title} habits", "building", it.stage, it.level)
    }
    layout.villagers.filter { it.kind != "pet" }.forEach {
        val pos = it.route.first()
        out += pos to WorldTap(if (it.kind == "child") "🧒" else if (it.kind == "elder") "🧓" else "🧑", it.name,
            "Currently: ${it.activity}", "villager", 0, 0, it.mood, it.contribution)
    }
    return out
}

data class WorldTap(
    val emoji: String, val title: String, val subtitle: String, val kind: String,
    val stage: Int = 0, val level: Int = 0, val mood: String = "", val contribution: String = "",
)

// ── the render ───────────────────────────────────────────────────────────

private fun DrawScope.drawScene(
    t: Float, light: Lighting, weather: Weather, season: Season, layout: Layout,
    P: (Float, Float, Float) -> Offset,
) {
    val w = size.width; val h = size.height
    val u = (P(1f, 0f, 0f) - P(0f, 0f, 0f)).getDistance() * 2f    // ~pixels per world unit

    // ── sky
    drawRect(Brush.verticalGradient(listOf(light.skyTop, light.skyBottom)))
    // sun / moon with a soft bloom
    val cel = lerp(Color(0xFFFFE49B), Color(0xFFEAF0FF), light.night)
    val cc = Offset(w * (0.80f - light.night * 0.55f), h * 0.15f)
    drawCircle(cel.copy(alpha = 0.25f), h * 0.11f, cc)
    drawCircle(cel, h * 0.055f, cc)
    // twinkling stars
    if (light.night > 0.45f) {
        for (i in 0 until 46) {
            val a = ((light.night - 0.45f) * 1.8f) * (0.4f + 0.6f * sin(t / 500f + i * 1.3f))
            drawCircle(Color.White.copy(alpha = a.coerceIn(0f, 0.9f)), 1.6f + rnd(i, 71) * 1.4f, Offset(rnd(i, 72) * w, rnd(i, 73) * h * 0.42f))
        }
    }
    // drifting clouds
    val cloudN = when (weather) { Weather.CLOUDY, Weather.RAIN -> 5; else -> 3 }
    for (i in 0 until cloudN) {
        val cx = ((t / 110f + i * 230f) % (w + 260f)) - 130f
        val col = shade(Color.White, light).copy(alpha = if (weather == Weather.RAIN) 0.9f else 0.82f)
        cloud(Offset(cx, h * (0.09f + 0.05f * i)), u * (0.5f + 0.2f * i), col)
    }
    // soft distance fog band along the horizon
    drawRect(Brush.verticalGradient(listOf(Color.Transparent, shade(Color(0xFFEFF3F6), light).copy(alpha = 0.18f))), topLeft = Offset(0f, h * 0.18f), size = Size(w, h * 0.28f))

    // ── island base: soft shadow, extruded dirt side, grass top
    val landPath = groundBlob(P(0f, 0f, 0f), layout.landR * u * 0.5f, layout.landR * u * 0.25f, 4242, 12)
    drawPath(translated(landPath, 0f, u * 0.16f + 8f), Color.Black.copy(alpha = 0.22f))     // contact shadow
    drawPath(translated(landPath, 0f, u * 0.30f), shade(Color(0xFF6E4A2E), light))          // dirt side
    drawPath(translated(landPath, 0f, u * 0.30f), Color.Black.copy(alpha = 0.12f))          // side shading
    // grass top with a soft radial gradient (lighter centre → deeper edge)
    val gTop = shade(Color(0xFF74C24E), light); val gEdge = shade(Color(0xFF4E9A3C), light)
    drawPath(landPath, Brush.radialGradient(listOf(gTop, gEdge), center = P(0f, 0f, 0f), radius = layout.landR * u * 0.55f))

    // ── flat ground decorations (painted on the surface)
    // darker grass patches
    layout.patches.forEach { d ->
        val c = P(d.x, d.y, 0f)
        val sz = (0.9f + rnd(d.seed, 81) * 1.3f)
        drawPath(groundBlob(c, sz * u * 0.5f, sz * u * 0.25f, d.seed, 7), shade(lerp(Color(0xFF5EAE41), Color(0xFF3F8A34), rnd(d.seed, 82)), light).copy(alpha = 0.5f))
    }
    // river
    drawWater(layout.river.map { P(it.x, it.y, 0f) }, u * 0.62f, t, light, u)
    // pond
    val pc = P(layout.pond.x, layout.pond.y, 0f)
    drawPond(pc, layout.pondR * u * 0.5f, layout.pondR * u * 0.25f, t, light, u)
    // paths (curved, tan)
    layout.paths.forEach { pp ->
        val sp = smoothPath(pp.map { P(it.x, it.y, 0f) }, false)
        drawPath(sp, shade(Color(0xFFCBAE86), light), style = Stroke(width = u * 0.28f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
        drawPath(sp, shade(Color(0xFFB79468), light), style = Stroke(width = u * 0.20f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
    }
    // a small stone plaza + fountain at the centre
    drawPlaza(P, u, t, light)
    // flower clusters
    layout.flowers.forEach { f ->
        val c = P(f.x, f.y, 0f)
        val bounce = (sin(t / 260f + f.seed) + 1f) * u * 0.02f
        val col = shade(listOf(Color(0xFFFF6FA6), Color(0xFFFFD24B), Color(0xFF6FA8FF), Color(0xFFB58BFF), Color(0xFFFF9B57))[f.seed % 5], light)
        drawLine(shade(Color(0xFF4E9A32), light), c, c - Offset(0f, u * 0.1f), strokeWidth = u * 0.03f)
        drawCircle(col, u * 0.055f, c - Offset(0f, u * 0.12f + bounce))
        drawCircle(shade(Color(0xFFFFF3B0), light), u * 0.02f, c - Offset(0f, u * 0.12f + bounce))
    }
    // rocks & logs
    layout.rocks.forEach { d ->
        val c = P(d.x, d.y, 0f)
        drawOval(Color.Black.copy(alpha = 0.14f), Offset(c.x - u * 0.13f, c.y - u * 0.02f), Size(u * 0.26f, u * 0.09f))
        if (d.type == "rock") {
            drawCircle(shade(Color(0xFF9AA0A6), light), u * 0.1f, c - Offset(0f, u * 0.05f))
            drawCircle(shade(Color(0xFF7B8188), light), u * 0.07f, c - Offset(-u * 0.03f, u * 0.03f))
        } else {
            drawOval(shade(Color(0xFF7A5330), light), Offset(c.x - u * 0.16f, c.y - u * 0.1f), Size(u * 0.32f, u * 0.13f))
            drawOval(shade(Color(0xFF956B45), light), Offset(c.x - u * 0.14f, c.y - u * 0.09f), Size(u * 0.1f, u * 0.11f))
        }
    }

    // ── upright objects, back-to-front
    data class Drawable(val depth: Float, val draw: () -> Unit)
    val ds = ArrayList<Drawable>()
    layout.hills.forEach { hh -> ds += Drawable(hh.x + hh.y - 2f) { drawHill(P(hh.x, hh.y, 0f), u, light, season, hh.seed) } }
    layout.trees.forEach { tr -> ds += Drawable(tr.x + tr.y) { drawTree(P(tr.x, tr.y, 0f), u * tr.scale, t, light, season, tr.seed, tr.pine) } }
    layout.buildings.forEach { b -> ds += Drawable(b.x + b.y) { drawBuilding(b, P(b.x, b.y, 0f), u, t, light) } }
    layout.villagers.forEach { vg ->
        val p = villagerPos(vg, t)
        ds += Drawable(p.first.x + p.first.y + 0.01f) { drawVillager(P(p.first.x, p.first.y, 0f), u, t, light, vg, p.second) }
    }
    ds.sortedBy { it.depth }.forEach { it.draw() }

    // building emoji signs + names (screen-space text, over their tiles)
    layout.buildings.forEach { b ->
        val p = P(b.x, b.y, 0f)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textSize = u * 0.22f; textAlign = android.graphics.Paint.Align.CENTER
        }
        if (b.stage >= 2) drawContext.canvas.nativeCanvas.drawText(b.emoji, p.x, p.y - u * 0.92f, paint)
    }

    // ── flying life + weather + light overlays
    drawFlyingLife(t, light, weather, season, P, u)
    // gentle vignette
    drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.24f)), center = Offset(w / 2f, h * 0.46f), radius = w * 0.95f))
}

private fun translated(path: Path, dx: Float, dy: Float): Path =
    Path().apply { addPath(path, Offset(dx, dy)) }

private fun DrawScope.cloud(c: Offset, u: Float, col: Color) {
    drawOval(col, Offset(c.x - 3.2f * u, c.y - u * 0.8f), Size(6.4f * u, u * 1.7f))
    drawOval(col, Offset(c.x - 1.7f * u, c.y - u * 1.6f), Size(3.6f * u, u * 2.3f))
    drawOval(col, Offset(c.x + 0.4f * u, c.y - u * 1.1f), Size(3f * u, u * 1.9f))
}

private fun DrawScope.drawWater(pts: List<Offset>, width: Float, t: Float, light: Lighting, u: Float) {
    val sp = smoothPath(pts, false)
    drawPath(sp, shade(Color(0xFF2C7BD1), light), style = Stroke(width = width, cap = androidx.compose.ui.graphics.StrokeCap.Round))
    drawPath(sp, shade(Color(0xFF57B0EC), light), style = Stroke(width = width * 0.6f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
    // moving highlight + ripples
    for (i in pts.indices) {
        val p = pts[i]
        if (sin(t / 420f + i) > 0.5f) drawCircle(Color.White.copy(alpha = 0.35f * light.brightness), u * 0.05f, p)
    }
}

private fun DrawScope.drawPond(c: Offset, rx: Float, ry: Float, t: Float, light: Lighting, u: Float) {
    drawOval(Color.Black.copy(alpha = 0.12f), Offset(c.x - rx, c.y - ry + u * 0.05f), Size(rx * 2, ry * 2))
    drawOval(shade(Color(0xFF2C7BD1), light), Offset(c.x - rx, c.y - ry), Size(rx * 2, ry * 2))
    drawOval(shade(Color(0xFF4AA6E8), light), Offset(c.x - rx * 0.7f, c.y - ry * 0.55f), Size(rx * 1.4f, ry * 1.1f))
    // expanding ripple rings
    for (k in 0 until 3) {
        val prog = ((t / 1600f) + k / 3f) % 1f
        drawOval(Color.White.copy(alpha = (1f - prog) * 0.28f), Offset(c.x - rx * prog, c.y - ry * prog), Size(rx * 2 * prog, ry * 2 * prog), style = Stroke(width = 2f))
    }
    // two ducks
    for (d in 0 until 2) {
        val dx = c.x + sin(t / 1400f + d * 3f) * rx * 0.5f
        val dy = c.y + cos(t / 1700f + d * 2f) * ry * 0.4f
        drawOval(shade(Color(0xFFF4F1E8), light), Offset(dx - u * 0.08f, dy - u * 0.05f), Size(u * 0.16f, u * 0.1f))
        drawCircle(shade(Color(0xFFF4F1E8), light), u * 0.04f, Offset(dx + u * 0.07f, dy - u * 0.06f))
        drawCircle(shade(Color(0xFFF2B84D), light), u * 0.015f, Offset(dx + u * 0.1f, dy - u * 0.06f))
    }
}

private fun DrawScope.drawPlaza(P: (Float, Float, Float) -> Offset, u: Float, t: Float, light: Lighting) {
    val c = P(0f, 0f, 0f)
    drawOval(shade(Color(0xFFCFC3A6), light), Offset(c.x - u * 1.15f, c.y - u * 0.58f), Size(u * 2.3f, u * 1.16f))
    drawOval(shade(Color(0xFFBDB08E), light), Offset(c.x - u * 1.15f, c.y - u * 0.58f), Size(u * 2.3f, u * 1.16f), style = Stroke(width = u * 0.05f))
    // fountain base + water
    drawOval(shade(Color(0xFF9AA0A6), light), Offset(c.x - u * 0.3f, c.y - u * 0.16f), Size(u * 0.6f, u * 0.3f))
    drawOval(shade(Color(0xFF57B0EC), light), Offset(c.x - u * 0.22f, c.y - u * 0.12f), Size(u * 0.44f, u * 0.22f))
    drawRect(shade(Color(0xFFB6BCC4), light), Offset(c.x - u * 0.04f, c.y - u * 0.42f), Size(u * 0.08f, u * 0.3f))
    for (k in 0 until 3) {
        val prog = ((t / 900f) + k / 3f) % 1f
        drawCircle(Color(0xFFBFE6FF).copy(alpha = (1f - prog) * 0.7f), u * 0.02f, Offset(c.x - u * 0.06f + k * u * 0.06f, c.y - u * 0.42f + prog * u * 0.2f))
    }
}

private fun DrawScope.drawHill(base: Offset, u: Float, light: Lighting, season: Season, seed: Int) {
    val r = u * (1.6f + rnd(seed, 91) * 0.8f)
    drawOval(Color.Black.copy(alpha = 0.14f), Offset(base.x - r, base.y - r * 0.16f), Size(r * 2, r * 0.5f))
    val top = shade(lerp(foliage(season), Color(0xFF3E8B3A), 0.3f), light)
    val path = Path().apply {
        moveTo(base.x - r, base.y)
        cubicTo(base.x - r * 0.7f, base.y - r * 1.1f, base.x + r * 0.7f, base.y - r * 1.1f, base.x + r, base.y)
        close()
    }
    drawPath(path, top)
    drawPath(path, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.06f), Color.Black.copy(alpha = 0.12f)), startY = base.y - r, endY = base.y))
}

private fun foliage(season: Season): Color = when (season) {
    Season.SPRING -> Color(0xFF62C063); Season.SUMMER -> Color(0xFF43A047)
    Season.AUTUMN -> Color(0xFFE0862F); Season.WINTER -> Color(0xFFA8C4B2)
}

private fun DrawScope.drawTree(base: Offset, u: Float, t: Float, light: Lighting, season: Season, seed: Int, pine: Boolean) {
    val sway = sin(t / 760f + seed) * u * 0.06f
    drawOval(Color.Black.copy(alpha = 0.16f * (1f - light.night * 0.4f)), Offset(base.x - u * 0.28f, base.y - u * 0.04f), Size(u * 0.56f, u * 0.18f))
    drawPath(Path().apply {
        moveTo(base.x - u * 0.07f, base.y); lineTo(base.x + u * 0.07f, base.y)
        lineTo(base.x + u * 0.05f, base.y - u * 0.5f); lineTo(base.x - u * 0.05f, base.y - u * 0.5f); close()
    }, shade(Color(0xFF6E4A2A), light))
    val leaf = foliage(season)
    if (pine) {
        for (tier in 0 until 3) {
            val ty = base.y - u * (0.42f + tier * 0.36f); val tw = u * (0.5f - tier * 0.12f)
            drawPath(Path().apply {
                moveTo(base.x + sway, ty - u * 0.42f); lineTo(base.x - tw + sway, ty); lineTo(base.x + tw + sway, ty); close()
            }, shade(darker(leaf, 0.9f - tier * 0.05f), light))
        }
    } else {
        val cx = base.x + sway; val cy = base.y - u * 0.85f
        // faceted round canopy: three overlapping polys, lit top-left
        drawCircle(shade(darker(leaf, 0.78f), light), u * 0.4f, Offset(cx + u * 0.1f, cy + u * 0.12f))
        drawCircle(shade(leaf, light), u * 0.36f, Offset(cx - u * 0.06f, cy + u * 0.04f))
        drawCircle(shade(lerp(leaf, Color.White, 0.18f), light), u * 0.24f, Offset(cx - u * 0.12f, cy - u * 0.12f))
        if (season == Season.SPRING) for (k in 0 until 4) drawCircle(shade(Color(0xFFFFC0DA), light), u * 0.04f, Offset(cx + (rnd(seed + k, 92) - 0.5f) * u * 0.6f, cy + (rnd(seed + k, 93) - 0.5f) * u * 0.6f))
    }
}

// ── buildings, each with personality ─────────────────────────────────────

private fun roofColor(kind: String) = when (kind) {
    "library" -> Color(0xFF5C79C0); "gym" -> Color(0xFFD95E54); "temple" -> Color(0xFF8A6BEE)
    "workshop" -> Color(0xFF3E9B8E); "bakery" -> Color(0xFFC97C4A); "school" -> Color(0xFF5BA24C)
    "moon" -> Color(0xFF6A6BD0); else -> Color(0xFFC0503F)
}

private fun DrawScope.drawBuilding(b: Building, base: Offset, u: Float, t: Float, light: Lighting) {
    drawOval(Color.Black.copy(alpha = 0.18f * (1f - light.night * 0.4f)), Offset(base.x - u * 0.55f, base.y - u * 0.06f), Size(u * 1.1f, u * 0.3f))

    if (b.kind == "garden") { drawFarm(base, u, t, light); return }

    if (b.stage == 0) { // foundation + posts
        drawOval(shade(Color(0xFF9AA0A6), light), Offset(base.x - u * 0.5f, base.y - u * 0.16f), Size(u, u * 0.32f))
        for (sx in listOf(-0.4f, 0.4f)) drawRect(shade(Color(0xFF8A6A44), light), Offset(base.x + sx * u, base.y - u * 0.7f), Size(u * 0.07f, u * 0.6f))
        return
    }
    val bw = u * 0.9f; val bh = u * (0.5f + 0.08f * (b.stage - 1))
    val fx = base.x - bw / 2f; val fy = base.y - bh
    // right side face (darker) for depth
    drawPath(Path().apply {
        moveTo(base.x + bw / 2f, base.y); lineTo(base.x + bw / 2f + u * 0.14f, base.y - u * 0.07f)
        lineTo(fx + bw + u * 0.14f, fy - u * 0.07f); lineTo(fx + bw, fy); close()
    }, shade(darker(Color(0xFFF2E4C6), 0.8f), light))
    // front wall
    drawRect(shade(Color(0xFFF2E4C6), light), Offset(fx, fy), Size(bw, bh))
    if (b.stage == 1) { // framing only
        drawRect(shade(Color(0xFF9A7A50), light), Offset(fx, fy), Size(bw, bh), style = Stroke(width = u * 0.04f))
        return
    }
    // roof — two facets
    val roof = roofColor(b.kind)
    drawPath(Path().apply {
        moveTo(fx - u * 0.08f, fy); lineTo(base.x, fy - u * 0.34f); lineTo(fx + bw + u * 0.08f, fy); close()
    }, shade(roof, light))
    drawPath(Path().apply {
        moveTo(fx + bw + u * 0.08f, fy); lineTo(base.x, fy - u * 0.34f)
        lineTo(base.x + u * 0.14f, fy - u * 0.41f); lineTo(fx + bw + u * 0.22f, fy - u * 0.07f); close()
    }, shade(darker(roof, 0.82f), light))
    // door + glowing windows
    drawRect(shade(Color(0xFF6B4A2C), light), Offset(base.x - u * 0.08f, base.y - u * 0.24f), Size(u * 0.16f, u * 0.24f))
    val glow = lerp(Color(0xFF9AD0EE), Color(0xFFFFE08A), light.night)
    for (wx in listOf(-0.28f, 0.28f)) drawRect(glow, Offset(fx + bw / 2f + wx * u - u * 0.05f, fy + u * 0.12f), Size(u * 0.12f, u * 0.12f))
    if (light.night > 0.4f) for (wx in listOf(-0.28f, 0.28f)) drawCircle(Color(0xFFFFE08A).copy(alpha = light.night * 0.3f), u * 0.12f, Offset(fx + bw / 2f + wx * u + u * 0.01f, fy + u * 0.18f))

    // personality props
    when (b.kind) {
        "library" -> { // ivy + book stack + lantern
            for (k in 0 until 5) drawCircle(shade(Color(0xFF4E8B32), light), u * 0.04f, Offset(fx + u * 0.06f, fy + u * 0.08f + k * u * 0.08f))
            drawRect(shade(Color(0xFFB0472F), light), Offset(fx + u * 0.05f, base.y - u * 0.1f), Size(u * 0.16f, u * 0.05f))
            drawRect(shade(Color(0xFF3E6BB0), light), Offset(fx + u * 0.07f, base.y - u * 0.15f), Size(u * 0.12f, u * 0.05f))
            lantern(Offset(fx + bw + u * 0.05f, base.y - u * 0.34f), u, light, t)
        }
        "bakery" -> { // chimney + smoke + bread sign
            drawRect(shade(Color(0xFFB07A50), light), Offset(fx + bw * 0.66f, fy - u * 0.32f), Size(u * 0.12f, u * 0.2f))
            for (k in 0 until 3) { val pr = ((t / 1200f) + k / 3f) % 1f; drawCircle(Color(0xFFE8E1D6).copy(alpha = (1f - pr) * 0.5f), u * (0.05f + pr * 0.08f), Offset(fx + bw * 0.72f, fy - u * 0.32f - pr * u * 0.5f)) }
            drawCircle(shade(Color(0xFFD9A866), light), u * 0.07f, Offset(base.x, base.y - u * 0.42f))
        }
        "workshop" -> { // log pile + tools
            drawOval(shade(Color(0xFF7A5330), light), Offset(fx - u * 0.02f, base.y - u * 0.1f), Size(u * 0.18f, u * 0.08f))
            drawOval(shade(Color(0xFF95_6B45), light), Offset(fx, base.y - u * 0.11f), Size(u * 0.06f, u * 0.08f))
            drawLine(shade(Color(0xFF9AA0A6), light), Offset(fx + bw - u * 0.04f, base.y), Offset(fx + bw - u * 0.04f, base.y - u * 0.22f), strokeWidth = u * 0.03f)
        }
        "gym" -> { // banner + weights
            drawRect(shade(Color(0xFFD95E54), light), Offset(base.x - u * 0.3f, fy - u * 0.34f), Size(u * 0.2f, u * 0.14f))
            drawCircle(shade(Color(0xFF3A3A44), light), u * 0.05f, Offset(fx + u * 0.1f, base.y - u * 0.04f))
            drawCircle(shade(Color(0xFF3A3A44), light), u * 0.05f, Offset(fx + u * 0.24f, base.y - u * 0.04f))
        }
        "temple" -> { // hedge garden + incense
            drawOval(shade(Color(0xFF4E9A3C), light), Offset(fx - u * 0.05f, base.y - u * 0.08f), Size(u * 0.3f, u * 0.1f))
            for (k in 0 until 3) { val pr = ((t / 1500f) + k / 3f) % 1f; drawCircle(Color(0xFFE0DEEA).copy(alpha = (1f - pr) * 0.3f), u * 0.03f, Offset(base.x, fy - u * 0.34f - pr * u * 0.3f)) }
        }
        "school" -> { drawRect(shade(Color(0xFF5BA24C), light), Offset(base.x + u * 0.16f, fy - u * 0.34f), Size(u * 0.14f, u * 0.1f)) }
        "moon" -> { lantern(Offset(base.x, base.y - u * 0.34f), u, light, t); if (light.night > 0.3f) drawCircle(Color(0xFFBFC6FF).copy(alpha = light.night * 0.4f), u * 0.3f, Offset(base.x, fy)) }
    }
}

private fun DrawScope.lantern(p: Offset, u: Float, light: Lighting, t: Float) {
    val flick = 0.7f + 0.3f * sin(t / 180f)
    drawLine(shade(Color(0xFF5A4632), light), Offset(p.x, p.y - u * 0.2f), Offset(p.x, p.y), strokeWidth = u * 0.02f)
    if (light.night > 0.25f) drawCircle(Color(0xFFFFE08A).copy(alpha = light.night * flick * 0.5f), u * 0.16f, p)
    drawCircle(shade(Color(0xFFE0554E), light), u * 0.05f, p)
    drawCircle(lerp(shade(Color(0xFFFFE08A), light), Color(0xFFFFF3C0), light.night * flick), u * 0.03f, p)
}

private fun DrawScope.drawFarm(base: Offset, u: Float, t: Float, light: Lighting) {
    // fenced field with crop rows + a scarecrow
    val fw = u * 1.1f; val fd = u * 0.5f
    drawOval(shade(Color(0xFF7A5230), light), Offset(base.x - fw / 2f, base.y - fd / 2f), Size(fw, fd))
    for (r in 0 until 4) {
        val ry = base.y - fd * 0.3f + r * fd * 0.18f
        for (cX in 0 until 5) {
            val cx = base.x - fw * 0.36f + cX * fw * 0.18f
            val sway = sin(t / 300f + cX + r) * u * 0.02f
            drawLine(shade(Color(0xFF57A83E), light), Offset(cx, ry), Offset(cx + sway, ry - u * 0.1f), strokeWidth = u * 0.02f)
        }
    }
    // fence posts
    for (a in 0 until 8) {
        val ang = a * 45.0
        val px = base.x + (cos(Math.toRadians(ang)) * fw * 0.52f).toFloat()
        val py = base.y + (sin(Math.toRadians(ang)) * fd * 0.52f).toFloat()
        drawRect(shade(Color(0xFF9A7A50), light), Offset(px - u * 0.02f, py - u * 0.12f), Size(u * 0.04f, u * 0.12f))
    }
    // scarecrow
    drawLine(shade(Color(0xFF8A6A44), light), Offset(base.x, base.y - u * 0.05f), Offset(base.x, base.y - u * 0.45f), strokeWidth = u * 0.03f)
    drawLine(shade(Color(0xFF8A6A44), light), Offset(base.x - u * 0.14f, base.y - u * 0.32f), Offset(base.x + u * 0.14f, base.y - u * 0.32f), strokeWidth = u * 0.03f)
    drawCircle(shade(Color(0xFFD9B36A), light), u * 0.06f, Offset(base.x, base.y - u * 0.5f))
}

// ── villagers ─────────────────────────────────────────────────────────────

private fun villagerPos(v: Villager, t: Float): Pair<Offset, Boolean> {
    if (v.route.size < 2) return v.route.first() to true
    val loop = (t / 1000f * v.speed + v.phase) % 1f
    val seg = loop * v.route.size
    val i = seg.toInt() % v.route.size
    val f = seg - seg.toInt()
    val a = v.route[i]; val b = v.route[(i + 1) % v.route.size]
    val ease = f * f * (3 - 2 * f)   // smoothstep
    val p = Offset(a.x + (b.x - a.x) * ease, a.y + (b.y - a.y) * ease)
    return p to (b.x >= a.x)
}

private fun DrawScope.drawVillager(base: Offset, u: Float, t: Float, light: Lighting, v: Villager, facingRight: Boolean) {
    val s = when (v.kind) { "child" -> 0.62f; "elder" -> 0.9f; "pet" -> 0.5f; else -> 1f }
    val moving = v.speed > 0.03f
    val bob = if (moving) abs(sin(t / 150f + v.phase * 6f)) * u * 0.03f * s else 0f
    drawOval(Color.Black.copy(alpha = 0.16f * (1f - light.night * 0.4f)), Offset(base.x - u * 0.1f * s, base.y - u * 0.02f), Size(u * 0.2f * s, u * 0.07f))

    if (v.kind == "pet") {
        val c = shade(Color(0xFFD98A4A), light)
        drawOval(c, Offset(base.x - u * 0.1f, base.y - u * 0.1f - bob), Size(u * 0.2f, u * 0.1f))
        drawCircle(c, u * 0.05f, Offset(base.x + (if (facingRight) 0.09f else -0.09f) * u, base.y - u * 0.13f - bob))
        drawLine(c, Offset(base.x - u * 0.1f, base.y - u * 0.08f), Offset(base.x - u * 0.16f, base.y - u * 0.14f), strokeWidth = u * 0.02f)
        return
    }
    // body
    val bh = u * 0.24f * s
    drawPath(Path().apply {
        moveTo(base.x - u * 0.07f * s, base.y); lineTo(base.x + u * 0.07f * s, base.y)
        lineTo(base.x + u * 0.09f * s, base.y - bh); lineTo(base.x - u * 0.09f * s, base.y - bh); close()
    }, shade(v.shirt, light))
    // head
    val hc = Offset(base.x, base.y - bh - u * 0.08f * s - bob)
    drawCircle(shade(Color(0xFFF0C9A4), light), u * 0.075f * s, hc)
    // hair
    drawPath(Path().apply {
        moveTo(hc.x - u * 0.08f * s, hc.y); cubicTo(hc.x - u * 0.09f * s, hc.y - u * 0.12f * s, hc.x + u * 0.09f * s, hc.y - u * 0.12f * s, hc.x + u * 0.08f * s, hc.y); close()
    }, shade(v.hair, light))
    if (v.kind == "elder") drawLine(shade(Color(0xFF8A6A44), light), Offset(base.x + u * 0.1f * s, base.y), Offset(base.x + u * 0.1f * s, base.y - bh - u * 0.05f), strokeWidth = u * 0.02f)
}

// ── flying life + weather ─────────────────────────────────────────────────

private fun DrawScope.drawFlyingLife(t: Float, light: Lighting, weather: Weather, season: Season, P: (Float, Float, Float) -> Offset, u: Float) {
    val w = size.width; val h = size.height
    // butterflies over the flowers by day
    if (light.night < 0.55f) for (i in 0 until 5) {
        val c = P(2f * sin(t / 2000f + i) , 2f * cos(t / 1700f + i * 2f), 0f)
        val bx = c.x + sin(t / 500f + i) * u * 0.4f; val by = c.y - u * 0.5f + cos(t / 400f + i) * u * 0.3f
        val col = listOf(Color(0xFFFFB84D), Color(0xFFFF7FB0), Color(0xFFB58BFF))[i % 3]
        val flap = abs(sin(t / 60f + i)) * u * 0.06f
        drawOval(col, Offset(bx - u * 0.06f, by - flap), Size(u * 0.06f, u * 0.1f))
        drawOval(col, Offset(bx, by - flap), Size(u * 0.06f, u * 0.1f))
    }
    // birds crossing by day
    if (light.night < 0.5f && weather != Weather.RAIN) for (b in 0 until 3) {
        val bp = (t / 6000f + b * 0.33f) % 1f
        val bx = w * (0.05f + 0.9f * bp); val by = h * (0.2f + 0.06f * b) + sin(t / 260f + b) * u * 0.15f
        val flap = (sin(t / 80f + b * 2f) + 1f) / 2f
        val c = Color(0xCC33333F)
        drawLine(c, Offset(bx - u * 0.14f, by + flap * u * 0.04f), Offset(bx, by - flap * u * 0.05f), strokeWidth = u * 0.03f)
        drawLine(c, Offset(bx, by - flap * u * 0.05f), Offset(bx + u * 0.14f, by + flap * u * 0.04f), strokeWidth = u * 0.03f)
    }
    // fireflies at night
    if (light.night > 0.6f) for (i in 0 until 18) {
        val fx = w * rnd(i, 101) + sin(t / 800f + i) * u * 0.5f
        val fy = h * (0.4f + 0.5f * rnd(i, 102)) + cos(t / 700f + i * 2f) * u * 0.5f
        val a = (0.4f + 0.6f * sin(t / 380f + i * 3f)).coerceIn(0f, 1f)
        drawCircle(Color(0xFFFFF3A0).copy(alpha = a * 0.9f), u * 0.05f, Offset(fx, fy))
        drawCircle(Color(0xFFFFF3A0).copy(alpha = a * 0.3f), u * 0.12f, Offset(fx, fy))
    }
    // rain
    if (weather == Weather.RAIN) for (i in 0 until 110) {
        val rx = (rnd(i, 111) * w + t / 5f) % w; val ry = (rnd(i, 112) * h + t / 1.1f) % h
        drawLine(Color(0xFFBFD8EC).copy(alpha = 0.5f), Offset(rx, ry), Offset(rx - u * 0.06f, ry + u * 0.3f), strokeWidth = 2f)
    }
    // snow
    if (weather == Weather.SNOW) for (i in 0 until 80) {
        val sx = (rnd(i, 111) * w + sin(t / 700f + i) * u * 0.4f) % w; val sy = (rnd(i, 112) * h + t / 11f) % h
        drawCircle(Color.White.copy(alpha = 0.85f), u * 0.045f, Offset(sx, sy))
    }
    // autumn leaves
    if (season == Season.AUTUMN && weather != Weather.SNOW) for (i in 0 until 16) {
        val lx = (rnd(i, 121) * w + sin(t / 500f + i) * u * 0.6f) % w; val ly = (rnd(i, 122) * h + t / 20f) % h
        drawOval(Color(0xFFD98A3A).copy(alpha = 0.85f), Offset(lx, ly), Size(u * 0.1f, u * 0.06f))
    }
}

// ═════════════════════════════════════════════════ fullscreen host

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorldScreen(snap: FarmState.Snapshot, onClose: () -> Unit) {
    var selected by remember { mutableStateOf<WorldTap?>(null) }
    val sheet = rememberModalBottomSheetState()

    Box(Modifier.fillMaxSize().background(Color(0xFF10193C))) {
        WorldDiorama(snap, Modifier.fillMaxSize(), onTapObject = { selected = it as? WorldTap })

        Column(Modifier.statusBarsPadding().padding(16.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassChip {
                    Text(snap.stageName, style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text("  ·  Lv ${snap.level}", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f))
                }
                Spacer(Modifier.weight(1f))
                GlassIcon(Icons.AutoMirrored.Rounded.ArrowBack, onClose)
            }
        }
        GlassChip(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 20.dp)) {
            Icon(Icons.Rounded.CenterFocusWeak, contentDescription = null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Pinch, drag, double-tap · tap anything", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.85f))
        }
    }

    selected?.let { o ->
        ModalBottomSheet(onDismissRequest = { selected = null }, sheetState = sheet, containerColor = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().padding(24.dp).navigationBarsPadding()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(o.emoji, fontSize = 40.sp)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(o.title, style = MaterialTheme.typography.headlineSmall)
                        Text(o.subtitle, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    }
                }
                Spacer(Modifier.height(16.dp))
                if (o.kind == "building") {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        InfoTile("Stage", listOf("Foundation", "Framing", "Walls", "Finished")[o.stage.coerceIn(0, 3)], Modifier.weight(1f))
                        InfoTile("Level", "${o.level}", Modifier.weight(1f))
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        InfoTile("Mood", o.mood.ifBlank { "😊" }, Modifier.weight(1f))
                        InfoTile("Today", o.contribution, Modifier.weight(2f))
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun GlassChip(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Row(
        modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.12f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(50)).padding(horizontal = 16.dp, vertical = 10.dp),
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
        Text(value, style = MaterialTheme.typography.titleMedium, color = Violet, maxLines = 2)
    }
}
