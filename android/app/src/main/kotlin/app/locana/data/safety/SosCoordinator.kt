package app.locana.data.safety

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import app.locana.data.api.SosResultDto
import app.locana.data.prefs.SettingsStore
import app.locana.data.repo.SafetyRepository
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SosState {
    data object Idle : SosState
    data object Sending : SosState
    data class Sent(val result: SosResultDto, val calling: String?) : SosState
    data class Failed(val message: String) : SosState
}

/**
 * Sends an SOS: finds the best position it can in a few seconds, asks the server to tell the circle and the
 * emergency contacts, then places the call to the first contact. Used by the held SOS button and by the
 * crash alert when nobody answers. Works from the app scope, so it finishes even if the screen goes away.
 */
@Singleton
class SosCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val safety: SafetyRepository,
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<SosState>(SosState.Idle)
    val state: StateFlow<SosState> = _state.asStateFlow()

    fun dismiss() { if (_state.value !is SosState.Sending) _state.value = SosState.Idle }

    /** Starts an SOS and returns at once; progress is in [state]. A second press while one is going out is ignored. */
    fun send(reason: String) {
        if (_state.value is SosState.Sending) return
        _state.value = SosState.Sending
        scope.launch {
            val fix = bestFix()
            if (fix == null) {
                _state.value = SosState.Failed("No position yet. Go outside or turn on location, then try again.")
                return@launch
            }
            safety.sos(reason, fix.first, fix.second, fix.third).fold(
                { r ->
                    val first = r.contacts.firstOrNull()
                    if (first != null) placeCall(first.phone)
                    _state.value = SosState.Sent(r, first?.name)
                },
                { _state.value = SosState.Failed(it.message ?: "Couldn't send the SOS. Check your connection and try again.") },
            )
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun bestFix(): Triple<Double, Double, Double?>? {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!granted) return null
        val client = LocationServices.getFusedLocationProviderClient(context)
        val fresh = runCatching {
            withTimeoutOrNull(8_000) { client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token).await() }
        }.getOrNull()
        val loc = fresh ?: runCatching { client.lastLocation.await() }.getOrNull() ?: return null
        return Triple(loc.latitude, loc.longitude, if (loc.hasAccuracy()) loc.accuracy.toDouble() else null)
    }

    /** Places the call itself when allowed (CALL_PHONE and the person's choice), otherwise opens the dialer with the number filled in. */
    private suspend fun placeCall(phone: String) {
        val number = phone.filter { it.isDigit() || it == '+' }
        if (number.isEmpty()) return
        val canCall = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        val auto = runCatching { settings.snapshot().autoCall }.getOrDefault(false)
        val intent = Intent(if (auto && canCall) Intent.ACTION_CALL else Intent.ACTION_DIAL, Uri.parse("tel:$number")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }
}
