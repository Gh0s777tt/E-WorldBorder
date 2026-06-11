package com.wimbli.WorldBorder.cmd

import com.wimbli.WorldBorder.Config
import com.wimbli.WorldBorder.msg
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class CmdSet : WBCmd() {
    init {
        name = "set"
        permission = "set"
        hasWorldNameInput = true
        consoleRequiresWorldName = false
        minParams = 1
        maxParams = 4

        addCmdExample(nameEmphasizedW() + "<radiusX> [radiusZ] <x> <z> - use x/z coords.")
        addCmdExample(nameEmphasizedW() + "<radiusX> [radiusZ] ^spawn - use spawn point.")
        addCmdExample(nameEmphasized() + "<radiusX> [radiusZ] - set border, centered on you.", true, false, true)
        addCmdExample(nameEmphasized() + "<radiusX> [radiusZ] ^player <name> - center on player.")
        helpText = "Set a border for a world, with several options for defining the center location. [world] is " +
            "optional for players and defaults to the world the player is in. If [radiusZ] is not specified, the " +
            "radiusX value will be used for both. The <x> and <z> coordinates can be decimal values (ex. 1.234)."
    }

    override fun execute(sender: CommandSender, player: Player?, params: MutableList<String>, worldName: String?) {
        var curPlayer = player
        var curWorld = worldName

        // passing a single parameter (radiusX) is only acceptable from a player
        if (params.size == 1 && curPlayer == null) {
            sendErrorAndHelp(sender, "You have not provided a sufficient number of parameters.")
            return
        }

        // "set" command from player or console, world specified
        if (curWorld != null) {
            if (params.size == 2 && !params[params.size - 1].equals("spawn", ignoreCase = true)) {
                // command can only be this short if "spawn" is specified rather than x + z or a player name
                sendErrorAndHelp(sender, "You have not provided a sufficient number of arguments.")
                return
            }

            val world = sender.server.getWorld(curWorld)
            if (world == null) {
                if (params[params.size - 1].equals("spawn", ignoreCase = true)) {
                    sendErrorAndHelp(sender, "The world you specified (\"$curWorld\") could not be found on the server, so the spawn point cannot be determined.")
                    return
                }
                sender.msg("The world you specified (\"$curWorld\") could not be found on the server, but data for it will be stored anyway.")
            }
        }
        // from a player using the current world, or from console only if a player name is specified
        else {
            if (curPlayer == null) {
                if (!params[params.size - 2].equals("player", ignoreCase = true)) {
                    // can only be called by console without a world specified if a player is specified instead
                    sendErrorAndHelp(sender, "You must specify a world name from console if not specifying a player name.")
                    return
                }
                curPlayer = Bukkit.getPlayer(params[params.size - 1])
                if (curPlayer == null || !curPlayer.isOnline) {
                    sendErrorAndHelp(sender, "The player you specified (\"${params[params.size - 1]}\") does not appear to be online.")
                    return
                }
            }
            curWorld = curPlayer.world.name
        }

        val radiusX: Int
        val radiusZ: Int
        val x: Double
        val z: Double
        var radiusCount = params.size

        try {
            if (params[params.size - 1].equals("spawn", ignoreCase = true)) {
                // "spawn" specified for x/z coordinates
                val loc = sender.server.getWorld(curWorld!!)!!.spawnLocation
                x = loc.x
                z = loc.z
                radiusCount -= 1
            } else if (params.size > 2 && params[params.size - 2].equals("player", ignoreCase = true)) {
                // player name specified for x/z coordinates
                val playerT = Bukkit.getPlayer(params[params.size - 1])
                if (playerT == null || !playerT.isOnline) {
                    sendErrorAndHelp(sender, "The player you specified (\"${params[params.size - 1]}\") does not appear to be online.")
                    return
                }
                curWorld = playerT.world.name
                x = playerT.location.x
                z = playerT.location.z
                radiusCount -= 2
            } else {
                if (curPlayer == null || radiusCount > 2) {
                    // x and z specified
                    x = params[params.size - 2].toDouble()
                    z = params[params.size - 1].toDouble()
                    radiusCount -= 2
                } else {
                    // using the coordinates of the command sender (player)
                    x = curPlayer.location.x
                    z = curPlayer.location.z
                }
            }

            radiusX = params[0].toInt()
            radiusZ = if (radiusCount < 2) radiusX else params[1].toInt()

            if (radiusX < Config.knockBack || radiusZ < Config.knockBack) {
                sendErrorAndHelp(sender, "Radius value(s) must be more than the knockback distance.")
                return
            }
        } catch (ex: NumberFormatException) {
            sendErrorAndHelp(sender, "Radius value(s) must be integers and x and z values must be numerical.")
            return
        }

        Config.setBorder(curWorld!!, radiusX, radiusZ, x, z)
        sender.msg("Border has been set. " + Config.borderDescription(curWorld))
    }
}
