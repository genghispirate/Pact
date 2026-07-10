package com.pact.app.ui.world

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

// ═══════════════════════════════════════════════════════════════════════
//  WorldRenderer — DESIGN.md §7.3–§7.7. Stateless DrawScope functions.
//  P is the projection (world units → screen px); u is px per world unit.
// ═══════════════════════════════════════════════════════════════════════

internal typealias Proj = (Float, Float, Float) -> Offset

// ── path helpers ─────────────────────────────────────────────────────────

internal fun smoothPath(pts: List<Offset>, closed: Boolean): Path {
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

/** §7.3 — the island's FIXED coast multipliers. Gentle (±8%), never random. */
private val COAST = listOf(1.00f, 0.96f, 1.04f, 0.98f, 0.92f, 1.02f, 1.06f, 0.97f, 0.94f, 1.03f, 0.99f, 0.95f, 1.05f, 1.01f)

internal fun coastPath(center: Offset, rx: Float, ry: Float): Path {
    val pts = COAST.mapIndexed { i, m ->
        val a = i * (2 * Math.PI / COAST.size)
        Offset(center.x + (cos(a) * rx * m).toFloat(), center.y + (sin(a) * ry * m).toFloat())
    }
    return smoothPath(pts, true)
}

internal fun translated(path: Path, dx: Float, dy: Float): Path = Path().apply { addPath(path, Offset(dx, dy)) }

// ═══════════════════════════ STATIC GROUND (cached to a bitmap, §7.11) ═══

internal fun DrawScope.drawGroundStatic(layout: Layout, light: Lighting, season: Season, P: Proj, u: Float) {
    val c0 = P(0f, 0f, 0f)
    val land = coastPath(c0, layout.islandR * u * 0.5f, layout.islandR * u * 0.25f)
    val sea = coastPath(c0, (layout.islandR + 1.0f) * u * 0.5f, (layout.islandR + 1.0f) * u * 0.25f)

    // the small sea the island sits in — frames the diorama (§7.3)
    drawPath(sea, Brush.verticalGradient(listOf(shade(Color(0xFF2C6FB8), light), shade(Color(0xFF1E4E8C), light)),
        startY = c0.y - layout.islandR * u * 0.3f, endY = c0.y + layout.islandR * u * 0.35f))
    // island shadow on the water, then the dirt skirt (two bands), then land
    drawPath(translated(land, 0f, u * 0.10f), Color.Black.copy(alpha = 0.20f))
    drawPath(translated(land, 0f, u * 0.30f), shade(Color(0xFF54381F), light))
    drawPath(translated(land, 0f, u * 0.18f), shade(Color(0xFF6B4B2F), light))
    drawPath(land, Brush.radialGradient(
        listOf(grass(GRASS_CENTER, light, season), grass(GRASS_MID, light, season), grass(GRASS_EDGE, light, season)),
        center = c0, radius = layout.islandR * u * 0.55f,
    ))
    // rock clusters on the skirt (authored angles)
    listOf(15f, 70f, 140f, 200f, 265f, 320f).forEachIndexed { i, ang ->
        val e = polar(ang, layout.islandR - 0.1f)
        val p = P(e.x, e.y, 0f) + Offset(0f, u * 0.22f)
        drawCircle(shade(Color(0xFF6E747A), light), u * (0.07f + rnd(i, 61) * 0.05f), p)
    }

    // everything on land clips to the coast so nothing bleeds into the sea
    clipPath(land) {
        // darker grass patches — 9 authored soft areas, not blotches
        layout.patches.forEach { d ->
            val c = P(d.x, d.y, 0f)
            val sz = (1.1f + rnd(d.seed, 81) * 0.9f) * u
            drawOval(Color.Black.copy(alpha = 0.06f), Offset(c.x - sz * 0.5f, c.y - sz * 0.22f), Size(sz, sz * 0.45f))
        }
        // mown lighter ring under the plaza
        drawOval(Color.White.copy(alpha = 0.05f), Offset(c0.x - u * 1.7f, c0.y - u * 0.85f), Size(u * 3.4f, u * 1.7f))

        // the river — authored course, ends exactly in the pond (§7.3)
        val riverPts = layout.river.map { P(it.x, it.y, 0f) }
        val rp = smoothPath(riverPts, false)
        drawPath(rp, shade(Color(0xFF2C7BD1), light), style = Stroke(u * 0.40f, cap = StrokeCap.Round))
        drawPath(rp, shade(Color(0xFF4AA6E8), light), style = Stroke(u * 0.24f, cap = StrokeCap.Round))
        // stepping stones where the plaza path crosses
        val cross = P(0.8f, 1.8f, 0f)
        drawCircle(shade(Color(0xFFB9C0C8), light), u * 0.07f, cross + Offset(-u * 0.08f, 0f))
        drawCircle(shade(Color(0xFFB9C0C8), light), u * 0.07f, cross + Offset(u * 0.10f, u * 0.05f))

        // paths — curved, tan, appear with their buildings
        layout.paths.forEach { pp ->
            val sp = smoothPath(pp.map { P(it.x, it.y, 0f) }, false)
            drawPath(sp, shade(Color(0xFFCBAE86), light), style = Stroke(u * 0.26f, cap = StrokeCap.Round))
            drawPath(sp, shade(Color(0xFFB79468), light), style = Stroke(u * 0.18f, cap = StrokeCap.Round))
        }

        // pond base + sandy shore + lily pad (§7.3)
        val pc = P(layout.pond.x, layout.pond.y, 0f)
        val prx = layout.pondR * u * 0.5f; val pry = layout.pondR * u * 0.25f
        drawOval(shade(Color(0xFFD8C79A), light), Offset(pc.x - prx - u * 0.14f, pc.y - pry - u * 0.07f), Size(prx * 2 + u * 0.28f, pry * 2 + u * 0.14f))
        drawOval(shade(Color(0xFF2C7BD1), light), Offset(pc.x - prx, pc.y - pry), Size(prx * 2, pry * 2))
        drawOval(shade(Color(0xFF4AA6E8), light), Offset(pc.x - prx * 0.68f, pc.y - pry * 0.55f), Size(prx * 1.36f, pry * 1.1f))
        drawOval(shade(Color(0xFF5BAE58), light), Offset(pc.x + prx * 0.3f - u * 0.06f, pc.y - pry * 0.2f - u * 0.03f), Size(u * 0.12f, u * 0.06f))

        // plaza — stone circle + fountain base
        drawOval(shade(Color(0xFFCFC3A6), light), Offset(c0.x - u * 1.1f, c0.y - u * 0.55f), Size(u * 2.2f, u * 1.1f))
        drawOval(shade(Color(0xFFBDB08E), light), Offset(c0.x - u * 1.1f, c0.y - u * 0.55f), Size(u * 2.2f, u * 1.1f), style = Stroke(u * 0.045f))
        drawOval(shade(Color(0xFF9AA0A6), light), Offset(c0.x - u * 0.28f, c0.y - u * 0.15f), Size(u * 0.56f, u * 0.28f))
        drawRect(shade(Color(0xFFB6BCC4), light), Offset(c0.x - u * 0.04f, c0.y - u * 0.4f), Size(u * 0.08f, u * 0.3f))

        // rocks & logs
        layout.rocks.forEach { d ->
            val c = P(d.x, d.y, 0f)
            drawOval(Color.Black.copy(alpha = 0.12f), Offset(c.x - u * 0.12f, c.y - u * 0.02f), Size(u * 0.24f, u * 0.08f))
            if (d.type == "rock") {
                drawCircle(shade(Color(0xFF9AA0A6), light), u * 0.09f, c - Offset(0f, u * 0.05f))
                drawCircle(shade(Color(0xFF7B8188), light), u * 0.06f, c - Offset(-u * 0.03f, u * 0.02f))
            } else {
                drawOval(shade(Color(0xFF7A5330), light), Offset(c.x - u * 0.15f, c.y - u * 0.1f), Size(u * 0.3f, u * 0.12f))
                drawOval(shade(Color(0xFF956B45), light), Offset(c.x - u * 0.13f, c.y - u * 0.09f), Size(u * 0.06f, u * 0.1f))
            }
        }
    }
}

// ═══════════════════════════════════ DYNAMIC LAYERS (drawn every frame) ══

internal fun DrawScope.drawSky(light: Lighting, weather: Weather, t: Float, u: Float, peek: Boolean) {
    val w = size.width; val h = size.height
    drawRect(Brush.verticalGradient(listOf(light.skyTop, light.skyBottom)))
    // sun / moon with a soft bloom
    val cel = lerp(Color(0xFFFFE49B), Color(0xFFEAF0FF), light.night)
    val cc = Offset(w * (0.80f - light.night * 0.55f), h * 0.13f)
    drawCircle(cel.copy(alpha = 0.22f), h * 0.10f, cc)
    drawCircle(cel, h * 0.05f, cc)
    if (light.night > 0.45f) {
        val stars = if (peek) 23 else 46
        for (i in 0 until stars) {
            val a = ((light.night - 0.45f) * 1.8f) * (0.4f + 0.6f * sin(t / 500f + i * 1.3f))
            drawCircle(Color.White.copy(alpha = a.coerceIn(0f, 0.9f)), 1.5f + rnd(i, 71) * 1.4f,
                Offset(rnd(i, 72) * w, rnd(i, 73) * h * 0.34f))
        }
    }
    // clouds — above the horizon only, never clipping the card corners (§7 fix)
    val cloudN = when { peek -> 1; weather == Weather.CLOUDY || weather == Weather.RAIN -> 4; else -> 2 }
    for (i in 0 until cloudN) {
        val cx = ((t / 110f + i * 300f) % (w + 240f)) - 120f
        val cy = h * (0.14f + 0.05f * i)
        val col = shade(Color.White, light).copy(alpha = if (weather == Weather.RAIN) 0.9f else 0.8f)
        val cu = u * (0.42f + 0.16f * i)
        drawOval(col, Offset(cx - 3.2f * cu, cy - cu * 0.8f), Size(6.4f * cu, cu * 1.6f))
        drawOval(col, Offset(cx - 1.7f * cu, cy - cu * 1.5f), Size(3.6f * cu, cu * 2.2f))
        drawOval(col, Offset(cx + 0.4f * cu, cy - cu * 1.1f), Size(3f * cu, cu * 1.8f))
    }
}

internal fun DrawScope.drawPondLife(layout: Layout, light: Lighting, t: Float, P: Proj, u: Float) {
    val pc = P(layout.pond.x, layout.pond.y, 0f)
    val prx = layout.pondR * u * 0.5f; val pry = layout.pondR * u * 0.25f
    for (k in 0 until 3) {
        val prog = ((t / 2600f) + k / 3f) % 1f
        drawOval(Color.White.copy(alpha = (1f - prog) * 0.25f),
            Offset(pc.x - prx * prog, pc.y - pry * prog), Size(prx * 2 * prog, pry * 2 * prog), style = Stroke(2f))
    }
    if (sin(t / 420f) > 0.4f) drawCircle(Color.White.copy(alpha = 0.3f * light.brightness), u * 0.04f, pc + Offset(prx * 0.3f, -pry * 0.2f))
    for (d in 0 until 2) {
        val dx = pc.x + sin(t / 1400f + d * 3f) * prx * 0.5f
        val dy = pc.y + cos(t / 1700f + d * 2f) * pry * 0.4f
        drawOval(shade(Color(0xFFF4F1E8), light), Offset(dx - u * 0.08f, dy - u * 0.05f), Size(u * 0.16f, u * 0.1f))
        drawCircle(shade(Color(0xFFF4F1E8), light), u * 0.04f, Offset(dx + u * 0.07f, dy - u * 0.07f))
        drawCircle(shade(Color(0xFFF2B84D), light), u * 0.016f, Offset(dx + u * 0.10f, dy - u * 0.07f))
    }
    for (bi in 0 until layout.boats) {
        val bx = pc.x + (bi - 0.5f) * u * 0.6f + sin(t / 1800f + bi) * u * 0.08f
        val by = pc.y + (bi - 0.5f) * u * 0.16f
        drawOval(shade(Color(0xFF8A5A34), light), Offset(bx - u * 0.16f, by - u * 0.05f), Size(u * 0.32f, u * 0.12f))
        drawLine(shade(Color(0xFF6E4A2A), light), Offset(bx, by - u * 0.05f), Offset(bx, by - u * 0.3f), strokeWidth = u * 0.02f)
        drawPath(Path().apply { moveTo(bx, by - u * 0.3f); lineTo(bx + u * 0.12f, by - u * 0.12f); lineTo(bx, by - u * 0.12f); close() },
            shade(Color(0xFFF2E4C6), light))
    }
}

internal fun DrawScope.drawFlowersAndTufts(layout: Layout, light: Lighting, season: Season, t: Float, P: Proj, u: Float) {
    layout.flowers.forEach { f ->
        val c = P(f.x, f.y, 0f)
        val bounce = (sin(t / 260f + f.seed) + 1f) * u * 0.018f
        val col = shade(listOf(Color(0xFFFF6FA6), Color(0xFFFFD24B), Color(0xFF6FA8FF), Color(0xFFB58BFF), Color(0xFFFF9B57))[f.seed % 5], light)
        drawLine(shade(Color(0xFF4E9A32), light), c, c - Offset(0f, u * 0.1f), strokeWidth = u * 0.028f)
        drawCircle(col, u * 0.05f, c - Offset(0f, u * 0.12f + bounce))
        drawCircle(shade(Color(0xFFFFF3B0), light), u * 0.018f, c - Offset(0f, u * 0.12f + bounce))
    }
    // wind-blown grass tufts, seeded scatter inside the island
    for (i in 0 until 40) {
        val ang = rnd(i, 91) * 360f
        val r = 1.6f + rnd(i, 92) * (layout.islandR - 2.1f)
        val pos = polar(ang, r)
        val c = P(pos.x, pos.y, 0f)
        val sway = sin(t / 2400f * 6.28f + i) * u * 0.05f
        val g = grass(Color(0xFF4E9A32), light, season)
        drawLine(g, c, c + Offset(sway - u * 0.02f, -u * 0.12f), strokeWidth = u * 0.03f)
        drawLine(g, c + Offset(u * 0.05f, 0f), c + Offset(sway + u * 0.07f, -u * 0.09f), strokeWidth = u * 0.03f)
    }
    // fountain jet
    val c0 = P(0f, 0f, 0f)
    drawOval(shade(Color(0xFF57B0EC), light), Offset(c0.x - u * 0.20f, c0.y - u * 0.11f), Size(u * 0.4f, u * 0.2f))
    for (k in 0 until 3) {
        val prog = ((t / 900f) + k / 3f) % 1f
        drawCircle(Color(0xFFBFE6FF).copy(alpha = (1f - prog) * 0.7f), u * 0.02f,
            Offset(c0.x - u * 0.05f + k * u * 0.05f, c0.y - u * 0.4f + prog * u * 0.22f))
    }
}

// ── upright objects ──────────────────────────────────────────────────────

internal fun DrawScope.drawHill(base: Offset, u: Float, light: Lighting, season: Season, seed: Int) {
    val r = u * (1.7f + rnd(seed, 91) * 0.7f)
    val top = shade(lerp(foliage(season), Color(0xFF3E8B3A), 0.3f), light)
    val path = Path().apply {
        moveTo(base.x - r, base.y)
        cubicTo(base.x - r * 0.7f, base.y - r * 1.05f, base.x + r * 0.7f, base.y - r * 1.05f, base.x + r, base.y)
        close()
    }
    drawPath(path, top)
    drawPath(path, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.06f), Color.Black.copy(alpha = 0.12f)), startY = base.y - r, endY = base.y))
}

