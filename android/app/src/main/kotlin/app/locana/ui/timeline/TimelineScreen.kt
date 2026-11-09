package app.locana.ui.timeline

import androidx.compose.foundation.layout.Arrangement
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

@Composable
internal fun DayBar(day: LocalDate, onPrev: () -> Unit, onNext: () -> Unit, canNext: Boolean) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = ControlShape, color = floatingColor(), shadowElevation = 4.dp, modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrev) { Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous day") }
                Text(dayLabel(day), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                IconButton(onClick = onNext, enabled = canNext) { Icon(Icons.Filled.ChevronRight, contentDescription = "Next day") }
            }
        }
    }
}

@Composable
private fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = floatingColor(), shadowElevation = 6.dp, modifier = modifier.fillMaxWidth()) { content() }
}

@Composable
private fun Emoji(text: String, size: Int = 36) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(size.dp + 12.dp)) {
        Box(contentAlignment = Alignment.Center) { Text(text, fontSize = (size * 0.6f).sp) }
    }
}

private val Int.sp get() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)
private val Float.sp get() = androidx.compose.ui.unit.TextUnit(this, androidx.compose.ui.unit.TextUnitType.Sp)

@Composable
internal fun StayCard(s: TimelineItem.Stay) {
    Card(Modifier.semantics { contentDescription = "${s.title}, ${s.range}, ${s.duration}" }) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Emoji(s.emoji)
            Column(Modifier.padding(start = 16.dp).weight(1f)) {
                Text(s.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(s.range, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Stayed ${s.duration}")
                    if (s.ongoing) Chip("Happening now", highlight = true)
                }
            }
        }
    }
}

@Composable
internal fun TripCard(t: TimelineItem.Trip) {
    Card(Modifier.semantics { contentDescription = "${t.title}, ${t.range}, ${t.distance}, ${t.duration}" }) {
        Column {
            RouteMap(t.path)
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(t.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                        Text("${t.range} · ${t.distance}", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Emoji(t.emoji, 40)
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Chip("Took ${t.duration}")
                    if (t.ongoing) Chip("Happening now", highlight = true)
                    Chip("avg ${t.avgSpeed}")
                    if (t.driving) Chip("top ${t.maxSpeed}")
                }
            }
        }
    }
}

@Composable
private fun Chip(text: String, highlight: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (highlight) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (highlight) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
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
