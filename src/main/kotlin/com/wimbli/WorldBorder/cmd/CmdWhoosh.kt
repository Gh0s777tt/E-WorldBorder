package com.wimbli.WorldBorder.cmd

import com.wimbli.WorldBorder.Config
import com.wimbli.WorldBorder.msg
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class CmdWhoosh : WBCmd() {
    init {
        name = "whoosh"
        permission = "whoosh"
        minParams = 1
        maxParams = 1

        addCmdExample(nameEmphasized() + "<on|off> - turn knockback effect on or off.")
        helpText = "Default value: on. This will show a particle effect and play a sound where a player is knocked " +
            "back from the border."
    }

    override fun cmdStatus(sender: CommandSender) {
        sender.msg(C_HEAD + "\"Whoosh\" knockback effect is " + enabledColored(Config.whooshEffect) + C_HEAD + ".")
    }

    override fun execute(sender: CommandSender, player: Player?, params: MutableList<String>, worldName: String?) {
        Config.setWhooshEffect(strAsBool(params[0]))

        if (player != null) {
            Config.log((if (Config.whooshEffect) "Enabled" else "Disabled") + " \"whoosh\" knockback effect at the command of player \"" + player.name + "\".")
            cmdStatus(sender)
        }
    }
}
