package com.wimbli.WorldBorder.cmd

import com.wimbli.WorldBorder.WorldBorder
import com.wimbli.WorldBorder.msg
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class CmdHelp : WBCmd() {
    init {
        name = "help"
        permission = "help"
        minParams = 0
        maxParams = 10

        addCmdExample(nameEmphasized() + "[command] - get help on command usage.")
//      helpText = "If [command] is specified, info for that particular command will be provided."
    }

    override fun cmdStatus(sender: CommandSender) {
        val rawCommands = WorldBorder.wbCommand!!.getCommandNames().toString()
        val commands = rawCommands.replace(", ", C_DESC + ", " + C_CMD)
        sender.msg(C_HEAD + "Commands: " + C_CMD + commands.substring(1, commands.length - 1))
        sender.msg("Example, for info on \"set\" command: " + cmd(sender) + nameEmphasized() + C_CMD + "set")
        sender.msg(C_HEAD + "For a full command example list, simply run the root " + cmd(sender) + C_HEAD + "command by itself with nothing specified.")
    }

    override fun execute(sender: CommandSender, player: Player?, params: MutableList<String>, worldName: String?) {
        if (params.isEmpty()) {
            sendCmdHelp(sender)
            return
        }

        val commands = WorldBorder.wbCommand!!.getCommandNames()
        for (param in params) {
            if (commands.contains(param.lowercase())) {
                WorldBorder.wbCommand!!.subCommands[param.lowercase()]!!.sendCmdHelp(sender)
                return
            }
        }
        sendErrorAndHelp(sender, "No command recognized.")
    }
}
