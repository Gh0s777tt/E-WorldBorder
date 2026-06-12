package com.wimbli.WorldBorder

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.entity.Player
import java.text.DecimalFormat
import java.util.Collections
import java.util.LinkedHashMap
import java.util.LinkedHashSet
import java.util.UUID
import java.util.logging.Level
import java.util.logging.Logger

object Config {
    // private stuff used within this class
    private lateinit var plugin: WorldBorder
    private var cfg: FileConfiguration? = null
    private lateinit var wbLog: Logger

    // DecimalFormat is not thread-safe and coord.format() is called from the async Fill/Trim threads
    // as well as the main thread, so back it per-thread. Call sites (Config.coord.format(x)) are unchanged.
    private val coordTL: ThreadLocal<DecimalFormat> = ThreadLocal.withInitial { DecimalFormat("0.0") }
    val coord: DecimalFormat get() = coordTL.get()

    private var borderTask = -1
    private val rt = Runtime.getRuntime()

    @Volatile
    var fillTask: WorldFillTask? = null

    @Volatile
    var trimTask: WorldTrimTask? = null

    // ----- actual configuration values which can be changed -----

    var shapeRound = true
        private set
    private val borders: MutableMap<String, BorderData> = Collections.synchronizedMap(LinkedHashMap())
    private val bypassPlayers: MutableSet<UUID> = Collections.synchronizedSet(LinkedHashSet())

    private var msgRaw = ""                                  // raw configured message (legacy & codes or MiniMessage)
    private var msgComponent: Component = Component.empty()  // parsed Adventure component (what players receive)
    private var msgFmt = ""    // message rendered to legacy section codes (for any string consumers)
    private var msgClean = ""  // message cleaned of formatting codes

    var debug = false
        private set
    var knockBack = 3.0
        private set
    var timerTicks = 4
        private set
    var whooshEffect = true
        private set
    var portalRedirection = true
        private set
    var dynmapEnabled = true
        private set
    var dynmapMessage: String = "The border of the world."
        private set
    var dynmapPriority = 0
        private set
    var dynmapHideByDefault = false
        private set
    var remountTicks = 0
        private set
    var killPlayer = false
        private set
    var denyEnderpearl = false
        private set
    var fillAutosaveFrequency = 30
        private set
    private var fillMemoryTolerance = 500
    var preventBlockPlace = false
        private set
    var preventMobSpawn = false
        private set
    var vanillaBorder = false
        private set
    var bstatsPluginId = 0
        private set

    val message: String get() = msgFmt
    val messageComponent: Component get() = msgComponent
    val messageRaw: String get() = msgRaw
    val messageClean: String get() = msgClean

    fun now(): Long = System.currentTimeMillis()

    // ----- borders -----

    @JvmOverloads
    fun setBorder(world: String, border: BorderData, logIt: Boolean = true) {
        borders[world] = border
        if (logIt)
            log("Border set. ${borderDescription(world)}")
        save(true)
        DynMapFeatures.showBorder(world, border)
        VanillaBorder.apply(world, border, animateSeconds = 3L) // smooth client-side resize on user changes
    }

    fun setBorder(world: String, radiusX: Int, radiusZ: Int, x: Double, z: Double, shape: Boolean?) {
        val old = border(world)
        val oldWrap = old != null && old.wrapping
        setBorder(world, BorderData(x, z, radiusX, radiusZ, shape, oldWrap), true)
    }

    fun setBorder(world: String, radiusX: Int, radiusZ: Int, x: Double, z: Double) {
        val old = border(world)
        val oldShape = old?.shape
        val oldWrap = old != null && old.wrapping
        setBorder(world, BorderData(x, z, radiusX, radiusZ, oldShape, oldWrap), true)
    }

    // backwards-compatible single-radius variants
    fun setBorder(world: String, radius: Int, x: Double, z: Double, shape: Boolean?) {
        setBorder(world, BorderData(x, z, radius, radius, shape), true)
    }

    fun setBorder(world: String, radius: Int, x: Double, z: Double) {
        setBorder(world, radius, radius, x, z)
    }

    @JvmOverloads
    fun setBorderCorners(world: String, x1: Double, z1: Double, x2: Double, z2: Double, shape: Boolean?, wrap: Boolean = false) {
        val radiusX = Math.abs(x1 - x2) / 2
        val radiusZ = Math.abs(z1 - z2) / 2
        val x = (if (x1 < x2) x1 else x2) + radiusX
        val z = (if (z1 < z2) z1 else z2) + radiusZ
        setBorder(world, BorderData(x, z, Math.round(radiusX).toInt(), Math.round(radiusZ).toInt(), shape, wrap), true)
    }

