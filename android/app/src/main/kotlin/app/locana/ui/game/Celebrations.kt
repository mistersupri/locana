package app.locana.ui.game

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.locana.data.realtime.RealtimeClient
import app.locana.ui.theme.LocalReducedMotion
import app.locana.ui.theme.LocalReward
import app.locana.ui.theme.Motion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

data class LevelUp(val level: Int, val unlocked: List<String>)

/** Queue of rare moments worth a celebration. One at a time, never stacked. */
@Singleton
class Celebrations @Inject constructor() {
    private val _current = MutableStateFlow<LevelUp?>(null)
    val current: StateFlow<LevelUp?> = _current.asStateFlow()
    private var lastShown = 0

    fun levelUp(level: Int, unlocked: List<String> = emptyList()) {
        if (level <= lastShown) return
        lastShown = level
        _current.value = LevelUp(level, unlocked)
    }

    fun dismiss() {
        _current.value = null
    }
}

@HiltViewModel
class LevelUpViewModel @Inject constructor(val celebrations: Celebrations, realtime: RealtimeClient) : ViewModel() {
    init {
        viewModelScope.launch {
            realtime.events.collect { e ->
                if (e.type == "level.up") e.data["to"]?.jsonPrimitive?.intOrNull?.let { celebrations.levelUp(it) }
            }
        }
    }
}

/**
 * Level-up dialog. Rare tier, so it gets a small scale-in on the number (from 0.9, ease-out,
 * 260 ms). With reduced motion the number simply appears.
 */
@Composable
fun LevelUpHost(vm: LevelUpViewModel = hiltViewModel()) {
    val event by vm.celebrations.current.collectAsStateWithLifecycle()
    val e = event ?: return
    val reduced = LocalReducedMotion.current
    val scale = remember(e.level) { Animatable(if (reduced) 1f else 0.9f) }
    val alpha = remember(e.level) { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(e.level) {
        if (!reduced) {
            launch { scale.animateTo(1f, Motion.enter(260)) }
            alpha.animateTo(1f, Motion.enter(200))
        }
    }
    AlertDialog(
        onDismissRequest = vm.celebrations::dismiss,
        confirmButton = { TextButton(onClick = vm.celebrations::dismiss) { Text("Keep exploring") } },
        title = { Text("Level up") },
        text = {
            Column {
                Text(
                    "Level ${e.level}",
                    style = MaterialTheme.typography.displaySmall,
                    color = LocalReward.current.reward,
                    modifier = Modifier.graphicsLayer {
                        scaleX = scale.value
                        scaleY = scale.value
                        this.alpha = alpha.value
                    },
                )
                e.unlocked.forEach { Text("Unlocked: $it", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp)) }
            }
        },
    )
}
