package com.pact.app.ui.world

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

// ═══════════════════════════════════════════════════════════════════════
//  WorldRenderer — DESIGN.md §7-B. Dense top-down tile village, chunky
//  flat-color details, no outlines. P maps tile coords → screen px.
// ═══════════════════════════════════════════════════════════════════════

internal typealias Proj = (Float, Float, Float) -> Offset

private fun inZone(c: Int, r: Int, z: IntArray) = c >= z[0] && c <= z[2] && r >= z[1] && r <= z[3]

// ═════════════════════════════ STATIC GROUND (cached bitmap, §7.11) ═════

internal fun DrawScope.drawGroundStatic(layout: Layout, light: Lighting, season: Season, P: Proj, tile: Float) {
    val rowLo = -2
    val rowHi = MAP_ROWS + ceil(size.height / tile).toInt()

    // ── base tiles with per-tile variation (never uniform)
    for (r in rowLo..rowHi) {
        val zr = r.coerceIn(0, MAP_ROWS - 1)
        for (c in 0 until MAP_COLS) {
            val o = P(c.toFloat(), r.toFloat(), 0f)
            val v = rnd(c * 57 + r, 3)
            val base: Color = when {
                inZone(c, zr, PLAZA) -> lerp(Color(0xFFCFC3A6), Color(0xFFBFB294), v)
                zr in ROAD_ROWS[0]..ROAD_ROWS[1] -> lerp(Color(0xFFC9A868), Color(0xFFB8935A), v)
                inZone(c, zr, FIELD_A) || inZone(c, zr, FIELD_B) -> lerp(Color(0xFF5C4328), Color(0xFF4E3820), v)
                inZone(c, zr, WHEAT) -> lerp(Color(0xFFE3B84E), Color(0xFFD1A140), v)
                else -> listOf(Color(0xFF6FBF52), Color(0xFF67B54B), Color(0xFF74C558), Color(0xFF5FAE45))[(v * 4).toInt().coerceAtMost(3)]
            }
            drawRect(grass(base, light, season), o, Size(tile + 0.6f, tile + 0.6f))
            // 1-in-6 grass tiles get a tiny detail
            if (!inZone(c, zr, PLAZA) && zr !in ROAD_ROWS[0]..ROAD_ROWS[1] &&
                !inZone(c, zr, FIELD_A) && !inZone(c, zr, FIELD_B) && !inZone(c, zr, WHEAT) && rnd(c * 91 + r, 9) < 0.17f
            ) {
                val q = tile / 4f
                val d = rnd(c * 13 + r, 10)
                val dc = grass(Color(0xFF4E9A32), light, season)
                when {
                    d < 0.5f -> { // tuft
                        drawRect(dc, o + Offset(q, q * 1.6f), Size(q * 0.5f, q * 1.2f))
                        drawRect(dc, o + Offset(q * 2f, q * 1.2f), Size(q * 0.5f, q * 1.6f))
                    }
                    d < 0.8f -> drawCircle(shade(listOf(Color(0xFFFF8FB3), Color(0xFFFFD24B), Color(0xFFB58BFF))[(d * 10).toInt() % 3], light), q * 0.5f, o + Offset(q * 2f, q * 2f))
                    else -> drawCircle(shade(Color(0xFF9AA0A6), light), q * 0.55f, o + Offset(q * 2f, q * 2.4f))
                }
            }
        }
    }

    val q = tile / 4f

    // ── fields: furrows + crops (A) and seedling rows (B), wheat texture
    for (fld in listOf(FIELD_A, FIELD_B)) {
        for (r in fld[1]..fld[3]) {
            val o = P(fld[0].toFloat(), r + 0.72f, 0f)
            drawRect(Color.Black.copy(alpha = 0.14f), o, Size((fld[2] - fld[0] + 1) * tile, q * 0.5f))
        }
    }
    for (r in FIELD_A[1]..FIELD_A[3]) for (c in FIELD_A[0]..FIELD_A[2]) {
        if (rnd(c * 7 + r, 21) < 0.75f) {
            val o = P(c + 0.5f, r + 0.45f, 0f)
            val g = shade(Color(0xFF57C23A), light)
            drawRect(g, o + Offset(-q * 0.25f, -q), Size(q * 0.5f, q))
            drawRect(g, o + Offset(-q * 0.85f, -q * 0.5f), Size(q * 0.5f, q * 0.5f))
            drawRect(g, o + Offset(q * 0.35f, -q * 0.5f), Size(q * 0.5f, q * 0.5f))
        }
    }
    for (r in FIELD_B[1]..FIELD_B[3]) for (c in FIELD_B[0]..FIELD_B[2]) {
        if (rnd(c * 11 + r, 22) < 0.6f) drawRect(shade(Color(0xFF6FCF4F), light), P(c + 0.4f, r + 0.35f, 0f), Size(q * 0.6f, q * 0.9f))
    }
    for (r in WHEAT[1]..WHEAT[3]) for (c in WHEAT[0]..WHEAT[2]) {
        val o = P(c.toFloat(), r.toFloat(), 0f)
        drawRect(shade(Color(0xFFC08F2E), light), o + Offset(q * (0.6f + rnd(c + r, 23) * 2f), q * 0.4f), Size(q * 0.4f, q * 3.2f))
        drawRect(shade(Color(0xFFF0CB6A), light), o + Offset(q * (0.6f + rnd(c + r, 23) * 2f) - q * 0.2f, q * 0.2f), Size(q * 0.8f, q * 0.7f))
    }

    // ── plaza: stone grid, hedge ring, two lamps, fountain base
    for (r in PLAZA[1]..PLAZA[3]) for (c in PLAZA[0]..PLAZA[2]) {
        val o = P(c.toFloat(), r.toFloat(), 0f)
        drawRect(Color.Black.copy(alpha = 0.07f), o + Offset(0f, tile - 1.5f), Size(tile, 1.5f))
        drawRect(Color.Black.copy(alpha = 0.07f), o + Offset(tile - 1.5f, 0f), Size(1.5f, tile))
    }
    val hedge = shade(Color(0xFF3E8B3A), light)
    for (c in PLAZA[0]..PLAZA[2] step 2) {
        drawRoundRect(hedge, P(c + 0.15f, PLAZA[1] + 0.15f, 0f), Size(tile * 0.7f, tile * 0.7f), CornerRadius(q))
        drawRoundRect(hedge, P(c + 0.15f, PLAZA[3] - 0.85f + 1f, 0f), Size(tile * 0.7f, tile * 0.7f), CornerRadius(q))
    }
    // fountain base 2×2 at plaza center
    val fc = P(14.5f, 2.5f, 0f)
    drawRoundRect(shade(Color(0xFF8F97A0), light), fc + Offset(-tile, -tile * 0.9f), Size(tile * 2f, tile * 1.8f), CornerRadius(q * 1.5f))
    drawRoundRect(shade(Color(0xFF57B0EC), light), fc + Offset(-tile * 0.72f, -tile * 0.62f), Size(tile * 1.44f, tile * 1.24f), CornerRadius(q))

    // ── ponds: sand ring + two-tone water, rounded
    fun pond(center: Offset, wT: Float, hT: Float) {
        val pc = P(center.x, center.y, 0f)
        drawRoundRect(shade(Color(0xFFD8C79A), light), pc + Offset(-wT * tile / 2 - q * 0.7f, -hT * tile / 2 - q * 0.7f),
            Size(wT * tile + q * 1.4f, hT * tile + q * 1.4f), CornerRadius(tile * 0.5f))
        drawRoundRect(shade(Color(0xFF2C7BD1), light), pc + Offset(-wT * tile / 2, -hT * tile / 2), Size(wT * tile, hT * tile), CornerRadius(tile * 0.45f))
        drawRoundRect(shade(Color(0xFF4AA6E8), light), pc + Offset(-wT * tile / 2 + q, -hT * tile / 2 + q), Size(wT * tile - 2 * q, hT * tile - 2 * q), CornerRadius(tile * 0.4f))
        drawOval(shade(Color(0xFF5BAE58), light), pc + Offset(wT * tile * 0.16f, -q), Size(q * 1.6f, q))
    }
    pond(layout.pondA, 2f, 3f)
    pond(layout.pondB, 3f, 1.8f)

    // ── dotted stone paths
    layout.paths.forEach { pts ->
        for (i in 0 until pts.size - 1) {
            val a = pts[i]; val b = pts[i + 1]
            val len = hypot(b.x - a.x, b.y - a.y)
            val steps = (len / 0.7f).toInt().coerceAtLeast(1)
            for (s in 0..steps) {
                val f = s / steps.toFloat()
                val p = P(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f, 0f)
                drawOval(shade(Color(0xFFD9CDB0), light), p + Offset(-q * 1.1f, -q * 0.8f), Size(q * 2.2f, q * 1.6f))
            }
        }
    }

    // ── fences: field B perimeter + road sides
    val post = shade(Color(0xFF9A7A50), light); val rail = shade(Color(0xFF8A6A44), light)
    fun fenceH(cLo: Int, cHi: Int, y: Float) {
        drawRect(rail, P(cLo.toFloat(), y, 0f) + Offset(0f, -q * 0.5f), Size((cHi - cLo + 1) * tile, q * 0.4f))
        for (c in cLo..cHi) drawRect(post, P(c + 0.4f, y, 0f) + Offset(0f, -q * 1.4f), Size(q * 0.6f, q * 1.8f))
    }
    fenceH(FIELD_B[0], FIELD_B[2], FIELD_B[1].toFloat())
    fenceH(FIELD_B[0], FIELD_B[2], FIELD_B[3] + 1f)
    fenceH(0, MAP_COLS - 1, ROAD_ROWS[0].toFloat())
    fenceH(0, MAP_COLS - 1, ROAD_ROWS[1] + 1f)
}