internal fun DrawScope.drawTree(base: Offset, u0: Float, t: Float, light: Lighting, season: Season, seed: Int, pine: Boolean) {
    val u = u0
    val sway = sin(t / 760f + seed) * u * 0.05f
    drawOval(Color.Black.copy(alpha = 0.15f * (1f - light.night * 0.4f)), Offset(base.x - u * 0.28f, base.y - u * 0.05f), Size(u * 0.56f, u * 0.18f))
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
        drawCircle(shade(darker(leaf, 0.78f), light), u * 0.40f, Offset(cx + u * 0.10f, cy + u * 0.12f))
        drawCircle(shade(leaf, light), u * 0.36f, Offset(cx - u * 0.06f, cy + u * 0.04f))
        // moonlit / sunlit rim on the top-left (§7.6 depth)
        drawCircle(shade(lerp(leaf, Color.White, 0.20f), light), u * 0.24f, Offset(cx - u * 0.12f, cy - u * 0.12f))
        if (season == Season.SPRING) for (k in 0 until 4) {
            drawCircle(shade(Color(0xFFFFC0DA), light), u * 0.04f,
                Offset(cx + (rnd(seed + k, 92) - 0.5f) * u * 0.6f, cy + (rnd(seed + k, 93) - 0.5f) * u * 0.6f))
        }
    }
}

