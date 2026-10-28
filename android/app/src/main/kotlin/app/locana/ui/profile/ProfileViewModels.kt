package app.locana.ui.profile

import android.app.Application
import android.content.Intent
import android.os.PowerManager
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.locana.data.api.BlockDto
import app.locana.data.api.MeDto
import app.locana.data.api.NotificationDto
import app.locana.data.api.PrivacyPatch
import app.locana.data.api.SessionDto
import app.locana.data.api.SharingStateDto
import app.locana.data.api.UpdateProfileRequest
import app.locana.data.location.TrackingController
import app.locana.data.prefs.SettingsStore
import app.locana.data.prefs.ThemeMode
import app.locana.data.repo.AuthRepository
import app.locana.data.repo.CircleRepository
import app.locana.data.repo.LocationRepository
import app.locana.data.repo.MeRepository
import app.locana.data.repo.NotificationRepository
import app.locana.domain.Validation
import app.locana.ui.components.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val me: MeRepository,
    private val auth: AuthRepository,
    private val settings: SettingsStore,
    notifications: NotificationRepository,
) : ViewModel() {
    val profile: StateFlow<MeDto?> = me.me
    val unread = notifications.unread
    val theme: StateFlow<ThemeMode> = settings.settings.map { it.theme }.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)

    init { viewModelScope.launch { me.loadCached(); me.refresh(); notifications.page(null) } }

    fun setTheme(t: ThemeMode) = viewModelScope.launch { settings.setTheme(t) }
    fun signOut() = viewModelScope.launch { auth.logout() }
}

@HiltViewModel
class ProfileEditViewModel @Inject constructor(private val me: MeRepository, circles: CircleRepository) : ViewModel() {
    val circles = circles.circles
    val name = MutableStateFlow(me.me.value?.profile?.displayName.orEmpty())
    val username = MutableStateFlow(me.me.value?.username.orEmpty())
    val bio = MutableStateFlow(me.me.value?.profile?.bio.orEmpty())
    val avatarKey = MutableStateFlow(me.me.value?.profile?.avatarKey ?: "explorer_teal")
    val team = MutableStateFlow(me.me.value?.profile?.teamCircleId)
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    fun save(done: () -> Unit) {
        Validation.displayName(name.value)?.let { _error.value = it; return }
        Validation.username(username.value)?.let { _error.value = it; return }
        _busy.value = true
        viewModelScope.launch {
            me.update(UpdateProfileRequest(displayName = name.value.trim(), username = username.value.trim().lowercase(), bio = bio.value.trim(), avatarKey = avatarKey.value, teamCircleId = team.value))
                .onSuccess { _error.value = null; done() }
                .onFailure { e -> _error.value = if ((e as? app.locana.data.api.AppError)?.codeOrNull == "USERNAME_TAKEN") "That username is taken" else e.message }
            _busy.value = false
        }
    }
}

@HiltViewModel
class SharingViewModel @Inject constructor(
    private val app: Application,
    private val location: LocationRepository,
    circles: CircleRepository,
    val tracking: TrackingController,
) : ViewModel() {
    val state: StateFlow<SharingStateDto?> = location.sharing
    val circles = circles.circles
    val pending = location.pendingCount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init { refresh() }

    fun refresh() = viewModelScope.launch { location.refreshSharing().onFailure { _error.value = it.message } }

    private fun apply(block: suspend () -> Result<SharingStateDto>) = viewModelScope.launch {
        block().onSuccess { _error.value = null; tracking.ensureRunning(it.collectLocation) }.onFailure { _error.value = it.message }
    }

    fun start(circleId: String, minutes: Int?) = apply { location.start(circleId, minutes) }
    fun pause(id: String) = apply { location.pause(id) }
    fun resume(id: String) = apply { location.resume(id) }
    fun stop(id: String) = apply { location.stop(id) }
    fun pauseAll(p: Boolean) = apply { location.pauseAll(p) }
    fun stopAll() = apply { location.stopAll() }

    /** Battery optimization makes background sharing less reliable on many devices. */
    fun ignoringBatteryOptimizations(): Boolean =
        app.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(app.packageName)
}

@HiltViewModel
class PrivacyViewModel @Inject constructor(private val me: MeRepository, private val location: LocationRepository, private val tracking: TrackingController) : ViewModel() {
    val profile: StateFlow<MeDto?> = me.me
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun patch(p: PrivacyPatch) = viewModelScope.launch {
        me.privacy(p).onSuccess {
            if (p.explorationEnabled != null) {
                location.setExplorationLocally(p.explorationEnabled)
                location.refreshSharing().onSuccess { s -> tracking.ensureRunning(s.collectLocation) }
            }
            _message.value = null
        }.onFailure { _message.value = it.message }
    }

