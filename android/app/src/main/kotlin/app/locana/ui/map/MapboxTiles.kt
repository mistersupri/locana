package app.locana.ui.map

import com.google.android.gms.maps.model.TileProvider
import com.google.android.gms.maps.model.UrlTileProvider
import java.net.URL

/*
 * Mapbox look for the map (why this way): the official Mapbox SDK is a second map engine that needs a secret
 * download token and would mean rewriting every marker, overlay and gesture. Mapbox also serves its styles as
 * ordinary image tiles, so the Google map keeps doing the work (pins, tiles, territories, camera, padding) with
 * its own base map switched off and Mapbox's tiles drawn in its place. The map must show Mapbox's attribution.
 */
const val MAPBOX_ATTRIBUTION = "© Mapbox © OpenStreetMap"

fun mapboxTileProvider(token: String, dark: Boolean): TileProvider {
    val style = if (dark) "dark-v11" else "streets-v12"
    // 256 dp tiles delivered at 2x (512 px), so they are sharp on phone screens.
    return object : UrlTileProvider(512, 512) {
        override fun getTileUrl(x: Int, y: Int, zoom: Int): URL? =
            if (zoom !in 0..22) null else URL("https://api.mapbox.com/styles/v1/mapbox/$style/tiles/256/$zoom/$x/$y@2x?access_token=$token")
    }
}