private fun roofColor(kind: String) = when (kind) {
    "library" -> Color(0xFF5C79C0); "gym" -> Color(0xFFD95E54); "temple" -> Color(0xFF8A6BEE)
    "workshop" -> Color(0xFF3E9B8E); "bakery" -> Color(0xFFC97C4A); "school" -> Color(0xFF5BA24C)
    "moon" -> Color(0xFF6A6BD0); else -> Color(0xFFC0503F)
}

/** §7.5/§7.7 — scale law: bw 1.1u, wall 0.6u, door 0.48u (a villager fits). No emoji signs. */
internal fun DrawScope.drawBuilding(b: Building, base: Offset, u: Float, t: Float, light: Lighting) {
    drawOval(Color.Black.copy(alpha = 0.17f * (1f - light.night * 0.4f)), Offset(base.x - u * 0.62f, base.y - u * 0.07f), Size(u * 1.24f, u * 0.32f))

    if (b.kind == "garden") { drawFarmPlot(base, u, t, light); return }

    if (b.stage == 0) { // foundation + corner posts
        drawOval(shade(Color(0xFF9AA0A6), light), Offset(base.x - u * 0.55f, base.y - u * 0.18f), Size(u * 1.1f, u * 0.36f))
        for (sx in listOf(-0.45f, 0.45f)) drawRect(shade(Color(0xFF8A6A44), light), Offset(base.x + sx * u, base.y - u * 0.75f), Size(u * 0.07f, u * 0.65f))
        return
    }
    val bw = u * 1.1f
    val bh = u * (0.5f + 0.05f * (b.stage - 1))
    val fx = base.x - bw / 2f; val fy = base.y - bh
    // right side face for depth
    drawPath(Path().apply {
        moveTo(base.x + bw / 2f, base.y); lineTo(base.x + bw / 2f + u * 0.16f, base.y - u * 0.08f)
        lineTo(fx + bw + u * 0.16f, fy - u * 0.08f); lineTo(fx + bw, fy); close()
    }, shade(darker(Color(0xFFF2E4C6), 0.8f), light))
    drawRect(shade(Color(0xFFF2E4C6), light), Offset(fx, fy), Size(bw, bh))
    if (b.stage == 1) {
        drawRect(shade(Color(0xFF9A7A50), light), Offset(fx, fy), Size(bw, bh), style = Stroke(u * 0.045f))
        return
    }
    // two-facet roof
    val roof = roofColor(b.kind)
    drawPath(Path().apply { moveTo(fx - u * 0.09f, fy); lineTo(base.x, fy - u * 0.38f); lineTo(fx + bw + u * 0.09f, fy); close() }, shade(roof, light))
    drawPath(Path().apply {
        moveTo(fx + bw + u * 0.09f, fy); lineTo(base.x, fy - u * 0.38f)
        lineTo(base.x + u * 0.16f, fy - u * 0.46f); lineTo(fx + bw + u * 0.25f, fy - u * 0.08f); close()
    }, shade(darker(roof, 0.82f), light))
    // door (the door law: villagers fit) + warm windows
    drawRect(shade(Color(0xFF6B4A2C), light), Offset(base.x - u * 0.10f, base.y - u * 0.48f), Size(u * 0.20f, u * 0.48f))
    drawCircle(shade(Color(0xFFE7C868), light), u * 0.02f, Offset(base.x + u * 0.06f, base.y - u * 0.24f))
    val glow = lerp(Color(0xFF9AD0EE), Color(0xFFFFD98A), light.night)
    for (wx in listOf(-0.32f, 0.32f)) {
        drawRect(glow, Offset(fx + bw / 2f + wx * u - u * 0.06f, fy + u * 0.10f), Size(u * 0.13f, u * 0.13f))
        if (light.night > 0.35f) drawCircle(Color(0xFFFFD98A).copy(alpha = light.night * 0.35f), u * 0.15f,
            Offset(fx + bw / 2f + wx * u + u * 0.005f, fy + u * 0.165f))
    }
    // one personality prop each (full pass lands in M4)
    when (b.kind) {
        "library" -> for (k in 0 until 5) drawCircle(shade(Color(0xFF4E8B32), light), u * 0.04f, Offset(fx + u * 0.06f, fy + u * 0.06f + k * u * 0.09f))
        "bakery" -> {
            drawRect(shade(Color(0xFFB07A50), light), Offset(fx + bw * 0.68f, fy - u * 0.34f), Size(u * 0.12f, u * 0.2f))
            for (k in 0 until 3) {
                val pr = ((t / 1200f) + k / 3f) % 1f
                drawCircle(Color(0xFFE8E1D6).copy(alpha = (1f - pr) * 0.5f), u * (0.05f + pr * 0.08f),
                    Offset(fx + bw * 0.74f, fy - u * 0.34f - pr * u * 0.5f))
            }
        }
        "gym" -> {
            drawCircle(shade(Color(0xFF3A3A44), light), u * 0.05f, Offset(fx + u * 0.12f, base.y - u * 0.05f))
            drawCircle(shade(Color(0xFF3A3A44), light), u * 0.05f, Offset(fx + u * 0.26f, base.y - u * 0.05f))
        }
        "workshop" -> {
            drawOval(shade(Color(0xFF7A5330), light), Offset(fx - u * 0.04f, base.y - u * 0.11f), Size(u * 0.2f, u * 0.09f))
            drawOval(shade(Color(0xFF956B45), light), Offset(fx - u * 0.02f, base.y - u * 0.12f), Size(u * 0.07f, u * 0.09f))
        }
        "temple" -> drawOval(shade(Color(0xFF4E9A3C), light), Offset(fx - u * 0.06f, base.y - u * 0.09f), Size(u * 0.32f, u * 0.11f))
        "school" -> drawRect(shade(Color(0xFF5BA24C), light), Offset(base.x + u * 0.18f, fy - u * 0.38f), Size(u * 0.14f, u * 0.1f))
        "moon" -> {
            drawLantern(Offset(base.x + bw / 2f + u * 0.1f, base.y - u * 0.4f), u, light, t)
            if (light.night > 0.3f) drawCircle(Color(0xFFBFC6FF).copy(alpha = light.night * 0.35f), u * 0.32f, Offset(base.x, fy))
        }
    }
}

