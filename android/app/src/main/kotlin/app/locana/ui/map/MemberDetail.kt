package app.locana.ui.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.locana.data.api.PlaceDto
import app.locana.domain.Formatting
import app.locana.domain.Freshness
import app.locana.domain.Geo
import app.locana.domain.LatLon
import app.locana.domain.Presence
import app.locana.ui.components.EmptyState
import app.locana.ui.components.ErrorState
import app.locana.ui.components.LoadingState
import app.locana.ui.components.SecondaryButton
import app.locana.ui.components.UiState
import app.locana.ui.timeline.DayBar
import app.locana.ui.timeline.StayCard
import app.locana.ui.timeline.TimelineItem
import app.locana.ui.timeline.TripCard
import app.locana.ui.timeline.dayLabel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val clock = DateTimeFormatter.ofPattern("HH:mm")

/** What the person is doing right now, in two lines and an emoji. Pure so it can be tested. */
internal data class Status(val title: String, val subtitle: String, val emoji: String?)

internal fun statusFor(m: MemberPin, place: PlaceDto?, me: MemberPin?, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): Status {
    val caption = captionFor(m, place, nowMs)
    val away = if (me?.lat != null && me.lon != null && m.lat != null && m.lon != null && !m.isMe)
        Formatting.distance(Geo.distanceM(LatLon(me.lat, me.lon), LatLon(m.lat, m.lon)).toInt()) + " from you"
    else null
    fun join(vararg parts: String?) = parts.filterNotNull().joinToString(" · ")
    return when {
        m.presence == Presence.HIDDEN -> Status("Not sharing with you", "Their location is hidden from you right now.", null)
        m.lat == null -> Status("No position yet", "Nothing received from this phone.", null)
        m.presence == Presence.STALE -> Status("Last seen " + (m.recordedAtMs?.let { Freshness.ageLabel(it, nowMs) } ?: "a while ago"), join("This is not live", away), null)
        m.movement == "STAYING" && m.stationarySinceMs != null -> Status(
            "At ${place?.name ?: "this place"}",
            join("Since " + clock.format(Instant.ofEpochMilli(m.stationarySinceMs).atZone(zone)), "Here for ${Formatting.duration(nowMs - m.stationarySinceMs)}", away),
            caption.emoji,
        )
        caption.label == "Driving" || caption.label == "Walking" -> Status(caption.label!!, join(caption.value, away), caption.emoji)
        else -> Status("Live", join("Updated " + (m.recordedAtMs?.let { Freshness.ageLabel(it, nowMs) } ?: "just now"), away), null)
    }
}

/**
 * The sheet content after a pin is tapped: who they are, what they are doing, their day, and the
 * per-person privacy controls. Everything here already existed elsewhere; it now lives in one place.
 */
@Composable
internal fun MemberDetail(
    m: MemberPin,
    me: MemberPin?,
    place: PlaceDto?,
    focus: FocusUi?,
    onPrevDay: () -> Unit,
    onNextDay: () -> Unit,
    onRetry: () -> Unit,
    onHide: () -> Unit,
    onManage: () -> Unit,
) {
    val status = statusFor(m, place, me, System.currentTimeMillis())
    LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (m.isMe) "${m.name} (you)" else m.name,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    m.batteryPct?.takeIf { m.presence == Presence.LIVE || m.presence == Presence.STALE }?.let { BatteryChip(it) }
                }
                Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(status.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(status.subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    status.emoji?.let { Text(it, fontSize = 44.sp) }
                }
            }
        }
        item {
            DayBar(focus?.day ?: java.time.LocalDate.now(), onPrev = onPrevDay, onNext = onNextDay, canNext = focus?.day?.isBefore(java.time.LocalDate.now()) == true)
        }
        when (val items = focus?.items) {
            null, UiState.Loading -> item { LoadingState() }
            is UiState.Error -> item { ErrorState(items.message, onRetry) }
            is UiState.Data -> if (items.value.isEmpty()) {
                item {
                    EmptyState(
                        "Nothing recorded for ${dayLabel(focus.day).lowercase()}",
                        "Places and trips are recorded while someone belongs to a circle with location on. Days older than the history limit are removed.",
                    )
                }
            } else {
                items(items.value) { entry ->
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        when (entry) {
                            is TimelineItem.Stay -> StayCard(entry)
                            is TimelineItem.Trip -> TripCard(entry)
                        }
                    }
                }
            }
        }
        if (!m.isMe) item {
            Column(Modifier.padding(horizontal = 20.dp)) {
                HorizontalDivider(Modifier.padding(bottom = 8.dp))
                ListItem(
                    headlineContent = { Text("Hide my location from ${m.name}") },
                    supportingContent = { Text("They stay in the circle but see you as hidden.") },
                    trailingContent = { Switch(checked = m.hiddenFromThem, onCheckedChange = { onHide() }) },
                    modifier = Modifier.clickable(role = Role.Switch, onClick = onHide),
                )
                Spacer(Modifier.height(8.dp))
                SecondaryButton("Block or report", onManage, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun BatteryChip(pct: Int) {
    Surface(shape = RoundedCornerShape(16.dp), color = floatingColor(), shadowElevation = 3.dp, modifier = Modifier.padding(start = 12.dp)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Outlined.BatteryFull, contentDescription = null, modifier = Modifier.size(20.dp))
            Text("$pct%", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        }
    }
}
