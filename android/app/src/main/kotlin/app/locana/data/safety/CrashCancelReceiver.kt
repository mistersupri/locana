package app.locana.data.safety

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** The "I'm OK" button on the crash notification: answers the question without opening the app. */
@AndroidEntryPoint
class CrashCancelReceiver : BroadcastReceiver() {
    @Inject lateinit var crash: CrashAlertCoordinator

    override fun onReceive(context: Context, intent: Intent) = crash.imOk()
}
