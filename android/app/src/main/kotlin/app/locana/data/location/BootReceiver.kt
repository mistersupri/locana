package app.locana.data.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.locana.data.prefs.SettingsStore
import app.locana.data.system.Notifier
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * After a reboot or app update, resumes tracking only if the server last said this device
 * should collect location (sharing the user already turned on) and Android allows a
 * background start. Otherwise it tells the user sharing is waiting for them, instead of
 * pretending to be live.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    @Inject lateinit var settings: SettingsStore
    @Inject lateinit var controller: TrackingController
    @Inject lateinit var notifier: Notifier

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!settings.snapshot().lastKnownCollect) return@launch
                val perms = controller.refreshPermissions()
                if (perms.anyLocation && perms.background) {
                    controller.ensureRunning(true)
                } else {
                    notifier.post(
                        Notifier.TRACKING_ID + 1, "SHARING", "Location sharing is waiting",
                        "Open Locana to resume. Your circles see your last position as not live.", "sharing",
                    )
                }
            } finally {
                pending.finish()
            }
        }
    }
}