    fun setBorderCorners(world: String, x1: Double, z1: Double, x2: Double, z2: Double) {
        val old = border(world)
        val oldShape = old?.shape
        val oldWrap = old != null && old.wrapping
        setBorderCorners(world, x1, z1, x2, z2, oldShape, oldWrap)
    }

    fun removeBorder(world: String) {
        borders.remove(world)
        log("Removed border for world \"$world\".")
        save(true)
        DynMapFeatures.removeBorder(world)
        if (vanillaBorder) VanillaBorder.remove(world)
    }

    fun removeAllBorders() {
        borders.clear()
        log("Removed all borders for all worlds.")
        save(true)
        DynMapFeatures.removeAllBorders()
        if (vanillaBorder) VanillaBorder.removeAll()
    }

    fun borderDescription(world: String): String {
        val border = borders[world]
        return if (border == null)
            "No border was found for the world \"$world\"."
        else
            "World \"$world\" has border $border"
    }

    fun borderDescriptions(): Set<String> = borders.keys.map { borderDescription(it) }.toMutableSet()

    fun border(world: String): BorderData? = borders[world]

    fun getBorders(): Map<String, BorderData> = LinkedHashMap(borders)

    // ----- messages -----

    fun setMessage(msg: String) {
        updateMessage(msg)
        save(true)
    }

    fun updateMessage(msg: String) {
        msgRaw = msg
        msgComponent = parseMessage(msg)
        msgFmt = LegacyComponentSerializer.legacySection().serialize(msgComponent)
        msgClean = PlainTextComponentSerializer.plainText().serialize(msgComponent)
    }

    // Parse the configured message: MiniMessage if it contains tags (gradients/hover/click),
    // otherwise legacy '&' color codes, for backwards compatibility with existing configs.
    private fun parseMessage(msg: String): Component =
        if (msg.contains('<') && msg.contains('>'))
            MiniMessage.miniMessage().deserialize(msg)
        else
            LegacyComponentSerializer.legacyAmpersand().deserialize(msg)

    // ----- shape -----

    fun setShape(round: Boolean) {
        shapeRound = round
        log("Set default border shape to ${shapeName()}.")
        save(true)
        DynMapFeatures.showAllBorders()
    }

    fun shapeName(): String = shapeName(shapeRound)

    fun shapeName(round: Boolean?): String = when (round) {
        null -> "default"
        true -> "elliptic/round"
        false -> "rectangular/square"
    }

    // ----- simple toggles / values with side effects -----

    fun setDebug(debugMode: Boolean) {
        debug = debugMode
        log("Debug mode ${if (debug) "enabled" else "disabled"}.")
        save(true)
    }

    fun setWhooshEffect(enable: Boolean) {
        whooshEffect = enable
        log("\"Whoosh\" knockback effect ${if (enable) "enabled" else "disabled"}.")
        save(true)
    }

    fun showWhooshEffect(loc: Location) {
        if (!whooshEffect) return

        val world = loc.world
        // Modernized particle/sound effect (replaces the legacy Effect.* calls).
        world.spawnParticle(Particle.PORTAL, loc, 30)
        world.spawnParticle(Particle.SMOKE, loc, 12)
        world.playSound(loc, Sound.ENTITY_GHAST_SHOOT, 1.0f, 1.0f)
    }

    fun setPreventBlockPlace(enable: Boolean) {
        if (preventBlockPlace != enable)
            WorldBorder.plugin.enableBlockPlaceListener(enable)
        preventBlockPlace = enable
        log("Prevent block place ${if (enable) "enabled" else "disabled"}.")
        save(true)
    }

    fun setPreventMobSpawn(enable: Boolean) {
        if (preventMobSpawn != enable)
            WorldBorder.plugin.enableMobSpawnListener(enable)
        preventMobSpawn = enable
        log("Prevent mob spawn ${if (enable) "enabled" else "disabled"}.")
        save(true)
    }

    fun setVanillaBorder(enable: Boolean) {
        vanillaBorder = enable
        log("Vanilla (client-side) world border display ${if (enable) "enabled" else "disabled"}.")
        save(true)
        if (enable) VanillaBorder.applyAll() else VanillaBorder.removeAll()
    }

    fun setDenyEnderpearl(enable: Boolean) {
        denyEnderpearl = enable
        log("Direct cancellation of ender pearls thrown past the border ${if (enable) "enabled" else "disabled"}.")
        save(true)
    }

    fun setPortalRedirection(enable: Boolean) {
        portalRedirection = enable
        log("Portal redirection ${if (enable) "enabled" else "disabled"}.")
        save(true)
    }

