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

// Pact is deliberately dark-only: calm, low-stimulation, consistent.
// v1.1 palette: deeper space-blue base, higher-contrast surfaces, and an
// indigo→violet signature gradient used for primary actions and the brand.

val Ink = Color(0xFF070A14)
val Surface1 = Color(0xFF141A2E)
val Surface2 = Color(0xFF1E2742)
val Surface3 = Color(0xFF2A3554)
val CardBorder = Color(0x14FFFFFF)      // 8% white hairline on cards
val Periwinkle = Color(0xFFA5B8FF)
val Violet = Color(0xFF8E7CFF)
val VioletDeep = Color(0xFF6C5CE7)
val Mint = Color(0xFF5EEAD4)
val Amber = Color(0xFFFFC97E)
val Rose = Color(0xFFFF8093)
val TextPrimary = Color(0xFFF2F5FF)
val TextSecondary = Color(0xFFAAB4D4)
val TextTertiary = Color(0xFF7C87AB)

/** Signature gradient — buttons, brand marks, highlights. */
val PactGradient = Brush.linearGradient(listOf(Periwinkle, Violet))
val PactGradientDeep = Brush.linearGradient(listOf(Violet, VioletDeep))

private val PactColors = darkColorScheme(
    primary = Periwinkle,
    onPrimary = Ink,
    primaryContainer = Surface3,
    onPrimaryContainer = TextPrimary,
    secondary = Mint,
    onSecondary = Ink,
    background = Ink,
    onBackground = TextPrimary,
    surface = Surface1,
    onSurface = TextPrimary,
    surfaceVariant = Surface2,
    onSurfaceVariant = TextSecondary,
    outline = Color(0xFF3A4568),
    error = Rose,
    onError = Ink,
)

private val PactTypography = Typography(
    displaySmall = TextStyle(
        fontSize = 36.sp, lineHeight = 42.sp,
        fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp
    ),
    headlineMedium = TextStyle(
        fontSize = 28.sp, lineHeight = 34.sp,
        fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp
    ),
    headlineSmall = TextStyle(
        fontSize = 22.sp, lineHeight = 28.sp,
        fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp
    ),
    titleMedium = TextStyle(
        fontSize = 18.sp, lineHeight = 25.sp,
        fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp
    ),
    titleSmall = TextStyle(
        fontSize = 16.sp, lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp
    ),
    bodyLarge = TextStyle(
        fontSize = 16.sp, lineHeight = 25.sp, fontWeight = FontWeight.Normal
    ),
    bodyMedium = TextStyle(
        fontSize = 14.5.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal
    ),
    labelLarge = TextStyle(
        fontSize = 16.sp, lineHeight = 21.sp,
        fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp
    ),
    labelMedium = TextStyle(
        fontSize = 12.sp, lineHeight = 16.sp,
        fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp
    ),
)

private val PactShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(34.dp),
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
