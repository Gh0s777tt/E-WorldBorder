package com.wimbli.WorldBorder.cmd

import com.wimbli.WorldBorder.Config
import com.wimbli.WorldBorder.msg
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class CmdShape : WBCmd() {
    init {
        name = "shape"
        permission = "shape"
        minParams = 1
        maxParams = 1

        addCmdExample(nameEmphasized() + "<round|square> - set the default border shape.")
        addCmdExample(nameEmphasized() + "<elliptic|rectangular> - same as above.")
        helpText = "Default value: round/elliptic. The default border shape will be used on all worlds which don't " +
            "have an individual shape set using the " + commandEmphasized("wshape") + C_DESC + "command. Elliptic " +
            "and round work the same, as rectangular and square do. The difference is down to whether the X and Z " +
            "radius are the same."
    }

    override fun cmdStatus(sender: CommandSender) {
        sender.msg(C_HEAD + "The default border shape for all worlds is currently set to \"" + Config.shapeName() + "\".")
    }

    override fun execute(sender: CommandSender, player: Player?, params: MutableList<String>, worldName: String?) {
        val shape = params[0].lowercase()
        if (shape == "rectangular" || shape == "square")
            Config.setShape(false)
        else if (shape == "elliptic" || shape == "round")
            Config.setShape(true)
        else {
            sendErrorAndHelp(sender, "You must specify one of the 4 valid shape names below.")
            return
        }

        if (player != null)
            cmdStatus(sender)
    }
}