internal fun DrawScope.drawFarmPlot(base: Offset, u: Float, t: Float, light: Lighting) {
    val fw = u * 1.3f; val fd = u * 0.6f
    drawOval(shade(Color(0xFF7A5230), light), Offset(base.x - fw / 2f, base.y - fd / 2f), Size(fw, fd))
    for (r in 0 until 4) {
        val ry = base.y - fd * 0.3f + r * fd * 0.18f
        for (cX in 0 until 5) {
            val cx = base.x - fw * 0.36f + cX * fw * 0.18f
            val sway = sin(t / 300f + cX + r) * u * 0.02f
            drawLine(shade(Color(0xFF57A83E), light), Offset(cx, ry), Offset(cx + sway, ry - u * 0.12f), strokeWidth = u * 0.024f)
        }
    }
    for (a in 0 until 8) {
        val px = base.x + (cos(Math.toRadians(a * 45.0)) * fw * 0.52f).toFloat()
        val py = base.y + (sin(Math.toRadians(a * 45.0)) * fd * 0.52f).toFloat()
        drawRect(shade(Color(0xFF9A7A50), light), Offset(px - u * 0.02f, py - u * 0.14f), Size(u * 0.045f, u * 0.14f))
    }
    drawLine(shade(Color(0xFF8A6A44), light), Offset(base.x, base.y - u * 0.05f), Offset(base.x, base.y - u * 0.5f), strokeWidth = u * 0.035f)
    drawLine(shade(Color(0xFF8A6A44), light), Offset(base.x - u * 0.15f, base.y - u * 0.36f), Offset(base.x + u * 0.15f, base.y - u * 0.36f), strokeWidth = u * 0.035f)
    drawCircle(shade(Color(0xFFD9B36A), light), u * 0.065f, Offset(base.x, base.y - u * 0.55f))
}

