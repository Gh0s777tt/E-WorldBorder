package com.wimbli.WorldBorder.cmd

import com.wimbli.WorldBorder.Config
import com.wimbli.WorldBorder.msg
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class CmdGetmsg : WBCmd() {
    init {
        name = "getmsg"
        permission = "getmsg"
        minParams = 0
        maxParams = 0

        addCmdExample(nameEmphasized() + "- display border message.")
        helpText = "This command simply displays the message shown to players knocked back from the border."
    }

    override fun execute(sender: CommandSender, player: Player?, params: MutableList<String>, worldName: String?) {
        sender.msg("Border message is currently set to:")
        sender.msg(Config.messageRaw)
        sender.msg("Formatted border message:")
        sender.msg(Config.message)
    }
}
