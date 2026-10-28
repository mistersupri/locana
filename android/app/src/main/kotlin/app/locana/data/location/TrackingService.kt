package app.locana.data.location

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import app.locana.R
import app.locana.data.db.PendingSample
import app.locana.data.repo.FlushResult
import app.locana.data.repo.LocationRepository
import app.locana.data.system.DeviceSignals
import app.locana.data.system.Notifier
import app.locana.domain.Accuracy
import app.locana.domain.Fix
import app.locana.domain.MovementDetector
import app.locana.domain.TrackingInputs
import app.locana.domain.TrackingPlan
import app.locana.domain.TrackingPolicy
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service that collects location while sharing or exploration is on. Its
 * notification is the visible "Locana is using your location" indicator, with a one-tap pause.
 */
@AndroidEntryPoint
class TrackingService : LifecycleService() {
    companion object {
        const val ACTION_PAUSE_ALL = "app.locana.PAUSE_ALL"
    }

    @Inject lateinit var repo: LocationRepository
    @Inject lateinit var signals: DeviceSignals
    @Inject lateinit var notifier: Notifier
    @Inject lateinit var controller: TrackingController

    private lateinit var fused: FusedLocationProviderClient
    private var plan: TrackingPlan = TrackingPlan.OFF
    private val recent = ArrayDeque<Fix>()
    private var tick: Job? = null

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            lifecycleScope.launch { result.locations.forEach { onFix(it) } }
        }
    }

    override fun onCreate() {
        super.onCreate()
        fused = LocationServices.getFusedLocationProviderClient(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        // Must be in the foreground within seconds of starting, before any network call.
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        try {
            ServiceCompat.startForeground(this, Notifier.TRACKING_ID, notification("Starting location sharing"), type)
        } catch (e: Exception) {
            // Missing permission or background start not allowed: do not track.
            stopSelfSafely()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_PAUSE_ALL) {
            lifecycleScope.launch {
                repo.pauseAll(true)
                stopSelfSafely()
            }
            return START_NOT_STICKY
        }
        if (tick == null) startLoop()
        return START_STICKY
    }

    private fun startLoop() {
        lifecycleScope.launch {
            repo.refreshSharing()
            repo.sharing.collectLatest { s ->
                if (s != null && !s.collectLocation) {
                    stopSelfSafely()
                    return@collectLatest
                }
                updateNotification()
                replan()
            }
        }
        tick = lifecycleScope.launch {
            var minutes = 0
            while (isActive) {
                delay(60_000)
                minutes++
                replan()
                maybeFlush(force = false)
                // Catch server-side expiry or changes made on another device.
                if (minutes % 5 == 0) repo.refreshSharing()
            }
        }
        lifecycleScope.launch { signals.online.collectLatest { online -> if (online) maybeFlush(force = true) } }
    }

    private fun inputs(): TrackingInputs {
        val s = repo.sharing.value
        val b = signals.battery()
        return TrackingInputs(
            sharingActive = s != null && !s.globallyPaused && s.sessions.any { it.status == "ACTIVE" },
            explorationEnabled = s?.explorationEnabled == true,
            appInForeground = signals.appInForeground(),
            moving = MovementDetector.isMoving(recent.toList()),
            batteryPct = b.pct,
            charging = b.charging,
            powerSaveMode = b.powerSave,
            online = signals.isOnline(),
        )
    }

    @SuppressLint("MissingPermission")
    private fun replan() {
        if (!controller.refreshPermissions().anyLocation) {
            stopSelfSafely()
            return
        }
        val next = TrackingPolicy.plan(inputs())
        if (!next.active) {
            stopSelfSafely()
            return
        }
        val requestChanged = next.intervalMs != plan.intervalMs || next.accuracy != plan.accuracy || next.minDistanceM != plan.minDistanceM
        plan = next
        if (!requestChanged) return
        val priority = when (next.accuracy) {
            Accuracy.HIGH -> Priority.PRIORITY_HIGH_ACCURACY
            Accuracy.BALANCED -> Priority.PRIORITY_BALANCED_POWER_ACCURACY
            Accuracy.LOW_POWER -> Priority.PRIORITY_LOW_POWER
        }
        val req = LocationRequest.Builder(priority, next.intervalMs)
            .setMinUpdateIntervalMillis(next.minIntervalMs)
            .setMinUpdateDistanceMeters(next.minDistanceM)
            .setMaxUpdateDelayMillis(if (next.uploadBatchMs in 1 until Long.MAX_VALUE) next.uploadBatchMs else 0)
            .setWaitForAccurateLocation(false)
            .build()
        try {
            fused.removeLocationUpdates(callback)
            fused.requestLocationUpdates(req, callback, Looper.getMainLooper())
        } catch (_: SecurityException) {
            stopSelfSafely()
        }
    }

    private suspend fun onFix(l: Location) {
        // Fixes without a usable accuracy are not real positions; drop them.
        if (!l.hasAccuracy() || l.accuracy <= 0f) return
        val battery = signals.battery()
        val mock = if (Build.VERSION.SDK_INT >= 31) l.isMock else @Suppress("DEPRECATION") l.isFromMockProvider
        repo.enqueue(
            PendingSample(
                latitude = l.latitude,
                longitude = l.longitude,
                accuracyM = l.accuracy,
                recordedAtMs = l.time,
                sequence = 0,
                speedMps = if (l.hasSpeed()) l.speed else null,
                bearingDeg = if (l.hasBearing()) l.bearing else null,
                altitudeM = if (l.hasAltitude()) l.altitude else null,
                source = if (l.provider == "gps") "gps" else "fused",
                activity = null,
                isMock = mock,
                batteryPct = battery.pct,
            ),
        )
        recent.addLast(Fix(l.latitude, l.longitude, l.accuracy, l.time, if (l.hasSpeed()) l.speed else null))
        while (recent.size > 20) recent.removeFirst()
        val wasMoving = plan.intervalMs
        replan()
        maybeFlush(force = plan.uploadBatchMs == 0L || wasMoving != plan.intervalMs)
    }

    private suspend fun maybeFlush(force: Boolean) {
        val oldest = repo.oldestPendingMs() ?: return
        val due = force || plan.uploadBatchMs == 0L || System.currentTimeMillis() - oldest >= plan.uploadBatchMs
        if (!due || !signals.isOnline()) {
            if (!signals.isOnline()) UploadWorker.schedule(this)
            return
        }
        when (repo.flush()) {
            FlushResult.NotNeeded -> stopSelfSafely()
            FlushResult.Offline, is FlushResult.Failed -> UploadWorker.schedule(this)
            else -> Unit
        }
    }

    private fun updateNotification() {
        val s = repo.sharing.value
        val active = s?.sessions?.filter { it.status == "ACTIVE" }.orEmpty()
        val text = when {
            s == null -> "Checking your sharing settings"
            s.globallyPaused || active.isEmpty() -> if (s.explorationEnabled) "Exploration is on. Your location is not shared." else "Not sharing"
            else -> "Sharing with " + active.mapNotNull { it.circleName }.joinToString(", ")
        }
        val nm = getSystemService(android.app.NotificationManager::class.java)
        nm.notify(Notifier.TRACKING_ID, notification(text))
    }

    private fun notification(text: String): Notification {
        val pause = PendingIntent.getService(
            this, 1,
            Intent(this, TrackingService::class.java).setAction(ACTION_PAUSE_ALL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, Notifier.CH_SHARING)
            .setSmallIcon(R.drawable.ic_stat_locana)
            .setContentTitle("Locana is using your location")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(notifier.openAppIntent("sharing"))
            .addAction(0, "Pause sharing", pause)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun stopSelfSafely() {
        try { fused.removeLocationUpdates(callback) } catch (_: Exception) {}
        controller.markStopped()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        try { fused.removeLocationUpdates(callback) } catch (_: Exception) {}
        controller.markStopped()
        UploadWorker.schedule(this)
        super.onDestroy()
    }
}
