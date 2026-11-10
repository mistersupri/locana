package app.locana.ui.map

import app.locana.ui.theme.cardBrush
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import app.locana.ui.theme.LocalTokens
import app.locana.ui.components.LocanaCard
import app.locana.ui.components.Illustration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.border
import androidx.compose.foundation.background
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
import app.locana.ui.components.LocanaSwitch
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
        caption.label == "Driving" || caption.label == "Walking" || caption.label == "Cycling" -> Status(caption.label!!, join(caption.value, away), caption.emoji)
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
            val t = LocalTokens.current
            val (c1, c2, art) = when {
                m.movement == "STAYING" -> Triple(Color(0xFFFFE9D6), Color(0xFFFFF6E2), if (place?.type == "WORK") "office" else "house")
                status.title == "Driving" -> Triple(t.skySoft, Color.White, "car")
                status.title == "Walking" -> Triple(t.mintSoft, Color.White, "sneaker")
                else -> Triple(MaterialTheme.colorScheme.primaryContainer, Color.White, "compass")
            }
            Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(180.dp).clip(RoundedCornerShape(28.dp)).background(cardBrush(c1, c2))) {
                Column(Modifier.align(Alignment.CenterStart).padding(start = 20.dp).fillMaxWidth(0.58f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    m.batteryPct?.takeIf { m.presence == Presence.LIVE || m.presence == Presence.STALE }?.let { BatteryChip(it) }
                    Text(
                        (if (m.isMe) "You · " else "") + status.title,
                        style = MaterialTheme.typography.headlineSmall.copy(fontSize = 26.sp, lineHeight = 29.sp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(status.subtitle, style = MaterialTheme.typography.bodySmall, color = t.ink2)
                }
                Box(Modifier.align(Alignment.CenterEnd).padding(end = 18.dp).size(124.dp).clip(RoundedCornerShape(32.dp)).border(4.dp, Color.White, RoundedCornerShape(32.dp)), contentAlignment = Alignment.Center) {
                    Illustration(art, size = 100.dp)
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
                        if (focus.points > 0) "Collecting ${dayLabel(focus.day).lowercase()}" else "Nothing recorded for ${dayLabel(focus.day).lowercase()}",
                        if (focus.points > 0) "${focus.points} positions so far. A place shows up after 5 minutes in one spot, a trip once about 30 m have been covered."
                        else "A place is recorded after 5 minutes in one spot and a trip while travelling, once location is on and Locana has been running for a few minutes. Days older than the history limit are removed.",
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
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LocanaCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), shadow = 3.dp) {
                    Row(Modifier.padding(16.dp).clickable(role = Role.Switch, onClick = onHide), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Hide my location from ${m.name}", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold))
                            Text("They stay in the circle but see you as hidden.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.width(12.dp))
                        LocanaSwitch(checked = m.hiddenFromThem, onCheckedChange = { onHide() })
                    }
                }
                SecondaryButton("Block or report", onManage, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun BatteryChip(pct: Int) {
    val t = LocalTokens.current
    Row(
        Modifier.clip(RoundedCornerShape(999.dp)).background(t.card).padding(horizontal = 11.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(Icons.Outlined.BatteryFull, contentDescription = null, tint = if (pct <= 20) t.coralDark else app.locana.ui.theme.LocalTokens.current.onMint, modifier = Modifier.size(14.dp))
        Text("$pct%", style = MaterialTheme.typography.labelMedium, color = if (pct <= 20) t.coralDark else app.locana.ui.theme.LocalTokens.current.onMint)
    }
}
