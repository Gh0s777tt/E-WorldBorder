package com.wimbli.WorldBorder.cmd

import com.wimbli.WorldBorder.Config
import com.wimbli.WorldBorder.msg
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class CmdWrap : WBCmd() {
    init {
        name = "wrap"
        permission = "wrap"
        minParams = 1
        maxParams = 2

        addCmdExample(nameEmphasized() + "{world} <on|off> - can make border crossings wrap.")
        helpText = "When border wrapping is enabled for a world, players will be sent around to the opposite edge " +
            "of the border when they cross it instead of being knocked back. [world] is optional for players and " +
            "defaults to the world the player is in."
    }

    override fun execute(sender: CommandSender, player: Player?, params: MutableList<String>, worldName: String?) {
        var curWorld = worldName

        if (player == null && params.size == 1) {
            sendErrorAndHelp(sender, "When running this command from console, you must specify a world.")
            return
        }

        val wrap: Boolean

        // world and wrap on/off specified
        if (params.size == 2) {
            curWorld = params[0]
            wrap = strAsBool(params[1])
        }
        // no world specified, just wrap on/off
        else {
            curWorld = player!!.world.name
            wrap = strAsBool(params[0])
        }

        val border = Config.border(curWorld)
        if (border == null) {
            sendErrorAndHelp(sender, "This world (\"" + curWorld + "\") does not have a border set.")
            return
        }

        border.wrapping = wrap
        Config.setBorder(curWorld, border, false)

        sender.msg("Border for world \"" + curWorld + "\" is now set to " + (if (wrap) "" else "not ") + "wrap around.")
    }
}