// ═════════════════════════════════ DYNAMIC LAYERS ═══════════════════════

/** No sky in a top-down world — clouds read as soft drifting shadows. */
internal fun DrawScope.drawCloudShadows(t: Float, tile: Float) {
    for (i in 0 until 3) {
        val cx = ((t / 140f + i * 420f) % (size.width + tile * 8f)) - tile * 4f
        val cy = size.height * (0.15f + 0.3f * i)
        drawOval(Color.Black.copy(alpha = 0.05f), Offset(cx - tile * 3f, cy - tile * 1.2f), Size(tile * 6f, tile * 2.4f))
        drawOval(Color.Black.copy(alpha = 0.05f), Offset(cx - tile * 1.4f, cy - tile * 1.9f), Size(tile * 3.6f, tile * 2.2f))
    }
}

internal fun DrawScope.drawWaterLife(layout: Layout, light: Lighting, t: Float, P: Proj, tile: Float) {
    // fountain jet
    val fc = P(14.5f, 2.5f, 0f)
    for (k in 0 until 3) {
        val pr = ((t / 900f) + k / 3f) % 1f
        drawCircle(Color(0xFFBFE6FF).copy(alpha = (1f - pr) * 0.7f), tile * 0.06f, fc + Offset((k - 1) * tile * 0.14f, -tile * 0.5f + pr * tile * 0.4f))
    }
    drawCircle(shade(Color(0xFFB6BCC4), light), tile * 0.14f, fc + Offset(0f, -tile * 0.28f))
    // pond ripples + ducks + boats (pond B), sparkle on pond A
    val pb = P(layout.pondB.x, layout.pondB.y, 0f)
    for (k in 0 until 2) {
        val pr = ((t / 2600f) + k / 2f) % 1f
        drawOval(Color.White.copy(alpha = (1f - pr) * 0.3f), pb + Offset(-tile * 1.2f * pr, -tile * 0.7f * pr), Size(tile * 2.4f * pr, tile * 1.4f * pr), style = Stroke(2f))
    }
    for (d in 0 until 2) {
        val dx = pb.x + sin(t / 1400f + d * 3f) * tile * 0.8f
        val dy = pb.y + cos(t / 1700f + d * 2f) * tile * 0.4f
        drawOval(shade(Color(0xFFF4F1E8), light), Offset(dx - tile * 0.16f, dy - tile * 0.1f), Size(tile * 0.32f, tile * 0.2f))
        drawCircle(shade(Color(0xFFF4F1E8), light), tile * 0.08f, Offset(dx + tile * 0.14f, dy - tile * 0.14f))
        drawCircle(shade(Color(0xFFF2B84D), light), tile * 0.032f, Offset(dx + tile * 0.2f, dy - tile * 0.14f))
    }
    for (bi in 0 until layout.boats) {
        val bx = pb.x + (bi - 0.5f) * tile * 1.1f + sin(t / 1800f + bi) * tile * 0.15f
        val by = pb.y - tile * 0.1f
        drawOval(shade(Color(0xFF8A5A34), light), Offset(bx - tile * 0.3f, by - tile * 0.1f), Size(tile * 0.6f, tile * 0.22f))
        drawLine(shade(Color(0xFF6E4A2A), light), Offset(bx, by - tile * 0.1f), Offset(bx, by - tile * 0.55f), strokeWidth = tile * 0.04f)
        drawPath(Path().apply { moveTo(bx, by - tile * 0.55f); lineTo(bx + tile * 0.22f, by - tile * 0.22f); lineTo(bx, by - tile * 0.22f); close() },
            shade(Color(0xFFF2E4C6), light))
    }
    val pa = P(layout.pondA.x, layout.pondA.y, 0f)
    if (sin(t / 420f) > 0.4f) drawCircle(Color.White.copy(alpha = 0.35f * light.brightness), tile * 0.07f, pa + Offset(tile * 0.3f, -tile * 0.4f))
}

