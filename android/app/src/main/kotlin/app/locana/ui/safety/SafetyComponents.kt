package app.locana.ui.safety

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.locana.data.api.QuickMessageDefDto
import app.locana.data.safety.SosState
import app.locana.ui.theme.Display
import app.locana.ui.theme.LocalTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

const val SOS_HOLD_MS = 1_500

/** The same list the server offers, so the chips appear at once even before (or without) the network. */
val DefaultQuickMessages = listOf(
    QuickMessageDefDto("LOVE", "Love ya!", "❤️"),
    QuickMessageDefDto("SLOW_DOWN", "Slow down", "😳"),
    QuickMessageDefDto("CALL_ME", "Call me", "📞"),
    QuickMessageDefDto("WHERE_ARE_YOU", "Where are you?", "📍"),
    QuickMessageDefDto("ON_MY_WAY", "On my way", "🚗"),
    QuickMessageDefDto("BE_CAREFUL", "Be careful", "🙏"),
    QuickMessageDefDto("THANK_YOU", "Thank you", "🙌"),
    QuickMessageDefDto("HAHA", "Haha", "😂"),
)

/**
 * Hold to send an SOS. A ring fills while the finger stays down; letting go early cancels, so a pocket touch
 * or a quick tap never raises the alarm. Talkback users get a long-press action.
 */
@Composable
fun SosButton(onTriggered: () -> Unit, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var hint by remember { mutableStateOf(false) }
    LaunchedEffect(hint) { if (hint) { delay(2_200); hint = false } }
    val coral = LocalTokens.current.coral
    Box(modifier, contentAlignment = Alignment.Center) {
        if (hint) Surface(
            shape = RoundedCornerShape(14.dp), color = LocalTokens.current.card, shadowElevation = 6.dp,
            modifier = Modifier.align(Alignment.CenterStart).padding(end = 60.dp),
        ) { Text("Hold to send SOS", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) }
        Box(
            Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(coral)
                .semantics { role = Role.Button; contentDescription = "SOS. Press and hold to send an alert to your circles and emergency contacts."; onLongClick("Send SOS") { onTriggered(); true } }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            val job = scope.launch {
                                progress.snapTo(0f)
                                progress.animateTo(1f, tween(SOS_HOLD_MS, easing = LinearEasing))
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onTriggered()
                                progress.snapTo(0f)
                            }
                            tryAwaitRelease()
                            if (job.isActive) { job.cancel(); scope.launch { progress.animateTo(0f, tween(160)) } }
                        },
                        onTap = { hint = true },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(52.dp)) {
                val stroke = 5.dp.toPx()
                drawArc(Color.White, -90f, 360f * progress.value, false, topLeft = Offset(stroke / 2, stroke / 2), size = Size(size.width - stroke, size.height - stroke), style = Stroke(stroke, cap = StrokeCap.Round))
            }
            Text("SOS", color = Color.White, fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
        }
    }
}

/** What happened to the SOS: sending, sent (who was told) or failed with a way to try again. */
@Composable
fun SosDialog(state: SosState, onDismiss: () -> Unit, onRetry: () -> Unit) {
    if (state is SosState.Idle) return
    AlertDialog(
        onDismissRequest = { if (state !is SosState.Sending) onDismiss() },
        title = { Text(when (state) { is SosState.Sending -> "Sending your SOS…"; is SosState.Sent -> "SOS sent"; else -> "SOS not sent" }) },
        text = {
            Text(
                when (state) {
                    is SosState.Sending -> "Finding where you are and telling your circles."
                    is SosState.Sent -> buildString {
                        append("${state.result.notified} people in your circles were told")
                        if (state.result.emailed > 0) append(" and ${state.result.emailed} e-mails went out")
                        append(".")
                        state.calling?.let { append(" Calling $it.") } ?: append(" You have no emergency contact yet: add one under Profile → Safety.")
                    }
                    is SosState.Failed -> state.message
                    else -> ""
                },
            )
        },
        confirmButton = {
            when (state) {
                is SosState.Failed -> TextButton(onClick = onRetry) { Text("Try again") }
                is SosState.Sent -> TextButton(onClick = onDismiss) { Text("OK") }
                else -> Unit
            }
        },
        dismissButton = { if (state is SosState.Failed) TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

/** Chips of quick messages floating above the drawer while someone else's pin is in focus. */
@Composable
fun QuickMessageRow(messages: List<QuickMessageDefDto>, onSend: (QuickMessageDefDto) -> Unit, modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    LazyRow(modifier, contentPadding = PaddingValues(horizontal = 2.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(messages, key = { it.code }) { m ->
            Surface(
                onClick = { onSend(m) }, shape = RoundedCornerShape(26.dp), color = t.card, shadowElevation = 6.dp,
                modifier = Modifier.semantics { contentDescription = "Send ${m.text} to this person" },
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(m.emoji, fontSize = 20.sp)
                    Text(m.text, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold), color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
