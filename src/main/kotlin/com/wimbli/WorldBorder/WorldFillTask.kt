package com.wimbli.WorldBorder

import com.wimbli.WorldBorder.Events.WorldBorderFillFinishedEvent
import com.wimbli.WorldBorder.Events.WorldBorderFillStartEvent
import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import org.bukkit.Bukkit
import org.bukkit.Server
import org.bukkit.World
import org.bukkit.entity.Player
import java.util.concurrent.CompletableFuture
import kotlin.math.ceil

class WorldFillTask(
    server: Server?,
    private val notifyPlayer: Player?,
    worldName: String,
    fillDistance: Int,
    chunksPerRun: Int,
    tickFrequency: Int,
    forceLoad: Boolean = false,
) : Runnable {
    // general task-related reference data
    private var server: Server? = server
    var world: World? = null
        private set
    private var border: BorderData? = null
    private var worldData: WorldFileData? = null
    private var readyToGo = false
    private var paused = false
    private var pausedForMemory = false
    private var scheduledTask: ScheduledTask? = null
    private var continueNotice = false

    private val fillDistance = fillDistance
    private val chunksPerRun = chunksPerRun
    private val tickFrequency = tickFrequency
    private val forceLoad = forceLoad

    // these are only stored for saving the task to config
    var refX = 0
        private set
    private var lastLegX = 0
    var refZ = 0
        private set
    private var lastLegZ = 0
    var refLength = -1
        private set
    var refTotal = 0
        private set
    private var lastLegTotal = 0

    // values for the spiral pattern check which fills out the map to the border
    private var x = 0
    private var z = 0
    private var isZLeg = false
    private var isNeg = false
    private var length = -1
    private var current = 0
    private var insideBorder = true
    private var lastChunk = CoordXZ(0, 0)

    // for reporting progress back to the user occasionally
    private var lastReport = Config.now()
    private var lastAutosave = Config.now()
    private var reportTarget = 0
    private var reportTotal = 0
    private var reportNum = 0

    // chunks queued for async load, and their coordinates
    private var pendingChunks: MutableMap<CompletableFuture<Void>, CoordXZ> = HashMap()

    // "Chunk a needed for Chunk b" dependencies (a chunk may be needed for several others)
    private var preventUnload: MutableSet<UnloadDependency>? = HashSet()

    // config getters used when storing this task to config
    val refWorld: String get() = world!!.name
    val refFillDistance: Int get() = fillDistance
    val refChunksPerRun: Int get() = chunksPerRun
    val refTickFrequency: Int get() = tickFrequency
    val refForceLoad: Boolean get() = forceLoad

    private class UnloadDependency(val neededX: Int, val neededZ: Int, val forX: Int, val forZ: Int) {
        override fun equals(other: Any?): Boolean {
            if (other !is UnloadDependency) return false
            return neededX == other.neededX && neededZ == other.neededZ && forX == other.forX && forZ == other.forZ
        }

        override fun hashCode(): Int {
            var hash = 7
            hash = 79 * hash + neededX
            hash = 79 * hash + neededZ
            hash = 79 * hash + forX
            hash = 79 * hash + forZ
            return hash
        }
    }

    init {
        val srv = server
        val w = srv?.getWorld(worldName)
        if (srv == null || w == null) {
            if (worldName.isEmpty()) {
                sendMessage("You must specify a world!")
            } else {
                sendMessage("World \"$worldName\" not found!")
            }
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

                // load up world file data, used to scan region files for already-generated chunks
                val wd = WorldFileData.create(w, notifyPlayer)
                if (wd == null) {
                    stop()
                } else {
                    worldData = wd

                    bord.radiusX = bord.radiusX + fillDistance
                    bord.radiusZ = bord.radiusZ + fillDistance
                    x = CoordXZ.blockToChunk(bord.x.toInt())
                    z = CoordXZ.blockToChunk(bord.z.toInt())

                    val chunkWidthX = ceil(((bord.radiusX + 16) * 2).toDouble() / 16).toInt()
                    val chunkWidthZ = ceil(((bord.radiusZ + 16) * 2).toDouble() / 16).toInt()
                    // the spiral only stops once it reaches biggerWidth x biggerWidth
                    val biggerWidth = if (chunkWidthX > chunkWidthZ) chunkWidthX else chunkWidthZ
                    reportTarget = (biggerWidth * biggerWidth) + biggerWidth + 1

                    readyToGo = true
                    Bukkit.getServer().pluginManager.callEvent(WorldBorderFillStartEvent(this))
                }
            }
        }
    }

    fun setTask(task: ScheduledTask) {
        this.scheduledTask = task
    }

    override fun run() {
        if (continueNotice) {
            // notify the user the task has continued automatically
            continueNotice = false
            sendMessage("World map generation task automatically continuing.")
            sendMessage("Reminder: you can cancel at any time with \"wb fill cancel\", or pause/unpause with \"wb fill pause\".")
        }

        if (pausedForMemory) {
            // we auto-pause when memory gets too low
            if (Config.availableMemoryTooLow()) return
            pausedForMemory = false
            readyToGo = true
            sendMessage("Available memory is sufficient, automatically continuing.")
        }

        if (server == null || !readyToGo || paused) return

        val world = this.world ?: return
        val border = this.border ?: return
        val worldData = this.worldData ?: return

        // only do one iteration at a time, no matter how frequently the timer fires
        readyToGo = false
        // keep one iteration from dragging on too long if the user specified a really high frequency
        val loopStartTime = Config.now()

        // Process async results from last time (sync or async alike).

        // First, check which chunk generations finished; mark them existing+unloadable, and drop from pending.
        var chunksProcessedLastTick = 0
        val newPendingChunks = HashMap<CompletableFuture<Void>, CoordXZ>()
        val chunksToUnload = HashSet<CoordXZ>()
        for (cf in pendingChunks.keys) {
            if (cf.isDone) {
                ++chunksProcessedLastTick
                val xz = pendingChunks[cf]!!
                worldData.chunkExistsNow(xz.x, xz.z)
                chunksToUnload.add(xz)
            } else {
                newPendingChunks[cf] = pendingChunks[cf]!!
            }
        }
        pendingChunks = newPendingChunks

        // Next, check which chunks loaded as a dependency no longer need to be kept in memory.
        val newPreventUnload = HashSet<UnloadDependency>()
        for (dependency in preventUnload!!) {
            if (worldData.doesChunkExist(dependency.forX, dependency.forZ)) {
                chunksToUnload.add(CoordXZ(dependency.neededX, dependency.neededZ))
            } else {
                newPreventUnload.add(dependency)
            }
        }
        preventUnload = newPreventUnload

        // Unload all chunks that aren't needed anymore.
        for (unload in chunksToUnload) {
            if (!chunkOnUnloadPreventionList(unload.x, unload.z)) {
                world.removePluginChunkTicket(unload.x, unload.z, WorldBorder.plugin)
                world.unloadChunkRequest(unload.x, unload.z)
            }
        }

        // Damp chunksPerRun: only fill the queue to a bit more than we can process per tick, so user-induced
        // chunk generations don't pile up behind a long queue of fill-generations.
        var chunksToProcess = chunksPerRun
        if (chunksProcessedLastTick > 0 || pendingChunks.isNotEmpty()) {
            // we generally queue 3 chunks, so real numbers are 1/3 of these
            val chunksExpectedToGetProcessed = (chunksProcessedLastTick - pendingChunks.size) / 3 + 3
            if (chunksExpectedToGetProcessed < chunksToProcess) {
                chunksToProcess = chunksExpectedToGetProcessed
            }
        }

        for (loop in 0 until chunksToProcess) {
            // in case the task has been paused while we're repeating...
            if (paused || pausedForMemory) return

            val now = Config.now()

            // every 5 seconds or so, give a basic progress report
            if (now > lastReport + 5000) reportProgress()

            // if this iteration has been running 45ms (almost 1 tick) or more, stop to take a breather
            if (now > loopStartTime + 45) {
                readyToGo = true
                return
            }

            // if we've made it at least partly outside the border, skip past any such chunks
            while (!border.insideBorder((CoordXZ.chunkToBlock(x) + 8).toDouble(), (CoordXZ.chunkToBlock(z) + 8).toDouble())) {
                if (!moveToNext()) return
            }
            insideBorder = true

            if (!forceLoad) {
                // skip past chunks confirmed as fully generated
                var rLoop = 0
                while (worldData.isChunkFullyGenerated(x, z)) {
                    rLoop++
                    insideBorder = true
                    if (!moveToNext()) return
                    if (rLoop > 255) {
                        // only skim through max 256 chunks (~8 region files) at a time, to allow a break if needed
                        readyToGo = true
                        return
                    }
                }
            }

            pendingChunks[loadChunkAsync(world, x, z, true)] = CoordXZ(x, z)

            // enough nearby chunks must be loaded for the server to populate a chunk with trees, snow, etc.
            // so keep the last few chunks loaded, and temporarily load an extra inside chunk (toward center)
            val popX = if (!isZLeg) x else (x + (if (isNeg) -1 else 1))
            val popZ = if (isZLeg) z else (z + (if (!isNeg) -1 else 1))

            pendingChunks[loadChunkAsync(world, popX, popZ, false)] = CoordXZ(popX, popZ)
            preventUnload!!.add(UnloadDependency(popX, popZ, x, z))

            // make sure the previous chunk in our spiral is loaded as well (it might already exist and be skipped)
            pendingChunks[loadChunkAsync(world, lastChunk.x, lastChunk.z, false)] = CoordXZ(lastChunk.x, lastChunk.z)
            preventUnload!!.add(UnloadDependency(lastChunk.x, lastChunk.z, x, z))

            // move on to the next chunk
            if (!moveToNext()) return
        }
        // ready for the next iteration to run
        readyToGo = true
    }

    // step through chunks in a spiral pattern from center; returns false if we're done, otherwise true
    fun moveToNext(): Boolean {
        if (paused || pausedForMemory) return false

        reportNum++

        // keep track of progress in case we need to save to config to restore after a restart
        if (!isNeg && current == 0 && length > 3) {
            if (!isZLeg) {
                lastLegX = x
                lastLegZ = z
                lastLegTotal = reportTotal + reportNum
            } else {
                refX = lastLegX
                refZ = lastLegZ
                refTotal = lastLegTotal
                refLength = length - 1
            }
        }

        // make sure of the direction we're moving (X or Z? negative or positive?)
        if (current < length) {
            current++
        } else {
            // one leg/side of the spiral down...
            current = 0
            isZLeg = isZLeg xor true
            if (isZLeg) {
                // every second leg (between X and Z legs), length increases
                isNeg = isNeg xor true
                length++
            }
        }

        // keep track of the last chunk we were at
        lastChunk.x = x
        lastChunk.z = z

        // move one chunk further in the appropriate direction
        if (isZLeg) {
            z += if (isNeg) -1 else 1
        } else {
            x += if (isNeg) -1 else 1
        }

        // if we've been around one full loop (4 legs)...
        if (isZLeg && isNeg && current == 0) {
            // see if we've been outside the border for the whole loop
            if (!insideBorder) {
                // and finish if so
                finish()
                return false
            } else {
                insideBorder = false
            }
        }
        return true

        /* reference diagram, movement should follow this pattern:
         *  8 [>][>][>][>][>] etc.
         * [^][6][>][>][>][>][>][6]
         * [^][^][4][>][>][>][4][v]
         * [^][^][^][2][>][2][v][v]
         * [^][^][^][^][0][v][v][v]
         * [^][^][^][1][1][v][v][v]
         * [^][^][3][<][<][3][v][v]
         * [^][5][<][<][<][<][5][v]
         * [7][<][<][<][<][<][<][7]
         */
    }

    // for successful completion
    fun finish() {
        val world = this.world ?: return
        paused = true
        reportProgress()
        world.save()
        Bukkit.getServer().pluginManager.callEvent(WorldBorderFillFinishedEvent(world, reportTotal.toLong()))
        sendMessage("task successfully completed for world \"$refWorld\"!")
        stop()
    }

    // for cancelling prematurely
    fun cancel() = stop()

    // we're done, whether finished or cancelled
    private fun stop() {
        if (server == null) return

        readyToGo = false
        scheduledTask?.cancel()
        server = null

        // release any chunk tickets we still hold (clear preventUnload first so the unload listener stays out of the way)
        val tempPreventUnload = preventUnload
        preventUnload = null
        if (tempPreventUnload != null) {
            val world = this.world
            for (entry in tempPreventUnload) {
                world?.removePluginChunkTicket(entry.neededX, entry.neededZ, WorldBorder.plugin)
                world?.unloadChunkRequest(entry.neededX, entry.neededZ)
            }
        }
    }

    // is this task still valid/workable?
    fun valid(): Boolean = server != null

    // handle pausing/unpausing the task
    fun pause() {
        if (pausedForMemory) pause(false) else pause(!paused)
    }

    fun pause(pause: Boolean) {
        if (pausedForMemory && !pause) {
            pausedForMemory = false
        } else {
            paused = pause
        }
        if (paused) {
            Config.storeFillTask()
            reportProgress()
        } else {
            Config.unStoreFillTask()
        }
    }

    fun isPaused(): Boolean = paused || pausedForMemory

    fun chunkOnUnloadPreventionList(x: Int, z: Int): Boolean {
        val set = preventUnload ?: return false
        return set.any { it.neededX == x && it.neededZ == z }
    }

    // let the user know how things are coming along
    private fun reportProgress() {
        lastReport = Config.now()
        var perc = getPercentageCompleted()
        if (perc > 100) perc = 100.0
        sendMessage("$reportNum more chunks processed (" + (reportTotal + reportNum) + " total, ~" + Config.coord.format(perc) + "%)")
        reportTotal += reportNum
        reportNum = 0

        // save the world to disk periodically, just in case
        if (Config.fillAutosaveFrequency > 0 && lastAutosave + (Config.fillAutosaveFrequency * 1000) < lastReport) {
            lastAutosave = lastReport
            sendMessage("Saving the world to disk, just to be on the safe side.")
            world?.save()
        }
    }

    // send a message to the server console/log and possibly to an in-game player
    private fun sendMessage(text: String) {
        // chunk generation eats memory, so track availability
        val availMem = Config.availableMemory()

        Config.log("[Fill] $text (free mem: $availMem MB)")
        notifyPlayer?.msg("[Fill] $text")

        if (availMem < 200) {
            // running low on memory, auto-pause; the JVM will reclaim unloaded chunks on its own
            pausedForMemory = true
            Config.storeFillTask()
            val msg = "Available memory is very low, task is pausing. The task will automatically continue if/when sufficient memory is freed up.\n Alternatively, if you restart the server, this task will automatically continue once the server is back up."
            Config.log("[Fill] $msg")
            notifyPlayer?.msg("[Fill] $msg")
        }
    }

    // stuff for saving / restoring progress
    fun continueProgress(x: Int, z: Int, length: Int, totalDone: Int) {
        this.x = x
        this.z = z
        this.length = length
        this.reportTotal = totalDone
        this.continueNotice = true
    }

    /** Percentage completed for the fill task. */
    fun getPercentageCompleted(): Double = (reportTotal + reportNum).toDouble() / reportTarget.toDouble() * 100

    /** Number of chunks processed so far. */
    fun getChunksCompleted(): Int = reportTotal

    /** Total number of chunks that need to be generated. */
    fun getChunksTotal(): Int = reportTarget

    private fun loadChunkAsync(world: World, x: Int, z: Int, gen: Boolean): CompletableFuture<Void> = world.getChunkAtAsync(x, z, gen).thenAccept {
        // hold a plugin chunk ticket so this chunk isn't unloaded while we still need it
        world.addPluginChunkTicket(x, z, WorldBorder.plugin)
    }
}
