package com.wimbli.WorldBorder.cmd

import com.wimbli.WorldBorder.Config
import com.wimbli.WorldBorder.msg
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class CmdPreventSpawn : WBCmd() {
    init {
        name = "preventmobspawn"
        permission = "preventmobspawn"
        minParams = 1
        maxParams = 1

        addCmdExample(nameEmphasized() + "<on|off> - stop mob spawning past border.")
        helpText = "Default value: off. When enabled, this setting will prevent mobs from naturally spawning outside the world's border."
    }

    override fun cmdStatus(sender: CommandSender) {
        sender.msg(C_HEAD + "Prevention of mob spawning outside the border is " + enabledColored(Config.preventMobSpawn) + C_HEAD + ".")
    }

    override fun execute(sender: CommandSender, player: Player?, params: MutableList<String>, worldName: String?) {
        Config.setPreventMobSpawn(strAsBool(params[0]))

        if (player != null) {
            Config.log("${if (Config.preventMobSpawn) "Enabled" else "Disabled"} preventmobspawn at the command of player \"${player.name}\".")
            cmdStatus(sender)
        }
    }
}
