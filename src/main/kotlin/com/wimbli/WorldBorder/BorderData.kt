package com.wimbli.WorldBorder

import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.Block
import java.util.EnumSet
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sqrt

class BorderData {
    // the main data interacted with
    var x: Double = 0.0
        set(value) {
            field = value
            maxX = value + radiusX
            minX = value - radiusX
        }
    var z: Double = 0.0
        set(value) {
            field = value
            maxZ = value + radiusZ
            minZ = value - radiusZ
        }
    var radiusX: Int = 0
        set(value) {
            field = value
            maxX = x + value
            minX = x - value
            radiusXSquared = value.toDouble() * value.toDouble()
            // FIX: guard against division by zero (radiusZ may legitimately be 0,
            // or simply not set yet during construction).
            radiusSquaredQuotient = if (radiusZSquared != 0.0) radiusXSquared / radiusZSquared else 0.0
            definiteRectangleX = sqrt(0.5 * radiusXSquared)
        }
    var radiusZ: Int = 0
        set(value) {
            field = value
            maxZ = z + value
            minZ = z - value
            radiusZSquared = value.toDouble() * value.toDouble()
            radiusSquaredQuotient = if (radiusZSquared != 0.0) radiusXSquared / radiusZSquared else 0.0
            definiteRectangleZ = sqrt(0.5 * radiusZSquared)
        }

    /** Per-world shape override; null means "use the global default". */
    var shape: Boolean? = null
    var wrapping: Boolean = false

    // some extra data kept handy for faster border checks
    private var maxX = 0.0
    private var minX = 0.0
    private var maxZ = 0.0
    private var minZ = 0.0
    private var radiusXSquared = 0.0
    private var radiusZSquared = 0.0
    private var definiteRectangleX = 0.0
    private var definiteRectangleZ = 0.0
    private var radiusSquaredQuotient = 0.0

    @JvmOverloads
    constructor(x: Double, z: Double, radiusX: Int, radiusZ: Int, shape: Boolean? = null, wrapping: Boolean = false) {
        setData(x, z, radiusX, radiusZ, shape, wrapping)
    }

    constructor(x: Double, z: Double, radius: Int) : this(x, z, radius, radius)
    constructor(x: Double, z: Double, radius: Int, shape: Boolean?) : this(x, z, radius, radius, shape)

    @JvmOverloads
    fun setData(x: Double, z: Double, radiusX: Int, radiusZ: Int, shape: Boolean? = null, wrapping: Boolean = false) {
        this.shape = shape
        this.wrapping = wrapping
        // Set radiusZ before radiusX so radiusSquaredQuotient is computed with a
        // valid divisor, then x/z so min/max are derived from the final radii.
        this.radiusZ = radiusZ
        this.radiusX = radiusX
        this.x = x
        this.z = z
    }

    fun copy(): BorderData = BorderData(x, z, radiusX, radiusZ, shape, wrapping)

    // backwards-compatible average-radius accessors from before elliptical borders existed
    @Deprecated("Replaced by radiusX / radiusZ; returns an imprecise average.")
    fun getRadius(): Int = (radiusX + radiusZ) / 2

    fun setRadius(radius: Int) {
        radiusX = radius
        radiusZ = radius
    }

    override fun toString(): String {
        val radiusPart = if (radiusX == radiusZ) radiusX.toString() else "${radiusX}x$radiusZ"
        val shapePart = shape?.let { " (shape override: ${Config.shapeName(it)})" } ?: ""
        val wrapPart = if (wrapping) " (wrapping)" else ""
        return "radius $radiusPart at X: ${Config.coord.format(x)} Z: ${Config.coord.format(z)}$shapePart$wrapPart"
    }

    // This algorithm of course needs to be fast, since it will be run very frequently.
    fun insideBorder(xLoc: Double, zLoc: Double, round: Boolean): Boolean {
        // if this border has a shape override set, use it
        val isRound = shape ?: round

        // square border
        if (!isRound)
            return !(xLoc < minX || xLoc > maxX || zLoc < minZ || zLoc > maxZ)

        // round border
        // elegant round border checking algorithm is from rBorder by Reil, all credit to him for it
        val xDist = abs(x - xLoc)
        val zDist = abs(z - zLoc)

        return when {
            xDist < definiteRectangleX && zDist < definiteRectangleZ -> true   // definitely inside
            xDist >= radiusX || zDist >= radiusZ -> false                      // definitely outside
            xDist * xDist + zDist * zDist * radiusSquaredQuotient < radiusXSquared -> true // after further calc, inside
            else -> false                                                      // apparently outside
        }
    }

    fun insideBorder(xLoc: Double, zLoc: Double): Boolean = insideBorder(xLoc, zLoc, Config.shapeRound)
    fun insideBorder(loc: Location): Boolean = insideBorder(loc.x, loc.z, Config.shapeRound)
    fun insideBorder(coord: CoordXZ, round: Boolean): Boolean = insideBorder(coord.x.toDouble(), coord.z.toDouble(), round)
    fun insideBorder(coord: CoordXZ): Boolean = insideBorder(coord.x.toDouble(), coord.z.toDouble(), Config.shapeRound)

