package app.locana.ui.safety

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.locana.data.safety.IncomingQuickMessage
import app.locana.ui.theme.LocalReducedMotion
import app.locana.ui.theme.LocalTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlin.random.Random

/** The emoji that fall for each message, so "Love ya!" rains hearts and "Haha" rains laughter. */
internal fun confettiFor(code: String, fallback: String): List<String> = when (code) {
    "LOVE" -> listOf("❤️", "💖", "💕", "😍")
    "SLOW_DOWN" -> listOf("😳", "💦", "🐢")
    "CALL_ME" -> listOf("📞", "📱", "☎️")
    "WHERE_ARE_YOU" -> listOf("📍", "🔎", "🗺️")
    "ON_MY_WAY" -> listOf("🚗", "💨", "🛣️")
    "BE_CAREFUL" -> listOf("🙏", "🛡️", "⚠️")
    "THANK_YOU" -> listOf("🙌", "✨", "🙏")
    "HAHA" -> listOf("😂", "🤣", "😆")
    else -> listOf(fallback)
}

/**
 * Shows a quick message from someone in a circle: a banner at the top and emoji falling over the whole screen for
 * a few seconds. Touches pass through it. With "remove animations" on, only the banner appears.
 */
@Composable
fun QuickMessageHost(messages: SharedFlow<IncomingQuickMessage>) {
    var current by remember { mutableStateOf<IncomingQuickMessage?>(null) }
    var serial by remember { mutableIntStateOf(0) }
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(messages) {
        messages.collect {
            current = it
            serial++
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
    LaunchedEffect(serial) {
        if (serial == 0) return@LaunchedEffect
        delay(3_800)
        current = null
    }
    Box(Modifier.fillMaxSize()) {
        val m = current
        if (m != null && !LocalReducedMotion.current) androidx.compose.runtime.key(serial) { EmojiConfetti(confettiFor(m.code, m.emoji)) }
        AnimatedVisibility(
            visible = m != null,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = slideInVertically(spring(0.6f, 400f)) { -it } + fadeIn(),
            exit = slideOutVertically(tween(220)) { -it } + fadeOut(tween(180)),
        ) {
            val shown = m ?: return@AnimatedVisibility
            val t = LocalTokens.current
            Surface(
                shape = RoundedCornerShape(24.dp), color = t.card, shadowElevation = 10.dp,
                modifier = Modifier.statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)
                    .semantics { liveRegion = LiveRegionMode.Assertive; contentDescription = "${shown.fromName} says: ${shown.text}" },
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(shown.emoji, fontSize = 30.sp)
                    Column {
                        Text(shown.fromName, style = MaterialTheme.typography.labelMedium, color = t.ink2)
                        Text(shown.text, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold))
                    }
                }
            }
        }
    }
}

private data class Piece(val emoji: String, val x: Float, val size: Float, val start: Float, val speed: Float, val sway: Float, val spin: Float)

/** About thirty emoji falling and swaying once from the top to the bottom of the screen. */
@Composable
private fun EmojiConfetti(emojis: List<String>) {
    val pieces = remember(emojis) {
        val r = Random(emojis.hashCode())
        List(30) { Piece(emojis[it % emojis.size], r.nextFloat(), 22f + r.nextFloat() * 20f, r.nextFloat() * 0.4f, 0.75f + r.nextFloat() * 0.5f, 10f + r.nextFloat() * 30f, (r.nextFloat() - 0.5f) * 240f) }
    }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(3_000, easing = LinearEasing)) }
    val measurer = rememberTextMeasurer()
    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
        val p = progress.value
        pieces.forEach { piece ->
            val local = ((p - piece.start) / (1f - piece.start)).coerceIn(0f, 1f) * piece.speed
            if (local <= 0f || local > 1.15f) return@forEach
            val x = piece.x * size.width + kotlin.math.sin(local * 9f) * piece.sway * density
            val y = -60f + local * (size.height + 140f)
            val alpha = (1.15f - local).coerceIn(0f, 1f).coerceAtMost(1f)
            rotate(piece.spin * local, pivot = androidx.compose.ui.geometry.Offset(x, y)) {
                drawText(measurer, piece.emoji, androidx.compose.ui.geometry.Offset(x, y), style = TextStyle(fontSize = piece.size.sp, color = androidx.compose.ui.graphics.Color.Black.copy(alpha = alpha)))
            }
        }
    }
}
