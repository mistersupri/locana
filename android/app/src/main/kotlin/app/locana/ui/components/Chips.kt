package app.locana.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.locana.ui.theme.LocalReward

/** Small status label. Pill shape is reserved for these: they are labels, not buttons. */
@Composable
fun StatusChip(text: String, modifier: Modifier = Modifier, container: Color = MaterialTheme.colorScheme.primaryContainer, content: Color = MaterialTheme.colorScheme.primary) {
    Surface(color = container, contentColor = content, shape = RoundedCornerShape(50), modifier = modifier) {
        Text(text, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp))
    }
}

@Composable
fun XpChip(xp: Int, modifier: Modifier = Modifier) {
    XpBadge("+$xp XP", modifier)
}

fun rarityLabel(r: String) = r.lowercase().replaceFirstChar { it.uppercase() }
