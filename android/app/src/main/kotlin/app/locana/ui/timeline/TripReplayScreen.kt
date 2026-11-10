package app.locana.ui.timeline

import android.annotation.SuppressLint
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import app.locana.BuildConfig
import app.locana.R
import app.locana.data.prefs.MapProvider
import app.locana.data.prefs.SettingsStore
import app.locana.domain.Formatting
import app.locana.ui.components.EmptyState
import app.locana.ui.map.MAPBOX_ATTRIBUTION
import app.locana.ui.map.mapboxTileProvider
import app.locana.ui.map.renderDot
import app.locana.ui.map.renderEmojiDisc
import app.locana.ui.theme.LocalDarkTheme
import app.locana.ui.theme.LocalTokens
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.TileOverlay
import com.google.maps.android.compose.rememberCameraPositionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/** The trip the person tapped in the timeline, handed to the full-screen replay. */
@Singleton
class TripReplayStore @Inject constructor() {
    @Volatile var trip: TimelineItem.Trip? = null
}

@HiltViewModel
class TripReplayViewModel @Inject constructor(store: TripReplayStore, settings: SettingsStore) : ViewModel() {
    val trip: TimelineItem.Trip? = store.trip
    private val _provider = MutableStateFlow(MapProvider.GOOGLE)
    val provider: StateFlow<MapProvider> = _provider.asStateFlow()
    init { viewModelScope.launch { _provider.value = settings.snapshot().mapProvider } }
}

private val clockSeconds = DateTimeFormatter.ofPattern("HH:mm:ss")
private fun clock(ms: Long) = clockSeconds.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))

/**
 * Full-screen replay of one trip: the route on the map and a bar underneath whose handle is the person, moving from
 * the time they left to the time they arrived. Drag the bar to scrub, or press play. Risky driving moments appear on the route.
 */
