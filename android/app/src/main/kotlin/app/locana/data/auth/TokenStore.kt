package app.locana.data.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.locana.data.api.TokenPair
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

private val Context.tokenStore: DataStore<Preferences> by preferencesDataStore("tokens")

data class StoredTokens(val userId: String, val access: String, val refresh: String)

/**
 * Tokens live in memory for requests and in DataStore (refresh token encrypted with Keystore)
 * to survive restarts. Reads on the OkHttp thread use the in-memory copy.
 */
@Singleton
class TokenStore @Inject constructor(@ApplicationContext private val context: Context, private val cipher: TokenCipher) {
    private val kUser = stringPreferencesKey("user_id")
    private val kAccess = stringPreferencesKey("access")
    private val kRefresh = stringPreferencesKey("refresh_enc")

    @Volatile private var cached: StoredTokens? = null
    @Volatile private var loaded = false

    fun current(): StoredTokens? {
        if (!loaded) runBlocking { load() }
        return cached
    }

    suspend fun load(): StoredTokens? {
        if (loaded) return cached
        val p = context.tokenStore.data.first()
        val user = p[kUser]
        val refresh = p[kRefresh]?.let { cipher.decrypt(it) }
        cached = if (user != null && refresh != null) StoredTokens(user, p[kAccess].orEmpty(), refresh) else null
        loaded = true
        return cached
    }

    suspend fun save(userId: String, t: TokenPair) {
        cached = StoredTokens(userId, t.accessToken, t.refreshToken)
        loaded = true
        context.tokenStore.edit {
            it[kUser] = userId
            it[kAccess] = t.accessToken
            it[kRefresh] = cipher.encrypt(t.refreshToken)
        }
    }

    suspend fun clear() {
        cached = null
        loaded = true
        context.tokenStore.edit { it.clear() }
    }
}
