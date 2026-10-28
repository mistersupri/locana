package app.locana.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.locana.data.auth.SessionManager
import app.locana.data.auth.SessionState
import app.locana.data.prefs.SettingsStore
import app.locana.data.prefs.ThemeMode
import app.locana.data.repo.MeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class RootState(
    val session: SessionState = SessionState.Loading,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val onboardingSeen: Boolean = true,
    val needsProfileSetup: Boolean = false,
)

@HiltViewModel
class RootViewModel @Inject constructor(
    private val session: SessionManager,
    settings: SettingsStore,
    me: MeRepository,
) : ViewModel() {
    val state: StateFlow<RootState> = combine(session.state, settings.settings, me.me) { s, st, m ->
        RootState(
            session = s,
            theme = st.theme,
            onboardingSeen = st.onboardingSeen,
            needsProfileSetup = s is SessionState.SignedIn && m != null && !m.profile.onboardingDone,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, RootState())

    val expiredReason: String? get() = session.expiredReason
}
