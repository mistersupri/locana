package app.locana.data.location

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class PermissionState(val fine: Boolean, val coarse: Boolean, val background: Boolean, val notifications: Boolean) {
    val anyLocation: Boolean get() = fine || coarse
}

/**
 * Starts and stops the tracking service. It only ever follows the server's answer to
 * "should this device collect location?"; it never turns sharing on by itself.
 */
@Singleton
class TrackingController @Inject constructor(@ApplicationContext private val context: Context) {
    private val _permissions = MutableStateFlow(readPermissions())
    val permissions: StateFlow<PermissionState> = _permissions.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED

    fun readPermissions() = PermissionState(
        fine = granted(Manifest.permission.ACCESS_FINE_LOCATION),
        coarse = granted(Manifest.permission.ACCESS_COARSE_LOCATION),
        background = Build.VERSION.SDK_INT < 29 || granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
        notifications = Build.VERSION.SDK_INT < 33 || granted(Manifest.permission.POST_NOTIFICATIONS),
    )

    fun refreshPermissions(): PermissionState = readPermissions().also { _permissions.value = it }

    /** Call from the foreground (or boot with background permission). Android blocks other cases. */
    fun ensureRunning(collect: Boolean) {
        val perms = refreshPermissions()
        if (!collect || !perms.anyLocation) {
            stop()
            return
        }
        if (_running.value) return
        try {
            ContextCompat.startForegroundService(context, Intent(context, TrackingService::class.java))
            _running.value = true
        } catch (e: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException (API 31+) when started from the background.
            _running.value = false
        }
    }

    fun stop() {
        if (!_running.value) return
        context.stopService(Intent(context, TrackingService::class.java))
        _running.value = false
    }

    internal fun markStopped() {
        _running.value = false
    }
}
