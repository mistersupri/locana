package app.locana.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.locana.ui.theme.LocalReducedMotion
import app.locana.ui.theme.Motion

/**
 * Progress bar from the mockup: a rounded track with a rounded fill. [segments] is kept for call sites that
 * still pass it. Motion: the fill eases to its new value (state indication) and is skipped with reduced motion.
 */
@Composable
fun TileTrack(
    fraction: Float,
    modifier: Modifier = Modifier,
    segments: Int = 12,
    height: Dp = 10.dp,
    fill: Color = MaterialTheme.colorScheme.primary,
    track: Color = MaterialTheme.colorScheme.primaryContainer,
) {
    val target = fraction.coerceIn(0f, 1f)
    val animated by animateFloatAsState(target, animationSpec = Motion.move(260), label = "tiles")
    val f = if (LocalReducedMotion.current) target else animated
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(target, 0f..1f) },
    ) {
        val r = CornerRadius(size.height / 2)
        drawRoundRect(track, Offset.Zero, size, r)
        // A started bar always shows a knob, so "0 of 3" and "1 of 30" still read as a bar.
        val w = if (f <= 0f) 0f else maxOf(size.height, size.width * f)
        if (w > 0f) drawRoundRect(fill, Offset.Zero, Size(w, size.height), r)
    }
}

/** Quest progress: one tile per step for small targets, a scaled track for distances and big counts. */
@Composable
fun QuestProgressTrack(
    progress: Int,
    target: Int,
    unit: String,
    modifier: Modifier = Modifier,
    fill: Color = MaterialTheme.colorScheme.primary,
    track: Color = MaterialTheme.colorScheme.primaryContainer,
) {
    val discrete = unit != "meters" && target in 1..12
    TileTrack(
        fraction = if (target == 0) 0f else progress.toFloat() / target,
        segments = if (discrete) target else 12,
        modifier = modifier,
        fill = fill,
        track = track,
    )
}
