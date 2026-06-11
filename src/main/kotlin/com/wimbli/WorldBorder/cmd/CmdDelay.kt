package com.wimbli.WorldBorder.cmd

import com.wimbli.WorldBorder.Config
import com.wimbli.WorldBorder.msg
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class CmdDelay : WBCmd() {
    init {
        name = "delay"
        permission = "delay"
        minParams = 1
        maxParams = 1

        addCmdExample(nameEmphasized() + "<amount> - time between border checks.")
        helpText = "Default value: 5. The <amount> is in server ticks, of which there are roughly 20 every second, each " +
            "tick taking ~50ms. The default value therefore has border checks run about 4 times per second."
    }

    override fun cmdStatus(sender: CommandSender) {
        val delay = Config.timerTicks
        sender.msg(C_HEAD + "Timer delay is set to $delay tick(s). That is roughly ${delay * 50}ms.")
    }

    override fun execute(sender: CommandSender, player: Player?, params: MutableList<String>, worldName: String?) {
        val delay: Int
        try {
            delay = params[0].toInt()
            if (delay < 1)
                throw NumberFormatException()
        } catch (ex: NumberFormatException) {
            sendErrorAndHelp(sender, "The timer delay must be an integer of 1 or higher.")
            return
        }

        Config.setTimerTicks(delay)

        if (player != null)
            cmdStatus(sender)
    }
}
