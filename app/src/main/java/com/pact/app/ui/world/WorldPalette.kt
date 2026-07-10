package com.pact.app.ui.world

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import java.util.Calendar

// ═══════════════════════════════════════════════════════════════════════
//  WorldPalette — DESIGN.md §7.6. Six time-of-day buckets, smooth blends,
//  seasons and weather. Night is cozy, never murky: brightness floor 0.78.
// ═══════════════════════════════════════════════════════════════════════

internal data class Lighting(
    val skyTop: Color, val skyBottom: Color,
    val ambient: Color, val ambientStrength: Float,
    val brightness: Float,
    val night: Float,      // 0 day … 1 deep night
    val warmth: Float,     // golden-hour warmth 0..1
)

private fun l(skyTop: Long, skyBottom: Long, ambient: Long, strength: Float, bright: Float, night: Float, warmth: Float) =
    Lighting(Color(skyTop), Color(skyBottom), Color(ambient), strength, bright, night, warmth)

// bucket centers (startHour is the bucket's start; blend happens ±0.7h around boundaries)
private val BUCKETS = listOf(
    0f to l(0xFF101A3E, 0xFF26355F, 0xFF33447E, 0.34f, 0.78f, 1f, 0f),      // night
    5f to l(0xFFF3A96B, 0xFFFFE1B2, 0xFFFFB877, 0.22f, 0.95f, 0.20f, 0.7f), // dawn
    8f to l(0xFF7FC6EA, 0xFFCFEFF5, 0xFFFFFBEF, 0.08f, 1.02f, 0f, 0.2f),    // morning
    11f to l(0xFF7FCBEA, 0xFFD6F0F4, 0xFFFFFDF3, 0.06f, 1.06f, 0f, 0.1f),   // noon
    16f to l(0xFFFF9E5E, 0xFFFFD79A, 0xFFFF9860, 0.26f, 1.0f, 0.10f, 0.85f),// golden
    19f to l(0xFF6A4A8C, 0xFF2E3A66, 0xFF5A5390, 0.30f, 0.88f, 0.55f, 0.3f),// dusk
    22f to l(0xFF101A3E, 0xFF26355F, 0xFF33447E, 0.34f, 0.78f, 1f, 0f),     // night again
)

private fun lerpL(a: Lighting, b: Lighting, f: Float) = Lighting(
    lerp(a.skyTop, b.skyTop, f), lerp(a.skyBottom, b.skyBottom, f),
    lerp(a.ambient, b.ambient, f),
    a.ambientStrength + (b.ambientStrength - a.ambientStrength) * f,
    a.brightness + (b.brightness - a.brightness) * f,
    a.night + (b.night - a.night) * f,
    a.warmth + (b.warmth - a.warmth) * f,
)

/** Continuous lighting for hour-of-day in [0,24), blending 0.7h either side of boundaries. */
internal fun lightingFor(hf: Float): Lighting {
    val h = ((hf % 24f) + 24f) % 24f
    var idx = 0
    for (i in BUCKETS.indices) if (h >= BUCKETS[i].first) idx = i
    val cur = BUCKETS[idx].second
    val nextStart = if (idx + 1 < BUCKETS.size) BUCKETS[idx + 1].first else 24f
    val next = if (idx + 1 < BUCKETS.size) BUCKETS[idx + 1].second else BUCKETS.first().second
    val toBoundary = nextStart - h
    return if (toBoundary < 0.7f) lerpL(cur, next, 1f - toBoundary / 0.7f) else cur
}

internal fun currentHourFloat(): Float {
    val cal = Calendar.getInstance()
    return cal.get(Calendar.HOUR_OF_DAY) + cal.get(Calendar.MINUTE) / 60f
}

/** 20-minute palette bucket — the static-layer cache key ingredient (§7.11). */
internal fun paletteBucket(): Int {
    val cal = Calendar.getInstance()
    return (cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)) / 20
}

/** Shade a base color by the moment's ambient light + warmth. */
internal fun shade(base: Color, l: Lighting): Color {
    val warm = lerp(base, Color(0xFFFFDCA8), l.warmth * 0.10f)
    val tinted = lerp(warm, l.ambient, l.ambientStrength)
    return Color(
        (tinted.red * l.brightness).coerceIn(0f, 1f),
        (tinted.green * l.brightness).coerceIn(0f, 1f),
        (tinted.blue * l.brightness).coerceIn(0f, 1f),
        base.alpha,
    )
}

internal fun darker(c: Color, f: Float) = Color(c.red * f, c.green * f, c.blue * f, c.alpha)

// ── seasons ──────────────────────────────────────────────────────────────

internal enum class Season { SPRING, SUMMER, AUTUMN, WINTER }

internal fun seasonNow(): Season = when (Calendar.getInstance().get(Calendar.MONTH)) {
    11, 0, 1 -> Season.WINTER
    2, 3, 4 -> Season.SPRING
    5, 6, 7 -> Season.SUMMER
    else -> Season.AUTUMN
}

internal fun foliage(season: Season): Color = when (season) {
    Season.SPRING -> Color(0xFF62C063)
    Season.SUMMER -> Color(0xFF43A047)
    Season.AUTUMN -> Color(0xFFE0862F)
    Season.WINTER -> Color(0xFFA8C4B2)
}

// ── weather ──────────────────────────────────────────────────────────────

internal enum class Weather { CLEAR, CLOUDY, RAIN, SNOW }

internal fun weatherFor(t: Float, season: Season): Weather {
    val phase = (t / 1000f / 80f).toInt() % 5
    return when (season) {
        Season.WINTER -> if (phase >= 3) Weather.SNOW else if (phase == 2) Weather.CLOUDY else Weather.CLEAR
        else -> when (phase) { 2 -> Weather.CLOUDY; 3 -> Weather.RAIN; else -> Weather.CLEAR }
    }
}

// grass at noon (§7.3): radial 3-stop, tinted by season + light at draw time
internal val GRASS_CENTER = Color(0xFF7DCB58)
internal val GRASS_MID = Color(0xFF58A844)
internal val GRASS_EDGE = Color(0xFF47953A)

/** Night grass drifts to a readable moonlit green, never brown (§7.6). */
internal fun grass(base: Color, l: Lighting, season: Season): Color {
    val seasonal = when (season) {
        Season.AUTUMN -> lerp(base, Color(0xFFC7B25A), 0.30f)
        Season.WINTER -> lerp(base, Color(0xFFDDE8EE), 0.45f)
        else -> base
    }
    val night = lerp(seasonal, Color(0xFF3E6B4F), l.night * 0.55f)
    return shade(night, l)
}