@SuppressLint("MissingPermission")
@Composable
fun TripReplayScreen(nav: NavController, vm: TripReplayViewModel = hiltViewModel()) {
    val trip = vm.trip
    val provider by vm.provider.collectAsStateWithLifecycle()
    if (trip == null || trip.path.size < 2) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(24.dp)) {
            androidx.compose.material3.IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            EmptyState("No route to replay", "This trip has too few recorded points.", illustration = "compass")
        }
        return
    }
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val dark = LocalDarkTheme.current
    val tk = LocalTokens.current
    val accent = MaterialTheme.colorScheme.primary
    val mapbox = provider == MapProvider.MAPBOX && BuildConfig.HAS_MAPBOX
    val points = remember(trip) { trip.path.map { LatLng(it.first, it.second) } }
    val duration = (trip.endMs - trip.startMs).coerceAtLeast(1)

    var fraction by remember { mutableFloatStateOf(0f) }
    var playing by remember { mutableStateOf(false) }
    var speed by remember { mutableFloatStateOf(1f) }
    var follow by remember { mutableStateOf(true) }
    val nowMs = trip.startMs + (duration * fraction).toLong()
    val at = routeAt(trip.path, trip.pathAt, trip.startMs, trip.endMs, nowMs)!!
    val here = LatLng(at.lat, at.lon)

    // One full replay takes about 25 seconds at 1x, however long the trip was.
    LaunchedEffect(playing, speed) {
        if (!playing) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (playing) {
            val now = withFrameNanos { it }
            val dt = (now - last) / 1_000_000_000f
            last = now
            fraction = (fraction + dt * speed / 25f).coerceAtMost(1f)
            if (fraction >= 1f) playing = false
        }
    }

    val camera = rememberCameraPositionState()
    LaunchedEffect(Unit) {
        val b = LatLngBounds.builder().apply { points.forEach { include(it) } }.build()
        runCatching { camera.move(CameraUpdateFactory.newLatLngBounds(b, (72 * density).toInt())) }
    }
    var lastFollowMs by remember { mutableStateOf(0L) }
    LaunchedEffect(here, follow) {
        if (!follow || !playing) return@LaunchedEffect
        val now = System.currentTimeMillis()
        if (now - lastFollowMs < 120) return@LaunchedEffect
        lastFollowMs = now
        runCatching { camera.move(CameraUpdateFactory.newLatLng(here)) }
    }

    val endpointIcon = remember(accent, density) { BitmapDescriptorFactory.fromBitmap(renderDot(accent.toArgb(), density)) }
    val endIcon = remember(accent, density) { BitmapDescriptorFactory.fromBitmap(renderDot(accent.toArgb(), density, flag = true)) }
    val mover = remember(trip.emoji, density) { BitmapDescriptorFactory.fromBitmap(renderEmojiDisc(trip.emoji, density, selected = true)) }
    val brake = remember(density) { BitmapDescriptorFactory.fromBitmap(renderEmojiDisc("🛑", density)) }
    val accel = remember(density) { BitmapDescriptorFactory.fromBitmap(renderEmojiDisc("⚡", density)) }
    val phone = remember(density) { BitmapDescriptorFactory.fromBitmap(renderEmojiDisc("📵", density)) }

    Box(Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = camera,
            properties = MapProperties(
                mapType = if (mapbox) MapType.NONE else MapType.NORMAL,
                mapStyleOptions = if (mapbox) null else MapStyleOptions.loadRawResourceStyle(context, if (dark) R.raw.map_style_dark else R.raw.map_style_light),
            ),
            uiSettings = MapUiSettings(zoomControlsEnabled = false, mapToolbarEnabled = false, myLocationButtonEnabled = false),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 110.dp, bottom = 200.dp),
        ) {
            if (mapbox) TileOverlay(tileProvider = remember(dark) { mapboxTileProvider(BuildConfig.MAPBOX_TOKEN, dark) }, zIndex = -1f)
            Polyline(points = points, color = accent.copy(alpha = 0.28f), width = 6f * density / 2f, jointType = com.google.android.gms.maps.model.JointType.ROUND)
            // The part already travelled is drawn bold, up to the person's current position.
            Polyline(points = points.take(at.index + 1) + here, color = accent, width = 7f * density / 2f, jointType = com.google.android.gms.maps.model.JointType.ROUND)
            Marker(state = remember(points) { MarkerState(points.first()) }, icon = endpointIcon, anchor = Offset(0.5f, 0.5f))
            Marker(state = remember(points) { MarkerState(points.last()) }, icon = endIcon, anchor = Offset(0.5f, 0.5f))
            trip.events.forEach { e ->
                Marker(
                    state = remember(e.at, e.kind) { MarkerState(LatLng(e.latitude, e.longitude)) },
                    icon = when (e.kind) { "HARD_BRAKE" -> brake; "RAPID_ACCEL" -> accel; else -> phone },
                    anchor = Offset(0.5f, 0.5f), zIndex = 1f,
                    title = when (e.kind) { "HARD_BRAKE" -> "Hard braking"; "RAPID_ACCEL" -> "Rapid acceleration"; else -> "Phone use" } + " · " + clock(e.at),
                )
            }
            Marker(state = MarkerState(here).also { it.position = here }, icon = mover, anchor = Offset(0.5f, 0.5f), zIndex = 3f)
        }

        // Header
        Surface(
            shape = RoundedCornerShape(24.dp), color = tk.card, shadowElevation = 8.dp,
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp).fillMaxWidth(),
        ) {
            Row(Modifier.padding(horizontal = 6.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                Column(Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(trip.title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text("${trip.range} · ${trip.distance} · ${trip.duration}", style = MaterialTheme.typography.bodySmall, color = tk.ink2, maxLines = 1)
                }
            }
        }

        // Follow toggle
        Surface(
            onClick = { follow = !follow }, shape = androidx.compose.foundation.shape.CircleShape, color = if (follow) accent else tk.card, shadowElevation = 6.dp,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp).semantics { contentDescription = if (follow) "Stop following the person" else "Follow the person" },
        ) { Icon(Icons.Outlined.MyLocation, contentDescription = null, tint = if (follow) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(12.dp)) }

        // The trackbar
        Surface(
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), color = tk.card, shadowElevation = 14.dp,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
        ) {
            Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(clock(nowMs), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    Text(Formatting.distance(at.metresFromStart.toInt()) + " of " + trip.distance, style = MaterialTheme.typography.bodySmall, color = tk.ink2)
                }
                Slider(
                    value = fraction, onValueChange = { fraction = it; playing = false },
                    colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent, inactiveTrackColor = accent.copy(alpha = 0.2f)),
                    modifier = Modifier.semantics { contentDescription = "Trip position, ${clock(nowMs)}" },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(clock(trip.startMs), style = MaterialTheme.typography.labelMedium, color = tk.ink2, modifier = Modifier.weight(1f))
                    Text(clock(trip.endMs), style = MaterialTheme.typography.labelMedium, color = tk.ink2)
                }
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        onClick = { if (fraction >= 1f) fraction = 0f; playing = !playing }, shape = androidx.compose.foundation.shape.CircleShape, color = accent,
                        modifier = Modifier.semantics { contentDescription = if (playing) "Pause" else "Play" },
                    ) { Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.padding(14.dp)) }
                    listOf(1f, 2f, 4f).forEach { s ->
                        val selected = speed == s
                        val bg by animateFloatAsState(if (selected) 1f else 0f, label = "speed")
                        Surface(
                            onClick = { speed = s }, shape = RoundedCornerShape(16.dp),
                            color = if (bg > 0.5f) MaterialTheme.colorScheme.primaryContainer else tk.card,
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, if (selected) accent else tk.line),
                        ) { Text("${s.toInt()}×", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold), color = if (selected) accent else tk.ink2, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) }
                    }
                    val c = trip.counts
                    if (c != null && (c.hardBrake + c.rapidAccel + c.phoneUse) > 0) {
                        Text(
                            buildString {
                                if (c.hardBrake > 0) append("🛑 ${c.hardBrake}  ")
                                if (c.rapidAccel > 0) append("⚡ ${c.rapidAccel}  ")
                                if (c.phoneUse > 0) append("📵 ${c.phoneUse}")
                            },
                            style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End,
                        )
                    }
                }
                if (mapbox) Text(MAPBOX_ATTRIBUTION, style = MaterialTheme.typography.labelSmall, color = tk.ink3)
            }
        }
    }
}
