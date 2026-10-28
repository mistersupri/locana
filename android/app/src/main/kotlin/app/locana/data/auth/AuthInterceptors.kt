package app.locana.data.auth

import app.locana.data.api.ErrorJson
import app.locana.data.api.RefreshRequest
import app.locana.data.api.TokenPair
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/** Adds the current access token to every request except the auth endpoints. */
class AuthInterceptor @Inject constructor(private val tokens: TokenStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request()
        val path = req.url.encodedPath
        if (path.startsWith("/v1/auth/") && !path.contains("logout") && !path.contains("sessions") && !path.endsWith("/password/change")) {
            return chain.proceed(req)
        }
        val access = tokens.current()?.access
        return chain.proceed(if (access.isNullOrEmpty()) req else req.newBuilder().header("Authorization", "Bearer $access").build())
    }
}

/**
 * On 401, refreshes once (serialized across threads) and retries. If the refresh itself is
 * rejected, the session is over: tokens are cleared and the app returns to sign-in.
 */
@Singleton
class TokenRefresher @Inject constructor(
    private val tokens: TokenStore,
    private val session: SessionManager,
    @Named("bare") private val bare: OkHttpClient,
    @Named("baseUrl") private val baseUrl: String,
) : Authenticator {
    private val lock = Any()
    private val json = "application/json".toMediaType()

    /** Listeners (the WebSocket) re-authenticate after a refresh. */
    var onRefreshed: ((String) -> Unit)? = null

    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.request.header("X-Retry-After-Refresh") != null) return null
        val sent = response.request.header("Authorization")?.removePrefix("Bearer ")
        val newAccess = refreshIfNeeded(sent) ?: return null
        return response.request.newBuilder()
            .header("Authorization", "Bearer $newAccess")
            .header("X-Retry-After-Refresh", "1")
            .build()
    }

    /** Returns a usable access token, refreshing only if [stale] is still the current one. */
    fun refreshIfNeeded(stale: String?): String? = synchronized(lock) {
        val current = tokens.current() ?: return null
        if (stale != null && current.access != stale && current.access.isNotEmpty()) return current.access
        val body = ErrorJson.encodeToString(RefreshRequest.serializer(), RefreshRequest(current.refresh)).toRequestBody(json)
        val req = Request.Builder().url("${baseUrl}v1/auth/refresh").post(body).build()
        val result = runCatching { bare.newCall(req).execute() }.getOrNull() ?: return null // offline: keep the session
        result.use { r ->
            if (r.isSuccessful) {
                val pair = ErrorJson.decodeFromString(TokenPair.serializer(), r.body!!.string())
                runBlocking { tokens.save(current.userId, pair) }
                onRefreshed?.invoke(pair.accessToken)
                return pair.accessToken
            }
            if (r.code == 401) runBlocking { session.signOut("Your session ended. Sign in again to continue.") }
            return null
        }
    }
}
