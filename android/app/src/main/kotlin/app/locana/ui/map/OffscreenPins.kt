package app.locana.ui.map

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.locana.domain.Presence
import app.locana.ui.components.Avatar
import app.locana.ui.theme.LocalTokens
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.CameraPositionState
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Where an off-screen target sits on the edge of the visible area, and the angle (degrees, 0 = up) to point at it. */
internal data class EdgeMark(val x: Float, val y: Float, val angleDeg: Float)

/**
 * Clamps a point that is outside the rectangle to its edge, along the line from the rectangle's centre to the point,
 * so the mark appears on the side where the person really is. Returns null when the point is inside.
 */
internal fun edgeMarkFor(px: Float, py: Float, left: Float, top: Float, right: Float, bottom: Float, inset: Float): EdgeMark? {
    if (px in left..right && py in top..bottom) return null
    val cx = (left + right) / 2f
    val cy = (top + bottom) / 2f
    val dx = px - cx
    val dy = py - cy
    val halfW = (right - left) / 2f - inset
    val halfH = (bottom - top) / 2f - inset
    // Scale the direction so it just reaches the inset rectangle.
    val scale = min(if (dx != 0f) halfW / kotlin.math.abs(dx) else Float.MAX_VALUE, if (dy != 0f) halfH / kotlin.math.abs(dy) else Float.MAX_VALUE)
    val angle = Math.toDegrees(atan2(dx.toDouble(), -dy.toDouble())).toFloat()
    return EdgeMark(cx + dx * scale, cy + dy * scale, angle)
}

/**
 * People whose pin is off the screen get a small avatar on the edge nearest to them, with an arrow pointing the way.
 * Tapping one focuses that person and the map flies to them.
 */
@Composable
fun OffscreenPins(
    members: List<MemberPin>,
    camera: CameraPositionState,
    topPx: Float,
    bottomPx: Float,
    widthPx: Float,
    onClick: (String) -> Unit,
) {
    // Reading the camera position makes this recompose while the map moves.
    @Suppress("UNUSED_VARIABLE") val moving = camera.position
    val projection = camera.projection ?: return
    val density = LocalDensity.current.density
    val half = 22f * density
    members.filter { it.lat != null && it.lon != null && it.presence != Presence.HIDDEN }.forEach { m ->
        val p = projection.toScreenLocation(LatLng(m.lat!!, m.lon!!))
        val mark = edgeMarkFor(p.x.toFloat(), p.y.toFloat(), 0f, topPx, widthPx, bottomPx, half + 6f * density) ?: return@forEach
        Box(
            Modifier
                .offset { IntOffset((mark.x - half).roundToInt(), (mark.y - half).roundToInt()) }
                .size(44.dp)
                .semantics { contentDescription = "${m.name} is off the screen. Tap to find them." }
                .clickable(role = Role.Button) { onClick(m.userId) },
        ) {
            Avatar(
                m.name, m.avatarKey, size = 44.dp, framed = m.framed, frameKey = m.frameKey, userId = m.userId, photoVersion = m.photoVersion,
                modifier = Modifier.shadow(6.dp, CircleShape).clip(CircleShape).border(3.dp, Color.White, CircleShape),
            )
            // The arrow sits just outside the avatar, on the side that faces the person.
            val rad = Math.toRadians(mark.angleDeg.toDouble())
            Icon(
                Icons.Filled.ArrowDropUp, contentDescription = null, tint = LocalTokens.current.card,
                modifier = Modifier
                    .size(26.dp)
                    .offset((kotlin.math.sin(rad) * 29).dp, (-kotlin.math.cos(rad) * 29).dp)
                    .rotate(mark.angleDeg),
            )
        }
    }
}
