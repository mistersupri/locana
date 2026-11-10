package app.locana.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.locana.ui.theme.Display
import app.locana.ui.theme.LocalDarkTheme
import app.locana.ui.theme.LocalReducedMotion
import app.locana.ui.theme.LocalTokens
import app.locana.ui.theme.Motion
import app.locana.ui.theme.enterRise
import app.locana.ui.theme.popIn

/*
 * The mockup's building blocks: chunky buttons with a solid edge underneath, cards with a soft shadow,
 * pill chips, rows with a tinted icon tile. Press feedback is a 3 dp sink of the face over the edge (a
 * transform, 90 ms, strong ease-out); with reduced motion it is instant.
 */

val ChunkyShape = RoundedCornerShape(18.dp)
val CardShape = RoundedCornerShape(24.dp)
private val EdgeDepth = 4.dp

enum class ChunkyStyle { Primary, Secondary, Yellow, Coral }

@Composable
fun ChunkyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ChunkyStyle = ChunkyStyle.Primary,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
    compact: Boolean = false,
) {
    val t = LocalTokens.current
    val scheme = MaterialTheme.colorScheme
    val (face, edge, content) = when (style) {
        ChunkyStyle.Primary -> Triple(scheme.primary, t.primaryDark, scheme.onPrimary)
        ChunkyStyle.Secondary -> Triple(t.card, t.edge, scheme.primary)
        ChunkyStyle.Yellow -> Triple(t.yellow, t.yellowDark, Color(0xFF3A2800))
        ChunkyStyle.Coral -> Triple(t.coral, t.coralDark, Color.White)
    }
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val reduced = LocalReducedMotion.current
    val sink by animateFloatAsState(if (pressed && enabled) 1f else 0f, if (reduced) tween(0) else tween(90, easing = Motion.EaseOut), label = "sink")
    val density = androidx.compose.ui.platform.LocalDensity.current
    val dim = if (enabled || loading) 1f else 0.5f
    Box(modifier.padding(bottom = EdgeDepth).graphicsLayer { alpha = dim }, propagateMinConstraints = true) {
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { translationY = with(density) { EdgeDepth.toPx() } }
                .background(edge, ChunkyShape),
        )
        Surface(
            onClick = onClick,
            enabled = enabled && !loading,
            shape = ChunkyShape,
            color = face,
            contentColor = content,
            interactionSource = source,
            modifier = Modifier
                .heightIn(min = if (compact) 44.dp else 54.dp)
                .graphicsLayer { translationY = with(density) { (EdgeDepth - 1.dp).toPx() } * sink },
        ) {
            Row(Modifier.padding(horizontal = if (compact) 16.dp else 20.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                if (loading) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = content)
                    Spacer(Modifier.size(10.dp))
                } else if (icon != null) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.size(8.dp))
                }
                Text(text, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold, fontSize = if (compact) 14.sp else 16.sp), textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** The white square back button of the mockup, 42 dp. */
@Composable
fun SquareIconButton(onClick: () -> Unit, contentDescription: String, modifier: Modifier = Modifier, icon: ImageVector = Icons.AutoMirrored.Filled.ArrowBack) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = LocalTokens.current.card,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 3.dp,
        modifier = modifier.size(42.dp),
    ) { Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(20.dp)) } }
}

/** Back button, title in Bricolage 800 and an optional text action on the right. */
@Composable
fun LocanaTopBar(title: String, onBack: (() -> Unit)?, modifier: Modifier = Modifier, action: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            SquareIconButton(onBack, "Back")
            Spacer(Modifier.width(14.dp))
        }
        Text(title, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        action()
    }
}

/** A white card with the mockup's soft shadow. Pass [onClick] to make the whole card a button. */
@Composable
fun LocanaCard(
    modifier: Modifier = Modifier,
    color: Color = LocalTokens.current.card,
    shape: androidx.compose.ui.graphics.Shape = CardShape,
    onClick: (() -> Unit)? = null,
    border: BorderStroke? = null,
    shadow: Dp = 4.dp,
    content: @Composable () -> Unit,
) {
    if (onClick != null) {
        val source = remember { MutableInteractionSource() }
        Surface(onClick = onClick, shape = shape, color = color, shadowElevation = shadow, border = border, interactionSource = source, modifier = modifier.enterRise().pressScale(source)) { content() }
    } else {
        Surface(shape = shape, color = color, shadowElevation = shadow, border = border, modifier = modifier.enterRise()) { content() }
    }
}

