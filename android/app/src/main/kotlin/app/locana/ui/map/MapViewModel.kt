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
    val stationarySinceMs: Long? = null,
    val isMe: Boolean,
    val hiddenFromThem: Boolean,
    val blockedByMe: Boolean,
)

/** The member whose pin was tapped: their day of stays and trips shows in the sheet. */
data class FocusUi(val userId: String, val day: java.time.LocalDate, val items: app.locana.ui.components.UiState<List<app.locana.ui.timeline.TimelineItem>>)

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
    private data class Tick(val now: Long, val fix: android.location.Location?, val previous: android.location.Location?, val batteryPct: Int?)

    private var lastFix: android.location.Location? = null

    private val clock = flow {
        while (true) {
            val fix = ownFix()
            val previous = lastFix
            if (fix != null) lastFix = fix
            emit(Tick(System.currentTimeMillis(), fix, previous, signals.battery().pct))
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
            // Moving needs a recent, precise fix whose speed is backed by a real step since the last one.
            val step = if (own != null && tick.previous != null) own.distanceTo(tick.previous).toDouble() else 0.0
            val ownMoving = own != null && now - own.time <= 90_000 && own.hasSpeed() && own.speed >= 1.5f && own.accuracy <= 30f && step >= 15.0 && r?.movement != "STAYING"
            val movement = if (own != null) (if (ownMoving) "MOVING" else r?.movement?.takeIf { it == "STAYING" } ?: "UNKNOWN") else r?.movement
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
                bearingDeg = if (isMe) live.heading?.toDouble() ?: own?.takeIf { it.hasBearing() && ownMoving }?.bearing?.toDouble() ?: r?.bearingDeg else r?.bearingDeg,
                speedMps = if (ownMoving) own?.speed?.toDouble() else if (own != null) null else r?.speedMps,
                movement = movement,
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

    fun select(id: String) { _focus.value = null; selection.select(id) }

    private val _focus = MutableStateFlow<FocusUi?>(null)
    val focus: StateFlow<FocusUi?> = _focus.asStateFlow()

    fun focus(userId: String) {
        _focus.value = FocusUi(userId, java.time.LocalDate.now(), app.locana.ui.components.UiState.Loading)
        loadTimeline()
    }

    fun clearFocus() { _focus.value = null }

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
                else cur.copy(items = r.fold({ app.locana.ui.components.UiState.Data(app.locana.ui.timeline.buildItems(it.segments, today = f.day == java.time.LocalDate.now())) }, { app.locana.ui.components.UiState.Error(it.message ?: "Couldn't load places and trips") }))
            }
        }
    }

    fun setLayers(l: MapLayers) = viewModelScope.launch { settings.setLayers(l) }

    fun hideFrom(viewerId: String, hidden: Boolean) {
        val id = selection.selected.value ?: return
        viewModelScope.launch { circles.hideFrom(id, viewerId, hidden); loadCircle(id) }
    }

    /** Game layers follow the camera, debounced so panning does not flood the server. */
    fun onViewport(b: TileBounds, zoom: Float, centerLat: Double, centerLon: Double) {
        viewportJob?.cancel()
        viewportJob = viewModelScope.launch {
            delay(600)
            val layers = settings.snapshot().layers
            if (layers.tiles && zoom >= 13f) game.tiles(b).onSuccess { t -> overlay.update { it.copy(tiles = t.tiles) } }
            else overlay.update { it.copy(tiles = emptyList()) }
            if (layers.territories && zoom >= 11f) game.territories(b).onSuccess { t -> overlay.update { it.copy(territories = t) } }
            if (layers.landmarks && zoom >= 11f) game.landmarks(centerLat, centerLon).onSuccess { l -> overlay.update { it.copy(landmarks = l) } }
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun myLastLocation(): Pair<Double, Double>? {
        if (!tracking.refreshPermissions().anyLocation) return null
        return runCatching { LocationServices.getFusedLocationProviderClient(context).lastLocation.await() }.getOrNull()?.let { it.latitude to it.longitude }
    }
}
