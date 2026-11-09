package app.locana.data.system

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import app.locana.data.location.ActivityUpdatesReceiver
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.DetectedActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What the phone itself thinks the person is doing: in a vehicle, on a bicycle, walking, running or
 * still. Google's activity recognition uses the motion sensors, which is far better than guessing from
 * GPS speed (a car in a jam crawls at walking pace; a phone on a desk reports phantom speed). The values
 * are the ones the server accepts: vehicle, cycling, walking, running, still.
 *
 * Needs the ACTIVITY_RECOGNITION permission on Android 10 and later. Without it everything still works
 * and the server falls back to speed and distance.
 */
@Singleton
class ActivityMonitor @Inject constructor(@ApplicationContext private val context: Context) {
    data class Reading(val kind: String, val confidence: Int, val atMs: Long)

    @Volatile private var latest: Reading? = null
    private var started = false

    fun permitted(): Boolean = Build.VERSION.SDK_INT < 29 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED

    private fun pendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context, 2, Intent(context, ActivityUpdatesReceiver::class.java),
        // Activity updates are filled into the intent by Play services, so it must be mutable.
        PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0),
    )

    @SuppressLint("MissingPermission")
    fun start() {
        if (started || !permitted()) return
        runCatching { ActivityRecognition.getClient(context).requestActivityUpdates(20_000L, pendingIntent()) }
            .onSuccess { started = true }
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        if (!started) return
        runCatching { ActivityRecognition.getClient(context).removeActivityUpdates(pendingIntent()) }
        started = false
        latest = null
    }

    fun onResult(result: ActivityRecognitionResult, nowMs: Long = System.currentTimeMillis()) {
        val best = result.mostProbableActivity
        latest = Reading(kindOf(best.type), best.confidence, nowMs)
    }

    /** The activity to stamp on a fix: recent and reasonably sure, or null. */
    fun current(nowMs: Long = System.currentTimeMillis()): String? {
        val r = latest ?: return null
        return r.kind.takeIf { nowMs - r.atMs <= 3 * 60_000 && r.confidence >= 50 && it != "unknown" }
    }

    companion object {
        fun kindOf(type: Int): String = when (type) {
            DetectedActivity.IN_VEHICLE -> "vehicle"
            DetectedActivity.ON_BICYCLE -> "cycling"
            DetectedActivity.WALKING, DetectedActivity.ON_FOOT -> "walking"
            DetectedActivity.RUNNING -> "running"
            DetectedActivity.STILL -> "still"
            else -> "unknown"
        }
    }
}
