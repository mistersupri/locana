package app.locana.ui.map

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import app.locana.domain.LatLon
import app.locana.domain.Geo
import app.locana.data.places.NearbyKind
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.outlined.LocationOff
import app.locana.ui.theme.LocalTokens
import app.locana.ui.theme.Motion
import app.locana.ui.components.AvatarStack
import app.locana.ui.components.Illustration
import app.locana.ui.components.LocanaChip
import app.locana.ui.components.LocanaCard
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Park
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PauseCircleOutline
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ShareLocation
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.ui.semantics.liveRegion
import app.locana.ui.components.ChunkyButton
import app.locana.ui.components.ChunkyStyle
import androidx.compose.ui.draw.clip
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import app.locana.ui.components.LocanaSwitch
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.locana.BuildConfig
import app.locana.R
import app.locana.data.api.PlaceDto
import app.locana.data.prefs.MapLayers
import app.locana.domain.Freshness
import app.locana.domain.Presence
import app.locana.domain.TileBounds
import app.locana.ui.components.Avatar
import app.locana.ui.components.IconTile
import app.locana.ui.components.PrimaryButton
import app.locana.ui.components.SecondaryButton
import app.locana.ui.components.presenceLabel
import app.locana.ui.nav.Routes
import app.locana.ui.theme.LocalDarkTheme
import app.locana.ui.theme.Palette
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

private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
/** Collapsed, the sheet shows the handle, the circle head and the first member card; pull it up for the rest. */
private val SheetPeek = 186.dp
private val SheetPeekEmpty = 250.dp

