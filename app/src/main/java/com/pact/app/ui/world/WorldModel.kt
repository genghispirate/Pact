package com.pact.app.ui.world

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.pact.app.core.FarmState
import kotlin.math.cos
import kotlin.math.sin

// ═══════════════════════════════════════════════════════════════════════
//  WorldModel — DESIGN.md §7.1/§7.2/§7.4. Pure layout: authored anchors,
//  level gating, snapshot → Layout. No drawing, no Compose state.
// ═══════════════════════════════════════════════════════════════════════

/** Deterministic hash-noise in [0,1). Only for grass/flowers/trees jitter. */
internal fun rnd(i: Int, s: Int = 0): Float {
    var h = i * 374761393 + s * 668265263 + 1274126177
    h = (h xor (h ushr 13)) * 1274126177
    return ((h xor (h ushr 16)) and 0x7fffffff) / 0x7fffffff.toFloat()
}

internal fun polar(angleDeg: Float, r: Float): Offset {
    val a = Math.toRadians(angleDeg.toDouble())
    return Offset((cos(a) * r).toFloat(), (sin(a) * r).toFloat())
}

// isometric projection to camera-independent units
internal fun nx(tx: Float, ty: Float) = (tx - ty) * 0.5f
internal fun ny(tx: Float, ty: Float, tz: Float) = (tx + ty) * 0.25f - tz * 0.42f

/** §7.2 — camera view radius per level tier. The user never controls it. */
fun viewRadiusForLevel(level: Int): Float = when {
    level <= 2 -> 5.2f
    level <= 5 -> 5.7f
    level <= 9 -> 6.2f
    level <= 14 -> 6.8f
    level <= 24 -> 7.4f
    level <= 39 -> 8.0f
    else -> 8.6f
}

internal data class Building(
    val id: String, val x: Float, val y: Float, val kind: String,
    val emoji: String, val title: String, val stage: Int, val level: Int,
)

internal data class Tree(val x: Float, val y: Float, val seed: Int, val pine: Boolean, val scale: Float)
internal data class Deco(val x: Float, val y: Float, val type: String, val seed: Int)

internal data class Villager(
    val id: String, val route: List<Offset>, val speed: Float, val phase: Float,
    val kind: String, val hair: Color, val shirt: Color,
    val name: String, val activity: String, val mood: String, val contribution: String,
)

internal data class Layout(
    val level: Int,
    val viewR: Float,          // camera radius (§7.2)
    val islandR: Float,        // land radius = viewR − 0.4
    val patches: List<Deco>,
    val flowers: List<Deco>,
    val rocks: List<Deco>,
    val trees: List<Tree>,
    val hills: List<Deco>,
    val buildings: List<Building>,
    val villagers: List<Villager>,
    val river: List<Offset>,
    val pond: Offset, val pondR: Float,
    val paths: List<List<Offset>>,
    val windmill: Offset?, val cottage: Offset?, val castle: Offset?, val campfire: Offset,
    val boats: Int, val festival: Boolean,
) {
    /** Cache key ingredient — changes only when the scene's static content changes. */
    val staticKey: Int = level * 31 + buildings.sumOf { it.stage * 7 + it.x.toInt() } +
        trees.size * 131 + (if (windmill != null) 1 else 0) + (if (cottage != null) 2 else 0) +
        (if (castle != null) 4 else 0)
}

// §7.4 authored anchor map — NEVER randomize these.
private val SITE_ANCHORS = mapOf(
    "library" to Offset(-2.6f, -1.4f),
    "gym" to Offset(2.4f, -1.8f),
    "temple" to Offset(-3.0f, 1.6f),
    "workshop" to Offset(2.8f, 1.4f),
    "bakery" to Offset(-1.2f, 2.6f),
    "school" to Offset(1.4f, 2.9f),
    "garden" to Offset(-2.2f, -3.2f),   // the fenced farm plot
    "moon" to Offset(0.6f, -3.4f),
)
internal val POND_ANCHOR = Offset(3.6f, 4.4f)
internal val CAMPFIRE_ANCHOR = Offset(1.2f, 1.6f)
private val COTTAGE_ANCHOR = Offset(2.6f, -3.0f)
private val WINDMILL_ANCHOR = Offset(-4.4f, 0.4f)
private val CASTLE_ANCHOR = Offset(-1.6f, -5.2f)
internal val HILL_ANCHORS = listOf(Offset(-4.0f, -4.6f), Offset(-1.6f, -5.6f), Offset(1.2f, -5.2f))
internal val RIVER_COURSE = listOf(
    Offset(-2.2f, -4.0f), Offset(-1.6f, -2.2f), Offset(-0.6f, -0.2f),
    Offset(0.8f, 1.8f), Offset(2.2f, 3.2f), POND_ANCHOR,
)
private val FLOWER_BEDS = listOf(Offset(-1.8f, 0.8f), Offset(1.8f, -0.6f), Offset(0.4f, 3.6f), Offset(-3.6f, 3.0f))
private val PATCH_ANCHORS = listOf(
    Offset(-3.2f, -0.6f), Offset(2.0f, 0.6f), Offset(-0.8f, -2.4f), Offset(3.2f, 2.6f),
    Offset(-2.4f, 3.4f), Offset(0.8f, -4.2f), Offset(4.2f, 0.2f), Offset(-4.6f, -2.6f), Offset(1.8f, 4.6f),
)
private val FOREST_CLUMP_ANGLES = listOf(100f, 120f, 140f, 160f, 340f, 355f, 10f, 25f)

