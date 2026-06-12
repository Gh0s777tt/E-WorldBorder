package com.wimbli.WorldBorder

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class CoordXZTest {

    @Test
    fun `block - chunk - region transforms`() {
        assertEquals(0, CoordXZ.blockToChunk(15))
        assertEquals(1, CoordXZ.blockToChunk(16))
        assertEquals(-1, CoordXZ.blockToChunk(-1), "block -1 is in chunk -1")
        assertEquals(16, CoordXZ.chunkToBlock(1))
        assertEquals(0, CoordXZ.chunkToRegion(31))
        assertEquals(1, CoordXZ.chunkToRegion(32))
        assertEquals(32, CoordXZ.regionToChunk(1))
        assertEquals(512, CoordXZ.regionToBlock(1))
        assertEquals(0, CoordXZ.blockToRegion(511))
        assertEquals(1, CoordXZ.blockToRegion(512))
    }

    @Test
    fun `equality and hashCode`() {
        val a = CoordXZ(3, 7)
        val b = CoordXZ(3, 7)
        val c = CoordXZ(7, 3)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, c)
    }
}
