package com.wimbli.WorldBorder

import com.wimbli.WorldBorder.cmd.C_ERR
import com.wimbli.WorldBorder.cmd.CmdBypass
import com.wimbli.WorldBorder.cmd.CmdBypasslist
import com.wimbli.WorldBorder.cmd.CmdClear
import com.wimbli.WorldBorder.cmd.CmdCommands
import com.wimbli.WorldBorder.cmd.CmdDebug
import com.wimbli.WorldBorder.cmd.CmdDelay
import com.wimbli.WorldBorder.cmd.CmdDenypearl
import com.wimbli.WorldBorder.cmd.CmdDynmap
import com.wimbli.WorldBorder.cmd.CmdDynmapmsg
import com.wimbli.WorldBorder.cmd.CmdFill
import com.wimbli.WorldBorder.cmd.CmdFillautosave
import com.wimbli.WorldBorder.cmd.CmdGetmsg
import com.wimbli.WorldBorder.cmd.CmdHelp
import com.wimbli.WorldBorder.cmd.CmdKnockback
import com.wimbli.WorldBorder.cmd.CmdList
import com.wimbli.WorldBorder.cmd.CmdPortal
import com.wimbli.WorldBorder.cmd.CmdPreventPlace
import com.wimbli.WorldBorder.cmd.CmdPreventSpawn
import com.wimbli.WorldBorder.cmd.CmdRadius
import com.wimbli.WorldBorder.cmd.CmdReload
import com.wimbli.WorldBorder.cmd.CmdRemount
import com.wimbli.WorldBorder.cmd.CmdSet
import com.wimbli.WorldBorder.cmd.CmdSetcorners
import com.wimbli.WorldBorder.cmd.CmdSetmsg
import com.wimbli.WorldBorder.cmd.CmdShape
import com.wimbli.WorldBorder.cmd.CmdTrim
import com.wimbli.WorldBorder.cmd.CmdVanillaborder
import com.wimbli.WorldBorder.cmd.CmdWhoosh
import com.wimbli.WorldBorder.cmd.CmdWrap
import com.wimbli.WorldBorder.cmd.CmdWshape
import com.wimbli.WorldBorder.cmd.WBCmd
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.util.LinkedHashMap
import java.util.LinkedHashSet
import java.util.TreeSet

class WBCommand : CommandExecutor {
    // map of all sub-commands, keyed by command name for quick reference
    val subCommands: MutableMap<String, WBCmd> = LinkedHashMap()
    // commands which can have a world name in front of the command itself (ex. /wb _world_ radius 100)
    private val subCommandsWithWorldNames: MutableSet<String> = LinkedHashSet()

    init {
        addCmd(CmdHelp())
        addCmd(CmdSet())
        addCmd(CmdSetcorners())
        addCmd(CmdRadius())
        addCmd(CmdList())
        addCmd(CmdShape())
        addCmd(CmdClear())
        addCmd(CmdFill())
        addCmd(CmdTrim())
        addCmd(CmdBypass())
        addCmd(CmdBypasslist())
        addCmd(CmdKnockback())
        addCmd(CmdWrap())
        addCmd(CmdWhoosh())
        addCmd(CmdGetmsg())
        addCmd(CmdSetmsg())
        addCmd(CmdWshape())
        addCmd(CmdPreventPlace())
        addCmd(CmdPreventSpawn())
        addCmd(CmdVanillaborder())
        addCmd(CmdDelay())
        addCmd(CmdDynmap())
        addCmd(CmdDynmapmsg())
        addCmd(CmdRemount())
        addCmd(CmdFillautosave())
        addCmd(CmdPortal())
        addCmd(CmdDenypearl())
        addCmd(CmdReload())
        addCmd(CmdDebug())

        // default command, which shows command example pages; should be last just in case
        addCmd(CmdCommands())
    }

