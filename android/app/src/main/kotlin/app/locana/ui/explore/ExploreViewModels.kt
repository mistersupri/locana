package app.locana.ui.explore

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.locana.data.api.ClaimResultDto
import app.locana.data.api.GameEventDto
import app.locana.data.api.LandmarkDto
import app.locana.data.api.PrivacyPatch
import app.locana.data.api.QuestDto
import app.locana.data.api.StatsDto
import app.locana.data.api.TerritoryDetailDto
import app.locana.data.api.TileDto
import app.locana.data.location.TrackingController
import app.locana.data.repo.GameRepository
import app.locana.data.repo.LocationRepository
import app.locana.data.repo.MeRepository
import app.locana.domain.TileBounds
import app.locana.ui.components.UiState
import app.locana.ui.game.Celebrations
import com.google.android.gms.location.LocationServices
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

data class ExploreData(val quests: List<QuestDto>, val events: List<GameEventDto>, val stats: StatsDto?)

@HiltViewModel
class ExploreViewModel @Inject constructor(
    private val game: GameRepository,
    private val me: MeRepository,
    private val location: LocationRepository,
    val tracking: TrackingController,
) : ViewModel() {
    private val _state = MutableStateFlow<UiState<ExploreData>>(UiState.Loading)
    val state: StateFlow<UiState<ExploreData>> = _state.asStateFlow()
    val explorationEnabled: StateFlow<Boolean> = me.me.map { it?.privacy?.explorationEnabled == true }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    private val _toggleError = MutableStateFlow<String?>(null)
    val toggleError: StateFlow<String?> = _toggleError.asStateFlow()

    init {
        viewModelScope.launch { location.gameResults.collect { load(silent = true) } }
        load()
    }

    fun load(silent: Boolean = false) {
        viewModelScope.launch {
            val cached = game.cachedQuests()
            if (!silent) _state.value = cached?.let { UiState.Data(ExploreData(it.value, emptyList(), null), it.updatedAtMs) } ?: UiState.Loading
            val q = game.quests()
            val ev = game.events().getOrDefault(emptyList())
            val st = game.stats().getOrNull()
            _state.value = q.fold(
                onSuccess = { UiState.Data(ExploreData(it, ev, st)) },
                onFailure = { e -> cached?.let { UiState.Data(ExploreData(it.value, ev, st), it.updatedAtMs) } ?: UiState.Error(e.message ?: "Couldn't load quests") },
            )
        }
    }

    /** Exploration is opt-in: it lets the game use the device location, even without sharing. */
    fun setExploration(enabled: Boolean) {
        viewModelScope.launch {
            me.privacy(PrivacyPatch(explorationEnabled = enabled))
                .onSuccess {
                    location.setExplorationLocally(enabled)
                    location.refreshSharing().onSuccess { s -> tracking.ensureRunning(s.collectLocation) }
                    _toggleError.value = null
                }
                .onFailure { _toggleError.value = it.message }
        }
    }
}

@HiltViewModel
class QuestViewModel @Inject constructor(
    private val game: GameRepository,
    private val me: MeRepository,
    private val celebrations: Celebrations,
    handle: SavedStateHandle,
) : ViewModel() {
    private val id: String = checkNotNull(handle["id"])
    private val _state = MutableStateFlow<UiState<QuestDto>>(UiState.Loading)
    val state: StateFlow<UiState<QuestDto>> = _state.asStateFlow()
    private val _claim = MutableStateFlow<ClaimResultDto?>(null)
    val claim: StateFlow<ClaimResultDto?> = _claim.asStateFlow()
    private val _claiming = MutableStateFlow(false)
    val claiming: StateFlow<Boolean> = _claiming.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init { load() }

    fun load() = viewModelScope.launch {
        _state.value = UiState.Loading
        _state.value = game.quest(id).fold({ UiState.Data(it) }, { UiState.Error(it.message ?: "Couldn't load the quest") })
    }

    /** Safe to tap twice or retry after a timeout: the repository reuses one idempotency key. */
    fun claim() {
        if (_claiming.value) return
        _claiming.value = true
        _error.value = null
        viewModelScope.launch {
            game.claim(id)
                .onSuccess { r ->
                    _claim.value = r
                    _state.value = UiState.Data(r.quest)
                    me.refresh()
                    r.levelUps.lastOrNull()?.let { celebrations.levelUp(it.to, r.collectibles.map { c -> c.name }) }
                }
                .onFailure { _error.value = it.message }
            _claiming.value = false
        }
    }

    fun dismissClaim() {
        _claim.value = null
    }
}