internal fun DrawScope.drawFlowers(layout: Layout, light: Lighting, t: Float, P: Proj, tile: Float) {
    layout.flowers.forEach { f ->
        val c = P(f.x, f.y, 0f)
        val bounce = (sin(t / 260f + f.seed) + 1f) * tile * 0.03f
        val col = shade(listOf(Color(0xFFFF6FA6), Color(0xFFFFD24B), Color(0xFF6FA8FF), Color(0xFFB58BFF), Color(0xFFFF9B57))[f.seed % 5], light)
        drawLine(shade(Color(0xFF4E9A32), light), c, c - Offset(0f, tile * 0.22f), strokeWidth = tile * 0.05f)
        drawCircle(col, tile * 0.11f, c - Offset(0f, tile * 0.28f + bounce))
        drawCircle(shade(Color(0xFFFFF3B0), light), tile * 0.04f, c - Offset(0f, tile * 0.28f + bounce))
    }
}

// ── upright objects (front-facing sprites, y-sorted) ─────────────────────

internal fun DrawScope.drawTree(base: Offset, tile: Float, t: Float, light: Lighting, season: Season, seed: Int, kind: Int, scale: Float) {
    val s = tile * scale
    val sway = sin(t / 760f + seed) * s * 0.08f
    drawOval(Color.Black.copy(alpha = 0.16f * (1f - light.night * 0.4f)), base + Offset(-s * 0.7f, -s * 0.18f), Size(s * 1.4f, s * 0.45f))
    drawRect(shade(Color(0xFF6E4A2A), light), base + Offset(-s * 0.12f, -s * 0.9f), Size(s * 0.24f, s * 0.9f))
    val leaf = when (kind) {
        1 -> Color(0xFFE0862F)
        2 -> lerp(Color(0xFF2E7D5B), foliage(season), 0.25f)
        else -> foliage(season)
    }
    if (kind == 2) {
        for (tier in 0 until 3) {
            val ty = base.y - s * (0.8f + tier * 0.55f); val tw = s * (0.85f - tier * 0.22f)
            drawPath(Path().apply {
                moveTo(base.x + sway, ty - s * 0.65f); lineTo(base.x - tw + sway, ty); lineTo(base.x + tw + sway, ty); close()
            }, shade(darker(leaf, 0.92f - tier * 0.06f), light))
        }
    } else {
        val cx = base.x + sway; val cy = base.y - s * 1.5f
        drawCircle(shade(darker(leaf, 0.75f), light), s * 0.78f, Offset(cx + s * 0.14f, cy + s * 0.2f))
        drawCircle(shade(leaf, light), s * 0.7f, Offset(cx - s * 0.08f, cy))
        drawCircle(shade(lerp(leaf, Color.White, 0.18f), light), s * 0.42f, Offset(cx - s * 0.24f, cy - s * 0.3f))
        if (kind == 1) for (k in 0 until 3) drawCircle(shade(Color(0xFFC85A3A), light), s * 0.08f,
            Offset(cx + (rnd(seed + k, 92) - 0.5f) * s, cy + (rnd(seed + k, 93) - 0.5f) * s))
        if (season == Season.SPRING && kind == 0) for (k in 0 until 4) drawCircle(shade(Color(0xFFFFC0DA), light), s * 0.07f,
            Offset(cx + (rnd(seed + k, 94) - 0.5f) * s, cy + (rnd(seed + k, 95) - 0.5f) * s))
    }
}

