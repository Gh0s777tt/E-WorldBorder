package com.wimbli.WorldBorder.cmd

import com.wimbli.WorldBorder.Config
import com.wimbli.WorldBorder.msg
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class CmdDynmap : WBCmd() {
    init {
        name = "dynmap"
        permission = "dynmap"
        minParams = 1
        maxParams = 1

        addCmdExample(nameEmphasized() + "<on|off> - turn DynMap border display on or off.")
        helpText = "Default value: on. If you are running the DynMap plugin and this setting is enabled, all borders will " +
            "be visually shown in DynMap."
    }

    override fun cmdStatus(sender: CommandSender) {
        sender.msg(C_HEAD + "DynMap border display is " + enabledColored(Config.dynmapEnabled) + C_HEAD + ".")
    }

    override fun execute(sender: CommandSender, player: Player?, params: MutableList<String>, worldName: String?) {
        Config.setDynmapBorderEnabled(strAsBool(params[0]))

        if (player != null) {
            cmdStatus(sender)
            Config.log("${if (Config.dynmapEnabled) "Enabled" else "Disabled"} DynMap border display at the command of player \"${player.name}\".")
        }
    }
}
