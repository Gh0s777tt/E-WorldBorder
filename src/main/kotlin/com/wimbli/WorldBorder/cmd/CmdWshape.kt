package com.wimbli.WorldBorder.cmd

import com.wimbli.WorldBorder.Config
import com.wimbli.WorldBorder.msg
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class CmdWshape : WBCmd() {
    init {
        name = "wshape"
        permission = "wshape"
        minParams = 1
        maxParams = 2

        addCmdExample(nameEmphasized() + "{world} <elliptic|rectangular|default> - shape")
        addCmdExample(C_DESC + "     override for a single world.", true, true, false)
        addCmdExample(nameEmphasized() + "{world} <round|square|default> - same as above.")
        helpText = "This will override the default border shape for a single world. The value \"default\" implies " +
            "a world is just using the default border shape. See the " + commandEmphasized("shape") + C_DESC +
            "command for more info and to set the default border shape."
    }

    override fun execute(sender: CommandSender, player: Player?, params: MutableList<String>, worldName: String?) {
        var curWorld = worldName

        if (player == null && params.size == 1) {
            sendErrorAndHelp(sender, "When running this command from console, you must specify a world.")
            return
        }

        val shapeName: String

        // world and shape specified
        if (params.size == 2) {
            curWorld = params[0]
            shapeName = params[1].lowercase()
        }
        // no world specified, just shape
        else {
            curWorld = player!!.world.name
            shapeName = params[0].lowercase()
        }

        val border = Config.border(curWorld)
        if (border == null) {
            sendErrorAndHelp(sender, "This world (\"" + curWorld + "\") does not have a border set.")
            return
        }

        val shape: Boolean? = when {
            shapeName == "rectangular" || shapeName == "square" -> false
            shapeName == "elliptic" || shapeName == "round" -> true
            else -> null
        }

        border.shape = shape
        Config.setBorder(curWorld, border, false)

        sender.msg("Border shape for world \"" + curWorld + "\" is now set to \"" + Config.shapeName(shape) + "\".")
    }
}
