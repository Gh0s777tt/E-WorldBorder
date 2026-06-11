package com.wimbli.WorldBorder.cmd

import com.wimbli.WorldBorder.Config
import com.wimbli.WorldBorder.msg
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class CmdDynmapmsg : WBCmd() {
    init {
        name = "dynmapmsg"
        permission = "dynmapmsg"
        minParams = 1

        addCmdExample(nameEmphasized() + "<text> - DynMap border labels will show this.")
        helpText = "Default value: \"The border of the world.\". If you are running the DynMap plugin and the " +
            commandEmphasized("dynmap") + C_DESC + "command setting is enabled, the borders shown in DynMap will " +
            "be labelled with this text."
    }

    override fun cmdStatus(sender: CommandSender) {
        sender.msg(C_HEAD + "DynMap border label is set to: " + C_ERR + Config.dynmapMessage)
    }

    override fun execute(sender: CommandSender, player: Player?, params: MutableList<String>, worldName: String?) {
        val message = params.joinToString(" ")

        Config.setDynmapMessage(message)

        if (player != null)
            cmdStatus(sender)
    }
}
