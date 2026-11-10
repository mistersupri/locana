package app.locana

import android.content.Context
import android.os.Build
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import app.locana.data.auth.SessionManager
import app.locana.data.auth.SessionState
import app.locana.data.location.NotificationSyncWorker
import app.locana.data.location.TrackingController
import app.locana.data.realtime.RealtimeClient
import app.locana.data.repo.CircleRepository
import app.locana.data.repo.LocationRepository
import app.locana.data.repo.MeRepository
import app.locana.data.repo.NotificationRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Glue that runs while someone is signed in: realtime connection in the foreground, tracking
 * service following the server's sharing state, notification sync in the background.
 */
@Singleton
class AppSession @Inject constructor(
    @ApplicationContext private val context: Context,
    private val session: SessionManager,
    private val realtime: RealtimeClient,
    private val location: LocationRepository,
    private val circles: CircleRepository,
    private val me: MeRepository,
    private val notifications: NotificationRepository,
    private val tracking: TrackingController,
    private val scope: CoroutineScope,
    private val notifier: app.locana.data.system.Notifier,
    private val settings: app.locana.data.prefs.SettingsStore,
    private val signals: app.locana.data.system.DeviceSignals,
    private val incoming: app.locana.data.safety.IncomingMessages,
    private val battle: app.locana.data.repo.BattleRepository,
) : DefaultLifecycleObserver {
    private var started = false

    fun start() {
        if (started) return
        started = true
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        scope.launch {
            session.state.collectLatest { s ->
                if (s is SessionState.SignedIn) {
                    NotificationSyncWorker.schedule(context)
                    me.loadCached()
                    me.refresh()
                    battle.refresh()
                    me.registerDevice("${Build.MANUFACTURER} ${Build.MODEL}", BuildConfig.VERSION_NAME)
                } else {
                    realtime.stop()
                    NotificationSyncWorker.cancel(context)
                }
            }
        }
        scope.launch {
            tracking.running.collect { running ->
                if (session.userId == null) return@collect
                if (running) realtime.start() else if (!signals.appInForeground()) realtime.stop()
            }
        }
        scope.launch {
            circles.circles.map { c -> c?.value?.map { it.id }.orEmpty() }.distinctUntilChanged().collectLatest { realtime.subscribe(it) }
        }
        scope.launch {
            realtime.events.collect { e ->
                location.applyRealtime(e)
                // Events sent while disconnected are not replayed: re-read positions after every (re)connect.
                if (e.type == "ready") circles.circles.value?.value?.forEach { location.refreshLocations(it.id) }
                if (e.type == "notification") {
                    notifications.bump()
                    onNotification(e.data)
                }
                if (e.type == "level.up" || e.type == "achievement.unlocked") me.refresh()
            }
        }
    }

    /**
     * A notification arrived over the live connection. Quick messages play in the app when it is open; everything
     * else (and quick messages while the app is in the background) becomes a system notification right away,
     * so SOS, long trips and place alerts do not wait for the 15-minute background sync.
     */
    private fun onNotification(d: kotlinx.serialization.json.JsonObject) {
        fun str(o: kotlinx.serialization.json.JsonObject, k: String) = (o[k] as? kotlinx.serialization.json.JsonPrimitive)?.content
        val kind = str(d, "kind") ?: return
        val id = str(d, "id") ?: return
        val title = str(d, "title").orEmpty()
        val body = str(d, "body").orEmpty()
        val extra = d["data"] as? kotlinx.serialization.json.JsonObject
        val foreground = signals.appInForeground()
        if (kind == "MONSTER") scope.launch { battle.refresh() }
        if (kind == "QUICK_MESSAGE" && foreground && extra != null) {
            incoming.publish(
                app.locana.data.safety.IncomingQuickMessage(
                    fromName = str(extra, "fromName") ?: "Someone", text = str(extra, "text") ?: title, emoji = str(extra, "emoji") ?: "💬", code = str(extra, "code") ?: "",
                ),
            )
            return
        }
        if (foreground && kind != "SOS") return // the in-app list and badge already show it
        notifier.post(id.hashCode(), kind, title, body, when (kind) { "SOS" -> "map"; "MONSTER" -> "play/battle"; else -> "notifications" })
        str(d, "createdAt")?.let { at -> scope.launch { settings.setLastNotificationAt(at) } }
    }

    override fun onStart(owner: LifecycleOwner) {
        if (session.userId == null) return
        realtime.start()
        scope.launch {
            location.refreshSharing().onSuccess { tracking.ensureRunning(it.collectLocation) }
            circles.refresh()
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        // While Locana is tracking (its foreground service keeps the process alive) the connection stays open,
        // so SOS, quick messages and place alerts arrive within seconds instead of at the next background sync.
        if (!tracking.running.value) realtime.stop()
    }

}
