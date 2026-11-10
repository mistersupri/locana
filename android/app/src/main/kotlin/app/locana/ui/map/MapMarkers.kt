package app.locana.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import app.locana.data.api.PlaceDto
import app.locana.domain.Formatting
import app.locana.domain.Freshness
import app.locana.domain.Geo
import app.locana.domain.LatLon
import app.locana.domain.Presence
import app.locana.ui.theme.AvatarColors
import app.locana.ui.theme.LocalReward
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import androidx.compose.material3.MaterialTheme

/** Walking pace and above this speed (m/s, about 20 km/h) reads as driving. */
private const val DRIVING_MPS = 5.5

/** The saved place the person is standing in, if any. */
internal fun placeAt(m: MemberPin, places: List<PlaceDto>): PlaceDto? {
    if (m.lat == null || m.lon == null) return null
    return places
        .filter { Geo.distanceM(LatLon(m.lat, m.lon), LatLon(it.latitude, it.longitude)) <= maxOf(it.radiusM.toDouble(), 100.0) }
        .minByOrNull { Geo.distanceM(LatLon(m.lat, m.lon), LatLon(it.latitude, it.longitude)) }
}

private fun placeEmoji(type: String?) = when (type) {
    "HOME" -> "🏠"      // house
    "WORK" -> "💼"      // briefcase
    "SCHOOL" -> "🎒"    // school bag
    "CUSTOM" -> "📍"    // pin
    else -> "🏡"        // house with garden: staying somewhere unsaved
}

/** What the bubble and badge say about a member right now. Pure so it can be tested. */
internal data class Caption(val label: String?, val value: String?, val emoji: String?)

internal fun captionFor(m: MemberPin, place: PlaceDto?, nowMs: Long): Caption {
    val speed = m.speedMps ?: 0.0
    return when {
        m.presence == Presence.STALE && m.recordedAtMs != null -> Caption("Last seen", Freshness.ageLabel(m.recordedAtMs, nowMs), null)
        m.movement == "STAYING" && m.stationarySinceMs != null ->
            Caption("Here for", Formatting.duration(nowMs - m.stationarySinceMs), placeEmoji(place?.type))
        // Movement comes from the phone's activity recognition and the server's verdict, never from one speed reading.
        m.movement == "MOVING" -> {
            val value = if (m.speedMps != null) Formatting.speedKmh(m.speedMps) else "on the move"
            when (m.mode ?: if (speed >= DRIVING_MPS) "DRIVING" else "WALKING") {
                "DRIVING" -> Caption("Driving", value, "🚗")
                "CYCLING" -> Caption("Cycling", value, "🚲")
                else -> Caption("Walking", value, "🚶")
            }
        }
        else -> Caption(null, null, null)
    }
}

/**
 * One marker per member, plus a heading beam under it for people who are moving. Anchored at the
 * pin's dot so the dot sits on the exact coordinate.
 */
@Composable
fun MemberMarkers(members: List<MemberPin>, places: List<PlaceDto>, focusedId: String? = null, onClick: (String) -> Unit) {
    val density = LocalDensity.current.density
    val accent = MaterialTheme.colorScheme.primary.toArgb()
    val frame = LocalReward.current.reward.toArgb()
    val now = System.currentTimeMillis()
    val halo = remember(accent, density) { BitmapDescriptorFactory.fromBitmap(renderHalo(accent, density)) }
    val cone = remember(accent, density) { BitmapDescriptorFactory.fromBitmap(renderHeadingCone(accent, density)) }
    members.filter { it.lat != null && it.lon != null && it.presence != Presence.HIDDEN }.forEach { m -> androidx.compose.runtime.key(m.userId) {
        val position = rememberGlidingLatLng(LatLng(m.lat!!, m.lon!!))
        val caption = captionFor(m, placeAt(m, places), now)
        val (bg, fg) = AvatarColors[m.avatarKey] ?: AvatarColors.getValue("explorer_slate")
        val store = app.locana.ui.components.LocalPhotoStore.current
        val photo by produceState<android.graphics.Bitmap?>(
            initialValue = m.photoVersion?.let { store?.peek(m.userId, it) },
            m.userId, m.photoVersion, store,
        ) { value = m.photoVersion?.let { store?.get(m.userId, it) } }
        val spec = PinSpec(
            initials = app.locana.domain.Formatting.initials(m.name),
            avatarColor = bg.toArgb(),
            avatarTextColor = fg.toArgb(),
            accent = accent,
            framed = m.framed,
            frameKey = m.frameKey,
            frameColor = frame,
            stale = m.presence == Presence.STALE,
            photoKey = if (photo != null) m.photoVersion else null,
            bubbleLabel = caption.label,
            bubbleValue = caption.value,
            emoji = caption.emoji,
            highlighted = m.userId == focusedId,
        )
        val pin = remember(spec, density) { renderPin(spec, density, photo).let { BitmapDescriptorFactory.fromBitmap(it.bitmap) to it.anchorY } }
        val state = remember(m.userId) { MarkerState(position) }
        state.position = position
        if (m.userId == focusedId) {
            Marker(
                state = remember(m.userId + "halo") { MarkerState(position) }.also { it.position = position },
                icon = halo,
                anchor = Offset(0.5f, 0.5f),
                flat = true,
                zIndex = 0.2f,
                onClick = { onClick(m.userId); true },
            )
        }
        val heading = m.bearingDeg
        if (heading != null && m.presence == Presence.LIVE) {
            Marker(
                state = remember(m.userId + "cone") { MarkerState(position) }.also { it.position = position },
                icon = cone,
                anchor = Offset(0.5f, 1f),
                flat = true,
                rotation = heading.toFloat(),
                zIndex = if (m.isMe) 1f else 0.5f,
                onClick = { onClick(m.userId); true },
            )
        }
        Marker(
            state = state,
            icon = pin.first,
            anchor = Offset(0.5f, pin.second),
            zIndex = if (m.isMe) 3f else 2f,
            title = m.name,
            onClick = { onClick(m.userId); true },
        )
    } }
}

/**
 * A position that glides to each new fix instead of jumping, so a pin moves like a person does. The first
 * position is used as is; a long jump (someone appearing across town) also snaps rather than sliding over the map.
 */
@Composable
private fun rememberGlidingLatLng(target: LatLng): LatLng {
    val reduced = app.locana.ui.theme.LocalReducedMotion.current
    var from by remember { mutableStateOf(target) }
    var to by remember { mutableStateOf(target) }
    val t = remember { androidx.compose.animation.core.Animatable(1f) }
    androidx.compose.runtime.LaunchedEffect(target) {
        if (target == to) return@LaunchedEffect
        val here = LatLng(from.latitude + (to.latitude - from.latitude) * t.value, from.longitude + (to.longitude - from.longitude) * t.value)
        val metres = Geo.distanceM(LatLon(here.latitude, here.longitude), LatLon(target.latitude, target.longitude))
        from = here
        to = target
        if (reduced || metres > 400) { from = target; t.snapTo(1f); return@LaunchedEffect }
        t.snapTo(0f)
        t.animateTo(1f, androidx.compose.animation.core.tween(1_200, easing = androidx.compose.animation.core.LinearEasing))
    }
    return LatLng(from.latitude + (to.latitude - from.latitude) * t.value, from.longitude + (to.longitude - from.longitude) * t.value)
}
