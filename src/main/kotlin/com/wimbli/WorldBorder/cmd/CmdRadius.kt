package com.wimbli.WorldBorder.cmd

import com.wimbli.WorldBorder.Config
import com.wimbli.WorldBorder.msg
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class CmdRadius : WBCmd() {
    init {
        name = "radius"
        permission = "radius"
        hasWorldNameInput = true
        minParams = 1
        maxParams = 2

        addCmdExample(nameEmphasizedW() + "<radiusX> [radiusZ] - change radius.")
        helpText = "Using this command you can adjust the radius of an existing border. If [radiusZ] is not " +
            "specified, the radiusX value will be used for both. You can also optionally specify + or - at the start " +
            "of <radiusX> and [radiusZ] to increase or decrease the existing radius rather than setting a new value."
    }

    override fun execute(sender: CommandSender, player: Player?, params: MutableList<String>, worldName: String?) {
        var curWorld = worldName

        if (curWorld == null)
            curWorld = player!!.world.name

        val border = Config.border(curWorld)
        if (border == null) {
            sendErrorAndHelp(sender, "This world (\"$curWorld\") must first have a border set normally.")
            return
        }

        val x = border.x
        val z = border.z
        val radiusX: Int
        val radiusZ: Int
        try {
            radiusX = if (params[0].startsWith("+")) {
                border.radiusX + params[0].substring(1).toInt()
            } else if (params[0].startsWith("-")) {
                border.radiusX - params[0].substring(1).toInt()
            } else {
                params[0].toInt()
            }

            radiusZ = if (params.size == 2) {
                if (params[1].startsWith("+")) {
                    border.radiusZ + params[1].substring(1).toInt()
                } else if (params[1].startsWith("-")) {
                    border.radiusZ - params[1].substring(1).toInt()
                } else {
                    params[1].toInt()
                }
            } else {
                radiusX
            }
        } catch (ex: NumberFormatException) {
            sendErrorAndHelp(sender, "The radius value(s) must be integers.")
            return
        }

        Config.setBorder(curWorld, radiusX, radiusZ, x, z)

        if (player != null)
            sender.msg("Radius has been set. " + Config.borderDescription(curWorld))
    }
}
