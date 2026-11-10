package app.locana.data.safety

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/** A quick message that just arrived while the app is open: shown as a banner with falling emoji. */
data class IncomingQuickMessage(val fromName: String, val text: String, val emoji: String, val code: String)

@Singleton
class IncomingMessages @Inject constructor() {
    private val _flow = MutableSharedFlow<IncomingQuickMessage>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val flow: SharedFlow<IncomingQuickMessage> = _flow.asSharedFlow()
    fun publish(m: IncomingQuickMessage) { _flow.tryEmit(m) }
}
