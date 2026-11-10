package app.locana.data.safety

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.locana.ui.components.ChunkyButton
import app.locana.ui.components.ChunkyStyle
import app.locana.ui.theme.Display
import app.locana.ui.theme.LocanaTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Shown over the lock screen after a hard impact: a big countdown, one button to say you are fine. */
@AndroidEntryPoint
class CrashAlertActivity : ComponentActivity() {
    @Inject lateinit var crash: CrashAlertCoordinator
    @Inject lateinit var sos: SosCoordinator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LocanaTheme {
                val asking by crash.state.collectAsStateWithLifecycle()
                val sent by sos.state.collectAsStateWithLifecycle()
                // Nothing left to show: the question was answered, or the SOS result was seen.
                LaunchedEffect(asking, sent) {
                    if (asking is CrashState.Idle && (sent is SosState.Idle)) finish()
                }
                Column(
                    Modifier.fillMaxSize().background(Color(0xFFD13E2B)).statusBarsPadding().padding(28.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val a = asking
                    when {
                        a is CrashState.Asking -> {
                            Text("Are you okay?", style = MaterialTheme.typography.headlineLarge, color = Color.White, textAlign = TextAlign.Center)
                            Text(
                                "${a.secondsLeft}",
                                fontSize = 120.sp, fontFamily = Display, color = Color.White,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                            )
                            Text(
                                "A hard impact was detected. If you do not answer, an SOS goes to your circles and emergency contacts.",
                                style = MaterialTheme.typography.bodyLarge, color = Color.White, textAlign = TextAlign.Center,
                            )
                            ChunkyButton("I'm OK", { crash.imOk() }, Modifier.fillMaxWidth())
                            ChunkyButton("Send SOS now", { crash.sendNow() }, Modifier.fillMaxWidth(), style = ChunkyStyle.Yellow)
                        }
                        sent is SosState.Sending -> Text("Sending your SOS…", style = MaterialTheme.typography.headlineMedium, color = Color.White, textAlign = TextAlign.Center)
                        sent is SosState.Sent -> {
                            val r = (sent as SosState.Sent).result
                            Text("SOS sent", style = MaterialTheme.typography.headlineLarge, color = Color.White)
                            Text(
                                "${r.notified} people in your circles were told" + (if (r.emailed > 0) " and ${r.emailed} e-mails went out." else "."),
                                style = MaterialTheme.typography.bodyLarge, color = Color.White, textAlign = TextAlign.Center,
                            )
                            ChunkyButton("Close", { sos.dismiss() }, Modifier.fillMaxWidth(), style = ChunkyStyle.Yellow)
                        }
                        sent is SosState.Failed -> {
                            Text("SOS not sent", style = MaterialTheme.typography.headlineLarge, color = Color.White)
                            Text((sent as SosState.Failed).message, style = MaterialTheme.typography.bodyLarge, color = Color.White, textAlign = TextAlign.Center)
                            ChunkyButton("Try again", { sos.send("CRASH") }, Modifier.fillMaxWidth(), style = ChunkyStyle.Yellow)
                            ChunkyButton("Close", { sos.dismiss() }, Modifier.fillMaxWidth(), style = ChunkyStyle.Secondary)
                        }
                    }
                }
            }
        }
    }
}