    fun setKnockBack(numBlocks: Double) {
        knockBack = numBlocks
        log("Knockback set to $knockBack blocks inside the border.")
        save(true)
    }

    fun setTimerTicks(ticks: Int) {
        timerTicks = ticks
        log("Timer delay set to $timerTicks tick(s). That is roughly ${timerTicks * 50}ms / ${timerTicks * 50.0 / 1000.0} seconds.")
        startBorderTimer()
        save(true)
    }

    fun setRemountTicks(ticks: Int) {
        remountTicks = ticks
        if (remountTicks == 0)
            log("Remount delay set to 0. Players will be left dismounted when knocked back from the border while on a vehicle.")
        else {
            log("Remount delay set to $remountTicks tick(s). That is roughly ${remountTicks * 50}ms / ${remountTicks * 50.0 / 1000.0} seconds.")
            if (ticks < 10)
                logWarn("setting the remount delay to less than 10 (and greater than 0) is not recommended. This can lead to nasty client glitches.")
        }
        save(true)
    }

    fun setFillAutosaveFrequency(seconds: Int) {
        fillAutosaveFrequency = seconds
        if (fillAutosaveFrequency == 0)
            log("World autosave frequency during Fill process set to 0, disabling it. Note that much progress can be lost this way if there is a bug or crash in the world generation process.")
        else
            log("World autosave frequency during Fill process set to $fillAutosaveFrequency seconds. New chunks generated by the Fill process will be forcibly saved to disk this often to prevent loss of progress due to bugs or crashes in the world generation process.")
        save(true)
    }

    fun setDynmapBorderEnabled(enable: Boolean) {
        dynmapEnabled = enable
        log("DynMap border display is now ${if (enable) "enabled" else "disabled"}.")
        save(true)
        DynMapFeatures.showAllBorders()
    }

    fun setDynmapMessage(msg: String) {
        dynmapMessage = msg
        log("DynMap border label is now set to: $msg")
        save(true)
        DynMapFeatures.showAllBorders()
    }

    // ----- bypass list -----

    fun setPlayerBypass(player: UUID, bypass: Boolean) {
        if (bypass) bypassPlayers.add(player) else bypassPlayers.remove(player)
        save(true)
    }

    fun isPlayerBypassing(player: UUID): Boolean = bypassPlayers.contains(player)

    fun playerBypassList(): ArrayList<UUID> = ArrayList(bypassPlayers)

    private fun importBypassStringList(strings: List<String>) {
        for (s in strings) {
            try {
                bypassPlayers.add(UUID.fromString(s))
            } catch (ex: IllegalArgumentException) {
                logWarn("Ignoring invalid bypass UUID in config: $s")
            }
        }
    }

    private fun exportBypassStringList(): ArrayList<String> = ArrayList(bypassPlayers.map { it.toString() })

    // ----- border-checking timer -----

    fun isBorderTimerRunning(): Boolean {
        if (borderTask == -1) return false
        return plugin.server.scheduler.isQueued(borderTask) || plugin.server.scheduler.isCurrentlyRunning(borderTask)
    }

    fun startBorderTimer() {
        stopBorderTimer(false)

        borderTask = plugin.server.scheduler.scheduleSyncRepeatingTask(plugin, BorderCheckTask(), timerTicks.toLong(), timerTicks.toLong())

        if (borderTask == -1)
            logWarn("Failed to start timed border-checking task! This will prevent the plugin from working. Try restarting the server.")

        logConfig("Border-checking timed task started.")
    }

    @JvmOverloads
    fun stopBorderTimer(logIt: Boolean = true) {
        if (borderTask == -1) return
        plugin.server.scheduler.cancelTask(borderTask)
        borderTask = -1
        if (logIt)
            logConfig("Border-checking timed task stopped.")
    }

    // ----- fill / trim tasks -----

    fun stopFillTask() {
        fillTask?.let { if (it.valid()) it.cancel() }
    }

    fun storeFillTask() = save(false, true)

    fun unStoreFillTask() = save(false)

    @JvmOverloads
    fun restoreFillTask(world: String, fillDistance: Int, chunksPerRun: Int, tickFrequency: Int, x: Int, z: Int, length: Int, total: Int, forceLoad: Boolean = false) {
        val task = WorldFillTask(plugin.server, null, world, fillDistance, chunksPerRun, tickFrequency, forceLoad)
        fillTask = task
        if (task.valid()) {
            task.continueProgress(x, z, length, total)
            val id = plugin.server.scheduler.scheduleSyncRepeatingTask(plugin, task, 20L, tickFrequency.toLong())
            task.setTaskID(id)
        }
    }

