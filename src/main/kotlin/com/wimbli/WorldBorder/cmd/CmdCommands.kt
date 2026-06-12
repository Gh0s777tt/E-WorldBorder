package com.wimbli.WorldBorder.cmd

import com.wimbli.WorldBorder.WorldBorder
import com.wimbli.WorldBorder.msg
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import kotlin.math.ceil
import kotlin.math.min

class CmdCommands : WBCmd() {
    init {
        name = "commands"
        permission = "help"
        hasWorldNameInput = false
    }

    override fun execute(sender: CommandSender, player: Player?, params: MutableList<String>, worldName: String?) {
        // determine which page we're viewing
        var page = if (player == null) 0 else 1
        if (params.isNotEmpty()) {
            try {
                page = params[0].toInt()
            } catch (ignored: NumberFormatException) {
            }
        }

        // showing examples to a player or the console, and how many pages are available
        val examples = if (player == null) cmdExamplesConsole else cmdExamplesPlayer
        val pageCount = ceil(examples.size / pageSize.toDouble()).toInt()

        // if the specified page number is negative or higher than available, default back to the first page
        if (page < 0 || page > pageCount) {
            page = if (player == null) 0 else 1
        }

        // send command example header ("<name> <version>")
        val meta = WorldBorder.plugin.pluginMeta
        sender.msg(
            C_HEAD + "${meta.name} ${meta.version}" + "  -  key: " +
                commandEmphasized("command") + C_REQ + "<required> " + C_OPT + "[optional]",
        )

        if (page > 0) {
            // send examples for this page
            val first = (page - 1) * pageSize
            val count = min(pageSize, examples.size - first)
            for (i in first until first + count) {
                sender.msg(examples[i])
            }

            // send page footer, if relevant
            val footer = C_HEAD + " (Page $page/$pageCount)              " + cmd(sender)
            if (page < pageCount) {
                sender.msg(footer + (page + 1).toString() + C_DESC + " - view next page of commands.")
            } else if (page > 1) {
                sender.msg(footer + C_DESC + "- view first page of commands.")
            }
        } else {
            // if page "0" is specified, send all examples (default for console, can be requested by a player)
            for (example in examples) {
                sender.msg(example)
            }
        }
    }

    companion object {
        private const val pageSize = 8 // examples per page; 10 lines available, 1 for header, 1 for footer
    }
}