@OptIn(MapsComposeExperimentalApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(nav: NavController, vm: MapViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val unread by vm.unread.collectAsStateWithLifecycle()
    // Picks up permissions granted or revoked in system settings while we were away.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.tracking.refreshPermissions() }
    val perms by vm.tracking.permissions.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dark = LocalDarkTheme.current
    val screenHeight = LocalConfiguration.current.screenHeightDp
    val camera = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(LatLng(-6.2, 106.82), 11f) }
    val focus by vm.focus.collectAsStateWithLifecycle()
    val poi by vm.poi.collectAsStateWithLifecycle()
    val nearby by vm.nearby.collectAsStateWithLifecycle()
    val focusedId = focus?.userId
    // A notification asked for this person (an SOS, a quick message, a long trip): open them as soon as the circle is loaded.
    val pendingId by vm.pending.collectAsStateWithLifecycle()
    LaunchedEffect(pendingId, ui.members) {
        val id = pendingId ?: return@LaunchedEffect
        if (ui.members.any { it.userId == id }) { vm.focus(id); vm.consumePending() }
    }
    androidx.activity.compose.BackHandler(enabled = focusedId != null) { vm.clearFocus() }
    val scaffold = androidx.compose.material3.rememberBottomSheetScaffoldState()
    var layersOpen by remember { mutableStateOf(false) }
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
    // With no circle there is nothing to frame, so start on the person's own area instead of a default city.
    LaunchedEffect(ui.noCircles, perms.anyLocation) {
        if (!ui.noCircles || !perms.anyLocation) return@LaunchedEffect
        vm.myLastLocation()?.let { camera.move(CameraUpdateFactory.newLatLngZoom(LatLng(it.first, it.second), 15f)) }
    }

    // Tapping a pin (or a row) focuses that person and the camera then follows them: the map is padded by the
    // sheet, so the centre of what is left visible is where their pin goes.
    val focusedMember = ui.members.firstOrNull { it.userId == focusedId }
    var followedId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(focusedId, focusedMember?.lat, focusedMember?.lon) {
        if (focusedId == null) {
            followedId = null
            runCatching { scaffold.bottomSheetState.partialExpand() }
            return@LaunchedEffect
        }
        val first = followedId != focusedId
        if (first) { followedId = focusedId; runCatching { scaffold.bottomSheetState.expand() } }
        val m = focusedMember
        if (m?.lat != null && m.lon != null && m.presence != Presence.HIDDEN) {
            val at = LatLng(m.lat, m.lon)
            if (first) camera.animate(CameraUpdateFactory.newLatLngZoom(at, 16.5f), 450)
            else camera.animate(CameraUpdateFactory.newLatLng(at), 700)
        }
    }

    val primary = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outline
    val placeColor = MaterialTheme.colorScheme.secondary

    val bar = app.locana.ui.LocalBarInset.current
    val mapbox = ui.mapProvider == app.locana.data.prefs.MapProvider.MAPBOX && BuildConfig.HAS_MAPBOX
    // Where the drawer's top edge settles. The map is padded by it, so the camera's centre is the middle of what is
    // really visible between the header and the drawer: that is where a followed person's pin is kept.
    val densityNow = androidx.compose.ui.platform.LocalDensity.current.density
    var scaffoldHeightPx by remember { mutableStateOf(0) }
    var scaffoldWidthPx by remember { mutableStateOf(0) }
    var sheetTopPx by remember { mutableStateOf(0f) }
    LaunchedEffect(scaffold) {
        androidx.compose.runtime.snapshotFlow { runCatching { scaffold.bottomSheetState.requireOffset() }.getOrNull() }
            .filterNotNull()
            .collectLatest { v -> if (sheetTopPx == 0f) sheetTopPx = v else { delay(140); sheetTopPx = v } }
    }
    val visibleBottomPad = if (scaffoldHeightPx > 0 && sheetTopPx > 0f) ((scaffoldHeightPx - sheetTopPx) / densityNow).dp else 0.dp
    // When the drawer settles at a new height, put the followed pin back in the middle of what is left visible.
    LaunchedEffect(visibleBottomPad) {
        val m = focusedMember
        if (focusedId != null && m?.lat != null && m.lon != null && followedId == focusedId) camera.animate(CameraUpdateFactory.newLatLng(LatLng(m.lat, m.lon)), 350)
    }

    val statusTop = androidx.compose.foundation.layout.WindowInsets.Companion.statusBars.asPaddingValues().calculateTopPadding()
    BottomSheetScaffold(
        modifier = Modifier.onSizeChanged { scaffoldHeightPx = it.height; scaffoldWidthPx = it.width },
        scaffoldState = scaffold,
        // With no circle the sheet holds the only call to action, so it opens tall enough to show it.
        // The sheet reaches the bottom of the screen; the peek leaves room for the tab bar floating over it.
        sheetPeekHeight = (if (ui.noCircles) SheetPeekEmpty else SheetPeek) + bar - 24.dp,
        sheetContainerColor = MaterialTheme.colorScheme.background,
        sheetShape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
        sheetShadowElevation = 12.dp,
        sheetTonalElevation = 0.dp,
        sheetDragHandle = {
            Box(Modifier.padding(top = 10.dp, bottom = 10.dp).size(width = 40.dp, height = 5.dp).background(LocalTokens.current.line, RoundedCornerShape(3.dp)))
        },
        sheetContent = {
            // Expands to at most two thirds of the screen so the map stays in view.
            Box(Modifier.heightIn(max = (screenHeight * 0.66f).dp).padding(bottom = bar)) {
              androidx.compose.animation.AnimatedContent(
                targetState = focusedId,
                transitionSpec = { androidx.compose.animation.ContentTransform(androidx.compose.animation.fadeIn(Motion.enter(240)) + androidx.compose.animation.slideInVertically(Motion.enter(280)) { it / 12 }, androidx.compose.animation.fadeOut(Motion.enter(120))) },
                label = "sheet",
              ) { shownId ->
                val m = ui.members.firstOrNull { it.userId == shownId }
                if (m != null) MemberDetail(
                    m = m,
                    me = ui.members.firstOrNull { it.isMe },
                    place = placeAt(m, ui.places),
                    focus = focus,
                    onPrevDay = { vm.shiftDay(-1) },
                    onNextDay = { vm.shiftDay(1) },
                    onRetry = vm::retryTimeline,
                    onHide = { vm.hideFrom(m.userId, !m.hiddenFromThem) },
                    onManage = { ui.circleId?.let { nav.navigate(Routes.circleMembers(it)) } },
                    onOpenTrip = { vm.openReplay(it); nav.navigate(Routes.TRIP_REPLAY) },
                ) else CircleSheet(
                    ui = ui,
                    onMember = { vm.focus(it) },
                    onCreateCircle = { nav.navigate(Routes.CIRCLE_CREATE) },
                    onJoin = { nav.navigate(Routes.circleJoin()) },
                )
              }
            }
        },
    ) { pad ->
        val above = pad.calculateBottomPadding() + 16.dp
        Box(Modifier.fillMaxSize()) {
            if (BuildConfig.HAS_MAPS_KEY) {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = camera,
                    properties = MapProperties(
                        mapType = if (mapbox) com.google.maps.android.compose.MapType.NONE else com.google.maps.android.compose.MapType.NORMAL,
                        // Members (you included) are drawn as pins at your live fix, so the system dot would be a second, confusing position.
                        isMyLocationEnabled = perms.anyLocation && ui.noCircles,
                        mapStyleOptions = if (mapbox) null else MapStyleOptions.loadRawResourceStyle(context, if (dark) R.raw.map_style_dark else R.raw.map_style_light),
                    ),
                    uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false, mapToolbarEnabled = false),
                    // Restaurants, shops and buildings drawn by Google are tappable: a card with details opens.
                    onPOIClick = { p -> vm.selectPoi(p.placeId, p.name, p.latLng.latitude, p.latLng.longitude) },
                    onMapClick = { vm.clearPoi() },
                    contentPadding = PaddingValues(top = 76.dp + statusTop, bottom = if (visibleBottomPad > 0.dp) visibleBottomPad else pad.calculateBottomPadding()),
                ) {
                    if (mapbox) {
                        val provider = remember(dark) { mapboxTileProvider(BuildConfig.MAPBOX_TOKEN, dark) }
                        com.google.maps.android.compose.TileOverlay(tileProvider = provider, zIndex = -1f)
                    }
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
                            snippet = if (l.visited) "Unlocked" else "Locked · get within ${l.radiusM} m to claim, +${l.rewardXp} XP",
                            icon = BitmapDescriptorFactory.defaultMarker(if (l.visited) BitmapDescriptorFactory.HUE_AZURE else 172f),
                            alpha = if (l.visited) 0.7f else 1f,
                        )
                    }
                    if (ui.layers.members) MemberMarkers(ui.members, ui.places, focusedId) { vm.focus(it) }
                    nearby.kind?.let { kind ->
                        val density = androidx.compose.ui.platform.LocalDensity.current.density
                        nearby.places.forEach { p ->
                            val chosen = poi?.id == p.id
                            val icon = remember(kind, chosen, density) { BitmapDescriptorFactory.fromBitmap(renderEmojiDisc(kind.emoji, density, chosen)) }
                            Marker(
                                state = remember(p.id) { MarkerState(LatLng(p.lat, p.lon)) },
                                icon = icon,
                                anchor = androidx.compose.ui.geometry.Offset(0.5f, 0.5f),
                                title = p.name,
                                zIndex = 1.5f,
                                onClick = { vm.selectPoi(p.id, p.name, p.lat, p.lon, p); true },
                            )
                        }
                    }
                }
            } else {
                MissingMapKey(Modifier.fillMaxSize())
            }

            // Mapbox asks for its credit whenever its map is shown.
            if (mapbox && BuildConfig.HAS_MAPS_KEY) Text(
                MAPBOX_ATTRIBUTION,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF1F1645),
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = above + 62.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.78f)).padding(horizontal = 6.dp, vertical = 2.dp),
            )

            // People who are off the screen: an avatar on the nearest edge, pointing their way.
            if (BuildConfig.HAS_MAPS_KEY && ui.layers.members) {
                val dens = LocalDensity.current.density
                app.locana.ui.map.OffscreenPins(
                    members = ui.members.filter { it.userId != focusedId },
                    camera = camera,
                    topPx = (76.dp + statusTop).value * dens,
                    bottomPx = scaffoldHeightPx - (if (visibleBottomPad > 0.dp) visibleBottomPad.value * dens else (pad.calculateBottomPadding()).value * dens),
                    widthPx = scaffoldWidthPx.toFloat(),
                    onClick = { vm.focus(it) },
                )
            }

            // A soft wash under the status bar so its icons stay readable over any part of the map.
            Box(
                Modifier.align(Alignment.TopCenter).fillMaxWidth().height(statusTop + 14.dp)
                    .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(MaterialTheme.colorScheme.background.copy(alpha = 0.85f), Color.Transparent))),
            )

            // Top row, as in the reference: settings, circle switcher, notifications. While a person is focused: back and their name.
            Row(
                Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp + statusTop, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val focused = ui.members.firstOrNull { it.userId == focusedId }
                if (focused != null) {
                    MapControl(onClick = vm::clearFocus) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to the circle") }
                    Surface(shape = ControlShape, color = floatingColor(), shadowElevation = 6.dp, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalArrangement = Arrangement.Center) {
                            Text(focused.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                focused.recordedAtMs?.let { "Last updated " + Freshness.ageLabel(it, System.currentTimeMillis()) } ?: "No position yet",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    MapControl(onClick = { nav.navigate(Routes.PROFILE) }) { Icon(Icons.Outlined.Settings, contentDescription = "Profile and settings") }
                    CirclePill(ui = ui, onSelect = vm::select, onNew = { nav.navigate(Routes.CIRCLE_CREATE) }, onJoin = { nav.navigate(Routes.circleJoin()) }, modifier = Modifier.weight(1f))
                    MapControl(onClick = { nav.navigate(Routes.NOTIFICATIONS) }) {
                        BadgedBox(badge = { if (unread > 0) Badge { Text(if (unread > 9) "9+" else "$unread") } }) {
                            Icon(Icons.Outlined.Notifications, contentDescription = if (unread > 0) "Notifications, $unread unread" else "Notifications")
                        }
                    }
                }
            }
            // Without location permission the circle cannot see you, and sharing is automatic once you belong to one.
            if (!ui.noCircles && !perms.anyLocation && focusedId == null) {
                val tk = LocalTokens.current
                Box(Modifier.align(Alignment.TopCenter).padding(top = 12.dp + 48.dp + 10.dp + statusTop, start = 16.dp, end = 16.dp).fillMaxWidth().padding(bottom = 4.dp)) {
                    Box(Modifier.matchParentSize().graphicsLayer { translationY = 4.dp.toPx() }.background(tk.coralDark, ControlShape))
                    Surface(onClick = { nav.navigate(Routes.PERMISSIONS) }, shape = ControlShape, color = tk.coral, contentColor = Color.White, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Outlined.LocationOff, contentDescription = null, modifier = Modifier.size(20.dp))
                            Text("Allow location so your circle can see you", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold), modifier = Modifier.weight(1f))
                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, modifier = Modifier.rotate(-90f))
                        }
                    }
                }
            }

            // Standing at a locked landmark: offer to unlock it. Anything the claim answers is shown in the same spot.
            val claimMessage by vm.claimMessage.collectAsStateWithLifecycle()
            val claimable = ui.claimable
            // Keep what the card last showed so it can slide away with its content instead of going blank.
            var lastMessage by remember { mutableStateOf<String?>(null) }
            var lastClaimable by remember { mutableStateOf<app.locana.data.api.LandmarkDto?>(null) }
            if (claimMessage != null) lastMessage = claimMessage
            if (claimable != null) lastClaimable = claimable
            androidx.compose.animation.AnimatedVisibility(
                visible = focusedId == null && (claimable != null || claimMessage != null),
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = androidx.compose.animation.slideInVertically(androidx.compose.animation.core.spring(0.7f, 420f)) { it / 2 } + androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.slideOutVertically(Motion.enter(200)) { it / 2 } + androidx.compose.animation.fadeOut(Motion.enter(160)),
            ) {
                val tk = LocalTokens.current
                val shownClaim = claimable ?: lastClaimable
                Surface(
                    shape = RoundedCornerShape(22.dp), color = tk.card, shadowElevation = 6.dp,
                    modifier = Modifier.padding(start = 16.dp, end = 76.dp, bottom = above).fillMaxWidth().semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite },
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (claimMessage != null || (claimable == null && lastMessage != null)) {
                            Text(claimMessage ?: lastMessage.orEmpty(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            ChunkyButton("OK", { vm.claimShown() }, compact = true, style = ChunkyStyle.Secondary)
                        } else if (shownClaim != null) {
                            Icon(Icons.Outlined.LockOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column(Modifier.weight(1f)) {
                                Text(shownClaim.name, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold), maxLines = 1)
                                Text("You're here. Unlock it for +${shownClaim.rewardXp} XP", style = MaterialTheme.typography.bodySmall, color = tk.ink2)
                            }
                            ChunkyButton("Claim", { vm.claim(shownClaim) }, compact = true)
                        }
                    }
                }
            }

            // A monster on the road: a banner under the category chips, with a way to fight it now or ignore it.
            val monster by vm.monster.collectAsStateWithLifecycle()
            var ignoredMonster by remember { mutableStateOf<String?>(null) }
            androidx.compose.animation.AnimatedVisibility(
                visible = monster != null && monster?.id != ignoredMonster && focusedId == null,
                modifier = Modifier.align(Alignment.TopCenter),
                enter = androidx.compose.animation.slideInVertically(androidx.compose.animation.core.spring(0.6f, 380f)) { -it } + androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.slideOutVertically(Motion.enter(200)) { -it } + androidx.compose.animation.fadeOut(Motion.enter(150)),
            ) {
                var shown by remember { mutableStateOf(monster) }
                if (monster != null) shown = monster
                val e = shown
                if (e != null) {
                    val tk = LocalTokens.current
                    Surface(
                        shape = RoundedCornerShape(22.dp), color = tk.card, shadowElevation = 8.dp,
                        modifier = Modifier.padding(top = statusTop + 76.dp + 52.dp, start = 16.dp, end = 16.dp).fillMaxWidth().semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite },
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(e.monster.emoji, fontSize = 34.sp)
                            Column(Modifier.weight(1f)) {
                                Text("A wild ${e.monster.name} appeared!", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold), maxLines = 2)
                                Text("+${e.rewardCoins} coins, +${e.rewardXp} XP if you win", style = MaterialTheme.typography.bodySmall, color = tk.ink2)
                            }
                            ChunkyButton("Fight", { nav.navigate(Routes.BATTLE) }, compact = true)
                            Text("Later", style = MaterialTheme.typography.labelLarge, color = tk.ink2, modifier = Modifier.clickable(role = androidx.compose.ui.semantics.Role.Button) { ignoredMonster = e.id }.padding(6.dp))
                        }
                    }
                }
            }

            // Safety: hold SOS, and while someone else is in focus, quick messages to them.
            // Just above the drawer wherever it has settled (peek, or taller while someone is in focus).
            val overSheet = if (visibleBottomPad > 0.dp) visibleBottomPad + 16.dp else above
            val sosState by vm.sosState.collectAsStateWithLifecycle()
            val quickMessages by vm.quickMessages.collectAsStateWithLifecycle()
            val quickResult by vm.quickResult.collectAsStateWithLifecycle()
            app.locana.ui.safety.SosButton(
                onTriggered = vm::sendSos,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = overSheet + (if (focusedId == null) 64.dp else 0.dp)).size(52.dp),
            )
            app.locana.ui.safety.SosDialog(sosState, onDismiss = vm::dismissSos, onRetry = vm::sendSos)
            val focusedOther = ui.members.firstOrNull { it.userId == focusedId && !it.isMe && !it.blockedByMe }
            androidx.compose.animation.AnimatedVisibility(
                visible = focusedOther != null,
                modifier = Modifier.align(Alignment.BottomStart),
                enter = androidx.compose.animation.slideInVertically(androidx.compose.animation.core.spring(0.7f, 420f)) { it } + androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.slideOutVertically(Motion.enter(180)) { it } + androidx.compose.animation.fadeOut(Motion.enter(140)),
            ) {
                var lastOther by remember { mutableStateOf(focusedOther) }
                if (focusedOther != null) lastOther = focusedOther
                val target = focusedOther ?: lastOther
                Column(Modifier.padding(start = 16.dp, end = 80.dp, bottom = overSheet)) {
                    quickResult?.let { r ->
                        LaunchedEffect(r) { delay(2_400); vm.quickResultShown() }
                        Surface(shape = RoundedCornerShape(14.dp), color = LocalTokens.current.card, shadowElevation = 6.dp, modifier = Modifier.padding(bottom = 8.dp).semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite }) {
                            Text(r, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                        }
                    }
                    if (target != null) app.locana.ui.safety.QuickMessageRow(quickMessages, onSend = { vm.sendQuick(target.userId, target.name, it) })
                }
            }

            // Right: map layers, just above the sheet.
            if (focusedId == null) MapControl(onClick = { layersOpen = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = above)) {
                Icon(Icons.Outlined.Layers, contentDescription = "Map layers")
            }

            // Categories of places around the centre of the map. Each tap asks Google once; a second tap clears them.
            if (focusedId == null) {
                val chipTop = statusTop + (if (!ui.noCircles && !perms.anyLocation) 12.dp + 48.dp + 10.dp + 62.dp else 12.dp + 48.dp + 10.dp)
                Column(Modifier.align(Alignment.TopStart).padding(top = chipTop, start = 16.dp, end = 16.dp)) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NearbyKind.entries.forEach { kind ->
                            NearbyChip(kind, selected = nearby.kind == kind) {
                                val target = camera.position.target
                                val corner = camera.projection?.visibleRegion?.latLngBounds?.northeast
                                val radius = corner?.let { Geo.distanceM(LatLon(target.latitude, target.longitude), LatLon(it.latitude, it.longitude)) / 1.3 } ?: 1200.0
                                vm.toggleNearby(kind, target.latitude, target.longitude, radius.coerceIn(400.0, 3000.0))
                            }
                        }
                    }
                    val note = when {
                        nearby.loading -> "Looking around…"
                        nearby.error != null -> nearby.error
                        else -> null
                    }
                    if (note != null) {
                        Surface(shape = ControlShape, color = floatingColor(), shadowElevation = 4.dp, modifier = Modifier.padding(top = 8.dp)) {
                            Text(note, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                        }
                    }
                }
            }

            // The popup for a tapped place, above the buttons that sit over the sheet.
            poi?.takeIf { focusedId == null }?.let { p ->
                val me = ui.members.firstOrNull { it.isMe }
                PlaceCard(
                    poi = p,
                    from = if (me?.lat != null && me.lon != null) LatLon(me.lat, me.lon) else null,
                    onClose = vm::clearPoi,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(start = 16.dp, end = 16.dp, bottom = above + 52.dp + 12.dp),
                )
            }

            // Invite shortcut; sharing needs no button because belonging to a circle means sharing with it.
            if (!ui.noCircles && ui.circleId != null && focusedId == null) {
                MapPill("Invite", Icons.Outlined.PersonAdd, { nav.navigate(Routes.circleInvite(ui.circleId!!)) }, Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = above))
            }
        }
    }

    if (layersOpen) {
        ModalBottomSheet(onDismissRequest = { layersOpen = false }) { LayersSheet(ui.layers, vm::setLayers, ui.mapProvider, vm::setMapProvider) }
    }
}

