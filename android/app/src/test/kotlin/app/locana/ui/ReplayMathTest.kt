package app.locana.ui

import app.locana.ui.timeline.routeAt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ReplayMathTest {
    private val path = listOf(-6.2 to 106.8, -6.2 to 106.81, -6.21 to 106.81)
    private val times = listOf(1_000L, 2_000L, 4_000L)

    @Test fun `no route, no point`() = assertNull(routeAt(emptyList(), emptyList(), 0, 1, 0))

    @Test fun `halfway between two recorded points is halfway in space`() {
        val p = routeAt(path, times, 1_000, 4_000, 1_500)!!
        assertEquals(-6.2, p.lat, 1e-9)
        assertEquals(106.805, p.lon, 1e-9)
        assertEquals(0, p.index)
    }

    @Test fun `time inside a slower stretch moves proportionally`() {
        val p = routeAt(path, times, 1_000, 4_000, 3_000)!! // halfway through the 2 s second leg
        assertEquals(-6.205, p.lat, 1e-9)
        assertEquals(1, p.index)
    }

    @Test fun `times outside the trip clamp to its ends`() {
        assertEquals(-6.2, routeAt(path, times, 1_000, 4_000, -50)!!.lat, 1e-9)
        val end = routeAt(path, times, 1_000, 4_000, 99_000)!!
        assertEquals(-6.21, end.lat, 1e-9)
        assertNotNull(end)
    }

    @Test fun `without times the route is spread evenly`() {
        val p = routeAt(path, emptyList(), 0, 2_000, 1_000)!!
        assertEquals(106.81, p.lon, 1e-9)
        assertEquals(-6.2, p.lat, 1e-9)
    }

    @Test fun `metres from the start grow along the route`() {
        val early = routeAt(path, times, 1_000, 4_000, 1_500)!!.metresFromStart
        val late = routeAt(path, times, 1_000, 4_000, 3_500)!!.metresFromStart
        assert(late > early && early > 0)
    }
}
