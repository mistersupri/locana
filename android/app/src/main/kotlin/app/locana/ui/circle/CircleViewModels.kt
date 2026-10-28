package app.locana.ui.circle

import android.annotation.SuppressLint
import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.locana.data.api.ChallengeDto
import app.locana.data.api.CircleDetailDto
import app.locana.data.api.CircleExplorationDto
import app.locana.data.api.FeedItemDto
import app.locana.data.api.InvitationDto
import app.locana.data.api.PlaceDto
import app.locana.data.api.PlaceRequest
import app.locana.data.location.TrackingController
import app.locana.data.repo.CircleRepository
import app.locana.data.repo.MeRepository
import app.locana.domain.Validation
import app.locana.ui.components.UiState
import app.locana.ui.nav.CircleSelection
import com.google.android.gms.location.LocationServices
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

data class CircleHome(
    val detail: CircleDetailDto,
    val feed: List<FeedItemDto>,
    val nextCursor: String?,
    val challenges: List<ChallengeDto>,
    val exploration: CircleExplorationDto?,
)

@HiltViewModel
class CircleViewModel @Inject constructor(
    private val circles: CircleRepository,
    private val selection: CircleSelection,
    private val me: MeRepository,
) : ViewModel() {
    val list = circles.circles
    val selected = selection.selected
    val myId: String? get() = me.me.value?.id
    private val _state = MutableStateFlow<UiState<CircleHome>>(UiState.Loading)
    val state: StateFlow<UiState<CircleHome>> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            circles.loadCached()
            if (circles.circles.value == null) circles.refresh()
        }
        viewModelScope.launch { selection.selected.filterNotNull().collect { load(it) } }
        viewModelScope.launch { circles.circles.filterNotNull().collect { c -> selection.reconcile(c.value.map { it.id }) } }
    }

    fun select(id: String) = selection.select(id)

    fun load(id: String? = selection.selected.value) {
        if (id == null) return
        viewModelScope.launch {
            val cached = circles.cachedDetail(id)
            _state.value = cached?.let { UiState.Data(CircleHome(it.value, emptyList(), null, emptyList(), null), it.updatedAtMs) } ?: UiState.Loading
            val d = circles.detail(id)
            d.onSuccess { detail ->
                val feed = circles.feed(id, null).getOrNull()
                _state.value = UiState.Data(
                    CircleHome(detail, feed?.items.orEmpty(), feed?.nextCursor, circles.challenges(id).getOrDefault(emptyList()), circles.exploration(id).getOrNull()),
                )
            }.onFailure { e -> if (cached == null) _state.value = UiState.Error(e.message ?: "Couldn't load the circle") }
        }
    }

    fun moreFeed() {
        val cur = (_state.value as? UiState.Data)?.value ?: return
        val cursor = cur.nextCursor ?: return
        viewModelScope.launch {
            circles.feed(cur.detail.id, cursor).onSuccess { p ->
                _state.value = UiState.Data(cur.copy(feed = cur.feed + p.items, nextCursor = p.nextCursor))
            }
        }
    }

    fun react(item: FeedItemDto, emoji: String) {
        val next = if (item.myReaction == emoji) null else emoji
        viewModelScope.launch { circles.react(item.id, next).onSuccess { load() } }
    }
}

@HiltViewModel
class CircleFormViewModel @Inject constructor(
    private val circles: CircleRepository,
    private val selection: CircleSelection,
    handle: SavedStateHandle,
) : ViewModel() {
    val name = MutableStateFlow("")
    val code = MutableStateFlow(handle.get<String>("code").orEmpty())
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun create(done: (String) -> Unit) {
        Validation.circleName(name.value)?.let { _error.value = it; return }
        _busy.value = true
        viewModelScope.launch {
            circles.create(name.value, null)
                .onSuccess { selection.select(it.id); done(it.id) }
                .onFailure { _error.value = it.message }
            _busy.value = false
        }
    }

    fun join(done: () -> Unit) {
        val c = Validation.normalizeInviteCode(code.value) ?: run { _error.value = "Codes look like K7QH-M3XP-2R (10 letters and digits)"; return }
        _busy.value = true
        viewModelScope.launch {
            circles.join(c)
                .onSuccess { selection.select(it.id); done() }
                .onFailure { e ->
                    _error.value = when ((e as? app.locana.data.api.AppError)?.codeOrNull) {
                        "INVALID_INVITATION" -> "That code isn't valid. It may have expired, been used up or revoked."
                        "RATE_LIMITED" -> "Too many tries. Wait a while before entering another code."
                        "CIRCLE_FULL" -> "This circle is full."
                        else -> e.message
                    }
                }
            _busy.value = false
        }
    }
}