@HiltViewModel
class QuestHistoryViewModel @Inject constructor(private val game: GameRepository) : ViewModel() {
    private val _state = MutableStateFlow<UiState<List<QuestDto>>>(UiState.Loading)
    val state: StateFlow<UiState<List<QuestDto>>> = _state.asStateFlow()
    private var cursor: String? = null
    var hasMore = true
        private set

    init { more() }

    fun more() = viewModelScope.launch {
        if (!hasMore) return@launch
        val current = (_state.value as? UiState.Data)?.value.orEmpty()
        game.questHistory(cursor)
            .onSuccess { p ->
                cursor = p.nextCursor
                hasMore = p.nextCursor != null
                _state.value = UiState.Data(current + p.items)
            }
            .onFailure { e -> if (current.isEmpty()) _state.value = UiState.Error(e.message ?: "Couldn't load history") }
    }
}

@HiltViewModel
class LandmarksViewModel @Inject constructor(
    private val game: GameRepository,
    private val context: android.app.Application,
    private val tracking: TrackingController,
) : ViewModel() {
    private val _state = MutableStateFlow<UiState<List<LandmarkDto>>>(UiState.Loading)
    val state: StateFlow<UiState<List<LandmarkDto>>> = _state.asStateFlow()
    val query = MutableStateFlow("")
    private var job: Job? = null
    private var origin: Pair<Double, Double>? = null

    init { search() }

    @android.annotation.SuppressLint("MissingPermission")
    private suspend fun here(): Pair<Double, Double> {
        origin?.let { return it }
        val loc = if (tracking.refreshPermissions().anyLocation) {
            runCatching { LocationServices.getFusedLocationProviderClient(context).lastLocation.await() }.getOrNull()
        } else null
        // Without a location we can still search by name; distance is then measured from central Jakarta.
        return (loc?.let { it.latitude to it.longitude } ?: (-6.1754 to 106.8272)).also { origin = it }
    }

    val hasLocation: Boolean get() = tracking.permissions.value.anyLocation

    fun onQuery(q: String) {
        query.value = q
        search(debounce = true)
    }

    fun search(debounce: Boolean = false) {
        job?.cancel()
        job = viewModelScope.launch {
            if (debounce) delay(350)
            if ((_state.value as? UiState.Data) == null) _state.value = UiState.Loading
            val (lat, lon) = here()
            _state.value = game.landmarks(lat, lon, query.value).fold({ UiState.Data(it) }, { UiState.Error(it.message ?: "Couldn't search") })
        }
    }
}

@HiltViewModel
class TerritoryViewModel @Inject constructor(private val game: GameRepository, private val me: MeRepository, handle: SavedStateHandle) : ViewModel() {
    private val id: String = checkNotNull(handle["id"])
    private val _state = MutableStateFlow<UiState<TerritoryDetailDto>>(UiState.Loading)
    val state: StateFlow<UiState<TerritoryDetailDto>> = _state.asStateFlow()
    val teamCircleId: StateFlow<String?> = me.me.map { it?.profile?.teamCircleId }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init { load() }

    fun load() = viewModelScope.launch {
        _state.value = UiState.Loading
        _state.value = game.territory(id).fold({ UiState.Data(it) }, { UiState.Error(it.message ?: "Couldn't load the territory") })
    }
}

@HiltViewModel
class ExplorationMapViewModel @Inject constructor(private val game: GameRepository) : ViewModel() {
    private val _tiles = MutableStateFlow<List<TileDto>>(emptyList())
    val tiles: StateFlow<List<TileDto>> = _tiles.asStateFlow()
    private val _stats = MutableStateFlow<StatsDto?>(null)
    val stats: StateFlow<StatsDto?> = _stats.asStateFlow()
    private val _tooLarge = MutableStateFlow(false)
    val tooLarge: StateFlow<Boolean> = _tooLarge.asStateFlow()
    private var job: Job? = null

    init { viewModelScope.launch { _stats.value = game.stats().getOrNull() } }

    fun onViewport(b: TileBounds) {
        job?.cancel()
        job = viewModelScope.launch {
            delay(500)
            game.tiles(b).onSuccess { _tiles.value = it.tiles; _tooLarge.value = it.tooLarge }
        }
    }
}
