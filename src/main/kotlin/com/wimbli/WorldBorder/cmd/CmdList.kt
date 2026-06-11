package com.wimbli.WorldBorder.cmd

import com.wimbli.WorldBorder.Config
import com.wimbli.WorldBorder.msg
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class CmdList : WBCmd() {
    init {
        name = "list"
        permission = "list"
        minParams = 0
        maxParams = 0

        addCmdExample(nameEmphasized() + "- show border information for all worlds.")
        helpText = "This command will list full information for every border you have set including position, " +
            "radius, and shape. The default border shape will also be indicated."
    }

    override fun execute(sender: CommandSender, player: Player?, params: MutableList<String>, worldName: String?) {
        sender.msg("Default border shape for all worlds is \"" + Config.shapeName() + "\".")

        val list = Config.borderDescriptions()

        if (list.isEmpty()) {
            sender.msg("There are no borders currently set.")
            return
        }

        for (borderDesc in list) {
            sender.msg(borderDesc)
        }
    }
}
