package app.locana.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.locana.BuildConfig
import app.locana.R
import app.locana.data.prefs.MapLayers
import app.locana.domain.Freshness
import app.locana.domain.Presence
import app.locana.domain.TileBounds
import app.locana.ui.components.Avatar
import app.locana.ui.components.EmptyState
import app.locana.ui.components.PrimaryButton
import app.locana.ui.components.SecondaryButton
import app.locana.ui.components.StatusChip
import app.locana.ui.components.presenceLabel
import app.locana.ui.nav.Routes
import app.locana.ui.theme.LocalDarkTheme
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.clustering.ClusterItem
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MapsComposeExperimentalApi
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polygon
import com.google.maps.android.compose.clustering.Clustering
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private class MemberItem(val pin: MemberPin) : ClusterItem {
    override fun getPosition(): LatLng = LatLng(pin.lat!!, pin.lon!!)
    override fun getTitle(): String = pin.name
    override fun getSnippet(): String? = null
    override fun getZIndex(): Float = if (pin.isMe) 1f else 2f
}

private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

@OptIn(MapsComposeExperimentalApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(nav: NavController, vm: MapViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val perms by vm.tracking.permissions.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dark = LocalDarkTheme.current
    val camera = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(LatLng(-6.2, 106.82), 11f) }
    var selected by remember { mutableStateOf<String?>(null) }
    var layersOpen by remember { mutableStateOf(false) }
    var shareOpen by remember { mutableStateOf(false) }
    var centeredOn by rememberSaveable { mutableStateOf<String?>(null) }

    // Frame the circle once per circle, using only positions we are allowed to show.
    LaunchedEffect(ui.circleId, ui.members) {
        if (ui.circleId == null || centeredOn == ui.circleId) return@LaunchedEffect
        val pts = ui.members.filter { it.lat != null && it.presence != Presence.HIDDEN }.map { LatLng(it.lat!!, it.lon!!) }
        if (pts.isEmpty()) return@LaunchedEffect
        centeredOn = ui.circleId
        if (pts.size == 1) camera.move(CameraUpdateFactory.newLatLngZoom(pts[0], 15f))
        else camera.move(CameraUpdateFactory.newLatLngBounds(LatLngBounds.builder().apply { pts.forEach { include(it) } }.build(), 160))
    }
    LaunchedEffect(camera.isMoving) {
        if (camera.isMoving) return@LaunchedEffect
        val b = camera.projection?.visibleRegion?.latLngBounds ?: return@LaunchedEffect
        vm.onViewport(TileBounds(b.northeast.latitude, b.southwest.latitude, b.northeast.longitude, b.southwest.longitude), camera.position.zoom, camera.position.target.latitude, camera.position.target.longitude)
    }

    if (ui.noCircles) {
        Box(Modifier.fillMaxSize().statusBarsPadding(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                EmptyState(
                    "Start with a circle",
                    "A circle is a small private group: your family, a partner or friends. Make one and invite them, or join with a code.",
                    actionLabel = "Create a circle",
                    onAction = { nav.navigate(Routes.CIRCLE_CREATE) },
                )
                TextButton(onClick = { nav.navigate(Routes.circleJoin()) }) { Text("I have an invitation code") }
            }
        }
        return
    }

    val primary = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outline
    val placeColor = MaterialTheme.colorScheme.secondary
    Box(Modifier.fillMaxSize()) {
        if (BuildConfig.HAS_MAPS_KEY) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = camera,
                properties = MapProperties(
                    isMyLocationEnabled = perms.anyLocation,
                    mapStyleOptions = MapStyleOptions.loadRawResourceStyle(context, if (dark) R.raw.map_style_dark else R.raw.map_style_light),
                ),
                uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false, mapToolbarEnabled = false),
                contentPadding = PaddingValues(top = 88.dp, bottom = 120.dp),
            ) {
                if (ui.layers.tiles) ui.tiles.forEach { t ->
                    Polygon(
                        points = listOf(LatLng(t.north, t.west), LatLng(t.north, t.east), LatLng(t.south, t.east), LatLng(t.south, t.west)),
                        fillColor = primary.copy(alpha = 0.16f),
                        strokeColor = primary.copy(alpha = 0.35f),
                        strokeWidth = 1f,
                    )
                }
                if (ui.layers.territories) ui.territories.forEach { t ->
                    Circle(
                        center = LatLng(t.latitude, t.longitude),
                        radius = t.radiusM.toDouble(),
                        fillColor = (if (t.ownedByMyCircle) primary else outline).copy(alpha = 0.10f),
                        strokeColor = if (t.ownedByMyCircle) primary else outline,
                        strokeWidth = 3f,
                        clickable = true,
                        onClick = { nav.navigate(Routes.territory(t.id)) },
                    )
                }
                if (ui.layers.places) ui.places.forEach { p ->
                    Circle(
                        center = LatLng(p.latitude, p.longitude),
                        radius = p.radiusM.toDouble(),
                        fillColor = placeColor.copy(alpha = 0.08f),
                        strokeColor = placeColor.copy(alpha = 0.6f),
                        strokeWidth = 2f,
                    )
                }
                if (ui.layers.landmarks) ui.landmarks.forEach { l ->
                    Marker(
                        state = remember(l.id) { MarkerState(LatLng(l.latitude, l.longitude)) },
                        title = l.name,
                        snippet = if (l.visited) "Visited" else "+${l.rewardXp} XP on your first visit",
                        icon = BitmapDescriptorFactory.defaultMarker(if (l.visited) BitmapDescriptorFactory.HUE_AZURE else 172f),
                        alpha = if (l.visited) 0.7f else 1f,
                    )
                }
                if (ui.layers.members) {
                    val items = remember(ui.members) { ui.members.filter { it.lat != null && it.lon != null && it.presence != Presence.HIDDEN }.map(::MemberItem) }
                    Clustering(
                        items = items,
                        onClusterClick = { c ->
                            scope.launch { camera.animate(CameraUpdateFactory.newLatLngZoom(c.position, camera.position.zoom + 2f), 300) }
                            true
                        },
                        onClusterItemClick = { selected = it.pin.userId; true },
                        clusterContent = { c ->
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(44.dp)) {
                                Box(contentAlignment = Alignment.Center) { Text("${c.size}", style = MaterialTheme.typography.titleMedium) }
                            }
                        },
                        clusterItemContent = { item -> Avatar(item.pin.name, item.pin.avatarKey, size = 44.dp, presence = item.pin.presence, framed = item.pin.framed) },
                    )
                }
            }
        } else {
            MissingMapKey(Modifier.fillMaxSize())
        }

        TopBar(
            ui = ui,
            onSelect = vm::select,
            onRefresh = vm::refresh,
            onSharing = { nav.navigate(Routes.SHARING) },
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(12.dp),
        )

        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            Row(Modifier.align(Alignment.End).padding(end = 12.dp), verticalAlignment = Alignment.Bottom) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallFloatingActionButton(onClick = { layersOpen = true }) { Icon(Icons.Outlined.Layers, contentDescription = "Map layers") }
                    SmallFloatingActionButton(onClick = {
                        scope.launch {
                            val me = vm.myLastLocation()
                            if (me != null) camera.animate(CameraUpdateFactory.newLatLngZoom(LatLng(me.first, me.second), 16f), 400)
                        }
                    }) { Icon(Icons.Filled.MyLocation, contentDescription = "Center on me") }
                }
            }
            Spacer(Modifier.height(8.dp))
            if (ui.circle != null && !ui.sharingHere) {
                ShareCard(ui.circle!!.name, onShare = {
                    if (perms.anyLocation) shareOpen = true else nav.navigate(Routes.PERMISSIONS)
                })
            }
            MemberStrip(ui.members, onTap = { selected = it })
        }
    }

    ui.members.firstOrNull { it.userId == selected }?.let { m ->
        ModalBottomSheet(onDismissRequest = { selected = null }) {
            MemberSheet(
                m = m,
                onFocus = {
                    selected = null
                    if (m.lat != null) scope.launch { camera.animate(CameraUpdateFactory.newLatLngZoom(LatLng(m.lat, m.lon!!), 16f), 400) }
                },
                onHide = { vm.hideFrom(m.userId, !m.hiddenFromThem) },
                onManage = { selected = null; ui.circleId?.let { nav.navigate(Routes.circleMembers(it)) } },
            )
        }
    }
    if (layersOpen) {
        ModalBottomSheet(onDismissRequest = { layersOpen = false }) { LayersSheet(ui.layers, vm::setLayers) }
    }
    if (shareOpen && ui.circle != null) {
        ModalBottomSheet(onDismissRequest = { shareOpen = false }) {
            ShareSheet(ui.circle!!.name) { minutes ->
                shareOpen = false
                vm.startSharing(minutes)
            }
        }
    }
}