private fun roofColor(kind: String) = when (kind) {
    "library" -> Color(0xFF5C79C0); "gym" -> Color(0xFFD95E54); "temple" -> Color(0xFF8A6BEE)
    "workshop" -> Color(0xFF3E9B8E); "bakery" -> Color(0xFFC97C4A); "school" -> Color(0xFF5BA24C)
    "moon" -> Color(0xFF6A6BD0); else -> Color(0xFFC0503F)
}

/** Front-facing chunky house, 2.6×2.2 tiles. Identity = roof colour + prop; no emoji. */
internal fun DrawScope.drawBuilding(b: Building, base: Offset, tile: Float, t: Float, light: Lighting) {
    if (b.kind == "garden") { // scarecrow marks the farm field
        drawLine(shade(Color(0xFF8A6A44), light), base, base - Offset(0f, tile * 0.9f), strokeWidth = tile * 0.07f)
        drawLine(shade(Color(0xFF8A6A44), light), base - Offset(tile * 0.3f, tile * 0.62f), base - Offset(-tile * 0.3f, tile * 0.62f), strokeWidth = tile * 0.07f)
        drawCircle(shade(Color(0xFFD9B36A), light), tile * 0.14f, base - Offset(0f, tile * 1.0f))
        return
    }
    val bw = tile * 2.6f
    drawOval(Color.Black.copy(alpha = 0.16f * (1f - light.night * 0.4f)), base + Offset(-bw / 2, -tile * 0.16f), Size(bw, tile * 0.42f))
    if (b.stage == 0) {
        drawRoundRect(shade(Color(0xFF9AA0A6), light), base + Offset(-bw / 2, -tile * 0.5f), Size(bw, tile * 0.5f), CornerRadius(tile * 0.1f))
        for (sx in listOf(-1f, 1f)) drawRect(shade(Color(0xFF8A6A44), light), base + Offset(sx * bw * 0.42f, -tile * 1.4f), Size(tile * 0.14f, tile * 1.1f))
        return
    }
    val wallH = tile * 1.3f
    val fy = base.y - wallH
    drawRect(shade(Color(0xFFF2E4C6), light), Offset(base.x - bw / 2, fy), Size(bw, wallH))
    if (b.stage == 1) {
        drawRect(shade(Color(0xFF9A7A50), light), Offset(base.x - bw / 2, fy), Size(bw, wallH), style = Stroke(tile * 0.09f))
        return
    }
    val roof = roofColor(b.kind)
    drawRoundRect(shade(roof, light), Offset(base.x - bw / 2 - tile * 0.18f, fy - tile * 0.9f), Size(bw + tile * 0.36f, tile * 1.0f), CornerRadius(tile * 0.16f))
    drawRect(shade(darker(roof, 0.82f), light), Offset(base.x - bw / 2 - tile * 0.18f, fy - tile * 0.16f), Size(bw + tile * 0.36f, tile * 0.16f))
    drawRect(shade(lerp(roof, Color.White, 0.2f), light), Offset(base.x - bw / 2, fy - tile * 0.86f), Size(bw * 0.4f, tile * 0.14f))
    // door (villagers fit) + warm windows
    drawRoundRect(shade(Color(0xFF6B4A2C), light), Offset(base.x - tile * 0.24f, base.y - tile * 0.78f), Size(tile * 0.48f, tile * 0.78f), CornerRadius(tile * 0.1f))
    val glow = lerp(Color(0xFF9AD0EE), Color(0xFFFFD98A), light.night)
    for (wx in listOf(-0.75f, 0.75f)) {
        drawRoundRect(glow, Offset(base.x + wx * tile - tile * 0.18f, fy + tile * 0.28f), Size(tile * 0.36f, tile * 0.36f), CornerRadius(tile * 0.06f))
        if (light.night > 0.35f) drawCircle(Color(0xFFFFD98A).copy(alpha = light.night * 0.3f), tile * 0.35f, Offset(base.x + wx * tile, fy + tile * 0.46f))
    }
    when (b.kind) {
        "bakery" -> {
            drawRect(shade(Color(0xFFB07A50), light), Offset(base.x + bw * 0.28f, fy - tile * 1.3f), Size(tile * 0.26f, tile * 0.5f))
            for (k in 0 until 3) {
                val pr = ((t / 1200f) + k / 3f) % 1f
                drawCircle(Color(0xFFE8E1D6).copy(alpha = (1f - pr) * 0.5f), tile * (0.09f + pr * 0.14f), Offset(base.x + bw * 0.33f, fy - tile * 1.3f - pr * tile * 0.9f))
            }
        }
        "library" -> for (k in 0 until 4) drawCircle(shade(Color(0xFF4E8B32), light), tile * 0.08f, Offset(base.x - bw / 2 + tile * 0.12f, fy + tile * 0.15f + k * tile * 0.26f))
        "moon" -> if (light.night > 0.3f) drawCircle(Color(0xFFBFC6FF).copy(alpha = light.night * 0.35f), tile * 0.7f, Offset(base.x, fy - tile * 0.4f))
    }
}

