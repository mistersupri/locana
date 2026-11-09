package app.locana.ui.map

import android.annotation.SuppressLint
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.locana.data.api.CircleDetailDto
import app.locana.data.api.CircleSummaryDto
import app.locana.data.api.LandmarkDto
import app.locana.data.api.PlaceDto
import app.locana.data.api.SharingStateDto
import app.locana.data.api.TerritorySummaryDto
import app.locana.data.api.TileDto
import app.locana.data.location.TrackingController
import app.locana.data.prefs.MapLayers
import app.locana.data.prefs.SettingsStore
import app.locana.data.system.DeviceSignals
import app.locana.data.repo.CircleRepository
import app.locana.data.repo.GameRepository
import app.locana.data.repo.LocationRepository
import app.locana.data.repo.MeRepository
import app.locana.data.repo.NotificationRepository
import app.locana.domain.Freshness
import app.locana.domain.Presence
import app.locana.domain.TileBounds
import app.locana.ui.nav.CircleSelection
import com.google.android.gms.location.LocationServices
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

data class MemberPin(
    val userId: String,
    val name: String,
    val avatarKey: String?,
    val photoVersion: Long? = null,
    val framed: Boolean,
    val presence: Presence,
    val lat: Double?,
    val lon: Double?,
    val accuracyM: Double?,
    val recordedAtMs: Long?,
    val batteryPct: Int?,
    val bearingDeg: Double? = null,
    val speedMps: Double? = null,
    /** STAYING, MOVING or UNKNOWN. */
    val movement: String? = null,
    /** DRIVING, CYCLING or WALKING while moving. */
    val mode: String? = null,
    val stationarySinceMs: Long? = null,
    val isMe: Boolean,
    val hiddenFromThem: Boolean,
    val blockedByMe: Boolean,
)

/** The member whose pin was tapped: their day of stays and trips shows in the sheet. */
data class FocusUi(val userId: String, val day: java.time.LocalDate, val items: app.locana.ui.components.UiState<List<app.locana.ui.timeline.TimelineItem>>, /** Positions the server has for this day, even when they are not yet a stay or a trip. */ val points: Int = 0)

