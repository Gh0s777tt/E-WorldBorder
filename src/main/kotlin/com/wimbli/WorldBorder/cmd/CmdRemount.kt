package com.wimbli.WorldBorder.cmd

import com.wimbli.WorldBorder.Config
import com.wimbli.WorldBorder.msg
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class CmdRemount : WBCmd() {
    init {
        name = "remount"
        permission = "remount"
        minParams = 1
        maxParams = 1

        addCmdExample(nameEmphasized() + "<amount> - player remount delay after knockback.")
        helpText = "Default value: 0 (disabled). If set higher than 0, WorldBorder will attempt to re-mount players who " +
            "are knocked back from the border while riding something after this many server ticks. This setting can " +
            "cause really nasty glitches if enabled and set too low due to CraftBukkit teleportation problems."
    }

    override fun cmdStatus(sender: CommandSender) {
        val delay = Config.remountTicks
        if (delay == 0) {
            sender.msg(C_HEAD + "Remount delay set to 0. Players will be left dismounted when knocked back from the border while on a vehicle.")
        } else {
            sender.msg(C_HEAD + "Remount delay set to $delay tick(s). That is roughly ${delay * 50}ms / ${delay * 50.0 / 1000.0} seconds. Setting to 0 would disable remounting.")
            if (delay < 10) {
                sender.msg(C_ERR + "WARNING:" + C_DESC + " setting this to less than 10 (and greater than 0) is not recommended. This can lead to nasty client glitches.")
            }
        }
    }

    override fun execute(sender: CommandSender, player: Player?, params: MutableList<String>, worldName: String?) {
        val delay: Int
        try {
            delay = params[0].toInt()
            if (delay < 0) {
                throw NumberFormatException()
            }
        } catch (ex: NumberFormatException) {
            sendErrorAndHelp(sender, "The remount delay must be an integer of 0 or higher. Setting to 0 will disable remounting.")
            return
        }

        Config.setRemountTicks(delay)

        if (player != null) {
            cmdStatus(sender)
        }
    }
}
