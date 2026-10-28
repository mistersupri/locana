package app.locana.domain

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.sqrt
import kotlin.math.tan

data class LatLon(val lat: Double, val lon: Double)

object Geo {
    const val EARTH_RADIUS_M = 6_371_008.8

    private fun rad(d: Double) = d * PI / 180.0
    private fun deg(r: Double) = r * 180.0 / PI

    fun distanceM(a: LatLon, b: LatLon): Double {
        val dLat = rad(b.lat - a.lat)
        val dLon = rad(b.lon - a.lon)
        val h = sin(dLat / 2).let { it * it } + cos(rad(a.lat)) * cos(rad(b.lat)) * sin(dLon / 2).let { it * it }
        return 2 * EARTH_RADIUS_M * asin(min(1.0, sqrt(h)))
    }

    internal fun toDeg(r: Double) = deg(r)
    internal fun toRad(d: Double) = rad(d)
}

/**
 * Web-Mercator slippy tiles, identical to backend/src/common/geo.ts so both sides agree on
 * tile ids ("z/x/y"). Zoom 17 tiles are about 300 m wide around Indonesia.
 */
data class Tile(val z: Int, val x: Int, val y: Int) {
    val key: String get() = "$z/$x/$y"

    /** north, south, east, west in degrees */
    fun bounds(): TileBounds {
        val nw = origin(z, x, y)
        val se = origin(z, x + 1, y + 1)
        return TileBounds(north = nw.lat, south = se.lat, east = se.lon, west = nw.lon)
    }

    companion object {
        const val DEFAULT_ZOOM = 17

        fun of(lat: Double, lon: Double, z: Int = DEFAULT_ZOOM): Tile {
            val n = 1 shl z
            val x = floor((lon + 180.0) / 360.0 * n).toInt().coerceIn(0, n - 1)
            val latRad = Geo.toRad(lat)
            val y = floor((1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n).toInt().coerceIn(0, n - 1)
            return Tile(z, x, y)
        }

        fun parse(key: String): Tile? {
            val parts = key.split('/')
            if (parts.size != 3) return null
            val (z, x, y) = parts.map { it.toIntOrNull() ?: return null }
            val n = 1 shl z
            return if (z in 0..22 && x in 0 until n && y in 0 until n) Tile(z, x, y) else null
        }

        private fun origin(z: Int, x: Int, y: Int): LatLon {
            val n = (1 shl z).toDouble()
            val lon = x / n * 360.0 - 180.0
            val lat = Geo.toDeg(atan(sinh(PI * (1 - 2 * y / n))))
            return LatLon(lat, lon)
        }
    }
}

data class TileBounds(val north: Double, val south: Double, val east: Double, val west: Double)
