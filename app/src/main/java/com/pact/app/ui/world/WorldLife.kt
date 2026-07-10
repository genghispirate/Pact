package com.pact.app.ui.world

import androidx.compose.ui.geometry.Offset

// ═══════════════════════════════════════════════════════════════════════
//  WorldLife — DESIGN.md §7.8. Movement resolution for villagers/animals.
//  M2: waypoint routes with smoothstep. The real hourly schedule lands in M7.
// ═══════════════════════════════════════════════════════════════════════

/** Where a villager is right now on its route, and whether it faces right. */
internal fun villagerPos(v: Villager, t: Float): Pair<Offset, Boolean> {
    if (v.route.size < 2) return v.route.first() to true
    val loop = (t / 1000f * v.speed + v.phase) % 1f
    val seg = loop * v.route.size
    val i = seg.toInt() % v.route.size
    val f = seg - seg.toInt()
    val a = v.route[i]; val b = v.route[(i + 1) % v.route.size]
    val ease = f * f * (3 - 2 * f)
    val p = Offset(a.x + (b.x - a.x) * ease, a.y + (b.y - a.y) * ease)
    return p to (b.x >= a.x)
}
