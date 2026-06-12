package com.wimbli.WorldBorder

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause
import org.bukkit.util.Vector
import java.util.Collections
import java.util.LinkedHashSet

class BorderCheckTask : Runnable {

    override fun run() {
        // if knockback is set to 0, simply return
        if (Config.knockBack == 0.0) return

        // copy the player list so we aren't iterating a live view. Each player's check runs on that
        // player's own region thread (required on Folia for teleporting; runs next tick on Paper).
        for (player in Bukkit.getServer().onlinePlayers.toList()) {
            Sched.runForEntity(player) { checkPlayer(player, null, false, true) }
        }
    }

    companion object {
        // track players who are already being handled, so Bukkit sending stale teleport locations
        // can't trap us in a teleport loop
        private val handlingPlayers: MutableSet<String> = Collections.synchronizedSet(LinkedHashSet())

        // set targetLoc only if not current player location; set returnLocationOnly to true to have a new
        // Location returned instead of teleporting the player directly
        @JvmStatic
        @JvmOverloads
        fun checkPlayer(player: Player?, targetLoc: Location?, returnLocationOnly: Boolean, notify: Boolean = true): Location? {
            if (player == null || !player.isOnline) return null

            val loc = (targetLoc ?: player.location.clone()) ?: return null
            val world = loc.world ?: return null
            val border = Config.border(world.name) ?: return null

            if (border.insideBorder(loc.x, loc.z, Config.shapeRound))
                return null

            // bypassing players are allowed beyond the border; also ignore players currently being handled
            if (Config.isPlayerBypassing(player.uniqueId) || handlingPlayers.contains(player.name.lowercase()))
                return null

            // tag this player as being handled to avoid the teleport loop described above
            handlingPlayers.add(player.name.lowercase())

            val newLoc = newLocation(player, loc, border, notify)
            if (newLoc == null) {
                // player was killed (kill-on-bad-spawn) or no safe location exists; nothing more to do
                handlingPlayers.remove(player.name.lowercase())
                return null
            }

            var handlingVehicle = false

            // forcibly ejecting a player from a vehicle fires its own teleport event, so we handle the
            // vehicle here to avoid double messages/log entries, remounting after a short delay
            if (player.isInsideVehicle) {
                val ride: Entity? = player.vehicle
                player.leaveVehicle()
                if (ride != null) {
                    // vehicles need to be offset vertically and have velocity stopped
                    val vertOffset = if (ride is LivingEntity) 0.0 else ride.location.y - loc.y
                    val rideLoc = newLoc.clone()
                    rideLoc.y = newLoc.y + vertOffset
                    if (Config.debug)
                        Config.logWarn("Player was riding a \"$ride\".")

                    ride.velocity = Vector(0, 0, 0)
                    ride.teleportAsync(rideLoc, TeleportCause.PLUGIN)

                    if (Config.remountTicks > 0) {
                        setPassengerDelayed(ride, player, player.name, Config.remountTicks.toLong())
                        handlingVehicle = true
                    }
                }
            }

            // check if the player has passengers (only possible through odd plugins); they would otherwise
            // block all teleportation of the player, so they need handling
            val passengers = player.passengers
            if (passengers.isNotEmpty()) {
                player.eject()
                for (rider in passengers) {
                    rider.teleportAsync(newLoc, TeleportCause.PLUGIN)
                    if (Config.debug)
                        Config.logWarn("Player had a passenger riding on them: ${rider.type}")
                }
                player.msg("Your passenger" + (if (passengers.size > 1) "s have" else " has") + " been ejected.")
            }

            // particle and sound effects where the player was beyond the border, if enabled
            Config.showWhooshEffect(loc)

            if (returnLocationOnly) {
                // WBListener path: it sets event.to itself, so just release the guard and return the location
                if (!handlingVehicle)
                    handlingPlayers.remove(player.name.lowercase())
                return newLoc
            }

            // timer path: teleport asynchronously (Folia-safe) and release the guard once it completes
            val handledName = player.name.lowercase()
            player.teleportAsync(newLoc, TeleportCause.PLUGIN).thenRun {
                if (!handlingVehicle)
                    handlingPlayers.remove(handledName)
            }
            return null
        }

        private fun newLocation(player: Player, loc: Location, border: BorderData, notify: Boolean): Location? {
            if (Config.debug) {
                Config.logWarn((if (notify) "Border crossing" else "Check was run") + " in \"${loc.world.name}\". Border $border")
                Config.logWarn("Player position X: ${Config.coord.format(loc.x)} Y: ${Config.coord.format(loc.y)} Z: ${Config.coord.format(loc.z)}")
            }

            var newLoc = border.correctedPosition(loc, Config.shapeRound, player.isFlying)

            // it's remotely possible (such as in the Nether) a suitable location isn't available, in which case...
            if (newLoc == null) {
                if (Config.debug)
                    Config.logWarn("Target new location unviable, using spawn or killing player.")
                if (Config.killPlayer) {
                    player.health = 0.0
                    return null
                }
                newLoc = player.world.spawnLocation
            }

            if (Config.debug)
                Config.logWarn("New position in world \"${newLoc.world.name}\" at X: ${Config.coord.format(newLoc.x)} Y: ${Config.coord.format(newLoc.y)} Z: ${Config.coord.format(newLoc.z)}")

            if (notify)
                player.sendMessage(Config.messageComponent)

            return newLoc
        }

        private fun setPassengerDelayed(vehicle: Entity, player: Player, playerName: String, delay: Long) {
            Sched.runForEntityDelayed(vehicle, delay) {
                handlingPlayers.remove(playerName.lowercase())
                vehicle.addPassenger(player)
            }
        }
    }
}
