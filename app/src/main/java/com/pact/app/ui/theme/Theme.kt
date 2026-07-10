package com.pact.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ─────────────────────────────────────────────────────────────────────────
// v6 "Haven" tokens — see DESIGN.md §3. This file is the only place colors,
// type, and shapes are defined. One accent per card; Mint = good only;
// Coral = destructive only; Sky = screen-time data; hairlines always 1dp.
// Old token names are kept as aliases so screens migrate milestone by
// milestone without breaking.
// ─────────────────────────────────────────────────────────────────────────

// backgrounds
val Ink = Color(0xFF0A0A10)             // app background (lifted from pure black)
val Surface1 = Color(0xFF15151D)        // cards
val Surface2 = Color(0xFF1D1D27)        // raised elements, chips, inputs
val Surface3 = Color(0xFF262633)        // pressed states
val Hairline = Color(0x0FFFFFFF)        // 6% white — ALL borders, 1dp always

// brand & semantic accents
val Violet = Color(0xFF7C5CFF)          // interactive, brand, focus
val VioletDeep = Color(0xFF5B3FE0)      // gradient end only
val VioletGhost = Color(0x247C5CFF)     // 14% violet — selected-state tints
val Mint = Color(0xFF4ADE80)            // success, positive delta, streaks
val Amber = Color(0xFFFFC24B)           // caution
val Coral = Color(0xFFFF6B85)           // destructive buttons ONLY
val Sky = Color(0xFF6FC3FF)             // screen-time data accent

// text
val TextHi = Color(0xFFF5F5FA)
val TextMid = Color(0xFFA7A7BC)
val TextLow = Color(0xFF62627A)

/** Signature violet gradient — primary CTAs and the world FAB. Nowhere else. */
val PactGradient = Brush.linearGradient(listOf(Color(0xFF8B5CFF), Color(0xFF6D4AFF)))
val PactGradientDeep = Brush.linearGradient(listOf(Violet, VioletDeep))

/** Text/icon colour on top of the violet accent. */
val OnAccent = Color(0xFFFFFFFF)

// ── aliases (legacy names; migrate off these screen by screen) ───────────
val CardBorder = Hairline
val Rose = Coral
val Periwinkle = Violet
val TextPrimary = TextHi
val TextSecondary = TextMid
val TextTertiary = TextLow

private val PactColors = darkColorScheme(
    primary = Violet,
    onPrimary = OnAccent,
    primaryContainer = Surface3,
    onPrimaryContainer = TextHi,
    secondary = Violet,
    onSecondary = OnAccent,
    background = Ink,
    onBackground = TextHi,
    surface = Surface1,
    onSurface = TextHi,
    surfaceVariant = Surface2,
    onSurfaceVariant = TextMid,
    outline = Color(0xFF30303F),
    error = Coral,
    onError = OnAccent,
)

// DESIGN.md §3.2 — sentence case everywhere; no weight above Bold;
// ALL-CAPS only via labelMedium section labels.
private val PactTypography = Typography(
    displaySmall = TextStyle(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp),
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
    titleMedium = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    titleSmall = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.8.sp),
)

// DESIGN.md §3.3 — buttons 16, cards 20, world card 24, sheets 28.
private val PactShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun PactTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PactColors,
        typography = PactTypography,
        shapes = PactShapes,
    ) {
        Surface(color = PactColors.background, contentColor = PactColors.onBackground) {
            content()
        }
    }
}
