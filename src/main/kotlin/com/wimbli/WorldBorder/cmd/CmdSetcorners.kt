package com.wimbli.WorldBorder.cmd

import com.wimbli.WorldBorder.Config
import com.wimbli.WorldBorder.msg
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class CmdSetcorners : WBCmd() {
    init {
        name = "setcorners"
        permission = "set"
        hasWorldNameInput = true
        minParams = 4
        maxParams = 4

        addCmdExample(nameEmphasizedW() + "<x1> <z1> <x2> <z2> - corner coords.")
        helpText = "This is an alternate way to set a border, by specifying the X and Z coordinates of two opposite " +
            "corners of the border area ((x1, z1) to (x2, z2)). [world] is optional for players and defaults to the " +
            "world the player is in."
    }

    override fun execute(sender: CommandSender, player: Player?, params: MutableList<String>, worldName: String?) {
        var curWorld = worldName

        if (curWorld == null) {
            curWorld = player!!.world.name
        } else {
            val worldTest = sender.server.getWorld(curWorld)
            if (worldTest == null)
                sender.msg("The world you specified (\"$curWorld\") could not be found on the server, but data for it will be stored anyway.")
        }

        try {
            val x1 = params[0].toDouble()
            val z1 = params[1].toDouble()
            val x2 = params[2].toDouble()
            val z2 = params[3].toDouble()
            Config.setBorderCorners(curWorld!!, x1, z1, x2, z2)
        } catch (ex: NumberFormatException) {
            sendErrorAndHelp(sender, "The x1, z1, x2, and z2 coordinate values must be numerical.")
            return
        }

        if (player != null)
            sender.msg("Border has been set. " + Config.borderDescription(curWorld!!))
    }
}
