package app.locana.ui.map

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.locana.domain.Formatting
import app.locana.domain.Geo
import app.locana.domain.LatLon
import app.locana.ui.components.PrimaryButton
import app.locana.ui.components.SecondaryButton
import java.util.Locale

/** The popup for a tapped place: what it is, how good and how far, and ways to get there. */
@Composable
fun PlaceCard(poi: PoiUi, from: LatLon?, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val place = poi.place
    val scheme = MaterialTheme.colorScheme
    val away = from?.let { Formatting.distance(Geo.distanceM(it, LatLon(poi.lat, poi.lon)).toInt()) + " from you" }
    Surface(shape = RoundedCornerShape(24.dp), color = floatingColor(), shadowElevation = 10.dp, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f).padding(top = 6.dp)) {
                    Text(place?.name ?: poi.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val line = listOfNotNull(place?.type, away).joinToString(" · ")
                    if (line.isNotEmpty()) Text(line, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                }
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Close") }
            }
            if (poi.loading) {
                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text("Loading details", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                }
            }
            Column(Modifier.padding(end = 12.dp)) {
                if (place?.rating != null) {
                    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Filled.Star, contentDescription = null, tint = scheme.tertiary, modifier = Modifier.size(18.dp))
                        Text(String.format(Locale.US, "%.1f", place.rating), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        place.ratingCount?.let { Text("($it)", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant) }
                        place.openNow?.let { Text(if (it) "· Open now" else "· Closed now", style = MaterialTheme.typography.bodyMedium, color = if (it) scheme.primary else scheme.onSurfaceVariant) }
                    }
                } else if (place?.openNow != null) {
                    Text(if (place.openNow) "Open now" else "Closed now", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 10.dp))
                }
                place?.address?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp)) }
                poi.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp)) }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryButton("Directions", {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=${poi.lat},${poi.lon}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }, Modifier.weight(1f))
                    SecondaryButton("Open in Maps", {
                        val uri = place?.mapsUri?.let(Uri::parse)
                            ?: Uri.parse("geo:${poi.lat},${poi.lon}?q=${poi.lat},${poi.lon}(${Uri.encode(poi.name)})")
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }, Modifier.weight(1f))
                }
            }
        }
    }
}