@Composable
private fun TopBar(ui: MapUi, onSelect: (String) -> Unit, onRefresh: () -> Unit, onSharing: () -> Unit, modifier: Modifier) {
    var open by remember { mutableStateOf(false) }
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer, shadowElevation = 3.dp, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                TextButton(onClick = { open = true }) {
                    Text(ui.circle?.name ?: "Choose a circle", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = "Switch circle")
                }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    ui.circles.forEach { (id, name) -> DropdownMenuItem(text = { Text(name) }, onClick = { open = false; onSelect(id) }) }
                }
            }
            val sharing = ui.sharing
            val label = when {
                sharing == null -> "Checking"
                sharing.globallyPaused -> "Paused"
                ui.sharingHere -> sharing.sessions.firstOrNull { it.circleId == ui.circleId }?.expiresAt
                    ?.let { "Sharing until " + timeFmt.format(Instant.parse(it).atZone(ZoneId.systemDefault())) } ?: "Sharing"
                else -> "Not sharing"
            }
            StatusChip(
                label,
                modifier = Modifier.clickable(role = Role.Button, onClick = onSharing),
                container = if (ui.sharingHere && sharing?.globallyPaused == false) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                content = if (ui.sharingHere && sharing?.globallyPaused == false) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            )
            IconButton(onClick = onRefresh, enabled = !ui.refreshing) { Icon(Icons.Filled.Refresh, contentDescription = "Refresh locations") }
        }
    }
}