private val VNAMES = listOf("Emma", "Noah", "Mila", "Theo", "Ava", "Leo", "Ivy", "Finn", "Rosa", "Sol", "Nina", "Kai")
private val ACTS = listOf(
    "Reading" to "Read by the library", "Fishing" to "Caught two fish", "Planting" to "Planted flowers",
    "Baking" to "Baked fresh bread", "Walking" to "Took a long walk", "Building" to "Raised a new wall",
    "Watering" to "Watered the crops", "Stargazing" to "Named a new star",
)

internal fun buildLayout(snap: FarmState.Snapshot): Layout {
    val level = snap.level
    val viewR = viewRadiusForLevel(level)
    val islandR = viewR - 0.4f
    val forestLvl = snap.structureLevel("forest")

    // buildings on their authored plots, construction stage from habit progress
    val buildings = ArrayList<Building>()
    val paths = ArrayList<List<Offset>>()
    SITE_ANCHORS.forEach { (id, p) ->
        val cat = FarmState.category(id)
        val pts = snap.categoryPoints[id] ?: 0
        if (pts <= 0 && id != "garden") return@forEach
        val lvl = snap.structureLevel(id)
        val stage = when {
            lvl >= 2 -> 3; lvl == 1 -> 2; pts >= FarmState.STRUCTURE_POINTS / 2 -> 1; else -> 0
        }
        buildings += Building("b_$id", p.x, p.y, id, cat.emoji, cat.structure, stage, lvl)
        val mid = Offset(p.x * 0.5f + p.y * 0.12f, p.y * 0.5f - p.x * 0.12f)
        paths += listOf(Offset.Zero, mid, p)
    }
    paths += listOf(Offset.Zero, Offset(1.8f, 2.4f), POND_ANCHOR)          // plaza → pond
    paths += listOf(Offset.Zero, Offset(0.7f, 0.9f), CAMPFIRE_ANCHOR)      // plaza → campfire

    // forest — authored clump directions, density (not radius) grows with level
    val trees = ArrayList<Tree>()
    val perClump = (2 + level / 6 + forestLvl).coerceIn(2, 5)
    FOREST_CLUMP_ANGLES.forEachIndexed { c, ang ->
        val baseR = islandR * (0.76f + rnd(c, 2) * 0.12f)
        for (k in 0 until perClump) {
            val jr = baseR + rnd(c * 7 + k, 4) * 0.9f - 0.35f
            val ja = ang + rnd(c * 7 + k, 5) * 20f - 10f
            val p = polar(ja, jr.coerceAtMost(islandR - 0.35f))
            trees += Tree(p.x, p.y, c * 31 + k, rnd(c * 7 + k, 6) > 0.62f, 0.78f + rnd(c * 7 + k, 8) * 0.45f)
        }
    }
    // a few lone trees for depth, seeded but kept off the plaza and plots
    for (i in 0 until 4) {
        val p = polar(55f + i * 87f, 3.6f + rnd(i, 12) * 0.8f)
        trees += Tree(p.x, p.y, 900 + i, false, 0.7f + rnd(i, 13) * 0.3f)
    }

    // ground decoration — authored patches, flower beds that grow with the garden
    val patches = PATCH_ANCHORS.mapIndexed { i, p -> Deco(p.x, p.y, "patch", i) }
    val flowers = ArrayList<Deco>()
    val bedCount = (1 + snap.structureLevel("garden") + snap.structureLevel("temple") / 2).coerceIn(1, FLOWER_BEDS.size)
    FLOWER_BEDS.take(bedCount).forEachIndexed { b, bed ->
        val n = 5 + (rnd(b, 33) * 4).toInt() + level / 8
        for (k in 0 until n.coerceAtMost(11)) {
            flowers += Deco(
                bed.x + (rnd(b * 9 + k, 34) - 0.5f) * 1.2f,
                bed.y + (rnd(b * 9 + k, 35) - 0.5f) * 0.9f, "flower", b * 9 + k,
            )
        }
    }
    val rocks = ArrayList<Deco>()
    for (i in 0 until 7) {
        val p = polar(35f + i * 51f, 2.6f + rnd(i, 42) * (islandR - 3.2f))
        rocks += Deco(p.x, p.y, if (rnd(i, 43) > 0.6f) "log" else "rock", i)
    }

    val hills = HILL_ANCHORS.mapIndexed { i, p -> Deco(p.x, p.y, "hill", i + 1) }

    // villagers — story-filled baseline, more with level (§7.8 schedules land in M7)
    val villagers = ArrayList<Villager>()
    fun v(id: String, route: List<Offset>, kind: String, hairI: Int, shirtI: Int, actI: Int, speed: Float, phase: Float): Villager {
        val hairs = listOf(Color(0xFF3A2A1C), Color(0xFF6B4A2A), Color(0xFFC9A24B), Color(0xFF9AA0A6), Color(0xFF2A2A2E))
        val shirts = listOf(Color(0xFF4C79C9), Color(0xFFE0774A), Color(0xFF4E9A46), Color(0xFF8A6BEE), Color(0xFFD95E86), Color(0xFF3E9B8E))
        val act = ACTS[actI % ACTS.size]
        val moods = listOf("😊", "🙂", "😌", "🥰")
        return Villager(
            id, route, speed, phase, kind, hairs[hairI % hairs.size], shirts[shirtI % shirts.size],
            VNAMES[(id.hashCode() and 0xffff) % VNAMES.size], act.first, moods[(id.hashCode() ushr 3) and 3], act.second,
        )
    }
    val ring = (0..7).map { polar(it * 45f, 1.9f) }
    villagers += v("couple_a", ring, "adult", 0, 0, 4, 0.05f, 0f)
    villagers += v("couple_b", ring, "adult", 2, 4, 4, 0.05f, 0.5f)
    villagers += v("child", listOf(Offset(0.3f, 0.3f), Offset(1.3f, -0.7f), Offset(-0.9f, -1.0f), Offset(-0.5f, 1.0f)), "child", 1, 1, 7, 0.12f, 0.2f)
    villagers += v("elder", listOf(Offset(-1.4f, 0.7f), Offset(-1.2f, 0.7f)), "elder", 3, 3, 6, 0.02f, 0f)
    villagers += v("fisher", listOf(POND_ANCHOR + Offset(-1.5f, -0.4f), POND_ANCHOR + Offset(-1.45f, -0.35f)), "adult", 4, 2, 1, 0.02f, 0f)
    villagers += v("pet", ring.map { it * 0.85f }, "pet", 1, 1, 4, 0.09f, 0.7f)
    buildings.firstOrNull { it.stage in 1..2 }?.let { b ->
        villagers += v("builder", listOf(Offset(b.x - 0.7f, b.y + 0.1f), Offset(b.x - 0.65f, b.y + 0.1f)), "adult", 0, 5, 5, 0f, 0f)
    }
    val extra = level.coerceIn(0, 6)
    val stops = buildings.map { Offset(it.x, it.y + 0.4f) }.ifEmpty { listOf(Offset(0f, 1f)) }
    for (i in 0 until extra) {
        val a = stops[i % stops.size]; val b = stops[(i + 2) % stops.size]
        villagers += v("wander$i", listOf(Offset.Zero, a, Offset.Zero, b), if (i % 3 == 0) "child" else "adult",
            i + 1, i + 2, i + 2, 0.035f + rnd(i, 51) * 0.03f, rnd(i, 52))
    }

    return Layout(
        level = level, viewR = viewR, islandR = islandR,
        patches = patches, flowers = flowers, rocks = rocks, trees = trees, hills = hills,
        buildings = buildings, villagers = villagers,
        river = RIVER_COURSE, pond = POND_ANCHOR, pondR = 1.3f, paths = paths,
        windmill = if (level >= 6) WINDMILL_ANCHOR else null,
        cottage = if (level >= 3) COTTAGE_ANCHOR else null,
        castle = if (level >= 14) CASTLE_ANCHOR else null,
        campfire = CAMPFIRE_ANCHOR,
        boats = if (level >= 22) 2 else if (level >= 10) 1 else 0,
        festival = level >= 30,
    )
}