@Composable
private fun NearbyChip(kind: NearbyKind, selected: Boolean, onClick: () -> Unit) {
    val t = LocalTokens.current
    val (icon, tint) = when (kind) {
        NearbyKind.FOOD -> Icons.Outlined.Restaurant to t.coral
        NearbyKind.CAFE -> Icons.Outlined.LocalCafe to app.locana.ui.theme.LocalTokens.current.onAmber
        NearbyKind.SHOPS -> Icons.Outlined.ShoppingBag to t.sky
        NearbyKind.SIGHTS -> Icons.Outlined.AccountBalance to t.mint
        NearbyKind.PARKS -> Icons.Outlined.Park to t.mint
    }
    LocanaChip(kind.label, selected, onClick, icon = icon, iconTint = tint)
}

/** Circle switcher: a green dot means you are sharing with this circle, a hollow one that you are not. */
@Composable
private fun CirclePill(ui: MapUi, onSelect: (String) -> Unit, onNew: () -> Unit, onJoin: () -> Unit, modifier: Modifier) {
    var open by remember { mutableStateOf(false) }
    val sharing = ui.sharingHere
    val name = ui.circle?.name ?: if (ui.noCircles) "No circle yet" else "Choose a circle"
    val scheme = MaterialTheme.colorScheme
    val t = LocalTokens.current
    Box(modifier) {
        Surface(
            onClick = { open = true },
            shape = ControlShape,
            color = floatingColor(),
            contentColor = scheme.onSurface,
            shadowElevation = 6.dp,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { contentDescription = "Circle $name, " + if (sharing) "sharing" else "not sharing" },
        ) {
            Row(Modifier.padding(start = 14.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(10.dp)
                        .background(if (sharing) t.mint else Color.Transparent, CircleShape)
                        .border(2.dp, if (sharing) t.mintSoft else scheme.outline, CircleShape),
                )
                Spacer(Modifier.width(10.dp))
                Text(name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            ui.circles.forEach { (id, n) -> DropdownMenuItem(text = { Text(n) }, onClick = { open = false; onSelect(id) }) }
            if (ui.circles.isNotEmpty()) HorizontalDivider()
            DropdownMenuItem(text = { Text("New circle") }, onClick = { open = false; onNew() })
            DropdownMenuItem(text = { Text("Join with a code") }, onClick = { open = false; onJoin() })
        }
    }
}

@Composable
private fun CircleSheet(
    ui: MapUi,
    onMember: (String) -> Unit,
    onCreateCircle: () -> Unit,
    onJoin: () -> Unit,
) {
    if (ui.noCircles) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 4.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Start with a circle", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "A small private group: your family, a partner or friends. Make one and invite them, or join with a code.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Illustration("friends", size = 104.dp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton("Create a circle", onCreateCircle, Modifier.weight(1f))
                SecondaryButton("I have a code", onJoin, Modifier.weight(1f))
            }
        }
        return
    }
    val circle = ui.circle
    val members = remember(ui.members) { ui.members.sortedWith(compareBy({ !it.isMe }, { presenceOrder(it.presence) }, { it.name.lowercase() })) }
    val live = ui.members.filter { !it.isMe && it.presence == Presence.LIVE }
    LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(circle?.name ?: "Loading your circle", style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (circle != null) {
                        Text(
                            (if (members.size == 1) "1 member" else "${members.size} members") + when {
                                live.size == 1 -> " · ${live[0].name} is live"
                                live.size > 1 -> " · ${live.size} are live"
                                else -> " · nobody else live right now"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                AvatarStack(members.take(3).map { it.name to it.avatarKey }, size = 44.dp, ring = Color.White)
            }
        }
        items(members, key = { it.userId }) { m -> MemberRow(m, placeAt(m, ui.places)) { onMember(m.userId) } }
    }
}

private fun presenceOrder(p: Presence) = when (p) {
    Presence.LIVE -> 0
    Presence.STALE -> 1
    Presence.UNAVAILABLE -> 2
    Presence.HIDDEN -> 3
}

/** A white card for one member: avatar, "Name · At Home" and how long, with their battery. */
@Composable
private fun MemberRow(m: MemberPin, place: PlaceDto?, onClick: () -> Unit) {
    val now = System.currentTimeMillis()
    val age = m.recordedAtMs?.let { Freshness.ageLabel(it, now) }
    val caption = captionFor(m, place, now)
    val title = buildString {
        append(if (m.isMe) "${m.name} (you)" else m.name)
        if (m.presence == Presence.LIVE || m.presence == Presence.STALE) {
            when {
                m.movement == "STAYING" && place != null -> append(" · At ${place.name}")
                caption.label == "Driving" || caption.label == "Walking" || caption.label == "Cycling" -> append(" · ${caption.label}")
            }
        }
    }
    val line = when (m.presence) {
        Presence.LIVE -> when {
            caption.label == "Here for" -> "Here for ${caption.value}"
            caption.label != null -> "${caption.label} · ${caption.value}"
            else -> "Live" + (age?.let { " · $it" } ?: "")
        }
        Presence.STALE -> "Last seen " + (age ?: "a while ago")
        Presence.HIDDEN -> "Not sharing with you"
        Presence.UNAVAILABLE -> "No position yet"
    }
    val battery = m.batteryPct?.takeIf { m.presence == Presence.LIVE || m.presence == Presence.STALE }
    LocanaCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), onClick = onClick, shadow = 2.dp) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(m.name, m.avatarKey, size = 44.dp, framed = m.framed, frameKey = m.frameKey, userId = m.userId, photoVersion = m.photoVersion)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    line + (battery?.let { " · \uD83D\uDD0B $it%" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = LocalTokens.current.ink3, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun LayersSheet(l: MapLayers, onChange: (MapLayers) -> Unit, provider: app.locana.data.prefs.MapProvider, onProvider: (app.locana.data.prefs.MapProvider) -> Unit) {
    Column(Modifier.padding(bottom = 32.dp)) {
        Text("Map style", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        app.locana.ui.components.Segmented(
            listOf(app.locana.data.prefs.MapProvider.GOOGLE to "Google", app.locana.data.prefs.MapProvider.MAPBOX to "Mapbox"),
            provider, { onProvider(it) }, Modifier.padding(horizontal = 24.dp),
        )
        Text(
            if (BuildConfig.HAS_MAPBOX) "Mapbox draws the base map; pins, tiles and places stay the same. Search for places still uses Google."
            else "Mapbox is not set up in this build (it needs a MAPBOX_ACCESS_TOKEN), so the Google map is used.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
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
        trailingContent = { LocanaSwitch(checked = checked, onCheckedChange = onChange) },
        modifier = Modifier.clickable(role = Role.Switch) { onChange(!checked) },
    )
}

@Composable
private fun MissingMapKey(modifier: Modifier) {
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerLow), contentAlignment = Alignment.Center) {
        Text(
            "Map unavailable: this build has no Google Maps API key.\nBuild with MAPS_API_KEY set: in local.properties on your computer, or as the MAPS_API_KEY secret for GitHub releases (see docs/release.md).",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(32.dp),
        )
    }
}
