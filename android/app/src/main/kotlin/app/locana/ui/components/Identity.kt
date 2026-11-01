package app.locana.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Museum
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.locana.ui.theme.LocalHero
import app.locana.ui.theme.LocalReducedMotion
import app.locana.ui.theme.Motion
import kotlin.random.Random

/**
 * Press feedback (emil: buttons must feel responsive). Scales to 0.97 while pressed with a
 * 120 ms strong ease-out, so a tap is acknowledged the moment the finger lands. Used on cards
 * that act as buttons; Material buttons keep their ripple. Off with reduced motion.
 */
fun Modifier.pressScale(source: MutableInteractionSource): Modifier = composed {
    val pressed by source.collectIsPressedAsState()
    val reduced = LocalReducedMotion.current
    val scale by animateFloatAsState(if (pressed && !reduced) 0.97f else 1f, tween(Motion.PRESS_MS, easing = Motion.EaseOut), label = "press")
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * The "night map" panel that holds a tab's focal point. Navy in both themes (see HeroColors);
 * the large radius sets it apart from ordinary 14 dp cards.
 */
@Composable
fun HeroPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val hero = LocalHero.current
    Surface(color = hero.background, contentColor = hero.text, shape = MaterialTheme.shapes.extraLarge, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), content = content)
    }
}

/** Stable pseudo-random order, so tiles light up scattered like a map being uncovered, the same way every time. */
private fun revealOrder(count: Int, seed: Int): IntArray {
    val idx = IntArray(count) { it }
    val r = Random(seed)
    for (i in count - 1 downTo 1) {
        val j = r.nextInt(i + 1)
        val t = idx[i]; idx[i] = idx[j]; idx[j] = t
    }
    return idx
}

/**
 * Identity motif: a small field of map tiles where the lit ones are real data (tiles the player
 * revealed, quests done today). Never decoration on its own; the label next to it carries the number.
 * [animateIn] plays a one-time staggered reveal (onboarding, first view of a reward); it is skipped
 * with reduced motion.
 */
@Composable
fun TileField(
    lit: Int,
    modifier: Modifier = Modifier,
    columns: Int = 12,
    rows: Int = 4,
    tileSize: Dp = 14.dp,
    gap: Dp = 4.dp,
    litColor: Color = LocalHero.current.accent,
    hiddenColor: Color = LocalHero.current.tileHidden,
    animateIn: Boolean = false,
    seed: Int = 7,
    description: String? = null,
) {
    val total = columns * rows
    val order = remember(total, seed) { revealOrder(total, seed) }
    val litCount = lit.coerceIn(0, total)
    val reduced = LocalReducedMotion.current
    val progress = remember(litCount) { Animatable(if (animateIn && !reduced) 0f else litCount.toFloat()) }
    LaunchedEffect(litCount, animateIn) {
        // 40 ms per tile stagger, capped so a big field still finishes inside ~900 ms.
        if (animateIn && !reduced) progress.animateTo(litCount.toFloat(), tween(durationMillis = (litCount * 40).coerceIn(200, 900), easing = Motion.EaseOut))
    }
    val lane = tileSize + gap
    Canvas(
        modifier
            .size(width = lane * columns - gap, height = lane * rows - gap)
            .semantics { description?.let { contentDescription = it } },
    ) {
        val t = tileSize.toPx()
        val g = gap.toPx()
        val r = CornerRadius(3.dp.toPx())
        val shown = progress.value
        val litSet = BooleanArray(total)
        for (i in 0 until litCount) litSet[order[i]] = true
        var rank = 0
        val rankOf = IntArray(total)
        for (i in 0 until total) if (litSet[order[i]]) { rankOf[order[i]] = rank++ }
        for (cell in 0 until total) {
            val x = (cell % columns) * (t + g)
            val y = (cell / columns) * (t + g)
            drawRoundRect(hiddenColor, Offset(x, y), Size(t, t), r)
            if (litSet[cell]) {
                // Each lit tile fades and grows from 0.9 as the reveal reaches it (never from 0).
                val a = (shown - rankOf[cell]).coerceIn(0f, 1f)
                if (a > 0f) {
                    val s = 0.9f + 0.1f * a
                    val inset = t * (1 - s) / 2
                    drawRoundRect(litColor.copy(alpha = litColor.alpha * a), Offset(x + inset, y + inset), Size(t * s, t * s), r)
                }
            }
        }
    }
}

/** One square icon per quest condition: the card says what kind of outing it is before you read it. */
fun conditionIcon(condition: String): ImageVector = when (condition) {
    "DISCOVER_TILES" -> Icons.Outlined.GridView
    "VISIT_LANDMARKS" -> Icons.Outlined.Museum
    "COLLECT_LANDMARK_SET" -> Icons.Outlined.Collections
    "WALK_DISTANCE" -> Icons.AutoMirrored.Outlined.DirectionsWalk
    "CIRCLE_TILES" -> Icons.Outlined.Groups
    "ACTIVE_DAYS" -> Icons.Outlined.CalendarMonth
    else -> Icons.Outlined.GridView
}

@Composable
fun IconTile(icon: ImageVector, container: Color, content: Color, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    Box(modifier.size(size).background(container, MaterialTheme.shapes.small), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(size * 0.5f))
    }
}

/** Overlapping avatars for a circle header; the count after them stays readable text. */
@Composable
fun AvatarStack(people: List<Pair<String, String?>>, modifier: Modifier = Modifier, size: Dp = 36.dp, max: Int = 5, ring: Color = LocalHero.current.background) {
    Box(modifier) {
        people.take(max).forEachIndexed { i, (name, key) ->
            Avatar(
                name, key, size = size,
                modifier = Modifier
                    .offset(x = (size * 0.7f) * i)
                    .border(2.dp, ring, CircleShape),
            )
        }
        Box(Modifier.size(width = (size * 0.7f) * (people.take(max).size - 1).coerceAtLeast(0) + size, height = size))
    }
}

/**
 * Counts up to [value] once (rare reward moments only: claim and level up). 600 ms strong
 * ease-out; reduced motion shows the final number at once.
 */
@Composable
fun RollingNumber(value: Int, style: TextStyle, color: Color, prefix: String = "", suffix: String = "") {
    val reduced = LocalReducedMotion.current
    val n = remember(value) { Animatable(if (reduced) value.toFloat() else 0f) }
    LaunchedEffect(value) { if (!reduced) n.animateTo(value.toFloat(), tween(600, easing = Motion.EaseOut)) }
    Text("$prefix${n.value.toInt()}$suffix", style = style, color = color, modifier = Modifier.semantics { contentDescription = "$prefix$value$suffix" })
}

/** Buttons for use on a HeroPanel: teal fill for the main action, outlined for the rest. */
@Composable
fun HeroPrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val hero = LocalHero.current
    androidx.compose.material3.Button(
        onClick = onClick,
        shape = ButtonShape,
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = hero.accent, contentColor = app.locana.ui.theme.Palette.Navy950),
        modifier = modifier.heightIn(min = 48.dp),
    ) { Text(text) }
}

@Composable
fun HeroSecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val hero = LocalHero.current
    androidx.compose.material3.OutlinedButton(
        onClick = onClick,
        shape = ButtonShape,
        border = androidx.compose.foundation.BorderStroke(1.dp, hero.muted),
        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = hero.text),
        modifier = modifier.heightIn(min = 48.dp),
    ) { Text(text) }
}
