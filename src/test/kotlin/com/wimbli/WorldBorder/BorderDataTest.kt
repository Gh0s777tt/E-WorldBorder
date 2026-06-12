package com.wimbli.WorldBorder

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pure-geometry tests for [BorderData]. They don't touch Bukkit/Server state — only the border math
 * (insideBorder for square/elliptic shapes, the radius-derived values, and the div-by-zero fix).
 */
class BorderDataTest {

    @Test
    fun `square border contains interior, edge and rejects outside`() {
        val b = BorderData(0.0, 0.0, 100, 100, shape = false)
        assertTrue(b.insideBorder(0.0, 0.0, false), "center")
        assertTrue(b.insideBorder(99.0, 99.0, false), "interior corner")
        assertTrue(b.insideBorder(-100.0, -100.0, false), "on the min edge counts as inside")
        assertFalse(b.insideBorder(101.0, 0.0, false), "past +X")
        assertFalse(b.insideBorder(0.0, -100.1, false), "past -Z")
    }

    @Test
    fun `round border behaves like a circle`() {
        val b = BorderData(0.0, 0.0, 100, 100, shape = true)
        assertTrue(b.insideBorder(0.0, 0.0, true))
        assertTrue(b.insideBorder(70.0, 70.0, true), "dist ~99 is inside r=100")
        assertFalse(b.insideBorder(80.0, 80.0, true), "dist ~113 is outside r=100")
        assertFalse(b.insideBorder(101.0, 0.0, true))
    }

    @Test
    fun `elliptic border respects independent radii`() {
        val b = BorderData(0.0, 0.0, 200, 100, shape = true) // rx=200, rz=100
        assertTrue(b.insideBorder(150.0, 0.0, true), "within X radius")
        assertTrue(b.insideBorder(0.0, 99.0, true), "within Z radius")
        assertFalse(b.insideBorder(0.0, 150.0, true), "beyond Z radius")
    }

    @Test
    fun `per-border shape override wins over the passed default`() {
        val square = BorderData(0.0, 0.0, 100, 100, shape = false)
        // (99,99) is inside a square but well outside a circle of r=100; the override must keep it inside
        assertTrue(square.insideBorder(99.0, 99.0, true))
    }

    @Test
    fun `degenerate zero Z radius does not throw`() {
        // guards the div-by-zero handling in setRadius*: it must not throw or produce a NaN-driven result.
        // A zero Z-radius round border has no interior along Z, so every point is simply "outside" (no crash).
        val b = BorderData(0.0, 0.0, 100, 0, shape = true)
        assertFalse(b.insideBorder(0.0, 50.0, true))
        assertFalse(b.insideBorder(0.0, 0.0, true))
    }

    @Test
    fun `radii and center are stored correctly`() {
        val b = BorderData(10.0, 20.0, 50, 30)
        assertEquals(50, b.radiusX)
        assertEquals(30, b.radiusZ)
        assertEquals(10.0, b.x)
        assertEquals(20.0, b.z)
    }

    @Test
    fun `copy is equal to the original`() {
        val b = BorderData(1.0, 2.0, 30, 40, shape = true, wrapping = true)
        val c = b.copy()
        assertEquals(b, c)
        assertEquals(b.hashCode(), c.hashCode())
        assertTrue(c.wrapping)
        assertEquals(true, c.shape)
    }

    @Test
    fun `changing radius updates containment`() {
        val b = BorderData(0.0, 0.0, 10, 10, shape = false)
        assertFalse(b.insideBorder(50.0, 0.0, false))
        b.radiusX = 100
        assertTrue(b.insideBorder(50.0, 0.0, false))
    }
}
