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
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pact.app.R

// ─────────────────────────────────────────────────────────────────────────
// v5.3 look: "Neo-Brutalism × Tokyo Nightmode." Deep obsidian, one loud
// positive (electric lime), a social lavender, and a hard stop (coral).
// High contrast, sharp-ish edges, heavy type. Names are kept from earlier
// versions so the whole app re-skins by changing these values in one place.
// ─────────────────────────────────────────────────────────────────────────

val Ink = Color(0xFF0F0F0F)             // Deep Obsidian — app background
val Surface1 = Color(0xFF1A1A1A)        // Matte Charcoal — cards
val Surface2 = Color(0xFF242424)        // raised charcoal
val Surface3 = Color(0xFF2E2E2E)        // pressed / chips
val CardBorder = Color(0x1FFFFFFF)      // ~12% white — brutalist hairline

// Electric Lime — the one "go / positive / primary" colour.
val Periwinkle = Color(0xFFCCFF00)
val Mint = Color(0xFFCCFF00)            // success reads as the same "go"
// Digital Lavender — social, chat, secondary.
val Violet = Color(0xFFB19CD9)
val VioletDeep = Color(0xFF8B7BC0)
// Neon Coral — stop / warning.
val Rose = Color(0xFFFF6B6B)
val Amber = Color(0xFFFFC24D)           // caution (distinct from the coral stop)

val TextPrimary = Color(0xFFF5F5F5)
val TextSecondary = Color(0xFF9A9A9A)
val TextTertiary = Color(0xFF5E5E5E)

/** Signature gradient — used sparingly for brand marks and selected chips. */
val PactGradient = Brush.linearGradient(listOf(Periwinkle, Violet))
val PactGradientDeep = Brush.linearGradient(listOf(Violet, VioletDeep))

// ─────────────────────────────────── type: Space Grotesk (display) + Inter

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun grotesk(weight: Int) = Font(
    R.font.space_grotesk, FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun inter(weight: Int) = Font(
    R.font.inter, FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val SpaceGrotesk = FontFamily(grotesk(500), grotesk(600), grotesk(700))
val Inter = FontFamily(inter(400), inter(500), inter(600), inter(700))

private val PactColors = darkColorScheme(
    primary = Periwinkle,
    onPrimary = Ink,
    primaryContainer = Surface3,
    onPrimaryContainer = TextPrimary,
    secondary = Violet,
    onSecondary = Ink,
    background = Ink,
    onBackground = TextPrimary,
    surface = Surface1,
    onSurface = TextPrimary,
    surfaceVariant = Surface2,
    onSurfaceVariant = TextSecondary,
    outline = Color(0xFF3A3A3A),
    error = Rose,
    onError = Ink,
)

private val PactTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = SpaceGrotesk, fontSize = 38.sp, lineHeight = 42.sp,
        fontWeight = FontWeight(700), letterSpacing = (-1.0).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = SpaceGrotesk, fontSize = 30.sp, lineHeight = 34.sp,
        fontWeight = FontWeight(700), letterSpacing = (-0.8).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = SpaceGrotesk, fontSize = 23.sp, lineHeight = 28.sp,
        fontWeight = FontWeight(700), letterSpacing = (-0.5).sp
    ),
    titleMedium = TextStyle(
        fontFamily = SpaceGrotesk, fontSize = 18.sp, lineHeight = 24.sp,
        fontWeight = FontWeight(600), letterSpacing = (-0.2).sp
    ),
    titleSmall = TextStyle(
        fontFamily = SpaceGrotesk, fontSize = 16.sp, lineHeight = 22.sp,
        fontWeight = FontWeight(600), letterSpacing = 0.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = Inter, fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight(400)
    ),
    bodyMedium = TextStyle(
        fontFamily = Inter, fontSize = 14.5.sp, lineHeight = 21.sp, fontWeight = FontWeight(400)
    ),
    labelLarge = TextStyle(
        fontFamily = SpaceGrotesk, fontSize = 16.sp, lineHeight = 20.sp,
        fontWeight = FontWeight(700), letterSpacing = 0.3.sp
    ),
    labelMedium = TextStyle(
        fontFamily = Inter, fontSize = 12.sp, lineHeight = 16.sp,
        fontWeight = FontWeight(600), letterSpacing = 0.6.sp
    ),
)

// Sharper than before — brutalist, but not literally square (keeps it modern).
private val PactShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun PactTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PactColors,
        typography = PactTypography,
        shapes = PactShapes,
    ) {
        // Surface establishes LocalContentColor. Without it, any Text that
        // doesn't set an explicit color falls back to black — invisible on
        // our dark background.
        Surface(
            color = PactColors.background,
            contentColor = PactColors.onBackground,
        ) {
            content()
        }
    }
}
