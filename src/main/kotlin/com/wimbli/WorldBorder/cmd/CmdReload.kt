package com.wimbli.WorldBorder.cmd

import com.wimbli.WorldBorder.Config
import com.wimbli.WorldBorder.WorldBorder
import com.wimbli.WorldBorder.msg
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class CmdReload : WBCmd() {
    init {
        name = "reload"
        permission = "reload"
        minParams = 0
        maxParams = 0

        addCmdExample(nameEmphasized() + "- re-load data from config.yml.")
        helpText = "If you make manual changes to config.yml while the server is running, you can use this command " +
            "to make WorldBorder load the changes without needing to restart the server."
    }

    override fun execute(sender: CommandSender, player: Player?, params: MutableList<String>, worldName: String?) {
        if (player != null) {
            Config.log("Reloading config file at the command of player \"${player.name}\".")
        }

        Config.load(WorldBorder.plugin, true)

        if (player != null) {
            sender.msg("WorldBorder configuration reloaded.")
        }
    }
}
