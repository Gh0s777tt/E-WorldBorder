package com.wimbli.WorldBorder

import org.bstats.bukkit.Metrics
import org.bstats.charts.SimplePie
import org.bstats.charts.SingleLineChart
import org.bukkit.plugin.java.JavaPlugin

class WorldBorder : JavaPlugin() {

    private var blockPlaceListener: BlockPlaceListener? = null
    private var mobSpawnListener: MobSpawnListener? = null

    override fun onEnable() {
        plugin = this
        if (wbCommand == null) {
            wbCommand = WBCommand()
        }

        // Load (or create new) config file
        Config.load(this, false)

        // our one real command, with aliases "wb" and "worldborder"
        getCommand("wborder")?.let {
            it.setExecutor(wbCommand)
            it.tabCompleter = WBTabCompleter()
        }

        // keep an eye on teleports, to redirect them to a spot inside the border if necessary
        server.pluginManager.registerEvents(WBListener(), this)

        if (Config.preventBlockPlace) {
            enableBlockPlaceListener(true)
        }

        if (Config.preventMobSpawn) {
            enableMobSpawnListener(true)
        }

        // integrate with DynMap if it's available
        DynMapFeatures.setup()

        // sync Minecraft's built-in (client-side) world borders, if that feature is enabled
        VanillaBorder.applyAll()

        // bStats metrics — opt-in: set "bstats-plugin-id" in config.yml to your id from https://bstats.org
        if (Config.bstatsPluginId > 0) {
            val metrics = Metrics(this, Config.bstatsPluginId)
            metrics.addCustomChart(SingleLineChart("borders") { Config.getBorders().size })
            metrics.addCustomChart(SimplePie("default_shape") { Config.shapeName() })
        }

        // PlaceholderAPI integration, only when it's installed
        if (server.pluginManager.getPlugin("PlaceholderAPI") != null) {
            WBPlaceholders().register()
            Config.log("Hooked into PlaceholderAPI.")
        }

        // log the main world's spawn location for reference
        val spawn = server.worlds[0].spawnLocation
        Config.log("For reference, the main world's spawn location is at X: ${Config.coord.format(spawn.x)} Y: ${Config.coord.format(spawn.y)} Z: ${Config.coord.format(spawn.z)}")
    }

    override fun onDisable() {
        DynMapFeatures.removeAllBorders()
        Config.stopBorderTimer()
        Config.storeFillTask()
        Config.stopFillTask()
    }

    // for other plugins to hook into
    fun getWorldBorder(worldName: String): BorderData? = Config.border(worldName)

    fun enableBlockPlaceListener(enable: Boolean) {
        if (enable) {
            blockPlaceListener = BlockPlaceListener().also { server.pluginManager.registerEvents(it, this) }
        } else {
            blockPlaceListener?.unregister()
        }
    }

    fun enableMobSpawnListener(enable: Boolean) {
        if (enable) {
            mobSpawnListener = MobSpawnListener().also { server.pluginManager.registerEvents(it, this) }
        } else {
            mobSpawnListener?.unregister()
        }
    }

    companion object {
        @JvmStatic
        lateinit var plugin: WorldBorder
            private set

        @JvmStatic
        var wbCommand: WBCommand? = null
            private set
    }
}
