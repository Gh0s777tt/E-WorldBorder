package com.wimbli.WorldBorder

import org.bukkit.World
import org.bukkit.entity.Player
import java.io.EOFException
import java.io.File
import java.io.FileFilter
import java.io.FileNotFoundException
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.util.Collections

// This region file handler is based on the documented region file format:
// http://mojang.com/2011/02/16/minecraft-save-file-format-in-beta-1-3/
class WorldFileData private constructor(
    private val world: World,
    private val notifyPlayer: Player?,
) {
    private var regionFolder: File? = null
    private var regionFiles: Array<File> = emptyArray()

    // region coordinates -> file, built once so region lookups are O(1) instead of a linear scan
    private var regionFileByCoord: Map<CoordXZ, File> = emptyMap()
    private val regionChunkExistence: MutableMap<CoordXZ, MutableList<Boolean>> =
        Collections.synchronizedMap(HashMap())

    // number of region files this world has
    fun regionFileCount(): Int = regionFiles.size

    // folder where the world's region files are located
    fun regionFolder(): File? = regionFolder

    // return the entire list of region files
    fun regionFiles(): Array<File> = regionFiles.copyOf()

    // return a region file by index
    fun regionFile(index: Int): File? {
        if (regionFiles.size < index) return null
        return regionFiles[index]
    }

    // get the X and Z world coordinates of the region from the filename
    fun regionFileCoordinates(index: Int): CoordXZ? {
        val regionFile = this.regionFile(index) ?: return null
        val coords = regionFile.name.split("\\.".toRegex()).toTypedArray()
        return try {
            CoordXZ(coords[1].toInt(), coords[2].toInt())
        } catch (ex: Exception) {
            sendMessage("Error! Region file found with abnormal name: ${regionFile.name}")
            null
        }
    }

    // Find out if the chunk at the given coordinates exists.
    fun doesChunkExist(x: Int, z: Int): Boolean {
        val region = CoordXZ(CoordXZ.chunkToRegion(x), CoordXZ.chunkToRegion(z))
        val regionChunks = getRegionData(region)
        return regionChunks[coordToRegionOffset(x, z)]
    }

    // Find out if the chunk at the given coordinates has been fully generated.
    // Minecraft only fully generates a chunk when adjacent chunks are also loaded.
    fun isChunkFullyGenerated(x: Int, z: Int): Boolean {
        // For 1.13+, due to world gen changes, this is effectively a 3-chunk-radius requirement
        for (xx in x - 3..x + 3) {
            for (zz in z - 3..z + 3) {
                if (!doesChunkExist(xx, zz)) return false
            }
        }
        return true
    }

    // Let us know a chunk has been generated, to update our region map.
    fun chunkExistsNow(x: Int, z: Int) {
        val region = CoordXZ(CoordXZ.chunkToRegion(x), CoordXZ.chunkToRegion(z))
        val regionChunks = getRegionData(region)
        regionChunks[coordToRegionOffset(x, z)] = true
    }

    // region is 32x32 chunks; chunk pointers are stored at position: x + z*32 (1024 total).
    // input x/z can be world-based chunk coordinates or local-to-region either one
    private fun coordToRegionOffset(xIn: Int, zIn: Int): Int {
        var x = xIn % 32
        var z = zIn % 32
        // wrap negative values around for local coordinates
        if (x < 0) x += 32
        if (z < 0) z += 32
        return x + (z * 32)
    }

    private fun getRegionData(region: CoordXZ): MutableList<Boolean> {
        regionChunkExistence[region]?.let { return it }

        // data for this region isn't loaded yet; init it as empty and try to load the data from disk
        val data = ArrayList<Boolean>(1024)
        for (i in 0 until 1024) data.add(false)

        val regionFile = regionFileByCoord[region]
        if (regionFile != null) {
            try {
                RandomAccessFile(regionFile, "r").use { regionData ->
                    // ByteBuffer+IntBuffer header reading for performance, as suggested by aikar:
                    // https://github.com/PaperMC/Paper (Reduce-IO-ops-opening-a-new-region-file)
                    val header = ByteBuffer.allocate(8192)
                    while (header.hasRemaining()) {
                        if (regionData.channel.read(header) == -1) {
                            throw EOFException()
                        }
                    }
                    header.clear()
                    val headerAsInts = header.asIntBuffer()

                    // first 4096 bytes are 4-byte int pointers to chunk data (1024 chunks * 4 bytes)
                    for (j in 0 until 1024) {
                        // if chunk pointer data is 0, the chunk doesn't exist yet; otherwise it does
                        if (headerAsInts.get() != 0) data[j] = true
                    }
                    // Read timestamps
                    for (j in 0 until 1024) {
                        // if timestamp is zero, it is a protochunk (ignore it)
                        if (headerAsInts.get() == 0 && data[j]) data[j] = false
                    }
                }
            } catch (ex: FileNotFoundException) {
                sendMessage("Error! Could not open region file to find generated chunks: ${regionFile.name}")
            } catch (ex: IOException) {
                sendMessage("Error! Could not read region file to find generated chunks: ${regionFile.name}")
            }
        }
        regionChunkExistence[region] = data
        return data
    }

    // send a message to the server console/log and possibly to an in-game player
    private fun sendMessage(text: String) {
        Config.log("[WorldData] $text")
        if (notifyPlayer != null && notifyPlayer.isOnline) {
            notifyPlayer.msg("[WorldData] $text")
        }
    }

    // file filter used for region files
    private class ExtFileFilter(extension: String) : FileFilter {
        private val ext: String = extension.lowercase()
        override fun accept(file: File): Boolean = file.exists() && file.isFile && file.name.lowercase().endsWith(ext)
    }

    // file filter used for DIM* folders (nether, end, and custom world types)
    private class DimFolderFileFilter : FileFilter {
        override fun accept(file: File): Boolean = file.exists() && file.isDirectory && file.name.lowercase().startsWith("dim")
    }

    companion object {
        // Use this to create a new instance. If null is returned, there was a problem and any process
        // relying on this should be cancelled.
        fun create(world: World, notifyPlayer: Player?): WorldFileData? {
            val data = WorldFileData(world, notifyPlayer)

            var folder = File(world.worldFolder, "region")
            if (!folder.exists() || !folder.isDirectory) {
                // check for a region folder inside a DIM* folder (DIM-1 nether, DIM1 end, DIMx custom)
                val possibleDimFolders = world.worldFolder.listFiles(DimFolderFileFilter()) ?: emptyArray()
                for (possibleDimFolder in possibleDimFolders) {
                    val possible = File(world.worldFolder, possibleDimFolder.name + File.separator + "region")
                    if (possible.exists() && possible.isDirectory) {
                        folder = possible
                        break
                    }
                }
                if (!folder.exists() || !folder.isDirectory) {
                    data.sendMessage("Could not validate folder for world's region files. Looked in ${world.worldFolder.path} for a valid DIM* folder with a region folder in it.")
                    return null
                }
            }
            data.regionFolder = folder

            // Accepted region file formats: MCR (late beta - 1.1), MCA (1.2+)
            var files = folder.listFiles(ExtFileFilter(".MCA")) ?: emptyArray()
            if (files.isEmpty()) {
                files = folder.listFiles(ExtFileFilter(".MCR")) ?: emptyArray()
                if (files.isEmpty()) {
                    data.sendMessage("Could not find any region files. Looked in: ${folder.path}")
                    return null
                }
            }
            data.regionFiles = files

            // index region files by their parsed coordinates so getRegionData() is O(1)
            val byCoord = HashMap<CoordXZ, File>(files.size * 2)
            for (i in files.indices) {
                data.regionFileCoordinates(i)?.let { byCoord[it] = files[i] }
            }
            data.regionFileByCoord = byCoord

            return data
        }
    }
}
