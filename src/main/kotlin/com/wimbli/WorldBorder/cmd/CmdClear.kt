package com.wimbli.WorldBorder.cmd

import com.wimbli.WorldBorder.Config
import com.wimbli.WorldBorder.msg
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class CmdClear : WBCmd() {
    init {
        name = "clear"
        permission = "clear"
        hasWorldNameInput = true
        consoleRequiresWorldName = false
        minParams = 0
        maxParams = 1

        addCmdExample(nameEmphasizedW() + "- remove border for this world.")
        addCmdExample(nameEmphasized() + "^all - remove border for all worlds.")
        helpText = "If run by an in-game player and [world] or \"all\" isn't specified, the world you are currently " +
            "in is used."
    }

    override fun execute(sender: CommandSender, player: Player?, params: MutableList<String>, worldName: String?) {
        var curWorld = worldName

        // handle "clear all" command separately
        if (params.size == 1 && params[0].equals("all", ignoreCase = true)) {
            if (curWorld != null) {
                sendErrorAndHelp(sender, "You should not specify a world with \"clear all\".")
                return
            }

            Config.removeAllBorders()

            if (player != null)
                sender.msg("All borders have been cleared for all worlds.")
            return
        }

        if (curWorld == null) {
            if (player == null) {
                sendErrorAndHelp(sender, "You must specify a world name from console if not using \"clear all\".")
                return
            }
            curWorld = player.world.name
        }

        val border = Config.border(curWorld)
        if (border == null) {
            sendErrorAndHelp(sender, "This world (\"$curWorld\") does not have a border set.")
            return
        }

        Config.removeBorder(curWorld)

        if (player != null)
            sender.msg("Border cleared for world \"$curWorld\".")
    }
}
