package com.wimbli.WorldBorder

import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.entity.CreatureSpawnEvent

class MobSpawnListener : Listener {

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onCreatureSpawn(event: CreatureSpawnEvent) {
        val loc = event.entity.location
        val world = loc.world ?: return
        val border = Config.border(world.name) ?: return

        if (!border.insideBorder(loc.x, loc.z, Config.shapeRound))
            event.isCancelled = true
    }

    fun unregister() = HandlerList.unregisterAll(this)
}