@HiltViewModel
class InviteViewModel @Inject constructor(private val circles: CircleRepository, handle: SavedStateHandle) : ViewModel() {
    val circleId: String = checkNotNull(handle["id"])
    private val _state = MutableStateFlow<UiState<List<InvitationDto>>>(UiState.Loading)
    val state: StateFlow<UiState<List<InvitationDto>>> = _state.asStateFlow()
    private val _created = MutableStateFlow<InvitationDto?>(null)
    /** The plain code exists only here, right after creation. */
    val created: StateFlow<InvitationDto?> = _created.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init { load() }

    fun load() = viewModelScope.launch {
        _state.value = circles.invitations(circleId).fold({ UiState.Data(it) }, { UiState.Error(it.message ?: "Couldn't load invitations") })
    }

    fun create(hours: Int, uses: Int) = viewModelScope.launch {
        circles.createInvitation(circleId, hours, uses).onSuccess { _created.value = it; load() }.onFailure { _error.value = it.message }
    }

    fun revoke(id: String) = viewModelScope.launch { circles.revokeInvitation(circleId, id).onSuccess { if (_created.value?.id == id) _created.value = null; load() } }
}

@HiltViewModel
class MembersViewModel @Inject constructor(
    private val circles: CircleRepository,
    private val meRepo: MeRepository,
    handle: SavedStateHandle,
) : ViewModel() {
    val circleId: String = checkNotNull(handle["id"])
    val myId: String? get() = meRepo.me.value?.id
    private val _state = MutableStateFlow<UiState<CircleDetailDto>>(UiState.Loading)
    val state: StateFlow<UiState<CircleDetailDto>> = _state.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init { load() }

    fun load() = viewModelScope.launch {
        _state.value = circles.detail(circleId).fold({ UiState.Data(it) }, { UiState.Error(it.message ?: "Couldn't load members") })
    }

    private fun act(ok: String, block: suspend () -> Result<*>) = viewModelScope.launch {
        block().onSuccess { _message.value = ok; load() }.onFailure { _message.value = it.message }
    }

    fun setRole(userId: String, role: String) = act("Role updated") { circles.setRole(circleId, userId, role) }
    fun remove(userId: String) = act("Removed from the circle") { circles.removeMember(circleId, userId) }
    fun hide(userId: String, hidden: Boolean) = act(if (hidden) "They now see you as hidden" else "They can see you again") { circles.hideFrom(circleId, userId, hidden) }
    fun block(userId: String, blocked: Boolean) = act(if (blocked) "Blocked. Neither of you sees the other's location." else "Unblocked") {
        if (blocked) meRepo.block(userId) else meRepo.unblock(userId)
    }
    fun report(userId: String, reason: String, details: String?) = act("Report sent. Thank you.") { meRepo.report(userId, reason, details, circleId) }
    fun clearMessage() { _message.value = null }
}

@HiltViewModel
class CircleSettingsViewModel @Inject constructor(private val circles: CircleRepository, handle: SavedStateHandle) : ViewModel() {
    val circleId: String = checkNotNull(handle["id"])
    private val _state = MutableStateFlow<UiState<CircleDetailDto>>(UiState.Loading)
    val state: StateFlow<UiState<CircleDetailDto>> = _state.asStateFlow()
    val name = MutableStateFlow("")
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        viewModelScope.launch {
            _state.value = circles.detail(circleId).fold({ name.value = it.name; UiState.Data(it) }, { UiState.Error(it.message ?: "Couldn't load") })
        }
    }

    fun rename() = viewModelScope.launch {
        Validation.circleName(name.value)?.let { _error.value = it; return@launch }
        circles.update(circleId, name.value, null).onSuccess { _state.value = UiState.Data(it); _error.value = null }.onFailure { _error.value = it.message }
    }

    fun mute(m: Boolean) = viewModelScope.launch { circles.mute(circleId, m).onSuccess { circles.detail(circleId).onSuccess { _state.value = UiState.Data(it) } } }
    fun leave(done: () -> Unit) = viewModelScope.launch { circles.leave(circleId).onSuccess { done() }.onFailure { _error.value = it.message } }
    fun delete(done: () -> Unit) = viewModelScope.launch { circles.delete(circleId).onSuccess { done() }.onFailure { _error.value = it.message } }
}