internal fun DrawScope.drawWindmill(base: Offset, u: Float, t: Float, light: Lighting) {
    drawOval(Color.Black.copy(alpha = 0.15f), Offset(base.x - u * 0.32f, base.y - u * 0.08f), Size(u * 0.64f, u * 0.22f))
    drawPath(Path().apply {
        moveTo(base.x - u * 0.26f, base.y); lineTo(base.x + u * 0.26f, base.y)
        lineTo(base.x + u * 0.15f, base.y - u * 1.15f); lineTo(base.x - u * 0.15f, base.y - u * 1.15f); close()
    }, shade(Color(0xFFEDE0C4), light))
    drawPath(Path().apply {
        moveTo(base.x - u * 0.15f, base.y - u * 1.15f); lineTo(base.x + u * 0.15f, base.y - u * 1.15f); lineTo(base.x, base.y - u * 1.4f); close()
    }, shade(Color(0xFF9A5A3A), light))
    val hub = Offset(base.x, base.y - u * 1.1f)
    for (b in 0 until 4) {
        val a = t / 900f + b * (Math.PI / 2)
        val tip = Offset(hub.x + (cos(a) * u * 0.5f).toFloat(), hub.y + (sin(a) * u * 0.5f).toFloat())
        val side = Offset(hub.x + (cos(a + 0.3) * u * 0.14f).toFloat(), hub.y + (sin(a + 0.3) * u * 0.14f).toFloat())
        drawPath(Path().apply { moveTo(hub.x, hub.y); lineTo(tip.x, tip.y); lineTo(side.x, side.y); close() }, shade(Color(0xFFF2E8D2), light))
    }
    drawCircle(shade(Color(0xFF6E4A2A), light), u * 0.05f, hub)
}

