package com.wimbli.WorldBorder.cmd

import com.wimbli.WorldBorder.Config
import com.wimbli.WorldBorder.msg
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class CmdSetmsg : WBCmd() {
    init {
        name = "setmsg"
        permission = "setmsg"
        minParams = 1

        addCmdExample(nameEmphasized() + "<text> - set border message.")
        helpText = "Default value: \"&cYou have reached the edge of this world.\". This command lets you set the message shown to players who are knocked back from the border."
    }

    override fun cmdStatus(sender: CommandSender) {
        sender.msg(C_HEAD + "Border message is set to:")
        sender.msg(Config.messageRaw)
        sender.msg(C_HEAD + "Formatted border message:")
        sender.msg(Config.message)
    }

    override fun execute(sender: CommandSender, player: Player?, params: MutableList<String>, worldName: String?) {
        val message = StringBuilder()
        var first = true
        for (param in params) {
            if (!first)
                message.append(" ")
            message.append(param)
            first = false
        }

        Config.setMessage(message.toString())

        cmdStatus(sender)
    }
}