@HiltViewModel
class PlacesViewModel @Inject constructor(private val circles: CircleRepository, handle: SavedStateHandle) : ViewModel() {
    val circleId: String = checkNotNull(handle["id"])
    private val _state = MutableStateFlow<UiState<List<PlaceDto>>>(UiState.Loading)
    val state: StateFlow<UiState<List<PlaceDto>>> = _state.asStateFlow()
    init { load() }
    fun load() = viewModelScope.launch {
        _state.value = circles.places(circleId).fold({ UiState.Data(it) }, { UiState.Error(it.message ?: "Couldn't load places") })
    }
}

data class PlaceForm(
    val name: String = "",
    val type: String = "HOME",
    val lat: Double? = null,
    val lon: Double? = null,
    val radiusM: Int = 150,
    val notifyArrive: Boolean = true,
    val notifyLeave: Boolean = true,
    val busy: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class PlaceEditViewModel @Inject constructor(
    private val circles: CircleRepository,
    private val app: Application,
    private val tracking: TrackingController,
    handle: SavedStateHandle,
) : ViewModel() {
    val circleId: String = checkNotNull(handle["id"])
    val placeId: String? = handle.get<String>("placeId")?.takeIf { it.isNotBlank() }
    private val _form = MutableStateFlow(PlaceForm())
    val form: StateFlow<PlaceForm> = _form.asStateFlow()

    init {
        viewModelScope.launch {
            if (placeId != null) {
                circles.places(circleId).getOrNull()?.firstOrNull { it.id == placeId }?.let { p ->
                    _form.value = PlaceForm(p.name, p.type, p.latitude, p.longitude, p.radiusM, p.notifyArrive, p.notifyLeave)
                }
            } else useMyLocation()
        }
    }

    fun edit(f: (PlaceForm) -> PlaceForm) = _form.update { f(it).copy(error = null) }

    @SuppressLint("MissingPermission")
    fun useMyLocation() = viewModelScope.launch {
        if (!tracking.refreshPermissions().anyLocation) return@launch
        runCatching { LocationServices.getFusedLocationProviderClient(app).lastLocation.await() }.getOrNull()?.let { l ->
            _form.update { it.copy(lat = l.latitude, lon = l.longitude) }
        }
    }

    fun save(done: () -> Unit) {
        val f = _form.value
        if (f.name.isBlank()) { _form.update { it.copy(error = "Give the place a name") }; return }
        if (f.lat == null || f.lon == null) { _form.update { it.copy(error = "Move the map to the place first") }; return }
        val req = PlaceRequest(f.name.trim(), f.type, f.lat, f.lon, f.radiusM, f.notifyArrive, f.notifyLeave, emptyList())
        _form.update { it.copy(busy = true) }
        viewModelScope.launch {
            val r = if (placeId == null) circles.createPlace(circleId, req) else circles.updatePlace(placeId, req)
            _form.update { it.copy(busy = false, error = r.exceptionOrNull()?.message) }
            if (r.isSuccess) done()
        }
    }

    fun delete(done: () -> Unit) {
        val id = placeId ?: return
        viewModelScope.launch { circles.deletePlace(id).onSuccess { done() }.onFailure { e -> _form.update { it.copy(error = e.message) } } }
    }
}

@HiltViewModel
class ChallengeViewModel @Inject constructor(private val circles: CircleRepository, handle: SavedStateHandle) : ViewModel() {
    val circleId: String = checkNotNull(handle["id"])
    val title = MutableStateFlow("")
    val metric = MutableStateFlow("TILES")
    val target = MutableStateFlow(30)
    val days = MutableStateFlow(7)
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun create(done: () -> Unit) = viewModelScope.launch {
        if (title.value.trim().length < 3) { _error.value = "Give the challenge a short name"; return@launch }
        circles.createChallenge(circleId, title.value, metric.value, target.value, days.value).onSuccess { done() }.onFailure { _error.value = it.message }
    }
}