internal fun DrawScope.drawCottage(base: Offset, u: Float, t: Float, light: Lighting) {
    drawOval(Color.Black.copy(alpha = 0.15f), Offset(base.x - u * 0.44f, base.y - u * 0.07f), Size(u * 0.88f, u * 0.24f))
    val bw = u * 0.8f; val bh = u * 0.42f; val fx = base.x - bw / 2f; val fy = base.y - bh
    drawRect(shade(Color(0xFFEFDCBE), light), Offset(fx, fy), Size(bw, bh))
    drawPath(Path().apply { moveTo(fx - u * 0.1f, fy); lineTo(base.x, fy - u * 0.32f); lineTo(fx + bw + u * 0.1f, fy); close() }, shade(Color(0xFFB98A4E), light))
    for (k in 0 until 3) {
        val pr = ((t / 1300f) + k / 3f) % 1f
        drawCircle(Color(0xFFE8E1D6).copy(alpha = (1f - pr) * 0.45f), u * (0.04f + pr * 0.07f), Offset(fx + bw * 0.75f, fy - u * 0.1f - pr * u * 0.4f))
    }
    drawRect(shade(Color(0xFF6B4A2C), light), Offset(base.x - u * 0.08f, base.y - u * 0.3f), Size(u * 0.16f, u * 0.3f))
    drawRect(lerp(Color(0xFF9AD0EE), Color(0xFFFFD98A), light.night), Offset(fx + u * 0.12f, fy + u * 0.12f), Size(u * 0.11f, u * 0.11f))
}

internal fun DrawScope.drawCastle(base: Offset, u: Float, light: Lighting) {
    drawOval(Color.Black.copy(alpha = 0.16f), Offset(base.x - u * 0.75f, base.y - u * 0.08f), Size(u * 1.5f, u * 0.3f))
    val stone = shade(Color(0xFFCFC8BC), light); val dark = shade(Color(0xFFAAA192), light)
    drawRect(stone, Offset(base.x - u * 0.45f, base.y - u * 1.1f), Size(u * 0.9f, u * 1.1f))
    for (i in 0 until 4) drawRect(dark, Offset(base.x - u * 0.45f + i * u * 0.25f, base.y - u * 1.19f), Size(u * 0.13f, u * 0.1f))
    for (sx in listOf(-0.6f, 0.6f)) {
        drawRect(stone, Offset(base.x + sx * u - u * 0.13f, base.y - u * 0.95f), Size(u * 0.26f, u * 0.95f))
        drawPath(Path().apply {
            moveTo(base.x + sx * u - u * 0.18f, base.y - u * 0.95f); lineTo(base.x + sx * u, base.y - u * 1.2f)
            lineTo(base.x + sx * u + u * 0.18f, base.y - u * 0.95f); close()
        }, shade(Color(0xFF8A6BEE), light))
        drawLine(shade(Color(0xFFD95E86), light), Offset(base.x + sx * u, base.y - u * 1.2f), Offset(base.x + sx * u, base.y - u * 1.4f), strokeWidth = u * 0.02f)
    }
    drawPath(Path().apply {
        moveTo(base.x - u * 0.13f, base.y); lineTo(base.x - u * 0.13f, base.y - u * 0.3f)
        lineTo(base.x, base.y - u * 0.44f); lineTo(base.x + u * 0.13f, base.y - u * 0.3f); lineTo(base.x + u * 0.13f, base.y); close()
    }, shade(Color(0xFF5A4632), light))
}

