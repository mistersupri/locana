package app.locana.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.launch

/*
 * Motion tokens. Curves and durations follow the animate skill tables: strong ease-out for
 * things entering or responding, ease-in-out for on-screen movement, UI under 300 ms.
 * Frequent surfaces (tab switches, list scrolling, map) get no decorative motion at all.
 */
object Motion {
    val EaseOut = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
    val EaseInOut = CubicBezierEasing(0.77f, 0f, 0.175f, 1f)

    const val PRESS_MS = 120
    const val SMALL_MS = 180
    const val SHEET_MS = 260

    fun <T> enter(ms: Int = SMALL_MS): FiniteAnimationSpec<T> = tween(ms, easing = EaseOut)
    fun <T> move(ms: Int = SMALL_MS): FiniteAnimationSpec<T> = tween(ms, easing = EaseInOut)
}

/** Fade in while rising a little: cards and rows settling into place the first time they appear. */
fun Modifier.enterRise(delayMs: Int = 0, risePx: Float = 28f): Modifier = composed {
    val reduced = LocalReducedMotion.current
    val progress = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reduced) progress.animateTo(1f, tween(380, delayMillis = delayMs, easing = Motion.EaseOut))
    }
    graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * risePx
    }
}

/** A small springy pop: pictures and badges arriving on screen. */
fun Modifier.popIn(delayMs: Int = 0): Modifier = composed {
    val reduced = LocalReducedMotion.current
    val scale = remember { Animatable(if (reduced) 1f else 0.82f) }
    val alpha = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reduced) {
            launch { alpha.animateTo(1f, tween(200, delayMillis = delayMs)) }
            scale.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 380f))
        }
    }
    graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
        this.alpha = alpha.value
    }
}
