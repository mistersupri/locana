package app.locana.data.safety

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import app.locana.data.system.Notifier
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

sealed interface CrashState {
    data object Idle : CrashState
    /** The question is on screen; [secondsLeft] counts down to an automatic SOS. */
    data class Asking(val secondsLeft: Int) : CrashState
}

/**
 * "Are you okay?" after a hard impact. Fifteen seconds to answer; with no answer the SOS goes out by itself.
 * The countdown lives here, not in the screen, so it keeps running with the screen off or the activity gone.
 */
@Singleton
class CrashAlertCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sos: SosCoordinator,
    private val notifier: Notifier,
    private val scope: CoroutineScope,
) {
    companion object { const val ANSWER_SECONDS = 15 }

    private val _state = MutableStateFlow<CrashState>(CrashState.Idle)
    val state: StateFlow<CrashState> = _state.asStateFlow()
    private var job: Job? = null

    /** Called by the detector. Ignored while a question is already open. */
    fun onCrashDetected() {
        if (_state.value !is CrashState.Idle) return
        _state.value = CrashState.Asking(ANSWER_SECONDS)
        notifier.postCrashAlert()
        job = scope.launch {
            for (left in ANSWER_SECONDS downTo 1) {
                _state.value = CrashState.Asking(left)
                buzz()
                delay(1_000)
            }
            _state.value = CrashState.Idle
            notifier.cancelCrashAlert()
            sos.send("CRASH")
        }
    }

    /** "I'm OK". */
    fun imOk() {
        job?.cancel()
        job = null
        _state.value = CrashState.Idle
        notifier.cancelCrashAlert()
    }

    /** "Send SOS now": no point waiting out the clock. */
    fun sendNow() {
        job?.cancel()
        job = null
        _state.value = CrashState.Idle
        notifier.cancelCrashAlert()
        sos.send("CRASH")
    }

    private fun buzz() {
        runCatching {
            val v = if (Build.VERSION.SDK_INT >= 31) context.getSystemService(VibratorManager::class.java).defaultVibrator
            else @Suppress("DEPRECATION") context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            v.vibrate(VibrationEffect.createOneShot(400, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }
}
