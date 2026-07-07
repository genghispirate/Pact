package com.pact.app.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.pact.app.ui.theme.CardBorder
import com.pact.app.ui.theme.Ink
import com.pact.app.ui.theme.Mint
import com.pact.app.ui.theme.PactGradient
import com.pact.app.ui.theme.Periwinkle
import com.pact.app.ui.theme.Surface1
import com.pact.app.ui.theme.Surface2
import com.pact.app.ui.theme.Violet
import com.pact.app.ui.theme.VioletDeep
import kotlin.math.cos
import kotlin.math.sin

/**
 * Decorative hero illustrations for the intro pages, drawn entirely in
 * Compose — layered shapes, soft glows, and slow ambient motion. No image
 * assets, crisp at any density, tiny APK cost, and every mark is on-brand.
 */

private const val ART_SIZE = 280

@Composable
private fun ArtStage(glow: Color, content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit) {
    // A soft radial glow behind a floating stage, gently drifting.
    val transition = rememberInfiniteTransition(label = "art")
    val drift by transition.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(tween(4200), RepeatMode.Reverse),
        label = "drift",
    )
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(ART_SIZE.dp)) {
        Box(
            modifier = Modifier
                .size((ART_SIZE - 20).dp)
                .blur(60.dp)
                .background(
                    Brush.radialGradient(listOf(glow.copy(alpha = 0.5f), glow.copy(alpha = 0f))),
                    CircleShape,
                )
        )
        Box(
            modifier = Modifier.offset(y = drift.dp),
            contentAlignment = Alignment.Center,
            content = content,
        )
    }
}

/** A soft-shadowed tile with a rounded gradient, like a premium app icon. */
@Composable
private fun GlassTile(sizeDp: Int, corner: Int, gradient: Brush, content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit = {}) {
    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .clip(RoundedCornerShape(corner.dp))
            .background(gradient)
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(corner.dp)),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

private val TILE_COLORS = listOf(
    Color(0xFFFF6B81), Color(0xFFFFB65C), Color(0xFF5EEAD4),
    Color(0xFFA5B8FF), Color(0xFFF472B6), Color(0xFF7DD3FC),
    Color(0xFF8E7CFF), Color(0xFF60D394), Color(0xFFFACC15),
)

/** Page 1: a phone full of glowing app tiles, sealed by a big lock badge. */
@Composable
fun ArtLockedPhone() {
    ArtStage(glow = Violet) {
        Box(contentAlignment = Alignment.Center) {
            // phone body
            Column(
                verticalArrangement = Arrangement.spacedBy(11.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .size(width = 150.dp, height = 216.dp)
                    .clip(RoundedCornerShape(34.dp))
                    .background(Brush.verticalGradient(listOf(Surface2, Surface1)))
                    .border(1.5.dp, CardBorder, RoundedCornerShape(34.dp))
                    .padding(top = 22.dp),
            ) {
                repeat(3) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                        repeat(3) { col ->
                            val c = TILE_COLORS[row * 3 + col]
                            GlassTile(
                                sizeDp = 30,
                                corner = 10,
                                gradient = Brush.linearGradient(listOf(c, c.copy(alpha = 0.72f))),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Box(
                    Modifier
                        .size(width = 46.dp, height = 5.dp)
                        .clip(CircleShape)
                        .background(Surface2)
                )
            }
            // frosted seal over the grid
            Box(
                modifier = Modifier
                    .size(width = 150.dp, height = 216.dp)
                    .clip(RoundedCornerShape(34.dp))
                    .background(Ink.copy(alpha = 0.35f))
            )
            // lock badge
            GlassTile(sizeDp = 76, corner = 26, gradient = PactGradient) {
                Icon(Icons.Rounded.Lock, contentDescription = null, tint = Ink, modifier = Modifier.size(38.dp))
            }
        }
    }
}

/** Page 2: you at the centre, your circle of trusted people orbiting around. */
@Composable
fun ArtCircle() {
    val transition = rememberInfiniteTransition(label = "circle")
    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(38000), RepeatMode.Restart),
        label = "spin",
    )
    ArtStage(glow = Periwinkle) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(ART_SIZE.dp)) {
            // dashed orbit ring
            Canvas(Modifier.size(210.dp)) {
                drawCircle(
                    color = Color(0xFF3A4568),
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 9.dp.toPx())),
                    ),
                )
            }
            // orbiting avatars
            val radius = 105f
            val tints = listOf(Periwinkle, Mint, Color(0xFFFFB65C), Color(0xFFF472B6), Violet)
            tints.forEachIndexed { i, tint ->
                val angle = Math.toRadians((spin + i * (360.0 / tints.size)))
                Box(
                    modifier = Modifier
                        .offset(
                            x = (radius * cos(angle)).dp,
                            y = (radius * sin(angle)).dp,
                        ),
                ) {
                    AvatarChip(tint)
                }
            }
            // you, at the centre, holding a heart
            GlassTile(sizeDp = 84, corner = 42, gradient = PactGradient) {
                Icon(Icons.Rounded.Favorite, contentDescription = null, tint = Ink, modifier = Modifier.size(38.dp))
            }
        }
    }
}

@Composable
private fun AvatarChip(tint: Color) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Surface1)
            .border(2.dp, tint.copy(alpha = 0.8f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Person, contentDescription = null, tint = tint, modifier = Modifier.size(26.dp))
    }
}

/** Page 3: a shield wrapped in an encrypted ring — private by design. */
@Composable
fun ArtEncryptedShield() {
    val transition = rememberInfiniteTransition(label = "shield")
    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(26000), RepeatMode.Restart),
        label = "ringspin",
    )
    ArtStage(glow = Mint) {
        Box(contentAlignment = Alignment.Center) {
            // rotating encryption ring: dashes + bit dots
            Canvas(
                Modifier
                    .size(232.dp)
                    .rotate(spin)
            ) {
                drawCircle(
                    color = Color(0xFF3A4568),
                    style = Stroke(
                        width = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 12.dp.toPx())),
                    ),
                )
                val r = size.minDimension / 2f
                val cx = size.width / 2f
                val cy = size.height / 2f
                listOf(Periwinkle, Mint, Violet, Color(0xFFFFB65C)).forEachIndexed { i, c ->
                    val a = Math.toRadians(i * 90.0)
                    drawCircle(
                        color = c,
                        radius = 4.dp.toPx(),
                        center = Offset(cx + (r * cos(a)).toFloat(), cy + (r * sin(a)).toFloat()),
                    )
                }
            }
            // inner counter-rotating ring
            Canvas(
                Modifier
                    .size(168.dp)
                    .rotate(-spin * 1.4f)
            ) {
                drawCircle(
                    color = Color(0xFF2A3554),
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 8.dp.toPx())),
                        cap = StrokeCap.Round,
                    ),
                )
            }
            // shield
            GlassTile(sizeDp = 116, corner = 38, gradient = PactGradient) {
                Icon(
                    Icons.Rounded.Shield,
                    contentDescription = null,
                    tint = Color(0xFFF4F6FF),
                    modifier = Modifier.size(56.dp),
                )
                Icon(
                    Icons.Rounded.Bolt,
                    contentDescription = null,
                    tint = VioletDeep,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}