internal fun DrawScope.drawWindmill(base: Offset, u: Float, t: Float, light: Lighting) {
    drawOval(Color.Black.copy(alpha = 0.15f), Offset(base.x - u * 0.32f, base.y - u * 0.05f), Size(u * 0.64f, u * 0.2f))
    drawPath(Path().apply {
        moveTo(base.x - u * 0.26f, base.y); lineTo(base.x + u * 0.26f, base.y)
        lineTo(base.x + u * 0.15f, base.y - u * 1.15f); lineTo(base.x - u * 0.15f, base.y - u * 1.15f); close()
    }, shade(Color(0xFFEDE0C4), light))
    drawPath(Path().apply {
        moveTo(base.x - u * 0.15f, base.y - u * 1.15f); lineTo(base.x + u * 0.15f, base.y - u * 1.15f)
        lineTo(base.x, base.y - u * 1.4f); close()
    }, shade(Color(0xFF9A5A3A), light))
    val hub = Offset(base.x, base.y - u * 1.1f)
    val rot = t / 900f
    for (b in 0 until 4) {
        val a = rot + b * (Math.PI / 2)
        val tip = Offset(hub.x + (cos(a) * u * 0.5f).toFloat(), hub.y + (sin(a) * u * 0.5f).toFloat())
        val side = Offset(hub.x + (cos(a + 0.3) * u * 0.14f).toFloat(), hub.y + (sin(a + 0.3) * u * 0.14f).toFloat())
        drawPath(Path().apply { moveTo(hub.x, hub.y); lineTo(tip.x, tip.y); lineTo(side.x, side.y); close() }, shade(Color(0xFFF2E8D2), light))
    }
    drawCircle(shade(Color(0xFF6E4A2A), light), u * 0.05f, hub)
}