    private fun addCmd(cmd: WBCmd) {
        subCommands[cmd.name] = cmd
        if (cmd.hasWorldNameInput)
            subCommandsWithWorldNames.add(cmd.name)
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, split: Array<out String>): Boolean {
        val player = sender as? Player

        // if a world name is passed in quotation marks, handle that and get a List<String> instead of String[]
        val params = concatenateQuotedWorldName(split)

        var worldName: String? = null
        // is the second parameter the command and the first a world name? definitely a world name if it was quoted
        if (wasWorldQuotation || (params.size > 1 && !subCommands.containsKey(params[0]) && subCommandsWithWorldNames.contains(params[1])))
            worldName = params[0]

        // no command specified? show command examples / help
        if (params.isEmpty())
            params.add(0, "commands")

        // determine the command name
        var cmdName = if (worldName == null) params[0].lowercase() else params[1].lowercase()

        // remove command name and (if present) world name from the front of the param list
        params.removeAt(0)
        if (worldName != null)
            params.removeAt(0)

        // make sure the command is recognized; default to showing command examples / help if not, also check for a page number
        if (!subCommands.containsKey(cmdName)) {
            var page = if (player == null) 0 else 1
            try {
                page = cmdName.toInt()
            } catch (ignored: NumberFormatException) {
                sender.sendMessage(C_ERR + "Command not recognized. Showing command list.")
            }
            cmdName = "commands"
            params.add(0, page.toString())
        }

        val subCommand = subCommands[cmdName]!!

        // check permission
        if (!Config.hasPermission(player, subCommand.permission ?: subCommand.name))
            return true

        // if the command requires a world name when run by console, make sure that's in place
        if (player == null && subCommand.hasWorldNameInput && subCommand.consoleRequiresWorldName && worldName == null) {
            sender.sendMessage(C_ERR + "This command requires a world to be specified if run by the console.")
            subCommand.sendCmdHelp(sender)
            return true
        }

        // make sure a valid number of parameters has been provided
        if (params.size < subCommand.minParams || params.size > subCommand.maxParams) {
            if (subCommand.maxParams == 0)
                sender.sendMessage(C_ERR + "This command does not accept any parameters.")
            else
                sender.sendMessage(C_ERR + "You have not provided a valid number of parameters.")
            subCommand.sendCmdHelp(sender)
            return true
        }

        // execute the command
        subCommand.execute(sender, player, params, worldName)

        return true
    }

    private var wasWorldQuotation = false

    // if a world name is surrounded by quotation marks, combine it down and flag wasWorldQuotation if it's the first param
    private fun concatenateQuotedWorldName(split: Array<out String>): MutableList<String> {
        wasWorldQuotation = false
        var args: MutableList<String> = split.toMutableList()

        var startIndex = -1
        for (i in args.indices) {
            if (args[i].startsWith("\"")) {
                startIndex = i
                break
            }
        }
        if (startIndex == -1) return args

        if (args[startIndex].endsWith("\"")) {
            args[startIndex] = args[startIndex].substring(1, args[startIndex].length - 1)
            if (startIndex == 0)
                wasWorldQuotation = true
        } else {
            val concat = ArrayList(args)
            val concatI = concat.iterator()

            // skip past any parameters in front of the one we're starting on
            for (i in 1 until startIndex + 1) {
                concatI.next()
            }

            val quote = StringBuilder(concatI.next())
            while (concatI.hasNext()) {
                val next = concatI.next()
                concatI.remove()
                quote.append(" ")
                quote.append(next)
                if (next.endsWith("\"")) {
                    concat[startIndex] = quote.substring(1, quote.length - 1)
                    args = concat
                    if (startIndex == 0)
                        wasWorldQuotation = true
                    break
                }
            }
        }
        return args
    }

    fun getCommandNames(): Set<String> {
        // TreeSet to sort alphabetically
        val commands = TreeSet(subCommands.keys)
        // remove the default "commands" command as it's not normally shown or run like the others
        commands.remove("commands")
        return commands
    }
}
