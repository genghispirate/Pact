package com.pact.app.ui.world

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.pact.app.core.FarmState

// ═══════════════════════════════════════════════════════════════════════
//  WorldModel — DESIGN.md §7-B. A dense, rectangular, top-down tile
//  village. Coordinates are in TILES (18 cols × 20 authored rows).
// ═══════════════════════════════════════════════════════════════════════

const val MAP_COLS = 18
const val MAP_ROWS = 20

internal fun rnd(i: Int, s: Int = 0): Float {
    var h = i * 374761393 + s * 668265263 + 1274126177
    h = (h xor (h ushr 13)) * 1274126177
    return ((h xor (h ushr 16)) and 0x7fffffff) / 0x7fffffff.toFloat()
}

internal data class Building(
    val id: String, val x: Float, val y: Float, val kind: String,
    val emoji: String, val title: String, val stage: Int, val level: Int,
)

internal data class Tree(val x: Float, val y: Float, val seed: Int, val kind: Int, val scale: Float) // kind 0 green 1 autumn 2 pine

internal data class Deco(val x: Float, val y: Float, val type: String, val seed: Int)

internal data class Villager(
    val id: String, val route: List<Offset>, val speed: Float, val phase: Float,
    val kind: String, val hair: Color, val shirt: Color,
    val name: String, val activity: String, val mood: String, val contribution: String,
)

internal data class Layout(
    val level: Int,
    val flowers: List<Deco>,
    val trees: List<Tree>,
    val buildings: List<Building>,
    val villagers: List<Villager>,
    val pondA: Offset, val pondB: Offset,          // tile-rect centers (see renderer sizes)
    val paths: List<List<Offset>>,                 // dotted stone paths, tile coords
    val windmill: Offset?, val cottage: Offset?, val castle: Offset?, val campfire: Offset,
    val boats: Int, val festival: Boolean,
) {
    val staticKey: Int = level * 31 + buildings.sumOf { it.stage * 7 + (it.x * 10).toInt() } +
        trees.size * 131 + (if (windmill != null) 1 else 0) + (if (cottage != null) 2 else 0) +
        (if (castle != null) 4 else 0)
}

// §7-B authored anchors (tile coords; x = col center, y = base row)
private val SITE_ANCHORS = mapOf(
    "library" to Offset(1.9f, 4.3f),
    "bakery" to Offset(4.8f, 4.3f),
    "workshop" to Offset(7.7f, 4.3f),
    "school" to Offset(10.6f, 4.3f),
    "gym" to Offset(2.0f, 15.2f),
    "temple" to Offset(5.2f, 15.2f),
    "moon" to Offset(16.2f, 11.0f),
    "garden" to Offset(3.6f, 9.6f),      // scarecrow marker inside field A
)
internal val POND_A = Offset(3.5f, 11.0f)      // in field A, 2×3 rounded
internal val POND_B = Offset(14.0f, 13.2f)     // meadow pond, boats live here
internal val CAMPFIRE_ANCHOR = Offset(10.0f, 14.3f)
private val COTTAGE_ANCHOR = Offset(8.0f, 15.2f)
private val WINDMILL_ANCHOR = Offset(15.0f, 9.9f)
private val CASTLE_ANCHOR = Offset(16.0f, 3.4f)
internal val PLAZA = intArrayOf(12, 0, 17, 5)  // colMin,rowMin,colMax,rowMax
internal val FIELD_A = intArrayOf(1, 7, 6, 12)
internal val FIELD_B = intArrayOf(8, 7, 12, 12)
internal val WHEAT = intArrayOf(13, 7, 16, 10)
internal val ROAD_ROWS = intArrayOf(16, 17)

private val VNAMES = listOf("Emma", "Noah", "Mila", "Theo", "Ava", "Leo", "Ivy", "Finn", "Rosa", "Sol", "Nina", "Kai")
private val ACTS = listOf(
    "Reading" to "Read by the library", "Fishing" to "Caught two fish", "Planting" to "Planted flowers",
    "Baking" to "Baked fresh bread", "Walking" to "Took a long walk", "Building" to "Raised a new wall",
    "Watering" to "Watered the crops", "Stargazing" to "Named a new star",
)

