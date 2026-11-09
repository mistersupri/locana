package app.locana.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.locana.domain.Formatting
import app.locana.domain.Presence
import app.locana.ui.theme.AvatarColors
import app.locana.ui.theme.Display
import app.locana.ui.theme.LocalReward

fun presenceLabel(p: Presence) = when (p) {
    Presence.LIVE -> "live"
    Presence.STALE -> "last position is old"
    Presence.UNAVAILABLE -> "no position yet"
    Presence.HIDDEN -> "not sharing with you"
}

/**
 * Initials avatar (no photos are collected). Presence is encoded by shape as well as color:
 * solid ring = live, dashed ring = stale, faded with no ring = hidden or unavailable.
 * An avatar frame (cosmetic) adds an amber outer ring.
 */
/** Provided once at the root so any Avatar can show a photo without every screen passing a loader around. */
val LocalPhotoStore = androidx.compose.runtime.staticCompositionLocalOf<app.locana.data.repo.PhotoStore?> { null }

@Composable
fun Avatar(
    name: String,
    avatarKey: String?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    presence: Presence? = null,
    framed: Boolean = false,
    /** With both set and a photo on the server, the photo replaces the initials. */
    userId: String? = null,
    photoVersion: Long? = null,
) {
    val store = LocalPhotoStore.current
    val photo by produceState<ImageBitmap?>(
        initialValue = if (userId != null && photoVersion != null) store?.peek(userId, photoVersion)?.asImageBitmap() else null,
        userId, photoVersion, store,
    ) { value = if (userId != null && photoVersion != null && store != null) store.get(userId, photoVersion)?.asImageBitmap() else null }
    val (bg, fg) = AvatarColors[avatarKey] ?: AvatarColors.getValue("explorer_slate")
    val ring = MaterialTheme.colorScheme.primary
    val stale = MaterialTheme.colorScheme.outline
    val frame = LocalReward.current.reward
    val faded = presence == Presence.HIDDEN || presence == Presence.UNAVAILABLE
    Box(
        modifier
            .size(size)
            .semantics { contentDescription = name + (presence?.let { ", ${presenceLabel(it)}" } ?: "") },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size)) {
            val w = 2.5.dp.toPx()
            val r = this.size.minDimension / 2 - w / 2
            if (framed) drawCircle(frame, radius = r, style = Stroke(w))
            when (presence) {
                Presence.LIVE -> drawCircle(ring, radius = r - if (framed) w * 1.6f else 0f, style = Stroke(w))
                Presence.STALE -> drawCircle(
                    stale, radius = r - if (framed) w * 1.6f else 0f,
                    style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
                )
                else -> Unit
            }
        }
        val inner = size - if (presence != null || framed) 8.dp else 0.dp
        Box(
            Modifier.size(inner).clip(CircleShape).background(bg).alpha(if (faded) 0.45f else 1f),
            contentAlignment = Alignment.Center,
        ) {
            val p = photo
            if (p != null) {
                Image(p, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Text(
                    Formatting.initials(name),
                    color = fg,
                    fontFamily = Display,
                    fontWeight = FontWeight.Bold,
                    fontSize = (inner.value * 0.38f).sp,
                )
            }
        }
    }
}
