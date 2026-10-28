package app.locana.data.repo

import android.os.Build
import app.locana.data.api.AuthResponse
import app.locana.data.api.ChangePasswordRequest
import app.locana.data.api.ForgotRequest
import app.locana.data.api.GoogleRequest
import app.locana.data.api.LocanaApi
import app.locana.data.api.LoginRequest
import app.locana.data.api.MeDto
import app.locana.data.api.RegisterRequest
import app.locana.data.api.ResetRequest
import app.locana.data.api.SessionDto
import app.locana.data.api.apiCall
import app.locana.data.api.apiUnit
import app.locana.data.auth.SessionManager
import app.locana.data.auth.TokenStore
import app.locana.data.db.LocanaDatabase
import app.locana.data.location.TrackingController
import app.locana.data.prefs.SettingsStore
import androidx.room.withTransaction
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val api: LocanaApi,
    private val tokens: TokenStore,
    private val session: SessionManager,
    private val db: LocanaDatabase,
    private val settings: SettingsStore,
    private val cache: CacheStore,
    private val tracking: TrackingController,
    private val me: MeRepository,
    private val circles: CircleRepository,
) {
    private val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim().take(80)

    private suspend fun accept(r: AuthResponse): MeDto {
        tokens.save(r.user.id, r.tokens)
        cache.write(MeRepository.KEY, MeDto.serializer(), r.user)
        session.signedIn(r.user.id)
        return r.user
    }

    suspend fun register(email: String, password: String, username: String, displayName: String): Result<MeDto> =
        apiCall { accept(api.register(RegisterRequest(email.trim(), password, username.trim().lowercase(), displayName.trim(), deviceName))) }

    suspend fun login(identifier: String, password: String): Result<MeDto> =
        apiCall { accept(api.login(LoginRequest(identifier.trim().lowercase(), password, deviceName))) }

    suspend fun google(idToken: String): Result<MeDto> = apiCall { accept(api.google(GoogleRequest(idToken, deviceName))) }

    suspend fun forgot(email: String) = apiUnit { api.forgotPassword(ForgotRequest(email.trim().lowercase())) }

    suspend fun reset(email: String, code: String, newPassword: String) =
        apiUnit { api.resetPassword(ResetRequest(email.trim().lowercase(), code.trim(), newPassword)) }

    suspend fun changePassword(current: String, next: String) = apiUnit { api.changePassword(ChangePasswordRequest(current, next)) }

    suspend fun sessions(): Result<List<SessionDto>> = apiCall { api.sessions() }
    suspend fun revoke(id: String) = apiUnit { api.revokeSession(id) }
    suspend fun logoutOthers() = apiUnit { api.logoutOthers() }

    /** Signs out locally even when the server cannot be reached; the session then expires on its own. */
    suspend fun logout() {
        apiUnit { api.logout() }
        wipeLocal()
    }

    suspend fun wipeLocal(reason: String? = null) {
        tracking.stop()
        db.withTransaction {
            db.pending().clear()
            db.members().clear()
        }
        cache.clear()
        me.clear()
        circles.clear()
        settings.clearAccountState()
        session.signOut(reason)
    }
}
