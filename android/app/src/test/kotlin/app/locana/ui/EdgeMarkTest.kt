package app.locana.ui

import app.locana.ui.map.edgeMarkFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class EdgeMarkTest {
    // A 1000 x 2000 map whose visible band runs from y = 200 to y = 1500.
    private fun mark(x: Float, y: Float) = edgeMarkFor(x, y, 0f, 200f, 1000f, 1500f, inset = 50f)

    @Test fun `a point inside the visible area needs no mark`() = assertNull(mark(500f, 800f))

    @Test fun `a point far to the left sits on the left edge and points left`() {
        val m = mark(-900f, 850f)!!
        assertEquals(50f, m.x, 0.5f)
        assertEquals(850f, m.y, 1f)
        assertEquals(-90f, m.angleDeg, 1f)
    }

    @Test fun `a point above points up and stays inside the band`() {
        val m = mark(500f, -2000f)!!
        assertEquals(0f, m.angleDeg, 1f)
        assertEquals(250f, m.y, 1f)
    }

    @Test fun `a point below and to the right lands in the lower right corner area`() {
        val m = mark(3000f, 3000f)!!
        assertNotNull(m)
        assert(m.x in 0f..1000f && m.y in 200f..1500f)
        assert(m.angleDeg in 90f..180f)
    }
}
