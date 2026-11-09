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
import app.locana.ui.theme.LocalTokens
import app.locana.ui.components.TagPill
import app.locana.ui.components.SquareIconButton
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import app.locana.domain.Geo
import app.locana.domain.LatLon
import app.locana.ui.components.PrimaryButton
import app.locana.ui.components.SecondaryButton
import java.util.Locale

/**
 * The popup for a tapped place, laid out like the mockup's place sheet: name, kind and XP pills, a close
 * button, an info card (rating, status, address or the loading note) and two actions.
 */
@Composable
fun PlaceCard(poi: PoiUi, from: LatLon?, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val place = poi.place
    val scheme = MaterialTheme.colorScheme
    val t = LocalTokens.current
    val away = from?.let { Formatting.distance(Geo.distanceM(it, LatLon(poi.lat, poi.lon)).toInt()) + " from you" }
    Surface(shape = RoundedCornerShape(30.dp), color = scheme.background, shadowElevation = 14.dp, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(place?.name ?: poi.name, style = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp, lineHeight = 27.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        place?.type?.let { TagPill(it, t.yellowSoft, t.onYellowSoft) }
                        away?.let { TagPill(it, scheme.primaryContainer, scheme.primary) }
                    }
                }
                SquareIconButton(onClose, "Close", icon = Icons.Filled.Close)
            }
            Surface(shape = RoundedCornerShape(16.dp), color = t.card, shadowElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (poi.loading) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text("Loading details…", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    if (place?.rating != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Filled.Star, contentDescription = null, tint = t.yellowDark, modifier = Modifier.size(18.dp))
                            Text(String.format(Locale.US, "%.1f", place.rating), style = MaterialTheme.typography.titleSmall)
                            place.ratingCount?.let { Text("($it)", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant) }
                            place.openNow?.let { Text(if (it) "· Open now" else "· Closed now", style = MaterialTheme.typography.bodyMedium, color = if (it) Color(0xFF0E8A5D) else scheme.onSurfaceVariant) }
                        }
                    } else if (place?.openNow != null) {
                        Text(if (place.openNow) "Open now" else "Closed now", style = MaterialTheme.typography.labelLarge)
                    }
                    place?.address?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant) }
                    poi.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = t.ink3) }
                    if (!poi.loading && place == null && poi.error == null) Text("Tap Directions or open the place in Maps for more.", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
            }
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
