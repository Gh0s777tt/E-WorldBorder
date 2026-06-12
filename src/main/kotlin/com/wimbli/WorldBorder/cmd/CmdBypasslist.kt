package com.wimbli.WorldBorder.cmd

import com.wimbli.WorldBorder.Config
import com.wimbli.WorldBorder.Sched
import com.wimbli.WorldBorder.UUID.UUIDFetcher
import com.wimbli.WorldBorder.msg
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class CmdBypasslist : WBCmd() {
    init {
        name = "bypasslist"
        permission = "bypasslist"
        minParams = 0
        maxParams = 0

        addCmdExample(nameEmphasized() + "- list players with border bypass enabled.")
        helpText = "The bypass list will persist between server restarts, and applies to all worlds. Use the " +
            commandEmphasized("bypass") + C_DESC + "command to add or remove players."
    }

    override fun execute(sender: CommandSender, player: Player?, params: MutableList<String>, worldName: String?) {
        val uuids = Config.playerBypassList()
        if (uuids.isEmpty()) {
            sender.msg("Players with border bypass enabled: <none>")
            return
        }

        // FIX: name lookups can hit the network, so run them off the main thread (the original ran them
        // inside a sync task, which could freeze the server).
        Sched.runAsync {
            try {
                val names = UUIDFetcher.getNameList(uuids)
                val nameString = names.values.toString()
                sender.msg("Players with border bypass enabled: " + nameString.substring(1, nameString.length - 1))
            } catch (ex: Exception) {
                sendErrorAndHelp(sender, "Failed to look up names for the UUIDs in the border bypass list. ${ex.localizedMessage}")
            }
        }
    }
}