internal fun DrawScope.drawCottage(base: Offset, u: Float, t: Float, light: Lighting) {
    drawOval(Color.Black.copy(alpha = 0.15f), Offset(base.x - u * 0.44f, base.y - u * 0.05f), Size(u * 0.88f, u * 0.24f))
    val bw = u * 0.8f; val bh = u * 0.42f; val fx = base.x - bw / 2f; val fy = base.y - bh
    drawRect(shade(Color(0xFFEFDCBE), light), Offset(fx, fy), Size(bw, bh))
    drawPath(Path().apply { moveTo(fx - u * 0.1f, fy); lineTo(base.x, fy - u * 0.32f); lineTo(fx + bw + u * 0.1f, fy); close() }, shade(Color(0xFFB98A4E), light))
    drawRect(shade(Color(0xFF9A6A46), light), Offset(fx + bw * 0.7f, fy - u * 0.3f), Size(u * 0.1f, u * 0.2f))
    for (k in 0 until 3) {
        val pr = ((t / 1300f) + k / 3f) % 1f
        drawCircle(Color(0xFFE8E1D6).copy(alpha = (1f - pr) * 0.45f), u * (0.04f + pr * 0.07f), Offset(fx + bw * 0.75f, fy - u * 0.3f - pr * u * 0.4f))
    }
    drawRect(shade(Color(0xFF6B4A2C), light), Offset(base.x - u * 0.08f, base.y - u * 0.3f), Size(u * 0.16f, u * 0.3f))
    drawRect(lerp(Color(0xFF9AD0EE), Color(0xFFFFD98A), light.night), Offset(fx + u * 0.12f, fy + u * 0.12f), Size(u * 0.11f, u * 0.11f))
}

