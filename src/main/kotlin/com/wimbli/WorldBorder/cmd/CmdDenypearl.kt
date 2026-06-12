package com.wimbli.WorldBorder.cmd

import com.wimbli.WorldBorder.Config
import com.wimbli.WorldBorder.msg
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class CmdDenypearl : WBCmd() {
    init {
        name = "denypearl"
        permission = "denypearl"
        minParams = 1
        maxParams = 1

        addCmdExample(nameEmphasized() + "<on|off> - stop ender pearls past the border.")
        helpText = "Default value: on. When enabled, this setting will directly cancel attempts to use an ender pearl to " +
            "get past the border rather than just knocking the player back. This should prevent usage of ender " +
            "pearls to glitch into areas otherwise inaccessible at the border edge."
    }

    override fun cmdStatus(sender: CommandSender) {
        sender.msg(
            C_HEAD + "Direct cancellation of ender pearls thrown past the border is " +
                enabledColored(Config.denyEnderpearl) + C_HEAD + ".",
        )
    }

    override fun execute(sender: CommandSender, player: Player?, params: MutableList<String>, worldName: String?) {
        Config.setDenyEnderpearl(strAsBool(params[0]))

        if (player != null) {
            Config.log("${if (Config.denyEnderpearl) "Enabled" else "Disabled"} direct cancellation of ender pearls thrown past the border at the command of player \"${player.name}\".")
            cmdStatus(sender)
        }
    }
}