    @JvmOverloads
    fun correctedPosition(loc: Location, round: Boolean = Config.shapeRound, flying: Boolean = false): Location? {
        // if this border has a shape override set, use it
        val isRound = shape ?: round

        var xLoc = loc.x
        var zLoc = loc.z
        val yLoc = loc.y
        val knockBack = Config.knockBack

        // square border
        if (!isRound) {
            if (wrapping) {
                if (xLoc <= minX) xLoc = maxX - knockBack else if (xLoc >= maxX) xLoc = minX + knockBack
                if (zLoc <= minZ) zLoc = maxZ - knockBack else if (zLoc >= maxZ) zLoc = minZ + knockBack
            } else {
                if (xLoc <= minX) xLoc = minX + knockBack else if (xLoc >= maxX) xLoc = maxX - knockBack
                if (zLoc <= minZ) zLoc = minZ + knockBack else if (zLoc >= maxZ) zLoc = maxZ - knockBack
            }
        }
        // round border
        else {
            // algorithm from http://stackoverflow.com/questions/300871, modified by Lang Lukas for elliptical borders.
            // Transform the ellipse to a circle with radius 1 (transform the point the same way).
            val dX = xLoc - x
            val dZ = zLoc - z
            val dU = sqrt(dX * dX + dZ * dZ)                                  // distance of the untransformed point from the center
            val dT = sqrt(dX * dX / radiusXSquared + dZ * dZ / radiusZSquared) // distance of the transformed point from the center
            val f = (1 / dT - knockBack / dU)                                // "correction" factor for the distances
            if (wrapping) {
                xLoc = x - dX * f
                zLoc = z - dZ * f
            } else {
                xLoc = x + dX * f
                zLoc = z + dZ * f
            }
        }

        val ixLoc = Location.locToBlock(xLoc)
        val izLoc = Location.locToBlock(zLoc)

        // Make sure the chunk we're checking in is actually loaded
        val tChunk = loc.world.getChunkAt(CoordXZ.blockToChunk(ixLoc), CoordXZ.blockToChunk(izLoc))
        if (!tChunk.isLoaded) tChunk.load()

        val safeY = getSafeY(loc.world, ixLoc, Location.locToBlock(yLoc), izLoc, flying)
        if (safeY == -1.0) return null

        return Location(loc.world, floor(xLoc) + 0.5, safeY, floor(zLoc) + 0.5, loc.yaw, loc.pitch)
    }

    // Check if a particular spot consists of 2 breathable blocks over something relatively solid.
    private fun isSafeSpot(world: World, x: Int, y: Int, z: Int, flying: Boolean): Boolean {
        val safe = isSafeOpenBlock(world.getBlockAt(x, y, z))            // target block open and safe
            && isSafeOpenBlock(world.getBlockAt(x, y + 1, z))           // above target block open and safe
        if (!safe || flying) return safe

        val below = world.getBlockAt(x, y - 1, z)
        return (!below.isPassable || below.type == Material.WATER)      // below not passable (so solid), or is water
            && !painfulBlocks.contains(below.type)                     // and not painful to land on
    }

    // find closest safe Y position from the starting position
    private fun getSafeY(world: World, x: Int, yStart: Int, z: Int, flying: Boolean): Double {
        // Artificial height limit for Nether worlds since the highest block is the bedrock roof,
        // which we don't want to send players onto. The Nether roof sits at y=127 in all versions.
        val isNether = world.environment == World.Environment.NETHER
        val limBot = world.minHeight
        var limTop = if (isNether) min(world.maxHeight, 125) else world.maxHeight - 2
        val highestBlockBoundary = min(world.getHighestBlockYAt(x, z) + 1, limTop)

        var y = yStart

        // if Y is larger than the world can be and the player can fly, return Y - unless we're in the Nether (no roof)
        if (flying && y > limTop && !isNether)
            return y.toDouble()

        // make sure Y values are within the boundaries of the world
        if (y > limTop) {
            y = if (isNether) {
                limTop // because of the roof, the Nether can't rely on highestBlockBoundary
            } else {
                if (flying) limTop else highestBlockBoundary // no safe block to stand on above highestBlockBoundary
            }
        }
        if (y < limBot) y = limBot

        // for non-Nether non-flying we only need to check up to highestBlockBoundary
        if (!isNether && !flying)
            limTop = highestBlockBoundary

        // Expanding Y search method adapted from Acru's code in the Nether plugin
        var y1 = y
        var y2 = y
        while (y1 > limBot || y2 < limTop) {
            // Look below.
            if (y1 > limBot && isSafeSpot(world, x, y1, z, flying))
                return y1.toDouble()
            // Look above.
            if (y2 < limTop && y2 != y1 && isSafeSpot(world, x, y2, z, flying))
                return y2.toDouble()
            y1--
            y2++
        }

        return -1.0 // no safe Y location
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || other.javaClass != this.javaClass) return false
        other as BorderData
        return other.x == x && other.z == z && other.radiusX == radiusX && other.radiusZ == radiusZ
    }

    override fun hashCode(): Int =
        ((x * 10).toInt() shl 4) + z.toInt() + (radiusX shl 2) + (radiusZ shl 3)

    companion object {
        // Blocks we never want to drop a player onto (or stand in), like lava/fire/cactus/etc.
        // Modernized for 1.21: includes hazards that didn't exist when the original list was written.
        private val painfulBlocks: EnumSet<Material> = EnumSet.of(
            Material.LAVA,
            Material.FIRE,
            Material.SOUL_FIRE,
            Material.CACTUS,
            Material.MAGMA_BLOCK,
            Material.END_PORTAL,
            Material.POWDER_SNOW,
            Material.WITHER_ROSE,
            Material.SWEET_BERRY_BUSH
        )

        /**
         * Version-proof replacement for the old hand-maintained "safe open blocks" enum list:
         * any non-occluding, passable block the player can breathe in, except known hazards.
         * Using Block.isPassable() means new passable blocks in future MC versions are handled
         * automatically instead of needing the list updated by hand.
         */
        private fun isSafeOpenBlock(block: Block): Boolean =
            block.isPassable && !painfulBlocks.contains(block.type)
    }
}