internal fun buildLayout(snap: FarmState.Snapshot): Layout {
    val level = snap.level
    val forestLvl = snap.structureLevel("forest")

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
        if (p.y < 6f) paths += listOf(Offset(p.x, p.y + 0.3f), Offset(p.x, 5.5f))   // spur to main path
    }
    // main dotted path row 5.5, vertical spine col 11.5 down to the road, spur into plaza
    paths += listOf(Offset(0.5f, 5.5f), Offset(17.5f, 5.5f))
    paths += listOf(Offset(11.5f, 5.5f), Offset(11.5f, 16.2f))
    paths += listOf(Offset(13.0f, 5.5f), Offset(14.5f, 3.4f))

    // trees — orchard cluster left, road line, fringe; density grows with level+walking
    val trees = ArrayList<Tree>()
    fun tree(x: Float, y: Float, seed: Int) {
        trees += Tree(x, y, seed, (rnd(seed, 6) * 3f).toInt().coerceIn(0, 2), 0.85f + rnd(seed, 8) * 0.35f)
    }
    val orchard = listOf(0.9f to 1.2f, 2.3f to 0.8f, 0.8f to 3.0f, 2.0f to 2.4f, 0.9f to 6.6f, 1.8f to 6.2f)
    val roadline = listOf(3.0f to 15.6f, 6.5f to 15.7f, 12.6f to 15.5f, 16.8f to 15.6f)
    val fringe = listOf(1.5f to 18.8f, 4.5f to 19.2f, 7.5f to 18.7f, 10.5f to 19.1f, 13.5f to 18.8f, 16.5f to 19.2f)
    val extraSpots = listOf(6.8f to 6.4f, 12.4f to 6.5f, 17.2f to 7.5f, 0.7f to 13.5f, 7.2f to 13.2f, 17.3f to 16.8f)
    val budget = (6 + level + forestLvl * 2).coerceAtMost(orchard.size + roadline.size + fringe.size + extraSpots.size)
    (orchard + roadline + fringe + extraSpots).take(budget).forEachIndexed { i, p -> tree(p.first, p.second, i * 31) }

    // flowers — meadow rows 13–15 & plaza edge; count grows with garden/temple levels
    val flowers = ArrayList<Deco>()
    val beds = 6 + (snap.structureLevel("garden") + snap.structureLevel("temple")) * 4 + level
    for (k in 0 until beds.coerceAtMost(26)) {
        val zone = k % 3
        val fx: Float; val fy: Float
        when (zone) {
            0 -> { fx = 0.6f + rnd(k, 34) * 9f; fy = 13.2f + rnd(k, 35) * 2.2f }        // meadow
            1 -> { fx = 12.3f + rnd(k, 36) * 5f; fy = 0.4f + rnd(k, 37) * 1.2f }        // plaza rim
            else -> { fx = 12.6f + rnd(k, 38) * 4.6f; fy = 14.2f + rnd(k, 39) * 1.2f }  // pond side
        }
        flowers += Deco(fx, fy, "flower", k)
    }

    // villagers — routes in tile coords
    val villagers = ArrayList<Villager>()
    fun v(id: String, route: List<Offset>, kind: String, hairI: Int, shirtI: Int, actI: Int, speed: Float, phase: Float): Villager {
        val hairs = listOf(Color(0xFF3A2A1C), Color(0xFF6B4A2A), Color(0xFFC9A24B), Color(0xFF9AA0A6), Color(0xFF2A2A2E))
        val shirts = listOf(Color(0xFF4C79C9), Color(0xFFE0774A), Color(0xFF4E9A46), Color(0xFF8A6BEE), Color(0xFFD95E86), Color(0xFF3E9B8E))
        val act = ACTS[actI % ACTS.size]
        val moods = listOf("😊", "🙂", "😌", "🥰")
        return Villager(id, route, speed, phase, kind, hairs[hairI % hairs.size], shirts[shirtI % shirts.size],
            VNAMES[(id.hashCode() and 0xffff) % VNAMES.size], act.first, moods[(id.hashCode() ushr 3) and 3], act.second)
    }
    // farmer waters field B row by row (the reference's heart)
    villagers += v("farmer", listOf(Offset(8.5f, 8.0f), Offset(11.8f, 8.0f), Offset(11.8f, 9.6f), Offset(8.5f, 9.6f), Offset(8.5f, 11.2f), Offset(11.8f, 11.2f)), "adult", 1, 2, 6, 0.05f, 0f)
    // couple strolls the main path
    villagers += v("couple_a", listOf(Offset(1.0f, 5.5f), Offset(11.0f, 5.5f)), "adult", 0, 0, 4, 0.035f, 0f)
    villagers += v("couple_b", listOf(Offset(1.6f, 5.6f), Offset(11.6f, 5.6f)), "adult", 2, 4, 4, 0.035f, 0.04f)
    // child darts around the meadow; elder by the plaza fountain; fisher at pond B
    villagers += v("child", listOf(Offset(8.6f, 13.6f), Offset(10.8f, 14.6f), Offset(9.2f, 15.0f), Offset(7.8f, 14.2f)), "child", 1, 1, 7, 0.11f, 0.2f)
    villagers += v("elder", listOf(Offset(13.6f, 2.6f), Offset(13.8f, 2.6f)), "elder", 3, 3, 6, 0.015f, 0f)
    villagers += v("fisher", listOf(POND_B + Offset(-1.4f, 0.2f), POND_B + Offset(-1.35f, 0.22f)), "adult", 4, 2, 1, 0.015f, 0f)
    villagers += v("pet", listOf(Offset(2.0f, 16.5f), Offset(15.5f, 16.5f)), "pet", 1, 1, 4, 0.06f, 0.5f)
    buildings.firstOrNull { it.stage in 1..2 }?.let { b ->
        villagers += v("builder", listOf(Offset(b.x - 1.2f, b.y - 0.1f), Offset(b.x - 1.15f, b.y - 0.1f)), "adult", 0, 5, 5, 0f, 0f)
    }
    val extra = level.coerceIn(0, 5)
    for (i in 0 until extra) {
        val a = Offset(1f + rnd(i, 51) * 15f, 5.5f); val b = Offset(11.5f, 7f + rnd(i, 52) * 8f)
        villagers += v("wander$i", listOf(a, b, a), if (i % 3 == 0) "child" else "adult", i + 1, i + 2, i + 2, 0.03f + rnd(i, 53) * 0.03f, rnd(i, 54))
    }

    return Layout(
        level = level, flowers = flowers, trees = trees, buildings = buildings, villagers = villagers,
        pondA = POND_A, pondB = POND_B, paths = paths,
        windmill = if (level >= 6) WINDMILL_ANCHOR else null,
        cottage = if (level >= 3) COTTAGE_ANCHOR else null,
        castle = if (level >= 14) CASTLE_ANCHOR else null,
        campfire = CAMPFIRE_ANCHOR,
        boats = if (level >= 22) 2 else if (level >= 10) 1 else 0,
        festival = level >= 30,
    )
}