/** Pill chip. Selected chips are filled with the ink colour (violet in dark), others are white. */
@Composable
fun LocanaChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, iconTint: Color? = null) {
    val scheme = MaterialTheme.colorScheme
    val t = LocalTokens.current
    val dark = LocalDarkTheme.current
    val container by animateColorAsState(if (selected) (if (dark) scheme.primary else scheme.primary) else t.card, tween(160), label = "chip")
    val content by animateColorAsState(if (selected) scheme.onPrimary else scheme.onSurface, tween(160), label = "chipText")
    Surface(onClick = onClick, shape = RoundedCornerShape(999.dp), color = container, contentColor = content, shadowElevation = if (selected) 0.dp else 2.dp, modifier = modifier.heightIn(min = 40.dp)) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (icon != null) Icon(icon, contentDescription = null, tint = if (selected) content else (iconTint ?: content), modifier = Modifier.size(18.dp))
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Violet switch with a white knob, as in the mockup. */
@Composable
fun LocanaSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val scheme = MaterialTheme.colorScheme
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = scheme.primary,
            checkedBorderColor = Color.White,
            uncheckedThumbColor = Color.White,
            uncheckedTrackColor = LocalTokens.current.ink3.copy(alpha = 0.45f),
            uncheckedBorderColor = Color.White,
        ),
    )
}

/** Yellow pill with a star: "+30 XP". */
@Composable
fun XpBadge(text: String, modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    Row(modifier.clip(RoundedCornerShape(999.dp)).background(t.yellowSoft).padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(Icons.Filled.Star, contentDescription = null, tint = t.yellowDark, modifier = Modifier.size(13.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = t.onYellowSoft)
    }
}

/** A small tinted pill: "Rare", "Epic", "Owner". */
@Composable
fun TagPill(text: String, container: Color, content: Color, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Row(modifier.clip(RoundedCornerShape(999.dp)).background(container).padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (icon != null) Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(13.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = content)
    }
}

/** Row card: tinted icon tile, title and subtitle, chevron. The list rows of the mockup. */
@Composable
fun ListRowCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    tileColor: Color = MaterialTheme.colorScheme.primaryContainer,
    tileTint: Color = MaterialTheme.colorScheme.primary,
    trailing: @Composable (() -> Unit)? = { Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = LocalTokens.current.ink3, modifier = Modifier.size(20.dp)) },
    onClick: (() -> Unit)? = null,
) {
    LocanaCard(modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), onClick = onClick, shadow = 2.dp) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                IconTile(icon, tileColor, tileTint)
                Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.ExtraBold))
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            trailing?.let { it() }
        }
    }
}

/**
 * The mockup's 3D illustrations are image files that were not part of the design upload. Drop PNG or WebP
 * files named after the asset into res/drawable (ill_mascot, ill_house, ill_city, ill_office, ill_monas,
 * ill_trophy, ill_shield, ill_car, ill_mailbox, ill_friends, ill_phone, ill_sneaker, ill_compass,
 * ill_chest) and they replace the emoji shown here, with no code change.
 */
@Composable
fun Illustration(name: String, modifier: Modifier = Modifier, size: Dp = 96.dp) {
    val context = LocalContext.current
    val res = remember(name) { context.resources.getIdentifier("ill_$name", "drawable", context.packageName) }
    if (res != 0) {
        Image(painterResource(res), contentDescription = null, contentScale = ContentScale.Fit, modifier = modifier.size(size).popIn(120))
    } else {
        Box(modifier.size(size), contentAlignment = Alignment.Center) {
            Text(illustrationEmoji(name), fontSize = (size.value * 0.62f).sp, fontFamily = Display)
        }
    }
}

private fun illustrationEmoji(name: String) = when (name) {
    "mascot" -> "📍"
    "city" -> "🏙️"
    "house" -> "🏠"
    "office" -> "🏢"
    "monas" -> "🗼"
    "trophy" -> "🏆"
    "shield" -> "🛡️"
    "car" -> "🚗"
    "mailbox" -> "📬"
    "friends" -> "🧑‍🤝‍🧑"
    "phone" -> "📱"
    "sneaker" -> "👟"
    "compass" -> "🧭"
    "chest" -> "🎁"
    else -> "✨"
}

/** Section heading used between groups of cards. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) Text(action, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable(role = Role.Button, onClick = onAction).padding(8.dp))
    }
}

/** A segmented control: a white bar with the chosen option in a violet pill (2 hours, 2 days, 7 days). */
@Composable
fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(LocalTokens.current.card).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { (value, label) ->
            val on = value == selected
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (on) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable(role = Role.RadioButton, onClick = { onSelect(value) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}
