package com.wimbli.WorldBorder

import com.wimbli.WorldBorder.Events.WorldBorderTrimFinishedEvent
import com.wimbli.WorldBorder.Events.WorldBorderTrimStartEvent
import org.bukkit.Bukkit
import org.bukkit.Server
import org.bukkit.World
import org.bukkit.entity.Player
import java.io.FileNotFoundException
import java.io.IOException
import java.io.RandomAccessFile

class WorldTrimTask(
    server: Server?,
    private val notifyPlayer: Player?,
    worldName: String,
    trimDistance: Int,
    private val chunksPerRun: Int
) : Runnable {
    // general task-related reference data
    private var server: Server? = server
    private var world: World? = null
    private var worldData: WorldFileData? = null
    private var border: BorderData? = null
    private var readyToGo = false
    private var paused = false
    private var taskID = -1

    // values for which chunk in the current region we're at
    private var currentRegion = -1            // region(file) we're at in regionFiles
    private var regionX = 0                    // X location value of the current region
    private var regionZ = 0                    // Z location value of the current region
    private var currentChunk = 0              // chunk we've reached in the current region
    private var regionChunks: MutableList<CoordXZ> = ArrayList(1024)
    private var trimChunks: MutableList<CoordXZ> = ArrayList(1024)
    private var counter = 0

    // for reporting progress back to the user occasionally
    private var lastReport = Config.now()
    private var reportTarget = 0
    private var reportTotal = 0
    private var reportTrimmedRegions = 0
    private var reportTrimmedChunks = 0

    init {
        val srv = server
        val w = srv?.getWorld(worldName)
        if (srv == null || w == null) {
            if (worldName.isEmpty()) sendMessage("You must specify a world!")
            else sendMessage("World \"$worldName\" not found!")
            stop()
        } else {
            world = w
            val existing = Config.border(worldName)
            if (existing == null) {
                sendMessage("No border found for world \"$worldName\"!")
                stop()
            } else {
                val bord = existing.copy()
                border = bord
                bord.radiusX = bord.radiusX + trimDistance
                bord.radiusZ = bord.radiusZ + trimDistance

                val wd = WorldFileData.create(w, notifyPlayer)
                if (wd == null) {
                    stop()
                } else {
                    worldData = wd

                    // each region file covers up to 1024 chunks; allow 3X for all the operations we might do
                    reportTarget = wd.regionFileCount() * 3072

                    // queue up the first file
                    if (nextFile()) {
                        readyToGo = true
                        Bukkit.getServer().pluginManager.callEvent(WorldBorderTrimStartEvent(this))
                    }
                }
            }
        }
    }

    fun setTaskID(id: Int) {
        this.taskID = id
    }

    override fun run() {
        if (server == null || !readyToGo || paused) return

        val world = this.world ?: return

        // only do one iteration at a time, no matter how frequently the timer fires
        readyToGo = false
        // keep one iteration from dragging on too long if the user specified a really high frequency
        val loopStartTime = Config.now()

        counter = 0
        while (counter <= chunksPerRun) {
            // in case the task has been paused while we're repeating...
            if (paused) return

            val now = Config.now()

            // every 5 seconds or so, give a basic progress report
            if (now > lastReport + 5000) reportProgress()

            // if this iteration has been running 45ms (almost 1 tick) or more, stop to take a breather
            if (now > loopStartTime + 45) {
                readyToGo = true
                return
            }

            if (regionChunks.isEmpty()) {
                addCornerChunks()
            } else if (currentChunk == 4) {
                // determine if region is completely _inside_ the border based on corner chunks
                if (trimChunks.isEmpty()) {
                    // it is, so skip it and move on to the next file
                    counter += 4
                    nextFile()
                    continue
                }
                addEdgeChunks()
                addInnerChunks()
            } else if (currentChunk == 124 && trimChunks.size == 124) {
                // region is completely _outside_ the border based on edge chunks, so delete file and move on
                counter += 16
                trimChunks = regionChunks
                unloadChunks()
                reportTrimmedRegions++
                val regionFile = worldData!!.regionFile(currentRegion)
                if (regionFile != null && !regionFile.delete()) {
                    sendMessage("Error! Region file which is outside the border could not be deleted: ${regionFile.name}")
                    wipeChunks()
                }
                nextFile()
                continue
            } else if (currentChunk == 1024) {
                // last chunk of the region has been checked, time to wipe whichever chunks are outside the border
                counter += 32
                unloadChunks()
                wipeChunks()
                nextFile()
                continue
            }

            // check whether the chunk is inside the border, add it to the "trim" list if not
            val chunk = regionChunks[currentChunk]
            if (!isChunkInsideBorder(chunk))
                trimChunks.add(chunk)

            currentChunk++
            counter++
        }

        reportTotal += counter

        // ready for the next iteration to run
        readyToGo = true
    }

    // Advance to the next region file. Returns true if successful, false if the next file isn't accessible
    private fun nextFile(): Boolean {
        val worldData = this.worldData ?: return false
        reportTotal = currentRegion * 3072
        currentRegion++
        regionZ = 0
        regionX = regionZ
        currentChunk = 0
        regionChunks = ArrayList(1024)
        trimChunks = ArrayList(1024)

        // have we already handled all region files?
        if (currentRegion >= worldData.regionFileCount()) {
            // hey, we're done
            paused = true
            readyToGo = false
            finish()
            return false
        }

        counter += 16

        // get the X and Z coordinates of the current region
        val coord = worldData.regionFileCoordinates(currentRegion) ?: return false

        regionX = coord.x
        regionZ = coord.z
        return true
    }

    // add just the 4 corner chunks of the region; can determine if the entire region is _inside_ the border
    private fun addCornerChunks() {
        regionChunks.add(CoordXZ(CoordXZ.regionToChunk(regionX), CoordXZ.regionToChunk(regionZ)))
        regionChunks.add(CoordXZ(CoordXZ.regionToChunk(regionX) + 31, CoordXZ.regionToChunk(regionZ)))
        regionChunks.add(CoordXZ(CoordXZ.regionToChunk(regionX), CoordXZ.regionToChunk(regionZ) + 31))
        regionChunks.add(CoordXZ(CoordXZ.regionToChunk(regionX) + 31, CoordXZ.regionToChunk(regionZ) + 31))
    }

    // add all chunks along the 4 edges of the region (minus the corners); can determine if region is _outside_ border
    private fun addEdgeChunks() {
        var chunkX = 0
        var chunkZ: Int

        chunkZ = 1
        while (chunkZ < 31) {
            regionChunks.add(CoordXZ(CoordXZ.regionToChunk(regionX) + chunkX, CoordXZ.regionToChunk(regionZ) + chunkZ))
            chunkZ++
        }
        chunkX = 31
        chunkZ = 1
        while (chunkZ < 31) {
            regionChunks.add(CoordXZ(CoordXZ.regionToChunk(regionX) + chunkX, CoordXZ.regionToChunk(regionZ) + chunkZ))
            chunkZ++
        }
        chunkZ = 0
        chunkX = 1
        while (chunkX < 31) {
            regionChunks.add(CoordXZ(CoordXZ.regionToChunk(regionX) + chunkX, CoordXZ.regionToChunk(regionZ) + chunkZ))
            chunkX++
        }
        chunkZ = 31
        chunkX = 1
        while (chunkX < 31) {
            regionChunks.add(CoordXZ(CoordXZ.regionToChunk(regionX) + chunkX, CoordXZ.regionToChunk(regionZ) + chunkZ))
            chunkX++
        }
        counter += 4
    }

    // add the remaining interior chunks (after corners and edges)
    private fun addInnerChunks() {
        for (chunkX in 1 until 31) {
            for (chunkZ in 1 until 31) {
                regionChunks.add(CoordXZ(CoordXZ.regionToChunk(regionX) + chunkX, CoordXZ.regionToChunk(regionZ) + chunkZ))
            }
        }
        counter += 32
    }

    // make sure chunks set to be trimmed are not currently loaded by the server
    private fun unloadChunks() {
        val world = this.world ?: return
        for (unload in trimChunks) {
            if (world.isChunkLoaded(unload.x, unload.z))
                world.unloadChunk(unload.x, unload.z, false)
        }
        counter += trimChunks.size
    }

    // edit region file to wipe all chunk pointers for chunks outside the border
    private fun wipeChunks() {
        val regionFile = worldData!!.regionFile(currentRegion) ?: return
        if (!regionFile.canWrite()) {
            if (!regionFile.setWritable(true))
                throw RuntimeException()

            if (!regionFile.canWrite()) {
                sendMessage("Error! region file is locked and can't be trimmed: ${regionFile.name}")
                return
            }
        }

        // our stored chunk positions are world-based, so offset them to positions in the region file
        val offsetX = CoordXZ.regionToChunk(regionX)
        val offsetZ = CoordXZ.regionToChunk(regionZ)
        var chunkCount = 0

        try {
            RandomAccessFile(regionFile, "rwd").use { unChunk ->
                for (wipe in trimChunks) {
                    // if the chunk pointer is already empty, no need to wipe it
                    if (!worldData!!.doesChunkExist(wipe.x, wipe.z)) continue

                    // wipe this extraneous chunk's pointer; the chunk data is left orphaned, but Minecraft
                    // overwrites the orphaned sector when a new chunk is created in the region
                    val wipePos = (4 * ((wipe.x - offsetX) + ((wipe.z - offsetZ) * 32))).toLong()
                    unChunk.seek(wipePos)
                    unChunk.writeInt(0)
                    chunkCount++
                }
            }
            reportTrimmedChunks += chunkCount
        } catch (ex: FileNotFoundException) {
            sendMessage("Error! Could not open region file to wipe individual chunks: ${regionFile.name}")
        } catch (ex: IOException) {
            sendMessage("Error! Could not modify region file to wipe individual chunks: ${regionFile.name}")
        }
        counter += trimChunks.size
    }

    private fun isChunkInsideBorder(chunk: CoordXZ): Boolean =
        border!!.insideBorder((CoordXZ.chunkToBlock(chunk.x) + 8).toDouble(), (CoordXZ.chunkToBlock(chunk.z) + 8).toDouble())

    // for successful completion
    fun finish() {
        reportTotal = reportTarget
        reportProgress()
        world?.let { Bukkit.getServer().pluginManager.callEvent(WorldBorderTrimFinishedEvent(it, reportTotal.toLong())) }
        sendMessage("task successfully completed!")
        stop()
    }

    // for cancelling prematurely
    fun cancel() = stop()

    // we're done, whether finished or cancelled
    private fun stop() {
        if (server == null) return

        readyToGo = false
        if (taskID != -1)
            server!!.scheduler.cancelTask(taskID)
        server = null

        sendMessage("NOTICE: it is recommended that you restart your server after a Trim, to be on the safe side.")
        if (DynMapFeatures.renderEnabled())
            sendMessage("This is especially true with DynMap. You should also run a fullrender in DynMap for the trimmed world after restarting, so trimmed chunks are updated on the map.")
    }

    // is this task still valid/workable?
    fun valid(): Boolean = server != null

    // handle pausing/unpausing the task
    fun pause() = pause(!paused)

    fun pause(pause: Boolean) {
        this.paused = pause
        if (pause) reportProgress()
    }

    fun isPaused(): Boolean = paused

    // let the user know how things are coming along
    private fun reportProgress() {
        lastReport = Config.now()
        val perc = getPercentageCompleted()
        sendMessage("$reportTrimmedRegions entire region(s) and $reportTrimmedChunks individual chunk(s) trimmed so far (" + Config.coord.format(perc) + "% done)")
    }

    // send a message to the server console/log and possibly to an in-game player
    private fun sendMessage(text: String) {
        Config.log("[Trim] $text")
        notifyPlayer?.msg("[Trim] $text")
    }

    /** Percentage completed for the trim task. */
    fun getPercentageCompleted(): Double = reportTotal.toDouble() / reportTarget.toDouble() * 100

    /** Number of chunks processed so far. */
    fun getChunksCompleted(): Int = reportTotal

    /** Total number of chunks that need to be trimmed. */
    fun getChunksTotal(): Int = reportTarget
}
