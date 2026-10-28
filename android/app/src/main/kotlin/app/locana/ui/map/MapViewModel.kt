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
import app.locana.data.repo.CircleRepository
import app.locana.data.repo.GameRepository
import app.locana.data.repo.LocationRepository
import app.locana.data.repo.MeRepository
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
    val framed: Boolean,
    val presence: Presence,
    val lat: Double?,
    val lon: Double?,
    val accuracyM: Double?,
    val recordedAtMs: Long?,
    val batteryPct: Int?,
    val isMe: Boolean,
    val hiddenFromThem: Boolean,
    val blockedByMe: Boolean,
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
    private val settings: SettingsStore,
    private val selection: CircleSelection,
    val tracking: TrackingController,
) : ViewModel() {
    private val detail = MutableStateFlow<CircleDetailDto?>(null)
    private val overlay = MutableStateFlow(Overlay())
    private val status = MutableStateFlow(Triple(false, null as String?, false))
    private var viewportJob: Job? = null

    /** Ticks every 30 s so "LIVE" turns into "STALE" on screen without new data. */
    private val clock = flow { while (true) { emit(System.currentTimeMillis()); delay(30_000) } }

    private val pins = selection.selected.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else location.memberLocations(id) }

    val ui: StateFlow<MapUi> = combine(
        combine(circles.circles, selection.selected, detail, location.sharing, settings.settings) { c, sel, d, sh, st ->
            Base(c?.value.orEmpty(), sel, d, sh, st.layers)
        },
        pins, clock, overlay, status,
    ) { base, rows, now, ov, st ->
        val sel = base.selected
        val d = base.detail?.takeIf { it.id == sel }
        val sh = base.sharing
        val layers = base.layers
        val meId = me.me.value?.id
        val byId = rows.associateBy { it.userId }
        val members = if (d == null) emptyList() else d.members.map { m ->
            val r = byId[m.userId]
            val serverPresence = r?.status?.let { runCatching { Presence.valueOf(it) }.getOrNull() } ?: Presence.UNAVAILABLE
            MemberPin(
                userId = m.userId,
                name = m.displayName,
                avatarKey = m.avatarKey,
                framed = m.avatarFrameKey != null,
                presence = Freshness.presence(serverPresence, r?.recordedAtMs, now),
                lat = r?.latitude, lon = r?.longitude, accuracyM = r?.accuracyM,
                recordedAtMs = r?.recordedAtMs, batteryPct = r?.batteryPct,
                isMe = m.userId == meId,
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
            location.refreshSharing()
            selection.selected.value?.let { loadCircle(it) }
            status.update { it.copy(first = false, second = r.exceptionOrNull()?.message) }
        }
    }

    fun select(id: String) = selection.select(id)

    fun setLayers(l: MapLayers) = viewModelScope.launch { settings.setLayers(l) }

    fun startSharing(minutes: Int?) {
        val id = selection.selected.value ?: return
        viewModelScope.launch {
            location.start(id, minutes)
                .onSuccess { tracking.ensureRunning(it.collectLocation) }
                .onFailure { e -> status.update { s -> s.copy(second = e.message) } }
        }
    }

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
