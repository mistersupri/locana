package app.locana.ui.map

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.locana.domain.Formatting
import app.locana.domain.Freshness
import app.locana.domain.Presence
import app.locana.ui.components.presenceLabel
import app.locana.ui.components.pressScale
import app.locana.ui.theme.AvatarColors
import app.locana.ui.theme.Display
import app.locana.ui.theme.LocalDarkTheme
import app.locana.ui.theme.LocalReward

/*
 * Map overlays share one language, taken from the owner's reference screenshot: white rounded
 * controls with a soft shadow floating over a light map, a bold circle name, and a bottom sheet.
 * Radius 16 dp for controls (they are buttons, so clearly squarer than the 28 dp sheet).
 */

/** White in the light theme, a lifted dark surface in the dark theme. */
@Composable
fun floatingColor(): Color =
    if (LocalDarkTheme.current) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLowest

val ControlShape = RoundedCornerShape(16.dp)

/** Square floating button. The caller puts an [Icon] with a content description inside. */
@Composable
fun MapControl(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val source = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        interactionSource = source,
        shape = ControlShape,
        color = floatingColor(),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 6.dp,
        modifier = modifier.size(52.dp).pressScale(source),
    ) { Box(contentAlignment = Alignment.Center) { content() } }
}

/** Floating action with a label, like the "Check in" and "SOS" pills in the reference. */
@Composable
fun MapPill(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val source = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        interactionSource = source,
        shape = ControlShape,
        color = floatingColor(),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 6.dp,
        modifier = modifier.heightIn(min = 52.dp).pressScale(source),
    ) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * Measures its content and reports twice the height, placing the content in the top half. The
 * bottom of the content then sits exactly at the vertical centre, which is where the map anchors a
 * marker, so the tip of a pin lands on the member's coordinate instead of its head.
 */
@Composable
private fun TipAnchored(content: @Composable () -> Unit) {
    Layout(content = content) { measurables, constraints ->
        val p = measurables.single().measure(constraints.copy(minWidth = 0, minHeight = 0))
        layout(p.width, p.height * 2) { p.placeRelative(0, 0) }
    }
}

private fun pinPath(width: Float, height: Float): Path = Path().apply {
    val d = width
    addOval(Rect(0f, 0f, d, d))
    val tail = Path().apply {
        moveTo(d * 0.5f - d * 0.17f, d * 0.88f)
        lineTo(d * 0.5f, height)
        lineTo(d * 0.5f + d * 0.17f, d * 0.88f)
        close()
    }
    op(this, tail, androidx.compose.ui.graphics.PathOperation.Union)
}

/**
 * A member on the map: initials in a pin whose tip marks the position. A solid white edge means a
 * fresh position; a dashed edge plus an age bubble means the last known position is old. Shape,
 * not only colour, carries the difference. Frame cosmetics turn the edge amber.
 */
@Composable
fun MemberPinMarker(name: String, avatarKey: String?, presence: Presence, framed: Boolean, recordedAtMs: Long?) {
    val (bg, fg) = AvatarColors[avatarKey] ?: AvatarColors.getValue("explorer_slate")
    val edge = if (framed) LocalReward.current.reward else Color.White
    val dashColor = MaterialTheme.colorScheme.outline
    val stale = presence == Presence.STALE
    val bubble = if (stale && recordedAtMs != null) Freshness.ageLabel(recordedAtMs, System.currentTimeMillis()) else null
    val head: Dp = 46.dp
    val tail: Dp = 10.dp
    TipAnchored {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp).semantics { contentDescription = "$name, ${presenceLabel(presence)}" },
        ) {
            if (bubble != null) {
                Surface(shape = RoundedCornerShape(12.dp), color = Color.White, contentColor = Color(0xFF0F1B2B), border = BorderStroke(1.dp, Color(0x33000000))) {
                    Text(bubble, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                }
                Spacer(Modifier.height(4.dp))
            }
            Box(Modifier.size(head, head + tail), contentAlignment = Alignment.TopCenter) {
                Canvas(Modifier.size(head, head + tail)) {
                    val w = size.width
                    val h = size.height
                    val ring = 3.dp.toPx()
                    drawPath(pinPath(w, h), Color(0x26000000), style = Stroke(1.dp.toPx())) // hairline so a white edge shows on pale maps
                    drawPath(pinPath(w, h), edge)
                    translate(ring, ring) { drawPath(pinPath(w - 2 * ring, h - 2 * ring), bg) }
                    if (stale) {
                        translate(ring, ring) {
                            drawPath(
                                pinPath(w - 2 * ring, h - 2 * ring),
                                dashColor,
                                style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))),
                            )
                        }
                    }
                }
                Box(Modifier.size(head), contentAlignment = Alignment.Center) {
                    Text(Formatting.initials(name), color = fg, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}

/** A cluster of members that are close together on the map. */
@Composable
fun ClusterBubble(count: Int) {
    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary, border = BorderStroke(3.dp, Color.White), modifier = Modifier.size(46.dp)) {
        Box(contentAlignment = Alignment.Center) { Text("$count", style = MaterialTheme.typography.titleMedium, fontFamily = Display, fontWeight = FontWeight.Bold) }
    }
}
