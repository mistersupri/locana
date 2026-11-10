package app.locana.data.system

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.locana.MainActivity
import app.locana.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Android system notifications. Respects the user's notification permission and channel settings. */
@Singleton
class Notifier @Inject constructor(@ApplicationContext private val context: Context) {
    companion object {
        const val CH_SHARING = "sharing"
        const val CH_CIRCLE = "circle"
        const val CH_GAME = "game"
        const val CH_SECURITY = "security"
        const val CH_SAFETY = "safety"
        const val CRASH_ID = 1002
        const val TRACKING_ID = 1001
    }

    fun createChannels() {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(CH_SHARING, context.getString(R.string.channel_sharing), NotificationManager.IMPORTANCE_LOW)
                    .apply { description = context.getString(R.string.channel_sharing_desc); setShowBadge(false) },
                NotificationChannel(CH_CIRCLE, context.getString(R.string.channel_circle), NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = context.getString(R.string.channel_circle_desc) },
                NotificationChannel(CH_GAME, context.getString(R.string.channel_game), NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = context.getString(R.string.channel_game_desc) },
                NotificationChannel(CH_SECURITY, context.getString(R.string.channel_security), NotificationManager.IMPORTANCE_HIGH)
                    .apply { description = context.getString(R.string.channel_security_desc) },
                NotificationChannel(CH_SAFETY, context.getString(R.string.channel_safety), NotificationManager.IMPORTANCE_HIGH)
                    .apply { description = context.getString(R.string.channel_safety_desc); enableVibration(true) },
            ),
        )
    }

    fun canPost(): Boolean =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun openAppIntent(route: String? = null): PendingIntent {
        val i = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            route?.let { putExtra(MainActivity.EXTRA_ROUTE, it) }
        }
        return PendingIntent.getActivity(context, route?.hashCode() ?: 0, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun channelFor(kind: String) = when (kind) {
        "ARRIVAL", "CIRCLE", "CHALLENGE", "INVITATION" -> CH_CIRCLE
        "QUEST", "LEVEL_UP", "ACHIEVEMENT" -> CH_GAME
        "SHARING" -> CH_SHARING
        "SOS" -> CH_SAFETY
        "QUICK_MESSAGE", "TRIP" -> CH_CIRCLE
        else -> CH_SECURITY
    }

    fun post(id: Int, kind: String, title: String, body: String, route: String? = null) {
        if (!canPost()) return
        val n = NotificationCompat.Builder(context, channelFor(kind))
            .setSmallIcon(R.drawable.ic_stat_locana)
            .setContentTitle(title)
            .setContentText(body.ifBlank { null })
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(route))
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, n)
        } catch (_: SecurityException) {
            // permission revoked between the check and the call
        }
    }

    /** The crash question: full screen over the lock screen, with an "I'm OK" action that works without unlocking. */
    fun postCrashAlert() {
        if (!canPost()) return
        val open = PendingIntent.getActivity(
            context, 20, Intent(context, app.locana.data.safety.CrashAlertActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val ok = PendingIntent.getBroadcast(
            context, 21, Intent(context, app.locana.data.safety.CrashCancelReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CH_SAFETY)
            .setSmallIcon(R.drawable.ic_stat_locana)
            .setContentTitle("Are you okay?")
            .setContentText("A hard impact was detected. SOS is sent in 15 seconds unless you answer.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setFullScreenIntent(open, true)
            .setContentIntent(open)
            .addAction(0, "I'm OK", ok)
            .build()
        try { NotificationManagerCompat.from(context).notify(CRASH_ID, n) } catch (_: SecurityException) {}
    }

    fun cancelCrashAlert() = NotificationManagerCompat.from(context).cancel(CRASH_ID)
}
