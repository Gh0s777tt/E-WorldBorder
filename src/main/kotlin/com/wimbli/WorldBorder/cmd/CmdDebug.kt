package com.wimbli.WorldBorder.cmd

import com.wimbli.WorldBorder.Config
import com.wimbli.WorldBorder.msg
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class CmdDebug : WBCmd() {
    init {
        name = "debug"
        permission = "debug"
        minParams = 1
        maxParams = 1

        addCmdExample(nameEmphasized() + "<on|off> - turn console debug output on or off.")
        helpText = "Default value: off. Debug mode will show some extra debugging data in the server console/log when " +
            "players are knocked back from the border or are teleported."
    }

    override fun cmdStatus(sender: CommandSender) {
        sender.msg(C_HEAD + "Debug mode is " + enabledColored(Config.debug) + C_HEAD + ".")
    }

    override fun execute(sender: CommandSender, player: Player?, params: MutableList<String>, worldName: String?) {
        Config.setDebug(strAsBool(params[0]))

        if (player != null) {
            Config.log("${if (Config.debug) "Enabled" else "Disabled"} debug output at the command of player \"${player.name}\".")
            cmdStatus(sender)
        }
    }
}
