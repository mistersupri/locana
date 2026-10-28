package app.locana.ui.play

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.locana.data.api.AchievementDto
import app.locana.data.api.CollectibleDto
import app.locana.data.api.LeaderboardDto
import app.locana.data.api.LevelDto
import app.locana.data.api.MeDto
import app.locana.data.api.StatsDto
import app.locana.data.repo.CircleRepository
import app.locana.data.repo.GameRepository
import app.locana.data.repo.MeRepository
import app.locana.ui.components.UiState
import app.locana.ui.nav.CircleSelection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayViewModel @Inject constructor(private val me: MeRepository, private val game: GameRepository) : ViewModel() {
    val profile: StateFlow<MeDto?> = me.me
    private val _stats = MutableStateFlow<StatsDto?>(null)
    val stats: StateFlow<StatsDto?> = _stats.asStateFlow()
    private val _recent = MutableStateFlow<List<AchievementDto>>(emptyList())
    val recent: StateFlow<List<AchievementDto>> = _recent.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        me.loadCached()
        me.refresh().onFailure { _error.value = it.message }.onSuccess { _error.value = null }
        _stats.value = game.stats().getOrNull()
        _recent.value = game.achievements().getOrDefault(emptyList()).filter { it.unlockedAt != null }.sortedByDescending { it.unlockedAt }.take(3)
    }
}

@HiltViewModel
class AchievementsViewModel @Inject constructor(private val game: GameRepository) : ViewModel() {
    private val _state = MutableStateFlow<UiState<List<AchievementDto>>>(UiState.Loading)
    val state: StateFlow<UiState<List<AchievementDto>>> = _state.asStateFlow()
    init { load() }
    fun load() = viewModelScope.launch {
        _state.value = UiState.Loading
        _state.value = game.achievements().fold({ UiState.Data(it) }, { UiState.Error(it.message ?: "Couldn't load achievements") })
    }
}

@HiltViewModel
class CollectiblesViewModel @Inject constructor(private val game: GameRepository, private val me: MeRepository) : ViewModel() {
    private val _state = MutableStateFlow<UiState<List<CollectibleDto>>>(UiState.Loading)
    val state: StateFlow<UiState<List<CollectibleDto>>> = _state.asStateFlow()
    val profile: StateFlow<MeDto?> = me.me
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init { load() }

    fun load() = viewModelScope.launch {
        _state.value = UiState.Loading
        _state.value = game.collectibles().fold({ UiState.Data(it) }, { UiState.Error(it.message ?: "Couldn't load collectibles") })
    }

    /** Equip or unequip a cosmetic. The server checks ownership. */
    fun toggle(c: CollectibleDto) {
        val p = me.me.value?.profile ?: return
        var frame = p.avatarFrameKey
        var title = p.titleKey
        var marker = p.markerKey
        when (c.kind) {
            "AVATAR_FRAME" -> frame = if (frame == c.code) null else c.code
            "TITLE" -> title = if (title == c.code) null else c.code
            "MAP_MARKER" -> marker = if (marker == c.code) null else c.code
            else -> return
        }
        viewModelScope.launch { me.cosmetics(frame, title, marker).onFailure { _error.value = it.message } }
    }
}

data class BoardQuery(val scope: String = "friends", val metric: String = "xp", val period: String = "week", val circleId: String? = null)

@HiltViewModel
class LeaderboardViewModel @Inject constructor(
    private val game: GameRepository,
    circles: CircleRepository,
    selection: CircleSelection,
    handle: SavedStateHandle,
) : ViewModel() {
    val circleList = circles.circles
    private val _query = MutableStateFlow(BoardQuery(scope = handle.get<String>("scope") ?: "friends", circleId = selection.selected.value))
    val query: StateFlow<BoardQuery> = _query.asStateFlow()
    private val _state = MutableStateFlow<UiState<LeaderboardDto>>(UiState.Loading)
    val state: StateFlow<UiState<LeaderboardDto>> = _state.asStateFlow()
    private val _loadingMore = MutableStateFlow(false)
    val loadingMore: StateFlow<Boolean> = _loadingMore.asStateFlow()

    init { load() }

    fun set(f: (BoardQuery) -> BoardQuery) {
        _query.update(f)
        load()
    }

    fun load() = viewModelScope.launch {
        val q = _query.value
        _state.value = UiState.Loading
        _state.value = game.leaderboard(q.scope, q.metric, q.period, q.circleId.takeIf { q.scope == "circle" })
            .fold({ UiState.Data(it) }, { UiState.Error(it.message ?: "Couldn't load the leaderboard") })
    }

    /** Pagination for long boards. */
    fun more() {
        val cur = (_state.value as? UiState.Data)?.value ?: return
        if (cur.entries.size >= cur.total || _loadingMore.value) return
        val q = _query.value
        _loadingMore.value = true
        viewModelScope.launch {
            game.leaderboard(q.scope, q.metric, q.period, q.circleId.takeIf { q.scope == "circle" }, offset = cur.entries.size)
                .onSuccess { next -> _state.value = UiState.Data(cur.copy(entries = cur.entries + next.entries)) }
            _loadingMore.value = false
        }
    }
}

@HiltViewModel
class LevelsViewModel @Inject constructor(private val game: GameRepository, me: MeRepository) : ViewModel() {
    val profile: StateFlow<MeDto?> = me.me
    private val _state = MutableStateFlow<UiState<List<LevelDto>>>(UiState.Loading)
    val state: StateFlow<UiState<List<LevelDto>>> = _state.asStateFlow()
    init { load() }
    fun load() = viewModelScope.launch {
        _state.value = UiState.Loading
        _state.value = game.levels().fold({ UiState.Data(it.take(30)) }, { UiState.Error(it.message ?: "Couldn't load levels") })
    }
}
