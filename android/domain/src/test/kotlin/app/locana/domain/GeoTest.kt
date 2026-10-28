package app.locana.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoTest {
    @Test
    fun `tile ids match the backend for the same points`() {
        // Reference values printed by backend/src/common/geo.ts tileFor().
        assertEquals("17/104430/67788", Tile.of(-6.175392, 106.827153).key)
        assertEquals("17/65490/43588", Tile.of(51.5007, -0.1246).key)
        assertEquals("17/120591/78650", Tile.of(-33.8568, 151.2153).key)
    }

    @Test
    fun `a point lies inside its tile bounds`() {
        val b = Tile.of(-6.2, 106.8).bounds()
        assertTrue(-6.2 <= b.north && -6.2 >= b.south && 106.8 >= b.west && 106.8 <= b.east)
    }

    @Test
    fun `parses keys and rejects bad ones`() {
        assertEquals(Tile(17, 1, 2), Tile.parse("17/1/2"))
        assertNull(Tile.parse("2/9/9"))
        assertNull(Tile.parse("a/b/c"))
    }

    @Test
    fun `haversine distance is sane`() {
        val d = Geo.distanceM(LatLon(-6.175392, 106.827153), LatLon(-6.170166, 106.831375))
        assertTrue(d in 600.0..800.0)
    }
}
