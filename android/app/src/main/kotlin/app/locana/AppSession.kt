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
                    me.registerDevice("${Build.MANUFACTURER} ${Build.MODEL}", BuildConfig.VERSION_NAME)
                } else {
                    realtime.stop()
                    NotificationSyncWorker.cancel(context)
                }
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
                if (e.type == "notification") notifications.bump()
                if (e.type == "level.up" || e.type == "achievement.unlocked") me.refresh()
            }
        }
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
        realtime.stop()
    }

}
