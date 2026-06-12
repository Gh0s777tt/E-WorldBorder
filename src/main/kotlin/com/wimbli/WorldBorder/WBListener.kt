package com.wimbli.WorldBorder

import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerPortalEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.event.world.ChunkLoadEvent
import org.bukkit.event.world.WorldLoadEvent

class WBListener : Listener {

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onPlayerTeleport(event: PlayerTeleportEvent) {
        // if knockback is set to 0, simply return
        if (Config.knockBack == 0.0) return

        if (Config.debug)
            Config.log("Teleport cause: ${event.cause}")

        val newLoc = BorderCheckTask.checkPlayer(event.player, event.to, true, true)
        if (newLoc != null) {
            if (event.cause == PlayerTeleportEvent.TeleportCause.ENDER_PEARL && Config.denyEnderpearl) {
                event.isCancelled = true
                return
            }
            event.to = newLoc
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onPlayerPortal(event: PlayerPortalEvent) {
        // if knockback is set to 0, or portal redirection is disabled, simply return
        if (Config.knockBack == 0.0 || !Config.portalRedirection) return

        val newLoc = BorderCheckTask.checkPlayer(event.player, event.to, true, false)
        if (newLoc != null)
            event.to = newLoc
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onChunkLoad(event: ChunkLoadEvent) {
        // make sure our border-monitoring task is still running like it should
        if (Config.isBorderTimerRunning()) return

        Config.logWarn("Border-checking task was not running! Something on your server apparently killed it. It will now be restarted.")
        Config.startBorderTimer()
    }

    @EventHandler
    fun onWorldLoad(event: WorldLoadEvent) {
        // a world may load after borders are set (e.g. Multiverse); sync its vanilla border if enabled
        if (!Config.vanillaBorder) return
        val border = Config.border(event.world.name) ?: return
        VanillaBorder.apply(event.world.name, border)
    }

    // Note: the legacy onChunkUnload handler that reset the "force loaded" flag is no longer needed —
    // WorldFillTask now uses plugin chunk tickets, which it releases itself.
}
