package com.wimbli.WorldBorder.cmd

import com.wimbli.WorldBorder.Config
import com.wimbli.WorldBorder.msg
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class CmdPreventPlace : WBCmd() {
    init {
        name = "preventblockplace"
        permission = "preventblockplace"
        minParams = 1
        maxParams = 1

        addCmdExample(nameEmphasized() + "<on|off> - stop block placement past border.")
        helpText = "Default value: off. When enabled, this setting will prevent players from placing blocks outside the world's border."
    }

    override fun cmdStatus(sender: CommandSender) {
        sender.msg(C_HEAD + "Prevention of block placement outside the border is " + enabledColored(Config.preventBlockPlace) + C_HEAD + ".")
    }

    override fun execute(sender: CommandSender, player: Player?, params: MutableList<String>, worldName: String?) {
        Config.setPreventBlockPlace(strAsBool(params[0]))

        if (player != null) {
            Config.log("${if (Config.preventBlockPlace) "Enabled" else "Disabled"} preventblockplace at the command of player \"${player.name}\".")
            cmdStatus(sender)
        }
    }
}
