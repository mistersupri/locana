package app.locana.data.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.locana.data.system.ActivityMonitor
import com.google.android.gms.location.ActivityRecognitionResult
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Receives the activity guesses Play services produces and hands them to [ActivityMonitor]. */
@AndroidEntryPoint
class ActivityUpdatesReceiver : BroadcastReceiver() {
    @Inject lateinit var monitor: ActivityMonitor

    override fun onReceive(context: Context, intent: Intent) {
        if (!ActivityRecognitionResult.hasResult(intent)) return
        ActivityRecognitionResult.extractResult(intent)?.let(monitor::onResult)
    }
}