    fun deleteHistory() = viewModelScope.launch {
        _message.value = me.deleteHistory().fold({ "Deleted $it saved positions" }, { it.message })
    }
}

@HiltViewModel
class NotificationsViewModel @Inject constructor(private val repo: NotificationRepository) : ViewModel() {
    private val _state = MutableStateFlow<UiState<List<NotificationDto>>>(UiState.Loading)
    val state: StateFlow<UiState<List<NotificationDto>>> = _state.asStateFlow()
    private var cursor: String? = null
    var hasMore = false
        private set

    init { load() }

    fun load() = viewModelScope.launch {
        _state.value = UiState.Loading
        repo.page(null).onSuccess { p -> cursor = p.nextCursor; hasMore = p.nextCursor != null; _state.value = UiState.Data(p.items) }
            .onFailure { _state.value = UiState.Error(it.message ?: "Couldn't load notifications") }
    }

    fun more() = viewModelScope.launch {
        val cur = (_state.value as? UiState.Data)?.value ?: return@launch
        repo.page(cursor).onSuccess { p -> cursor = p.nextCursor; hasMore = p.nextCursor != null; _state.value = UiState.Data(cur + p.items) }
    }

    fun markAll() = viewModelScope.launch {
        repo.markAllRead().onSuccess {
            (_state.value as? UiState.Data)?.let { d -> _state.value = UiState.Data(d.value.map { it.copy(read = true) }) }
        }
    }
}

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val app: Application,
    private val auth: AuthRepository,
    private val me: MeRepository,
) : ViewModel() {
    val profile: StateFlow<MeDto?> = me.me
    private val _sessions = MutableStateFlow<UiState<List<SessionDto>>>(UiState.Loading)
    val sessions: StateFlow<UiState<List<SessionDto>>> = _sessions.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    init { loadSessions() }

    fun loadSessions() = viewModelScope.launch {
        _sessions.value = auth.sessions().fold({ UiState.Data(it) }, { UiState.Error(it.message ?: "Couldn't load devices") })
    }

    fun revoke(id: String) = viewModelScope.launch { auth.revoke(id).onSuccess { loadSessions() } }
    fun signOutOthers() = viewModelScope.launch { auth.logoutOthers().onSuccess { _message.value = "Other devices were signed out"; loadSessions() } }

    fun changePassword(current: String, next: String, done: () -> Unit) {
        Validation.password(next)?.let { _message.value = it; return }
        viewModelScope.launch {
            auth.changePassword(current, next).onSuccess { _message.value = "Password changed. Other devices were signed out."; done() }
                .onFailure { _message.value = it.message }
        }
    }

    /** Writes the export to app cache and returns a share intent through FileProvider. */
    fun export(onReady: (Intent) -> Unit) {
        _busy.value = true
        viewModelScope.launch {
            me.export().onSuccess { json ->
                val dir = File(app.cacheDir, "exports").apply { mkdirs() }
                val f = File(dir, "locana-export.json").apply { writeText(json) }
                val uri = FileProvider.getUriForFile(app, "${app.packageName}.files", f)
                onReady(
                    Intent.createChooser(
                        Intent(Intent.ACTION_SEND).setType("application/json").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                        "Save your Locana data",
                    ),
                )
            }.onFailure { _message.value = it.message }
            _busy.value = false
        }
    }

    fun deleteAccount(password: String?) {
        _busy.value = true
        viewModelScope.launch {
            me.deleteAccount(password)
                .onSuccess { auth.wipeLocal("Your account was deleted.") }
                .onFailure { e -> _message.value = if ((e as? app.locana.data.api.AppError)?.codeOrNull == "WRONG_PASSWORD") "That password is not right" else e.message }
            _busy.value = false
        }
    }
}

@HiltViewModel
class BlockedViewModel @Inject constructor(private val me: MeRepository) : ViewModel() {
    private val _state = MutableStateFlow<UiState<List<BlockDto>>>(UiState.Loading)
    val state: StateFlow<UiState<List<BlockDto>>> = _state.asStateFlow()
    init { load() }
    fun load() = viewModelScope.launch { _state.value = me.blocks().fold({ UiState.Data(it) }, { UiState.Error(it.message ?: "Couldn't load") }) }
    fun unblock(id: String) = viewModelScope.launch { me.unblock(id).onSuccess { load() } }
}
