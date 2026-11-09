package app.locana.ui.timeline

import androidx.compose.foundation.layout.Arrangement
import app.locana.ui.theme.LocalTokens
import app.locana.ui.components.TagPill
import app.locana.ui.components.Illustration
import app.locana.ui.components.LocanaCard
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.HourglassBottom
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.locana.BuildConfig
import app.locana.R
import app.locana.ui.components.EmptyState
import app.locana.ui.components.ErrorState
import app.locana.ui.components.LoadingState
import app.locana.ui.components.UiState
import app.locana.ui.map.ControlShape
import app.locana.ui.map.floatingColor
import app.locana.ui.map.renderDot
import app.locana.ui.theme.LocalDarkTheme
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayFmt = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())

internal fun dayLabel(d: LocalDate): String = when (d) {
    LocalDate.now() -> "Today"
    LocalDate.now().minusDays(1) -> "Yesterday"
    else -> dayFmt.format(d)
}

/** Day switcher: two small square buttons around the day name, on a white bar. */
@Composable
internal fun DayBar(day: LocalDate, onPrev: () -> Unit, onNext: () -> Unit, canNext: Boolean) {
    val t = LocalTokens.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(48.dp).clip(RoundedCornerShape(16.dp)).background(t.card).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DayButton(Icons.Filled.ChevronLeft, "Previous day", true, onPrev)
        Text(dayLabel(day), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold), modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        DayButton(Icons.Filled.ChevronRight, "Next day", canNext, onNext)
    }
}

@Composable
private fun DayButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).clickable(enabled = enabled, role = androidx.compose.ui.semantics.Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = label, tint = if (enabled) MaterialTheme.colorScheme.onSurface else LocalTokens.current.ink3, modifier = Modifier.size(18.dp)) }
}

@Composable
private fun TimelineCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    LocanaCard(modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), shadow = 3.dp) { content() }
}

/** A pill with an optional leading icon and its own tint, like "Stayed 7 hrs, 15 mins". */
@Composable
private fun StatPill(text: String, container: Color, content: Color, icon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    TagPill(text, container, content, icon = icon)
}

@Composable
internal fun StayCard(s: TimelineItem.Stay) {
    val t = LocalTokens.current
    TimelineCard(Modifier.semantics { contentDescription = "${s.title}, ${s.range}, ${s.duration}" }) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(t.coralSoft), contentAlignment = Alignment.Center) {
                Illustration(if (s.emoji == "\uD83C\uDFE2") "office" else "house", size = 44.dp)
            }
            Column(Modifier.padding(start = 14.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(s.title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold))
                Text(s.range, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatPill("Stayed ${s.duration}", t.coralSoft, t.coralDark, Icons.Outlined.HourglassBottom)
                    if (s.ongoing) StatPill("Happening now", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
internal fun TripCard(t: TimelineItem.Trip) {
    val tk = LocalTokens.current
    TimelineCard(Modifier.semantics { contentDescription = "${t.title}, ${t.range}, ${t.distance}, ${t.duration}" }) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.clip(RoundedCornerShape(16.dp))) { RouteMap(t.path) }
            Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(t.title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold))
                    Text("${t.range} · ${t.distance}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(tk.skySoft), contentAlignment = Alignment.Center) { Illustration(if (t.driving) "car" else "sneaker", size = 42.dp) }
            }
            Row(Modifier.padding(start = 4.dp, end = 4.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatPill("Took ${t.duration}", tk.skySoft, Color(0xFF1E6FB0), Icons.Outlined.Timer)
                StatPill("avg ${t.avgSpeed}", MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.onSurface)
                if (t.driving) StatPill("top ${t.maxSpeed}", tk.yellowSoft, tk.onYellowSoft, Icons.Outlined.Bolt)
                if (t.ongoing) StatPill("Now", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/** A still map of the route: start dot, finish dot and the path between. Lite mode, so it never takes touches. */
@Composable
private fun RouteMap(path: List<Pair<Double, Double>>) {
    val mapModifier = Modifier.fillMaxWidth().height(180.dp)
    if (!BuildConfig.HAS_MAPS_KEY || path.size < 2) {
        Box(mapModifier.then(Modifier), contentAlignment = Alignment.Center) { Text("Route unavailable", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        return
    }
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val accent = MaterialTheme.colorScheme.primary
    val dark = LocalDarkTheme.current
    val points = remember(path) { path.map { LatLng(it.first, it.second) } }
    val camera = rememberCameraPositionState()
    val start = remember(accent, density) { BitmapDescriptorFactory.fromBitmap(renderDot(accent.toArgb(), density)) }
    val end = remember(accent, density) { BitmapDescriptorFactory.fromBitmap(renderDot(accent.toArgb(), density, flag = true)) }
    GoogleMap(
        modifier = mapModifier,
        cameraPositionState = camera,
        googleMapOptionsFactory = { GoogleMapOptions().liteMode(true) },
        properties = MapProperties(mapStyleOptions = MapStyleOptions.loadRawResourceStyle(context, if (dark) R.raw.map_style_dark else R.raw.map_style_light)),
        uiSettings = MapUiSettings(zoomControlsEnabled = false, mapToolbarEnabled = false, scrollGesturesEnabled = false, zoomGesturesEnabled = false),
        onMapLoaded = {
            val b = LatLngBounds.builder().apply { points.forEach { include(it) } }.build()
            camera.move(CameraUpdateFactory.newLatLngBounds(b, (32 * density).toInt()))
        },
    ) {
        Polyline(points = points, color = accent, width = 6f * density / 2f, jointType = com.google.android.gms.maps.model.JointType.ROUND)
        Marker(state = remember(points) { MarkerState(points.first()) }, icon = start, anchor = androidx.compose.ui.geometry.Offset(0.5f, 0.5f))
        Marker(state = remember(points) { MarkerState(points.last()) }, icon = end, anchor = androidx.compose.ui.geometry.Offset(0.5f, 0.5f))
    }
}
