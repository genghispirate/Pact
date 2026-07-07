package com.pact.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pact.app.ui.theme.CardBorder
import com.pact.app.ui.theme.Periwinkle
import com.pact.app.ui.theme.Surface1
import com.pact.app.ui.theme.TextSecondary
import com.pact.app.ui.theme.TextTertiary

private data class Lang(val tag: String, val flag: String, val name: String, val greeting: String)

private val LANGS = listOf(
    Lang("en", "🇺🇸", "English", "Hey"),
    Lang("es", "🇪🇸", "Español", "Hola"),
    Lang("fr", "🇫🇷", "Français", "Salut"),
    Lang("de", "🇩🇪", "Deutsch", "Hallo"),
    Lang("pt", "🇵🇹", "Português", "Oi"),
    Lang("ar", "🇸🇦", "العربية", "مرحبا"),
    Lang("hi", "🇮🇳", "हिन्दी", "नमस्ते"),
    Lang("ru", "🇷🇺", "Русский", "Привет"),
    Lang("ja", "🇯🇵", "日本語", "やあ"),
    Lang("zh", "🇨🇳", "中文", "你好"),
)

/**
 * Page 0.1 — "Where are we dropping?" A spinning globe and big, tappable
 * language pills with native greetings. No dropdowns, no settings trip.
 */
@Composable
fun LanguageScreen(onPick: (String) -> Unit) {
    val spin = rememberInfiniteTransition(label = "globe")
    val angle by spin.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart),
        label = "angle",
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(28.dp))
        Text("🌍", fontSize = 84.sp, modifier = Modifier.align(Alignment.CenterHorizontally).rotate(angle))
        Spacer(Modifier.height(20.dp))
        Text(
            "Where are we\ndropping?",
            style = MaterialTheme.typography.displaySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Pick your language",
            style = MaterialTheme.typography.bodyMedium,
            color = TextTertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(20.dp))
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f),
        ) {
            items(LANGS, key = { it.tag }) { lang ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Surface1)
                        .border(1.5.dp, CardBorder, RoundedCornerShape(16.dp))
                        .clickable { onPick(lang.tag) }
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                ) {
                    Text(lang.flag, fontSize = 30.sp)
                    Spacer(Modifier.width(16.dp))
                    Text(lang.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(lang.greeting, style = MaterialTheme.typography.titleSmall, color = Periwinkle)
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}
