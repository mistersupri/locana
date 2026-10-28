package app.locana.data.location

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.locana.data.auth.SessionManager
import app.locana.data.repo.FlushResult
import app.locana.data.repo.LocationRepository
import app.locana.data.repo.NotificationRepository
import app.locana.data.prefs.SettingsStore
import app.locana.data.system.DeviceSignals
import app.locana.data.system.Notifier
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/** Uploads queued samples when a network is available. WorkManager handles Doze and retries. */
@HiltWorker
class UploadWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val repo: LocationRepository,
) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = when (repo.flush()) {
        FlushResult.Offline, is FlushResult.Failed -> Result.retry()
        else -> Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val req = OneTimeWorkRequestBuilder<UploadWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("location-upload", ExistingWorkPolicy.KEEP, req)
        }
    }
}

/**
 * Periodically fetches server notifications and shows the unread ones as system
 * notifications while the app is closed. Android runs this at most every 15 minutes and
 * may defer it further, so alerts can be late; the in-app list is always complete.
 */
@HiltWorker
class NotificationSyncWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val session: SessionManager,
    private val notifications: NotificationRepository,
    private val notifier: Notifier,
    private val settings: SettingsStore,
    private val signals: DeviceSignals,
) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        session.restore()
        if (session.userId == null) return Result.success()
        val page = notifications.page(null).getOrElse { return Result.retry() }
        val since = settings.lastNotificationAt()
        val fresh = page.items.filter { !it.read && (since == null || it.createdAt > since) }
        if (!signals.appInForeground()) {
            fresh.take(5).forEach { notifier.post(it.id.hashCode(), it.kind, it.title, it.body, "notifications") }
        }
        page.items.firstOrNull()?.let { settings.setLastNotificationAt(it.createdAt) }
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<NotificationSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("notification-sync", ExistingPeriodicWorkPolicy.KEEP, req)
        }

        fun cancel(context: Context) = WorkManager.getInstance(context).cancelUniqueWork("notification-sync")
    }
}