@Composable
private fun ShareCard(circleName: String, onShare: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium, shadowElevation = 3.dp, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("You're not sharing with $circleName", style = MaterialTheme.typography.titleSmall)
                Text("They see you as hidden until you start.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(12.dp))
            PrimaryButton("Share", onShare)
        }
    }
}

@Composable
private fun MemberStrip(members: List<MemberPin>, onTap: (String) -> Unit) {
    if (members.isEmpty()) return
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(members, key = { it.userId }) { m ->
                Column(
                    Modifier.width(64.dp).clickable(role = Role.Button, onClickLabel = "Show ${m.name}") { onTap(m.userId) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Avatar(m.name, m.avatarKey, size = 48.dp, presence = m.presence, framed = m.framed)
                    Text(if (m.isMe) "You" else m.name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        m.recordedAtMs?.takeIf { m.presence == Presence.LIVE || m.presence == Presence.STALE }?.let { Freshness.ageLabel(it, System.currentTimeMillis()) } ?: shortPresence(m.presence),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

private fun shortPresence(p: Presence) = when (p) {
    Presence.HIDDEN -> "hidden"
    Presence.UNAVAILABLE -> "no fix"
    Presence.STALE -> "old"
    Presence.LIVE -> "live"
}

@Composable
private fun MemberSheet(m: MemberPin, onFocus: () -> Unit, onHide: () -> Unit, onManage: () -> Unit) {
    Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(m.name, m.avatarKey, size = 56.dp, presence = m.presence, framed = m.framed)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(if (m.isMe) "${m.name} (you)" else m.name, style = MaterialTheme.typography.titleLarge)
                Text(presenceLabel(m.presence).replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(16.dp))
        if (m.recordedAtMs != null && m.presence != Presence.HIDDEN) {
            Fact("Updated", Freshness.ageLabel(m.recordedAtMs, System.currentTimeMillis()))
            m.accuracyM?.let { Fact("Accuracy", "within ${it.toInt()} m") }
            m.batteryPct?.let { Fact("Battery", "$it%") }
            if (m.presence == Presence.STALE) {
                Text(
                    "This is the last position we received. It is not live; their phone may be offline or restricting background updates.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        } else if (!m.isMe) {
            Text(
                if (m.presence == Presence.HIDDEN) "${m.name} is not sharing their location with you right now." else "No position received yet.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(Modifier.height(16.dp))
        if (m.lat != null && m.presence != Presence.HIDDEN) PrimaryButton("Show on map", onFocus, Modifier.fillMaxWidth())
        if (!m.isMe) {
            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            ListItem(
                headlineContent = { Text("Hide my location from ${m.name}") },
                supportingContent = { Text("They stay in the circle but see you as hidden.") },
                trailingContent = { Switch(checked = m.hiddenFromThem, onCheckedChange = { onHide() }) },
                modifier = Modifier.clickable(role = Role.Switch, onClick = onHide),
            )
            SecondaryButton("Block or report", onManage, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun Fact(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(96.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun LayersSheet(l: MapLayers, onChange: (MapLayers) -> Unit) {
    Column(Modifier.padding(bottom = 32.dp)) {
        Text("Map layers", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        LayerRow("Circle members", "Everyone sharing with you", l.members) { onChange(l.copy(members = it)) }
        LayerRow("Saved places", "Geofences for arrival alerts", l.places) { onChange(l.copy(places = it)) }
        LayerRow("My explored tiles", "Only you can see these", l.tiles) { onChange(l.copy(tiles = it)) }
        LayerRow("Territories", "Circle rounds around landmarks", l.territories) { onChange(l.copy(territories = it)) }
        LayerRow("Landmarks", "Public places worth a visit", l.landmarks) { onChange(l.copy(landmarks = it)) }
    }
}

@Composable
private fun LayerRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(sub) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
        modifier = Modifier.clickable(role = Role.Switch) { onChange(!checked) },
    )
}

@Composable
private fun ShareSheet(circleName: String, onPick: (Int?) -> Unit) {
    Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
        Text("Share with $circleName", style = MaterialTheme.typography.titleLarge)
        Text(
            "Members of this circle will see your live position. You can pause or stop at any time from the Map or the notification.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp, bottom = 16.dp),
        )
        PrimaryButton("For 1 hour", { onPick(60) }, Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        SecondaryButton("For 8 hours", { onPick(480) }, Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        SecondaryButton("Until I stop it", { onPick(null) }, Modifier.fillMaxWidth())
    }
}

@Composable
private fun MissingMapKey(modifier: Modifier) {
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerLow), contentAlignment = Alignment.Center) {
        Text(
            "Map unavailable: this build has no Google Maps API key.\nAdd MAPS_API_KEY to local.properties (see docs/android.md). Members are listed below.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(32.dp),
        )
    }
}

