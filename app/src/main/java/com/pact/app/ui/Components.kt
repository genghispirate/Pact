package com.pact.app.ui

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.rotate as rotateDraw
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.pact.app.ui.theme.CardBorder
import com.pact.app.ui.theme.Ink
import com.pact.app.ui.theme.PactGradient
import com.pact.app.ui.theme.Surface1
import com.pact.app.ui.theme.Surface2
import com.pact.app.ui.theme.TextTertiary
import kotlinx.coroutines.delay

/** A clock that ticks once a second, for countdowns. */
@Composable
fun rememberNow(): State<Long> = produceState(initialValue = System.currentTimeMillis()) {
    while (true) {
        value = System.currentTimeMillis()
        delay(1000L)
    }
}

/** A faster clock (4 Hz) for smooth progress rings. */
@Composable
fun rememberNowFast(): State<Long> = produceState(initialValue = System.currentTimeMillis()) {
    while (true) {
        value = System.currentTimeMillis()
        delay(250L)
    }
}

fun formatCountdown(millis: Long): String {
    val total = (millis / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return when {
        h > 0 -> "%dh %02dm".format(h, m)
        m > 0 -> "%dm %02ds".format(m, s)
        else -> "%ds".format(s)
    }
}

/** Primary action: a loud, solid electric-lime pill. Tonal variant for secondary actions. */
@Composable
fun PactButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tonal: Boolean = false,
) {
    val shape = RoundedCornerShape(16.dp)
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    Box(
        modifier = modifier
            .height(56.dp)
            .clip(shape)
            .then(
                if (tonal) Modifier
                    .background(Surface2)
                    .border(1.5.dp, CardBorder, shape)
                else Modifier
                    .background(com.pact.app.ui.theme.Periwinkle)
            )
            .alpha(if (enabled) 1f else 0.4f)
            .clickable(enabled = enabled) {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (tonal) MaterialTheme.colorScheme.onSurface else Ink,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
}

@Composable
fun AppIconImage(drawable: Drawable?, sizeDp: Int, modifier: Modifier = Modifier) {
    val bitmap = drawable?.toBitmap(sizeDp * 3, sizeDp * 3)
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = modifier.size(sizeDp.dp).clip(RoundedCornerShape((sizeDp / 4).dp)),
        )
    } else {
        Box(
            modifier = modifier
                .size(sizeDp.dp)
                .clip(CircleShape)
                .background(Surface2)
        )
    }
}

/**
 * Six-digit code entry rendered as individual cells. A single invisible
 * text field underneath receives input, so paste and system keyboards
 * behave normally.
 */
@Composable
fun CodeInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
    autoFocus: Boolean = false,
) {
    val focusRequester = remember { FocusRequester() }
    if (autoFocus) {
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
    }
    BasicTextField(
        value = value,
        onValueChange = { raw -> onValueChange(raw.filter { it.isDigit() }.take(6)) },
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        cursorBrush = SolidColor(Color.Transparent),
        textStyle = TextStyle(color = Color.Transparent, fontSize = 1.sp),
        modifier = modifier.focusRequester(focusRequester),
        decorationBox = { innerTextField ->
            Box {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(6) { i ->
                        val filled = i < value.length
                        val active = enabled && i == value.length
                        Box(
                            modifier = Modifier
                                .size(width = 46.dp, height = 58.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Surface2)
                                .border(
                                    width = if (active || isError) 2.dp else 1.dp,
                                    color = when {
                                        isError -> MaterialTheme.colorScheme.error
                                        active -> MaterialTheme.colorScheme.primary
                                        filled -> MaterialTheme.colorScheme.outline
                                        else -> CardBorder
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = value.getOrNull(i)?.toString() ?: "",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
                // Invisible input surface covering the cells.
                Box(Modifier.matchParentSize()) { innerTextField() }
            }
        },
    )
}

/** A quick one-shot confetti burst for wins — armed on first composition. */
@Composable
fun Confetti(modifier: Modifier = Modifier) {
    val colors = listOf(
        com.pact.app.ui.theme.Periwinkle,   // lime
        com.pact.app.ui.theme.Violet,        // lavender
        com.pact.app.ui.theme.Rose,          // coral
        Color(0xFFF5F5F5),
    )
    data class Bit(val x: Float, val drift: Float, val fall: Float, val size: Float, val spin: Float, val color: Color)
    val bits = remember {
        List(44) {
            Bit(
                x = kotlin.random.Random.nextFloat(),
                drift = (kotlin.random.Random.nextFloat() - 0.5f) * 0.4f,
                fall = 0.7f + kotlin.random.Random.nextFloat() * 0.6f,
                size = 10f + kotlin.random.Random.nextFloat() * 14f,
                spin = kotlin.random.Random.nextFloat() * 720f,
                color = colors[kotlin.random.Random.nextInt(colors.size)],
            )
        }
    }
    val anim = remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        anim.animateTo(1f, androidx.compose.animation.core.tween(1500))
    }
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val t = anim.value
        if (t >= 1f) return@Canvas
        bits.forEach { b ->
            val x = size.width * (b.x + b.drift * t)
            val y = -30f + (size.height + 60f) * (t * t) * b.fall
            rotateDraw(b.spin * t, androidx.compose.ui.geometry.Offset(x, y)) {
                drawRoundRect(
                    color = b.color.copy(alpha = (1f - t).coerceIn(0f, 1f)),
                    topLeft = androidx.compose.ui.geometry.Offset(x, y),
                    size = androidx.compose.ui.geometry.Size(b.size, b.size * 0.6f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f, 2f),
                )
            }
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = TextTertiary,
        modifier = modifier.padding(horizontal = 4.dp),
    )
}

/** Elevated card: soft surface with a hairline border — the v1.1 house style. */
@Composable
fun PactCard(
    modifier: Modifier = Modifier,
    background: Color = Surface1,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background)
            .border(1.5.dp, CardBorder, shape)
            .padding(20.dp),
        content = content,
    )
}

/** A numbered instruction row for guided setup steps. */
@Composable
fun NumberedStep(number: Int, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(PactGradient),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "$number",
                style = MaterialTheme.typography.labelMedium,
                color = Ink,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp, top = 2.dp),
        )
    }
}
