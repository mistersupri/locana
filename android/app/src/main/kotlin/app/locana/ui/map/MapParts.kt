package app.locana.ui.map

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.locana.domain.Formatting
import app.locana.domain.Freshness
import app.locana.domain.Presence
import app.locana.ui.components.presenceLabel
import app.locana.ui.components.pressScale
import app.locana.ui.theme.AvatarColors
import app.locana.ui.theme.Display
import app.locana.ui.theme.LocalDarkTheme
import app.locana.ui.theme.LocalReward

/*
 * Map overlays share one language, taken from the owner's reference screenshot: white rounded
 * controls with a soft shadow floating over a light map, a bold circle name, and a bottom sheet.
 * Radius 16 dp for controls (they are buttons, so clearly squarer than the 28 dp sheet).
 */

/** White in the light theme, a lifted dark surface in the dark theme. */
@Composable
fun floatingColor(): Color =
    if (LocalDarkTheme.current) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLowest

val ControlShape = RoundedCornerShape(16.dp)

/** Square floating button. The caller puts an [Icon] with a content description inside. */
@Composable
fun MapControl(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val source = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        interactionSource = source,
        shape = ControlShape,
        color = floatingColor(),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 6.dp,
        modifier = modifier.size(52.dp).pressScale(source),
    ) { Box(contentAlignment = Alignment.Center) { content() } }
}

/** Floating action with a label, like the "Check in" and "SOS" pills in the reference. */
@Composable
fun MapPill(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val source = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        interactionSource = source,
        shape = ControlShape,
        color = floatingColor(),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 6.dp,
        modifier = modifier.heightIn(min = 52.dp).pressScale(source),
    ) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        }
    }
}