/** A place the person tapped on the map (a Google point of interest, or one of our nearby markers). */
data class PoiUi(
    val id: String?,
    val name: String,
    val lat: Double,
    val lon: Double,
    val place: app.locana.data.places.Place? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

data class NearbyUi(
    val kind: app.locana.data.places.NearbyKind? = null,
    val places: List<app.locana.data.places.Place> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

data class MapUi(
    val circles: List<Pair<String, String>> = emptyList(),
    val circleId: String? = null,
    val circle: CircleDetailDto? = null,
    val members: List<MemberPin> = emptyList(),
    val sharing: SharingStateDto? = null,
    val sharingHere: Boolean = false,
    val layers: MapLayers = MapLayers(),
    val places: List<PlaceDto> = emptyList(),
    val tiles: List<TileDto> = emptyList(),
    val territories: List<TerritorySummaryDto> = emptyList(),
    val landmarks: List<LandmarkDto> = emptyList(),
    val refreshing: Boolean = false,
    val error: String? = null,
    val noCircles: Boolean = false,
)

private data class Base(val circles: List<CircleSummaryDto>, val selected: String?, val detail: CircleDetailDto?, val sharing: SharingStateDto?, val layers: MapLayers)

private data class Overlay(val places: List<PlaceDto> = emptyList(), val tiles: List<TileDto> = emptyList(), val territories: List<TerritorySummaryDto> = emptyList(), val landmarks: List<LandmarkDto> = emptyList())

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MapViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val circles: CircleRepository,
    private val location: LocationRepository,
    private val game: GameRepository,
    private val me: MeRepository,
    notifications: NotificationRepository,
    private val settings: SettingsStore,
    private val selection: CircleSelection,
    val tracking: TrackingController,
    private val signals: DeviceSignals,
    private val heading: app.locana.data.system.HeadingSensor,
    private val activity: app.locana.data.system.ActivityMonitor,
    private val places: app.locana.data.places.NearbyPlaces,
) : ViewModel() {
    /** Unread notifications, for the badge on the bell. */
    val unread: StateFlow<Int> = notifications.unread

    private val detail = MutableStateFlow<CircleDetailDto?>(null)
    private val overlay = MutableStateFlow(Overlay())
    private val status = MutableStateFlow(Triple(false, null as String?, false))
    private var viewportJob: Job? = null

    /**
     * Ticks every 15 s so "LIVE" turns into "STALE" on screen without new data, and carries this
     * phone's own newest fix and battery: your own pin should be where you are now, not where the
     * last upload happened to be.
     */
    private data class Tick(val now: Long, val fix: android.location.Location?, val previous: android.location.Location?, val batteryPct: Int?, val activity: String?)

    private var lastFix: android.location.Location? = null

    private val clock = flow {
        while (true) {
            val fix = ownFix()
            val previous = lastFix
            if (fix != null) lastFix = fix
            emit(Tick(System.currentTimeMillis(), fix, previous, signals.battery().pct, activity.current()))
            delay(15_000)
        }
    }

    /** This phone's compass, only while the map is on screen. */
    private val compass = flow<Float?> { emitAll(heading.live { lastFix }) }

    private data class Live(val tick: Tick, val heading: Float?)

    @SuppressLint("MissingPermission")
    private suspend fun ownFix(): android.location.Location? {
        if (!tracking.refreshPermissions().anyLocation) return null
        return runCatching { LocationServices.getFusedLocationProviderClient(context).lastLocation.await() }.getOrNull()
    }

    private val pins = selection.selected.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else location.memberLocations(id) }

    val ui: StateFlow<MapUi> = combine(
        combine(circles.circles, selection.selected, detail, location.sharing, settings.settings) { c, sel, d, sh, st ->
            Base(c?.value.orEmpty(), sel, d, sh, st.layers)
        },
        pins, combine(clock, compass.onStart { emit(null) }) { t, h -> Live(t, h) }, overlay, status,
    ) { base, rows, live, ov, st ->
        val tick = live.tick
        val now = tick.now
        val sel = base.selected
        val d = base.detail?.takeIf { it.id == sel }
        val sh = base.sharing
        val layers = base.layers
        val meId = me.me.value?.id
        val byId = rows.associateBy { it.userId }
        val members = if (d == null) emptyList() else d.members.map { m ->
            val r = byId[m.userId]
            val serverPresence = r?.status?.let { runCatching { Presence.valueOf(it) }.getOrNull() } ?: Presence.UNAVAILABLE
            val isMe = m.userId == meId
            // Own pin: prefer this phone's own fix when it is newer than what the server has.
            val own = tick.fix?.takeIf { isMe && it.time >= (r?.recordedAtMs ?: 0L) && now - it.time < 10 * 60_000 }
            val recordedAt = own?.time ?: r?.recordedAtMs
            // Your own state comes from this phone's activity recognition when allowed (no lag, and it knows a jam
            // from a stroll); otherwise from the server's verdict on your last upload, like everyone else's.
            val serverMovement = r?.movement
            val local = if (own != null) tick.activity else null
            val localMode = when (local) { "vehicle" -> "DRIVING"; "cycling" -> "CYCLING"; "walking", "running" -> "WALKING"; else -> null }
            val movement = when {
                local != null && localMode != null && serverMovement != "STAYING" -> "MOVING"
                local == "still" && serverMovement != "STAYING" -> "UNKNOWN"
                else -> serverMovement
            }
            val mode = if (movement == "MOVING") localMode ?: r?.mode else null
            val ownSpeed = own?.takeIf { it.hasSpeed() && now - it.time <= 90_000 }?.speed?.toDouble()
            MemberPin(
                userId = m.userId,
                name = m.displayName,
                avatarKey = m.avatarKey,
                photoVersion = m.avatarPhotoVersion,
                framed = m.avatarFrameKey != null,
                presence = if (own != null) Presence.LIVE.let { Freshness.presence(it, recordedAt, now) } else Freshness.presence(serverPresence, r?.recordedAtMs, now),
                lat = own?.latitude ?: r?.latitude, lon = own?.longitude ?: r?.longitude, accuracyM = own?.accuracy?.toDouble() ?: r?.accuracyM,
                recordedAtMs = recordedAt,
                batteryPct = if (isMe) tick.batteryPct ?: r?.batteryPct else r?.batteryPct,
                // Your own pin points where your phone points, live; others show the heading of their last upload.
                bearingDeg = if (isMe) live.heading?.toDouble() ?: r?.bearingDeg else r?.bearingDeg,
                speedMps = if (movement == "MOVING") (if (isMe) ownSpeed ?: r?.speedMps else r?.speedMps) else null,
                movement = movement,
                mode = mode,
                stationarySinceMs = if (r?.movement == "STAYING" && movement == "STAYING") r.stationarySinceMs else null,
                isMe = isMe,
                hiddenFromThem = m.userId in d.hiddenFromViewerIds,
                blockedByMe = m.blockedByMe,
            )
        }
        MapUi(
            circles = base.circles.map { it.id to it.name },
            circleId = sel,
            circle = d,
            members = members,
            sharing = sh,
            sharingHere = sh != null && !sh.globallyPaused && sh.sessions.any { it.circleId == sel && it.status == "ACTIVE" },
            layers = layers,
            places = ov.places,
            tiles = ov.tiles,
            territories = ov.territories,
            landmarks = ov.landmarks,
            refreshing = st.first,
            error = st.second,
            noCircles = st.third,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MapUi())

    init {
        viewModelScope.launch {
            circles.loadCached()
            circles.circles.filterNotNull().map { c -> c.value.map { it.id } }.collect { ids ->
                selection.reconcile(ids)
                status.update { it.copy(third = ids.isEmpty()) }
            }
        }
        viewModelScope.launch { selection.selected.filterNotNull().collect { loadCircle(it) } }
        refresh()
    }

    private suspend fun loadCircle(id: String) {
        circles.cachedDetail(id)?.let { detail.value = it.value }
        circles.detail(id).onSuccess { detail.value = it }
        location.refreshLocations(id)
        circles.places(id).onSuccess { p -> overlay.update { it.copy(places = p) } }
    }

    fun refresh() {
        viewModelScope.launch {
            status.update { it.copy(first = true, second = null) }
            val r = circles.refresh()
            // Belonging to a circle means sharing with it; the server opens the sessions, we start collecting.
            location.refreshSharing().onSuccess { tracking.ensureRunning(it.collectLocation) }
            selection.selected.value?.let { loadCircle(it) }
            status.update { it.copy(first = false, second = r.exceptionOrNull()?.message) }
        }
    }

    fun select(id: String) { clearFocus(); selection.select(id) }

    private val _poi = MutableStateFlow<PoiUi?>(null)
    val poi: StateFlow<PoiUi?> = _poi.asStateFlow()
    private val _nearby = MutableStateFlow(NearbyUi())
    val nearby: StateFlow<NearbyUi> = _nearby.asStateFlow()
    val placesAvailable: Boolean get() = places.available

    /** Shows what we already know at once (the name from the map), then fills in details when Google answers. */
    fun selectPoi(id: String?, name: String, lat: Double, lon: Double, known: app.locana.data.places.Place? = null) {
        _poi.value = PoiUi(id, name, lat, lon, known, loading = id != null && known?.address == null)
        if (id == null || known?.address != null || !places.available) {
            _poi.update { it?.copy(loading = false) }
            return
        }
        viewModelScope.launch {
            val r = places.details(id)
            _poi.update { cur ->
                if (cur?.id != id) cur
                else r.fold({ cur.copy(place = it, loading = false, error = null) }, { cur.copy(loading = false, error = it.message) })
            }
        }
    }

    fun clearPoi() { _poi.value = null }

    /** Tapping the active category again turns it off. */
    fun toggleNearby(kind: app.locana.data.places.NearbyKind, lat: Double, lon: Double, radiusM: Double) {
        if (_nearby.value.kind == kind) { _nearby.value = NearbyUi(); return }
        _nearby.value = NearbyUi(kind, loading = true)
        viewModelScope.launch {
            val r = places.nearby(lat, lon, radiusM, kind)
            _nearby.update { cur ->
                if (cur.kind != kind) cur
                else r.fold({ cur.copy(places = it, loading = false, error = if (it.isEmpty()) "Nothing of this kind found around here" else null) }, { cur.copy(loading = false, error = it.message) })
            }
        }
    }

    private val _focus = MutableStateFlow<FocusUi?>(null)
    val focus: StateFlow<FocusUi?> = _focus.asStateFlow()

    private var refreshJob: Job? = null

    fun focus(userId: String) {
        _poi.value = null
        _focus.value = FocusUi(userId, java.time.LocalDate.now(), app.locana.ui.components.UiState.Loading)
        loadTimeline()
        // New positions keep arriving, so today's day is reloaded quietly while the panel is open.
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            while (true) {
                delay(15_000)
                val f = _focus.value ?: break
                if (f.userId != userId) break
                if (f.day == java.time.LocalDate.now()) loadTimeline()
            }
        }
    }

    fun clearFocus() { refreshJob?.cancel(); _focus.value = null }

    fun shiftDay(days: Long) {
        val f = _focus.value ?: return
        val next = f.day.plusDays(days)
        if (next.isAfter(java.time.LocalDate.now())) return
        _focus.value = f.copy(day = next, items = app.locana.ui.components.UiState.Loading)
        loadTimeline()
    }

    fun retryTimeline() {
        _focus.update { f -> f?.copy(items = app.locana.ui.components.UiState.Loading) }
        loadTimeline()
    }

    private fun loadTimeline() {
        val f = _focus.value ?: return
        val circle = selection.selected.value ?: return
        viewModelScope.launch {
            val r = location.timeline(circle, f.userId, f.day)
            // Ignore an answer for a person or day the sheet has already moved away from.
            _focus.update { cur ->
                if (cur?.userId != f.userId || cur.day != f.day) cur
                else cur.copy(points = r.getOrNull()?.points ?: 0, items = r.fold({ app.locana.ui.components.UiState.Data(app.locana.ui.timeline.buildItems(it.segments, today = f.day == java.time.LocalDate.now())) }, { app.locana.ui.components.UiState.Error(it.message ?: "Couldn't load places and trips") }))
            }
        }
    }

    fun setLayers(l: MapLayers) = viewModelScope.launch { settings.setLayers(l) }

    fun hideFrom(viewerId: String, hidden: Boolean) {
        val id = selection.selected.value ?: return
        viewModelScope.launch { circles.hideFrom(id, viewerId, hidden); loadCircle(id) }
    }

    // Tiles and landmarks seen so far are kept and only added to: panning back shows them at once, and each
    // request covers a margin around the screen, so the next pan usually finds its tiles already loaded.
    private val tileCache = LinkedHashMap<String, TileDto>()
    private val landmarkCache = LinkedHashMap<String, LandmarkDto>()

    /** Game layers follow the camera, debounced so panning does not flood the server. */
    fun onViewport(b: TileBounds, zoom: Float, centerLat: Double, centerLon: Double) {
        viewportJob?.cancel()
        viewportJob = viewModelScope.launch {
            delay(250)
            val layers = settings.snapshot().layers
            if (layers.tiles && zoom >= 13f) {
                val padLat = (b.north - b.south) * 0.5
                val padLon = (b.east - b.west) * 0.5
                game.tiles(TileBounds(b.north + padLat, b.south - padLat, b.east + padLon, b.west - padLon)).onSuccess { t ->
                    if (!t.tooLarge) {
                        t.tiles.forEach { tileCache[it.id] = it }
                        while (tileCache.size > 4000) tileCache.remove(tileCache.keys.first())
                        overlay.update { it.copy(tiles = tileCache.values.toList()) }
                    }
                }
            } else if (!layers.tiles) overlay.update { it.copy(tiles = emptyList()) }
            if (layers.territories && zoom >= 11f) game.territories(b).onSuccess { t -> overlay.update { it.copy(territories = t) } }
            if (layers.landmarks && zoom >= 11f) game.landmarks(centerLat, centerLon).onSuccess { l ->
                l.forEach { landmarkCache[it.id] = it }
                while (landmarkCache.size > 500) landmarkCache.remove(landmarkCache.keys.first())
                overlay.update { it.copy(landmarks = landmarkCache.values.toList()) }
            }
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun myLastLocation(): Pair<Double, Double>? {
        if (!tracking.refreshPermissions().anyLocation) return null
        return runCatching { LocationServices.getFusedLocationProviderClient(context).lastLocation.await() }.getOrNull()?.let { it.latitude to it.longitude }
    }
}
