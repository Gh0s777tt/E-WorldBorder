package com.wimbli.WorldBorder

import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter

/**
 * Tab completion for /wb. The original plugin shipped none.
 *
 * Position 1: subcommand names + world names (a world may precede a command, e.g. `/wb world radius 100`).
 * Position 2/3: command-specific values (on/off, shapes, player names, fill/trim actions, ...).
 */
class WBTabCompleter : TabCompleter {

    private val onOff = listOf("on", "off")
    private val shapeValues = listOf("elliptic", "rectangular", "round", "square")

    private fun worldNames(): List<String> = Bukkit.getWorlds().map { it.name }
    private fun commandNames(): List<String> = WorldBorder.wbCommand?.getCommandNames()?.toList() ?: emptyList()

    // suggestions for the value(s) of a given subcommand
    private fun valueCompletions(cmd: String): List<String> = when (cmd) {
        "whoosh", "portal", "denypearl", "preventblockplace", "preventmobspawn", "dynmap", "debug", "vanillaborder" -> onOff
        "wrap" -> worldNames() + onOff
        "shape" -> shapeValues
        "wshape" -> worldNames() + shapeValues + "default"
        "clear" -> worldNames() + "all"
        "bypass" -> Bukkit.getOnlinePlayers().map { it.name }
        "help" -> commandNames()
        "fill", "trim" -> listOf("cancel", "pause", "confirm")
        else -> emptyList()
    }

    override fun onTabComplete(sender: CommandSender, command: Command, label: String, args: Array<out String>): MutableList<String> {
        val isCommand = { s: String -> commandNames().any { it.equals(s, ignoreCase = true) } }
        val isWorld = { s: String -> worldNames().any { it.equals(s, ignoreCase = true) } }

        val suggestions: List<String> = when (args.size) {
            0, 1 -> commandNames() + worldNames()
            2 -> when {
                isCommand(args[0]) -> valueCompletions(args[0].lowercase())
                isWorld(args[0]) -> commandNames()
                else -> emptyList()
            }
            3 -> when {
                args[0].equals("bypass", ignoreCase = true) -> onOff
                isWorld(args[0]) && isCommand(args[1]) -> valueCompletions(args[1].lowercase())
                else -> emptyList()
            }
            else -> emptyList()
        }

        val prefix = args.lastOrNull() ?: ""
        return suggestions
            .filter { it.startsWith(prefix, ignoreCase = true) }
            .distinct()
            .sorted()
            .toMutableList()
    }
}
