package com.wimbli.WorldBorder

/** Simple storage class for chunk x/z values. */
class CoordXZ(@JvmField var x: Int, @JvmField var z: Int) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CoordXZ) return false
        return x == other.x && z == other.z
    }

    override fun hashCode(): Int = (x shl 9) + z

    override fun toString(): String = "($x, $z)"

    companion object {
        // Transform values between block, chunk, and region.
        // Bit-shifting is used because it's mucho rapido.
        fun blockToChunk(blockVal: Int): Int = blockVal shr 4 // ">>4" == "/16"  (1 chunk = 16x16 blocks)
        fun blockToRegion(blockVal: Int): Int = blockVal shr 9 // ">>9" == "/512" (1 region = 512x512 blocks)
        fun chunkToRegion(chunkVal: Int): Int = chunkVal shr 5 // ">>5" == "/32"  (1 region = 32x32 chunks)
        fun chunkToBlock(chunkVal: Int): Int = chunkVal shl 4 // "<<4" == "*16"
        fun regionToBlock(regionVal: Int): Int = regionVal shl 9 // "<<9" == "*512"
        fun regionToChunk(regionVal: Int): Int = regionVal shl 5 // "<<5" == "*32"
    }
}
