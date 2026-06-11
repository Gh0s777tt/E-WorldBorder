package com.wimbli.WorldBorder.cmd

import com.wimbli.WorldBorder.Config
import com.wimbli.WorldBorder.msg
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class CmdKnockback : WBCmd() {
    init {
        name = "knockback"
        permission = "knockback"
        minParams = 1
        maxParams = 1

        addCmdExample(nameEmphasized() + "<distance> - how far to move the player back.")
        helpText = "Default value: 3.0 (blocks). Players who cross the border will be knocked back to this distance inside."
    }

    override fun cmdStatus(sender: CommandSender) {
        val kb = Config.knockBack
        if (kb < 1)
            sender.msg(C_HEAD + "Knockback is set to 0, disabling border enforcement.")
        else
            sender.msg(C_HEAD + "Knockback is set to $kb blocks inside the border.")
    }

    override fun execute(sender: CommandSender, player: Player?, params: MutableList<String>, worldName: String?) {
        val numBlocks: Double
        try {
            numBlocks = params[0].toDouble()
            if (numBlocks < 0.0 || (numBlocks > 0.0 && numBlocks < 1.0))
                throw NumberFormatException()
        } catch (ex: NumberFormatException) {
            sendErrorAndHelp(sender, "The knockback must be a decimal value of at least 1.0, or it can be 0.")
            return
        }

        Config.setKnockBack(numBlocks)

        if (player != null)
            cmdStatus(sender)
    }
}
