package app.locana.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween

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
