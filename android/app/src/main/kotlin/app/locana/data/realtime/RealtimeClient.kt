package app.locana.data.realtime

import android.util.Log
import app.locana.data.api.ErrorJson
import app.locana.data.auth.TokenRefresher
import app.locana.data.auth.TokenStore
import app.locana.domain.Backoff
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED }

data class RealtimeEvent(val type: String, val data: JsonObject)

/**
 * One WebSocket to /ws while the app is in use. Reconnects with exponential backoff and
 * jitter, re-authenticates after token refresh and re-subscribes to circles after every
 * reconnect. It does not try to stay alive in the background: Android will kill it, and
 * the tracking service uploads over REST instead.
 */
class RealtimeClient(
    private val client: OkHttpClient,
    private val wsUrl: String,
    private val tokens: TokenStore,
    private val refresher: TokenRefresher,
    private val scope: CoroutineScope,
    private val backoff: Backoff = Backoff(baseMs = 1_000, maxMs = 60_000),
) {
    private val _state = MutableStateFlow(ConnectionState.DISCONNECTED)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<RealtimeEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<RealtimeEvent> = _events.asSharedFlow()

    private val circles = mutableSetOf<String>()
    private var socket: WebSocket? = null
    private var wanted = false
    private var reconnectJob: Job? = null

    init {
        refresher.onRefreshed = { token -> socket?.send(msg("auth") { put("token", token) }) }
    }

    @Synchronized
    fun start() {
        if (wanted) return
        wanted = true
        connect()
    }

    @Synchronized
    fun stop() {
        wanted = false
        reconnectJob?.cancel()
        socket?.close(1000, "app in background")
        socket = null
        _state.value = ConnectionState.DISCONNECTED
    }

    @Synchronized
    fun subscribe(circleIds: Collection<String>) {
        val added = circleIds.filter { circles.add(it) }
        val removed = circles.filter { it !in circleIds }
        removed.forEach { circles.remove(it); socket?.send(msg("unsubscribe") { put("circleId", it) }) }
        if (_state.value == ConnectionState.CONNECTED) added.forEach { socket?.send(msg("subscribe") { put("circleId", it) }) }
    }

    private fun msg(type: String, body: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit = {}) =
        buildJsonObject { put("type", type); body() }.toString()

    @Synchronized
    private fun connect() {
        if (!wanted) return
        val token = tokens.current()?.access ?: return
        _state.value = ConnectionState.CONNECTING
        val req = Request.Builder().url(wsUrl).header("Authorization", "Bearer $token").build()
        socket = client.newWebSocket(req, Listener())
    }

    private fun scheduleReconnect(refreshFirst: Boolean) {
        synchronized(this) {
            socket = null
            _state.value = ConnectionState.DISCONNECTED
            if (!wanted) return
        }
        val wait = backoff.nextDelayMs()
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            if (refreshFirst) refresher.refreshIfNeeded(tokens.current()?.access)
            delay(wait)
            connect()
        }
    }

    private inner class Listener : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) {
            val obj = runCatching { ErrorJson.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
            val type = obj["type"]?.jsonPrimitive?.content ?: return
            val data = (obj["data"] as? JsonElement)?.let { runCatching { it.jsonObject }.getOrNull() } ?: JsonObject(emptyMap())
            if (type == "ready") {
                backoff.reset()
                _state.value = ConnectionState.CONNECTED
                synchronized(this@RealtimeClient) { circles.forEach { webSocket.send(msg("subscribe") { put("circleId", it) }) } }
            }
            _events.tryEmit(RealtimeEvent(type, data))
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(code, reason)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (socket === webSocket || socket == null) scheduleReconnect(refreshFirst = code == 4401)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.d("Realtime", "socket failure: ${t.javaClass.simpleName}")
            if (socket === webSocket || socket == null) scheduleReconnect(refreshFirst = response?.code == 401)
        }
    }
}