    fun stopTrimTask() {
        trimTask?.let { if (it.valid()) it.cancel() }
    }

    // ----- memory -----

    fun availableMemory(): Int =
        ((rt.maxMemory() - rt.totalMemory() + rt.freeMemory()) / 1048576L).toInt() // bytes in 1 MB

    fun availableMemoryTooLow(): Boolean = availableMemory() < fillMemoryTolerance

    // ----- permissions -----

    @JvmOverloads
    fun hasPermission(player: Player?, request: String, notify: Boolean = true): Boolean {
        if (player == null) return true // console, always permitted

        if (player.hasPermission("worldborder.$request")) return true

        if (notify)
            player.msg("You do not have sufficient permissions.")

        return false
    }

    // ----- color codes -----

    // translate '&' color codes into the section (§) codes used internally, via Adventure
    fun replaceAmpColors(message: String): String =
        LegacyComponentSerializer.legacySection().serialize(
            LegacyComponentSerializer.legacyAmpersand().deserialize(message)
        )

    // strip all color/formatting codes, via Adventure
    fun stripAmpColors(message: String): String =
        PlainTextComponentSerializer.plainText().serialize(
            LegacyComponentSerializer.legacyAmpersand().deserialize(message)
        )

    // ----- logging -----

    fun log(level: Level, text: String) = wbLog.log(level, text)
    fun log(text: String) = log(Level.INFO, text)
    fun logWarn(text: String) = log(Level.WARNING, text)
    fun logConfig(text: String) = log(Level.INFO, "[CONFIG] $text")

    // ----- load / save -----

    private const val currentCfgVersion = 11

    @JvmOverloads
    fun load(master: WorldBorder, logIt: Boolean = true) {
        plugin = master
        wbLog = plugin.logger

        plugin.reloadConfig()
        val c = plugin.config
        cfg = c

        val cfgVersion = c.getInt("cfg-version", currentCfgVersion)

        var msg = c.getString("message")
        shapeRound = c.getBoolean("round-border", true)
        debug = c.getBoolean("debug-mode", false)
        whooshEffect = c.getBoolean("whoosh-effect", true)
        portalRedirection = c.getBoolean("portal-redirection", true)
        knockBack = c.getDouble("knock-back-dist", 3.0)
        timerTicks = c.getInt("timer-delay-ticks", 5)
        remountTicks = c.getInt("remount-delay-ticks", 0)
        dynmapEnabled = c.getBoolean("dynmap-border-enabled", true)
        dynmapMessage = c.getString("dynmap-border-message", "The border of the world.")!!
        dynmapHideByDefault = c.getBoolean("dynmap-border-hideByDefault", false)
        dynmapPriority = c.getInt("dynmap-border-priority", 0)
        logConfig("Using ${shapeName()} border, knockback of $knockBack blocks, and timer delay of $timerTicks.")
        killPlayer = c.getBoolean("player-killed-bad-spawn", false)
        denyEnderpearl = c.getBoolean("deny-enderpearl", true)
        fillAutosaveFrequency = c.getInt("fill-autosave-frequency", 30)
        importBypassStringList(c.getStringList("bypass-list-uuids"))
        fillMemoryTolerance = c.getInt("fill-memory-tolerance", 500)
        preventBlockPlace = c.getBoolean("prevent-block-place")
        preventMobSpawn = c.getBoolean("prevent-mob-spawn")
        vanillaBorder = c.getBoolean("vanilla-border", false)
        bstatsPluginId = c.getInt("bstats-plugin-id", 0)

        startBorderTimer()

        borders.clear()

        // if empty border message, assume no config
        if (msg.isNullOrEmpty()) {
            logConfig("Configuration not present, creating new file.")
            msg = "&cYou have reached the edge of this world."
            updateMessage(msg)
            save(false)
            return
        }
        // if loading older config which didn't support color codes, make sure default red color code is added
        else if (cfgVersion < 8 && !msg.startsWith("&"))
            updateMessage("&c$msg")
        else
            updateMessage(msg)

        // this option defaulted to false previously, but its behavior changed to something almost everyone wants
        if (cfgVersion < 10)
            denyEnderpearl = true

        // the bypass list used to be stored as names rather than UUIDs; wipe the old list
        if (cfgVersion < 11)
            c.set("bypass-list", null)

        val worlds = c.getConfigurationSection("worlds")
        if (worlds != null) {
            for (worldNameRaw in worlds.getKeys(false)) {
                val bord = worlds.getConfigurationSection(worldNameRaw) ?: continue

                // we swap "<" to "." at load since periods denote config nodes; world names with periods are stored with "<"
                val worldName = if (cfgVersion > 3) worldNameRaw.replace("<", ".") else worldNameRaw

                // backwards compatibility for config from before elliptical/rectangular borders
                if (bord.isSet("radius") && !bord.isSet("radiusX")) {
                    val radius = bord.getInt("radius")
                    bord.set("radiusX", radius)
                    bord.set("radiusZ", radius)
                }

                val overrideShape = bord.get("shape-round") as? Boolean
                val wrap = bord.getBoolean("wrapping", false)
                val border = BorderData(
                    bord.getDouble("x", 0.0), bord.getDouble("z", 0.0),
                    bord.getInt("radiusX", 0), bord.getInt("radiusZ", 0),
                    overrideShape, wrap
                )
                borders[worldName] = border
                logConfig(borderDescription(worldName))
            }
        }

        // if we have an unfinished fill task stored from a previous run, load it up
        val storedFillTask = c.getConfigurationSection("fillTask")
        if (storedFillTask != null) {
            val worldName = storedFillTask.getString("world") ?: ""
            val fillDistance = storedFillTask.getInt("fillDistance", 176)
            val chunksPerRun = storedFillTask.getInt("chunksPerRun", 5)
            val tickFrequency = storedFillTask.getInt("tickFrequency", 20)
            val fillX = storedFillTask.getInt("x", 0)
            val fillZ = storedFillTask.getInt("z", 0)
            val fillLength = storedFillTask.getInt("length", 0)
            val fillTotal = storedFillTask.getInt("total", 0)
            val forceLoad = storedFillTask.getBoolean("forceLoad", false)
            restoreFillTask(worldName, fillDistance, chunksPerRun, tickFrequency, fillX, fillZ, fillLength, fillTotal, forceLoad)
            save(false)
        }

        if (logIt)
            logConfig("Configuration loaded.")

        if (cfgVersion < currentCfgVersion)
            save(false)
    }