internal fun DrawScope.drawCastle(base: Offset, u: Float, light: Lighting) {
    drawOval(Color.Black.copy(alpha = 0.16f), Offset(base.x - u * 0.75f, base.y - u * 0.06f), Size(u * 1.5f, u * 0.3f))
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

internal fun DrawScope.drawLantern(p: Offset, u: Float, light: Lighting, t: Float) {
    val flick = 0.7f + 0.3f * sin(t / 180f)
    drawLine(shade(Color(0xFF5A4632), light), Offset(p.x, p.y - u * 0.22f), Offset(p.x, p.y), strokeWidth = u * 0.02f)
    if (light.night > 0.25f) drawCircle(Color(0xFFFFD98A).copy(alpha = light.night * flick * 0.5f), u * 0.17f, p)
    drawCircle(shade(Color(0xFFE0554E), light), u * 0.05f, p)
    drawCircle(lerp(shade(Color(0xFFFFD98A), light), Color(0xFFFFF3C0), light.night * flick), u * 0.03f, p)
}

/** §7.5 — chibi villager, 0.43u tall. */
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

// ── flying life, weather, tap fx, grade ──────────────────────────────────

internal fun DrawScope.drawFlyingLife(
    t: Float, light: Lighting, weather: Weather, season: Season, birdStartle: Float, P: Proj, u: Float,
) {
    val w = size.width; val h = size.height
    if (light.night < 0.55f) for (i in 0 until 6) {
        val c = P(2f * sin(t / 2000f + i), 2f * cos(t / 1700f + i * 2f), 0f)
        val bx = c.x + sin(t / 500f + i) * u * 0.4f; val by = c.y - u * 0.5f + cos(t / 400f + i) * u * 0.3f
        val col = listOf(Color(0xFFFFB84D), Color(0xFFFF7FB0), Color(0xFFB58BFF))[i % 3]
        val flap = abs(sin(t / 60f + i)) * u * 0.06f
        drawOval(col, Offset(bx - u * 0.06f, by - flap), Size(u * 0.06f, u * 0.1f))
        drawOval(col, Offset(bx, by - flap), Size(u * 0.06f, u * 0.1f))
    }
    val startled = (t - birdStartle) in 0f..1400f
    if (light.night < 0.5f && weather != Weather.RAIN) for (b in 0 until 3) {
        val bp = (t / 6000f + b * 0.33f) % 1f
        val lift = if (startled) ((t - birdStartle) / 1400f) * h * 0.5f else 0f
        val bx = w * (0.05f + 0.9f * bp); val by = h * (0.2f + 0.06f * b) + sin(t / 260f + b) * u * 0.15f - lift
        val flap = (sin(t / (if (startled) 40f else 80f) + b * 2f) + 1f) / 2f
        val c = Color(0xCC33333F)
        drawLine(c, Offset(bx - u * 0.14f, by + flap * u * 0.04f), Offset(bx, by - flap * u * 0.05f), strokeWidth = u * 0.03f)
        drawLine(c, Offset(bx, by - flap * u * 0.05f), Offset(bx + u * 0.14f, by + flap * u * 0.04f), strokeWidth = u * 0.03f)
    }
    if (light.night > 0.6f) for (i in 0 until 18) {
        val fx = w * rnd(i, 101) + sin(t / 800f + i) * u * 0.5f
        val fy = h * (0.42f + 0.45f * rnd(i, 102)) + cos(t / 700f + i * 2f) * u * 0.5f
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

/** §7.6 — the single end-of-frame grade. Nothing else color-grades. */
internal fun DrawScope.grade(light: Lighting) {
    val w = size.width; val h = size.height
    if (light.night > 0f) drawRect(Color(0xFF16204A).copy(alpha = 0.22f * light.night))
    if (light.warmth > 0.5f) drawRect(Color(0xFFFF9A5E).copy(alpha = 0.10f * light.warmth))
    drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.16f)),
        center = Offset(w / 2f, h * 0.46f), radius = w * 0.95f))
}
