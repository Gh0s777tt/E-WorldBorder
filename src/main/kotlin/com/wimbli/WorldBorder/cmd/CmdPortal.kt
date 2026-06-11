package com.wimbli.WorldBorder.cmd

import com.wimbli.WorldBorder.Config
import com.wimbli.WorldBorder.msg
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class CmdPortal : WBCmd() {
    init {
        name = "portal"
        permission = "portal"
        minParams = 1
        maxParams = 1

        addCmdExample(nameEmphasized() + "<on|off> - turn portal redirection on or off.")
        helpText = "Default value: on. This feature monitors new portal creation and changes the target new portal " +
            "location if it is outside of the border. Try disabling this if you have problems with other plugins " +
            "related to portals."
    }

    override fun cmdStatus(sender: CommandSender) {
        sender.msg(C_HEAD + "Portal redirection is " + enabledColored(Config.portalRedirection) + C_HEAD + ".")
    }

    override fun execute(sender: CommandSender, player: Player?, params: MutableList<String>, worldName: String?) {
        Config.setPortalRedirection(strAsBool(params[0]))

        if (player != null) {
            Config.log((if (Config.portalRedirection) "Enabled" else "Disabled") + " portal redirection at the command of player \"${player.name}\".")
            cmdStatus(sender)
        }
    }
}
