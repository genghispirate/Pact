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
// v5.8 look: deep near-black with a violet hero accent — a premium, high-
// contrast OLED palette (black + purple, with green for "good/online" and
// coral for "stop"). Uses the reliable system font for maximum stability.
// Names are kept from earlier versions so the whole app re-skins from here.
// ─────────────────────────────────────────────────────────────────────────

val Ink = Color(0xFF08080C)             // deep obsidian-black — app background
val Surface1 = Color(0xFF14141C)        // cards
val Surface2 = Color(0xFF1E1E2A)        // raised
val Surface3 = Color(0xFF2A2A38)        // pressed / chips
val CardBorder = Color(0x14FFFFFF)      // ~8% white hairline

// Violet — the hero accent (buttons, active states, brand).
val Violet = Color(0xFF7C5CFF)
val VioletDeep = Color(0xFF5B3FE0)
val Periwinkle = Color(0xFFA78BFA)      // lighter violet, for accented text/icons
// Green — good / online / positive.
val Mint = Color(0xFF4ADE80)
// Coral — stop / destructive.
val Rose = Color(0xFFFF5C7A)
val Amber = Color(0xFFFBBF24)           // caution

val TextPrimary = Color(0xFFF4F4F8)
val TextSecondary = Color(0xFF9A9AB0)
val TextTertiary = Color(0xFF5C5C70)

/** Signature violet gradient — primary buttons, the FAB, brand marks. */
val PactGradient = Brush.linearGradient(listOf(Color(0xFF8B5CFF), Color(0xFF6D4AFF)))
val PactGradientDeep = Brush.linearGradient(listOf(Violet, VioletDeep))

/** Text/icon colour that sits on top of the violet accent. */
val OnAccent = Color(0xFFFFFFFF)

private val PactColors = darkColorScheme(
    primary = Violet,
    onPrimary = OnAccent,
    primaryContainer = Surface3,
    onPrimaryContainer = TextPrimary,
    secondary = Periwinkle,
    onSecondary = Ink,
    background = Ink,
    onBackground = TextPrimary,
    surface = Surface1,
    onSurface = TextPrimary,
    surfaceVariant = Surface2,
    onSurfaceVariant = TextSecondary,
    outline = Color(0xFF34344A),
    error = Rose,
    onError = OnAccent,
)

private val PactTypography = Typography(
    displaySmall = TextStyle(fontSize = 38.sp, lineHeight = 44.sp, fontWeight = FontWeight.Black, letterSpacing = (-1.0).sp),
    headlineMedium = TextStyle(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.6).sp),
    headlineSmall = TextStyle(fontSize = 23.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
    titleMedium = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.5.sp, lineHeight = 21.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
)

private val PactShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp),
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