    @JvmOverloads
    fun save(logIt: Boolean, storeFillTask: Boolean = false) {
        val c = cfg ?: return

        c.set("cfg-version", currentCfgVersion)
        c.set("message", msgRaw)
        c.set("round-border", shapeRound)
        c.set("debug-mode", debug)
        c.set("whoosh-effect", whooshEffect)
        c.set("portal-redirection", portalRedirection)
        c.set("knock-back-dist", knockBack)
        c.set("timer-delay-ticks", timerTicks)
        c.set("remount-delay-ticks", remountTicks)
        c.set("dynmap-border-enabled", dynmapEnabled)
        c.set("dynmap-border-message", dynmapMessage)
        c.set("dynmap-border-hideByDefault", dynmapHideByDefault)
        c.set("dynmap-border-priority", dynmapPriority)
        c.set("player-killed-bad-spawn", killPlayer)
        c.set("deny-enderpearl", denyEnderpearl)
        c.set("fill-autosave-frequency", fillAutosaveFrequency)
        c.set("bypass-list-uuids", exportBypassStringList())
        c.set("fill-memory-tolerance", fillMemoryTolerance)
        c.set("prevent-block-place", preventBlockPlace)
        c.set("prevent-mob-spawn", preventMobSpawn)
        c.set("vanilla-border", vanillaBorder)
        c.set("bstats-plugin-id", bstatsPluginId)

        c.set("worlds", null)
        // snapshot under lock to avoid ConcurrentModificationException with async tasks
        val snapshot = synchronized(borders) { LinkedHashMap(borders) }
        for ((key, bord) in snapshot) {
            val name = key.replace(".", "<")
            c.set("worlds.$name.x", bord.x)
            c.set("worlds.$name.z", bord.z)
            c.set("worlds.$name.radiusX", bord.radiusX)
            c.set("worlds.$name.radiusZ", bord.radiusZ)
            c.set("worlds.$name.wrapping", bord.wrapping)
            bord.shape?.let { c.set("worlds.$name.shape-round", it) }
        }

        val task = fillTask
        if (storeFillTask && task != null && task.valid()) {
            c.set("fillTask.world", task.refWorld)
            c.set("fillTask.fillDistance", task.refFillDistance)
            c.set("fillTask.chunksPerRun", task.refChunksPerRun)
            c.set("fillTask.tickFrequency", task.refTickFrequency)
            c.set("fillTask.x", task.refX)
            c.set("fillTask.z", task.refZ)
            c.set("fillTask.length", task.refLength)
            c.set("fillTask.total", task.refTotal)
            c.set("fillTask.forceLoad", task.refForceLoad)
        } else {
            c.set("fillTask", null)
        }

        plugin.saveConfig()

        if (logIt)
            logConfig("Configuration saved.")
    }
}