internal fun DrawScope.drawCampfire(base: Offset, u: Float, t: Float, light: Lighting) {
    drawOval(shade(Color(0xFF6E4A2A), light), Offset(base.x - u * 0.15f, base.y - u * 0.05f), Size(u * 0.3f, u * 0.1f))
    drawOval(shade(Color(0xFF8A5A34), light), Offset(base.x - u * 0.11f, base.y - u * 0.07f), Size(u * 0.22f, u * 0.08f))
    val fl = 0.8f + 0.2f * sin(t / 90f)
    drawPath(Path().apply { moveTo(base.x - u * 0.09f, base.y - u * 0.05f); lineTo(base.x, base.y - u * 0.36f * fl); lineTo(base.x + u * 0.09f, base.y - u * 0.05f); close() }, Color(0xFFFF7A3C))
    drawPath(Path().apply { moveTo(base.x - u * 0.05f, base.y - u * 0.05f); lineTo(base.x, base.y - u * 0.25f * fl); lineTo(base.x + u * 0.05f, base.y - u * 0.05f); close() }, Color(0xFFFFD24B))
    if (light.night > 0.2f) drawCircle(Color(0xFFFF9A4D).copy(alpha = light.night * 0.32f), u * 0.45f, Offset(base.x, base.y - u * 0.12f))
    for (k in 0 until 3) {
        val pr = ((t / 700f) + k / 3f) % 1f
        drawCircle(Color(0xFFFFCE7A).copy(alpha = (1f - pr) * 0.6f), u * 0.02f, Offset(base.x + sin(t / 120f + k) * u * 0.06f, base.y - u * 0.36f - pr * u * 0.3f))
    }
}

/** Chibi villager (unchanged proportions from M2 — fits every door). */
internal fun DrawScope.drawVillager(base: Offset, u: Float, t: Float, light: Lighting, v: Villager, facingRight: Boolean) {
    val s = when (v.kind) { "child" -> 0.66f; "elder" -> 0.92f; "pet" -> 0.5f; else -> 1f }
    val moving = v.speed > 0.03f
    val bob = if (moving) abs(sin(t / 150f + v.phase * 6f)) * u * 0.03f * s else 0f
    drawOval(Color.Black.copy(alpha = 0.15f * (1f - light.night * 0.4f)), Offset(base.x - u * 0.11f * s, base.y - u * 0.025f), Size(u * 0.22f * s, u * 0.08f))
    if (v.kind == "pet") {
        val c = shade(Color(0xFFD98A4A), light)
        drawOval(c, Offset(base.x - u * 0.11f, base.y - u * 0.11f - bob), Size(u * 0.22f, u * 0.11f))
        drawCircle(c, u * 0.055f, Offset(base.x + (if (facingRight) 0.1f else -0.1f) * u, base.y - u * 0.14f - bob))
        drawLine(c, Offset(base.x - u * 0.11f, base.y - u * 0.09f), Offset(base.x - u * 0.17f, base.y - u * 0.15f), strokeWidth = u * 0.02f)
        return
    }
    val bh = u * 0.26f * s
    drawPath(Path().apply {
        moveTo(base.x - u * 0.08f * s, base.y); lineTo(base.x + u * 0.08f * s, base.y)
        lineTo(base.x + u * 0.10f * s, base.y - bh); lineTo(base.x - u * 0.10f * s, base.y - bh); close()
    }, shade(v.shirt, light))
    val hc = Offset(base.x, base.y - bh - u * 0.085f * s - bob)
    drawCircle(shade(Color(0xFFF0C9A4), light), u * 0.085f * s, hc)
    drawPath(Path().apply {
        moveTo(hc.x - u * 0.09f * s, hc.y)
        cubicTo(hc.x - u * 0.10f * s, hc.y - u * 0.13f * s, hc.x + u * 0.10f * s, hc.y - u * 0.13f * s, hc.x + u * 0.09f * s, hc.y)
        close()
    }, shade(v.hair, light))
    if (v.kind == "elder") drawLine(shade(Color(0xFF8A6A44), light),
        Offset(base.x + u * 0.11f * s, base.y), Offset(base.x + u * 0.11f * s, base.y - bh - u * 0.05f), strokeWidth = u * 0.02f)
}

// ── flying life, weather, grade ─────────────────────────────────────────

