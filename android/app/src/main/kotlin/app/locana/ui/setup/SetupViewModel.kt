package app.locana.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.locana.data.api.UpdateProfileRequest
import app.locana.data.location.TrackingController
import app.locana.data.prefs.SettingsStore
import app.locana.data.repo.MeRepository
import app.locana.domain.Validation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SetupState(val displayName: String = "", val avatarKey: String = "explorer_teal", val busy: Boolean = false, val error: String? = null)

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val me: MeRepository,
    private val settings: SettingsStore,
    val tracking: TrackingController,
) : ViewModel() {
    private val _state = MutableStateFlow(SetupState())
    val state: StateFlow<SetupState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            (me.me.value ?: me.loadCached())?.let { m -> _state.update { it.copy(displayName = m.profile.displayName, avatarKey = m.profile.avatarKey) } }
        }
    }

    fun edit(f: (SetupState) -> SetupState) = _state.update { f(it).copy(error = null) }

    fun saveProfile(next: () -> Unit) {
        val s = _state.value
        Validation.displayName(s.displayName)?.let { e -> _state.update { it.copy(error = e) }; return }
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val r = me.update(UpdateProfileRequest(displayName = s.displayName.trim(), avatarKey = s.avatarKey))
            _state.update { it.copy(busy = false, error = r.exceptionOrNull()?.message) }
            if (r.isSuccess) next()
        }
    }

    fun backgroundExplained() = viewModelScope.launch { settings.setBackgroundExplained() }

    /** Ends onboarding; the root then shows the main app. */
    fun finish() {
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val r = me.update(UpdateProfileRequest(onboardingDone = true))
            _state.update { it.copy(busy = false, error = r.exceptionOrNull()?.message) }
        }
    }
}
