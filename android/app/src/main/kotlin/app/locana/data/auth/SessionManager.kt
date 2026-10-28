package app.locana.data.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SessionState {
    data object Loading : SessionState
    data object SignedOut : SessionState
    data class SignedIn(val userId: String) : SessionState
}

/** Single source of truth for "who is signed in". Network code reports expiry here. */
@Singleton
class SessionManager @Inject constructor(private val tokens: TokenStore) {
    private val _state = MutableStateFlow<SessionState>(SessionState.Loading)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    /** Set when the server ended the session (refresh failed), so the UI can say why. */
    var expiredReason: String? = null
        private set

    suspend fun restore() {
        val t = tokens.load()
        _state.value = if (t != null) SessionState.SignedIn(t.userId) else SessionState.SignedOut
    }

    fun signedIn(userId: String) {
        expiredReason = null
        _state.value = SessionState.SignedIn(userId)
    }

    suspend fun signOut(reason: String? = null) {
        tokens.clear()
        expiredReason = reason
        _state.value = SessionState.SignedOut
    }

    val userId: String? get() = (state.value as? SessionState.SignedIn)?.userId
}