internal fun DrawScope.drawFlyingLife(
    t: Float, light: Lighting, weather: Weather, season: Season, birdStartle: Float, P: Proj, u: Float,
) {
    val w = size.width; val h = size.height
    if (light.night < 0.55f) for (i in 0 until 6) {
        val c = P(9f + 4f * sin(t / 2000f + i), 11f + 3f * cos(t / 1700f + i * 2f), 0f)
        val bx = c.x + sin(t / 500f + i) * u * 0.4f; val by = c.y + cos(t / 400f + i) * u * 0.3f
        val col = listOf(Color(0xFFFFB84D), Color(0xFFFF7FB0), Color(0xFFB58BFF))[i % 3]
        val flap = abs(sin(t / 60f + i)) * u * 0.06f
        drawOval(col, Offset(bx - u * 0.06f, by - flap), Size(u * 0.06f, u * 0.1f))
        drawOval(col, Offset(bx, by - flap), Size(u * 0.06f, u * 0.1f))
    }
    val startled = (t - birdStartle) in 0f..1400f
    if (light.night < 0.5f && weather != Weather.RAIN) for (b in 0 until 3) {
        val bp = (t / 6000f + b * 0.33f) % 1f
        val lift = if (startled) ((t - birdStartle) / 1400f) * h * 0.5f else 0f
        val bx = w * (0.05f + 0.9f * bp); val by = h * (0.12f + 0.06f * b) + sin(t / 260f + b) * u * 0.15f - lift
        val flap = (sin(t / (if (startled) 40f else 80f) + b * 2f) + 1f) / 2f
        val c = Color(0xCC33333F)
        drawLine(c, Offset(bx - u * 0.14f, by + flap * u * 0.04f), Offset(bx, by - flap * u * 0.05f), strokeWidth = u * 0.03f)
        drawLine(c, Offset(bx, by - flap * u * 0.05f), Offset(bx + u * 0.14f, by + flap * u * 0.04f), strokeWidth = u * 0.03f)
        drawOval(Color.Black.copy(alpha = 0.06f), Offset(bx - u * 0.1f, by + u * 0.6f), Size(u * 0.2f, u * 0.07f))
    }
    if (light.night > 0.6f) for (i in 0 until 18) {
        val fx = w * rnd(i, 101) + sin(t / 800f + i) * u * 0.5f
        val fy = h * (0.1f + 0.8f * rnd(i, 102)) + cos(t / 700f + i * 2f) * u * 0.5f
        val a = (0.4f + 0.6f * sin(t / 380f + i * 3f)).coerceIn(0f, 1f)
        drawCircle(Color(0xFFFFF3A0).copy(alpha = a * 0.9f), u * 0.05f, Offset(fx, fy))
        drawCircle(Color(0xFFFFF3A0).copy(alpha = a * 0.3f), u * 0.12f, Offset(fx, fy))
    }
    if (weather == Weather.RAIN) for (i in 0 until 110) {
        val rx = (rnd(i, 111) * w + t / 5f) % w; val ry = (rnd(i, 112) * h + t / 1.1f) % h
        drawLine(Color(0xFFBFD8EC).copy(alpha = 0.5f), Offset(rx, ry), Offset(rx - u * 0.06f, ry + u * 0.3f), strokeWidth = 2f)
    }
    if (weather == Weather.SNOW) for (i in 0 until 80) {
        val sx = (rnd(i, 111) * w + sin(t / 700f + i) * u * 0.4f) % w; val sy = (rnd(i, 112) * h + t / 11f) % h
        drawCircle(Color.White.copy(alpha = 0.85f), u * 0.045f, Offset(sx, sy))
    }
    if (season == Season.AUTUMN && weather != Weather.SNOW) for (i in 0 until 16) {
        val lx = (rnd(i, 121) * w + sin(t / 500f + i) * u * 0.6f) % w; val ly = (rnd(i, 122) * h + t / 20f) % h
        drawOval(Color(0xFFD98A3A).copy(alpha = 0.85f), Offset(lx, ly), Size(u * 0.1f, u * 0.06f))
    }
}

/** §7.6 — the single end-of-frame grade. */
internal fun DrawScope.grade(light: Lighting) {
    val w = size.width; val h = size.height
    if (light.night > 0f) drawRect(Color(0xFF16204A).copy(alpha = 0.22f * light.night))
    if (light.warmth > 0.5f) drawRect(Color(0xFFFF9A5E).copy(alpha = 0.10f * light.warmth))
    drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.14f)),
        center = Offset(w / 2f, h * 0.5f), radius = w * 1.0f))
}
