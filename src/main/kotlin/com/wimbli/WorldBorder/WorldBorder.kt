package com.wimbli.WorldBorder

import org.bukkit.plugin.java.JavaPlugin

class WorldBorder : JavaPlugin() {

    private var blockPlaceListener: BlockPlaceListener? = null
    private var mobSpawnListener: MobSpawnListener? = null

    override fun onEnable() {
        plugin = this
        if (wbCommand == null)
            wbCommand = WBCommand()

        // Load (or create new) config file
        Config.load(this, false)

        // our one real command, with aliases "wb" and "worldborder"
        getCommand("wborder")?.setExecutor(wbCommand)

        // keep an eye on teleports, to redirect them to a spot inside the border if necessary
        server.pluginManager.registerEvents(WBListener(), this)

        if (Config.preventBlockPlace)
            enableBlockPlaceListener(true)

        if (Config.preventMobSpawn)
            enableMobSpawnListener(true)

        // integrate with DynMap if it's available
        DynMapFeatures.setup()

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
        if (enable)
            blockPlaceListener = BlockPlaceListener().also { server.pluginManager.registerEvents(it, this) }
        else
            blockPlaceListener?.unregister()
    }

    fun enableMobSpawnListener(enable: Boolean) {
        if (enable)
            mobSpawnListener = MobSpawnListener().also { server.pluginManager.registerEvents(it, this) }
        else
            mobSpawnListener?.unregister()
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
