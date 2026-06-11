package com.wimbli.WorldBorder.cmd

import com.wimbli.WorldBorder.msg
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

// ----- color values as legacy section (§) codes; deserialized to Adventure Components on send -----
const val C_CMD: String = "§b"   // aqua        - main commands
const val C_DESC: String = "§f"  // white       - command descriptions
const val C_ERR: String = "§c"   // red         - errors / notices
const val C_HEAD: String = "§e"  // yellow      - command listing header
const val C_OPT: String = "§2"   // dark green  - optional values
const val C_REQ: String = "§a"   // green       - required values

private const val UNDERLINE = "§n"
private const val RESET = "§r"

// colorized root command, for console and for player
const val CMD_C: String = C_CMD + "wb "
const val CMD_P: String = C_CMD + "/wb "

// much like the per-command lists, but used to display the full command list from the root /wb command
val cmdExamplesConsole: MutableList<String> = ArrayList(48)
val cmdExamplesPlayer: MutableList<String> = ArrayList(48)

abstract class WBCmd {
    /*
     * Primary variables, set as needed in the subclass constructors.
     */

    // command name and command permission; normally the same thing
    @JvmField var name: String = ""
    @JvmField var permission: String? = null

    // whether the command can accept a world name before itself
    @JvmField var hasWorldNameInput: Boolean = false
    @JvmField var consoleRequiresWorldName: Boolean = true

    // minimum and maximum number of accepted parameters
    @JvmField var minParams: Int = 0
    @JvmField var maxParams: Int = 9999

    // help/explanation text shown after the command example(s)
    @JvmField var helpText: String? = null

    // command examples for this command (player and console variants), normally set via addCmdExample()
    val cmdExamplePlayer: MutableList<String> = ArrayList()
    val cmdExampleConsole: MutableList<String> = ArrayList()

    /*
     * The guts of the command; overridden in subclasses.
     */
    abstract fun execute(sender: CommandSender, player: Player?, params: MutableList<String>, worldName: String?)

    /*
     * Optional override, to provide extra command status info (like the currently set value).
     */
    open fun cmdStatus(sender: CommandSender) {}

    /*
     * Helper methods.
     */

    // add a command example for the "/wb" command list and for internal usage reference, formatted and colorized
    fun addCmdExample(example: String) = addCmdExample(example, forPlayer = true, forConsole = true, prefix = true)

    fun addCmdExample(exampleIn: String, forPlayer: Boolean, forConsole: Boolean, prefix: Boolean) {
        // colorize required "<>" and optional "[]" parameters, extra command words, and the description
        val example = exampleIn
            .replace("<", C_REQ + "<")
            .replace("[", C_OPT + "[")
            .replace("^", C_CMD)
            .replace("- ", C_DESC + "- ")

        // all "{}" are replaced by "[]" (optional) for player, "<>" (required) for console
        if (forPlayer) {
            val exampleP = (if (prefix) CMD_P else "") + example.replace("{", C_OPT + "[").replace("}", "]")
            cmdExamplePlayer.add(exampleP)
            cmdExamplesPlayer.add(exampleP)
        }
        if (forConsole) {
            val exampleC = (if (prefix) CMD_C else "") + example.replace("{", C_REQ + "<").replace("}", ">")
            cmdExampleConsole.add(exampleC)
            cmdExamplesConsole.add(exampleC)
        }
    }

    // return the root command formatted for player or console, based on sender
    fun cmd(sender: CommandSender): String = if (sender is Player) CMD_P else CMD_C

    // formatted and colorized text, intended for marking a command name
    fun commandEmphasized(text: String): String = C_CMD + UNDERLINE + text + RESET + " "

    // returns green "enabled" or red "disabled" text
    fun enabledColored(enabled: Boolean): String = if (enabled) C_REQ + "enabled" else C_ERR + "disabled"

    // formatted and colorized command name, optionally prefixed with "[world]"/"<world>"
    fun nameEmphasized(): String = commandEmphasized(name)
    fun nameEmphasizedW(): String = "{world} " + nameEmphasized()

    // send command example message(s) and other helpful info
    fun sendCmdHelp(sender: CommandSender) {
        for (example in (if (sender is Player) cmdExamplePlayer else cmdExampleConsole)) {
            sender.msg(example)
        }
        cmdStatus(sender)
        if (!helpText.isNullOrEmpty())
            sender.msg(C_DESC + helpText)
    }

    // send an error message followed by command example message(s)
    fun sendErrorAndHelp(sender: CommandSender, error: String) {
        sender.msg(C_ERR + error)
        sendCmdHelp(sender)
    }

    // interpret a string as a boolean value (yes/no, true/false, on/off, +/-, 1/0)
    fun strAsBool(str: String): Boolean {
        val s = str.lowercase()
        return s.startsWith("y") || s.startsWith("t") || s.startsWith("on") || s.startsWith("+") || s.startsWith("1")
    }
}
