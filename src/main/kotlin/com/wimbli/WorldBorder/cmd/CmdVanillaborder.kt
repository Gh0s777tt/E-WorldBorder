package com.wimbli.WorldBorder.cmd

import com.wimbli.WorldBorder.Config
import com.wimbli.WorldBorder.msg
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class CmdVanillaborder : WBCmd() {
    init {
        name = "vanillaborder"
        permission = "vanillaborder"
        minParams = 1
        maxParams = 1

        addCmdExample(nameEmphasized() + "<on|off> - show the border as the client-side red wall.")
        helpText = "When enabled, the plugin also sets Minecraft's built-in world border so players see the red " +
            "warning wall/overlay at the edge. The vanilla border is square-only, so for round/rectangular borders " +
            "it is a square approximation; the plugin's knockback still enforces the real shape. Vanilla border " +
            "damage is disabled, so it is purely visual."
    }

    override fun cmdStatus(sender: CommandSender) {
        sender.msg(C_HEAD + "Vanilla border display is currently " + enabledColored(Config.vanillaBorder) + C_HEAD + ".")
    }

    override fun execute(sender: CommandSender, player: Player?, params: MutableList<String>, worldName: String?) {
        Config.setVanillaBorder(strAsBool(params[0]))
        cmdStatus(sender)
    }
}
