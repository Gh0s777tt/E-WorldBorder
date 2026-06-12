package com.wimbli.WorldBorder.cmd

import com.wimbli.WorldBorder.Config
import com.wimbli.WorldBorder.CoordXZ
import com.wimbli.WorldBorder.Sched
import com.wimbli.WorldBorder.WorldBorder
import com.wimbli.WorldBorder.WorldTrimTask
import com.wimbli.WorldBorder.msg
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class CmdTrim : WBCmd() {
    /* with "view-distance=10" in server.properties on a fast VM test server and "Render Distance: Far" in client,
     * hitting border during testing was loading 11+ chunks beyond the border in a couple of directions (10 chunks in
     * the other two directions). This could be worse on a more loaded or worse server, so:
     */
    private val defaultPadding: Int = CoordXZ.chunkToBlock(13)

    private var trimWorld = ""
    private var trimFrequency = 5000
    private var trimPadding = defaultPadding

    init {
        name = "trim"
        permission = "trim"
        hasWorldNameInput = true
        consoleRequiresWorldName = false
        minParams = 0
        maxParams = 2

        addCmdExample(nameEmphasizedW() + "[freq] [pad] - trim world outside of border.")
        helpText = "This command will remove chunks which are outside the world's border. [freq] is the frequency " +
            "of chunks per second that will be checked (default 5000). [pad] is the number of blocks padding kept " +
            "beyond the border itself (default 208, to cover player visual range)."
    }

    override fun execute(sender: CommandSender, player: Player?, params: MutableList<String>, worldName: String?) {
        var curWorld = worldName

        var confirm = false
        // check for "cancel", "pause", or "confirm"
        if (params.size >= 1) {
            val check = params[0].lowercase()

            if (check == "cancel" || check == "stop") {
                if (!makeSureTrimIsRunning(sender)) {
                    return
                }
                sender.msg(C_HEAD + "Cancelling the world map trimming task.")
                trimDefaults()
                Config.stopTrimTask()
                return
            } else if (check == "pause") {
                if (!makeSureTrimIsRunning(sender)) {
                    return
                }
                Config.trimTask?.pause()
                val isPaused = Config.trimTask?.isPaused() ?: false
                sender.msg(C_HEAD + "The world map trimming task is now " + (if (isPaused) "" else "un") + "paused.")
                return
            }

            confirm = check == "confirm"
        }

        // if not just confirming, make sure a world name is available
        if (curWorld == null && !confirm) {
            if (player != null) {
                curWorld = player.world.name
            } else {
                sendErrorAndHelp(sender, "You must specify a world!")
                return
            }
        }

        // colorized "/wb trim "
        val cmd = cmd(sender) + nameEmphasized() + C_CMD

        // make sure Trim isn't already running
        if (Config.trimTask?.valid() == true) {
            sender.msg(C_ERR + "The world map trimming task is already running.")
            sender.msg(C_DESC + "You can cancel at any time with " + cmd + "cancel" + C_DESC + ", or pause/unpause with " + cmd + "pause" + C_DESC + ".")
            return
        }

        // set frequency and/or padding if those were specified
        try {
            if (params.size >= 1 && !confirm) {
                trimFrequency = Math.abs(params[0].toInt())
            }
            if (params.size >= 2 && !confirm) {
                trimPadding = Math.abs(params[1].toInt())
            }
        } catch (ex: NumberFormatException) {
            sendErrorAndHelp(sender, "The frequency and padding values must be integers.")
            trimDefaults()
            return
        }
        if (trimFrequency <= 0) {
            sendErrorAndHelp(sender, "The frequency value must be greater than zero.")
            trimDefaults()
            return
        }

        // set world if it was specified
        if (curWorld != null) {
            trimWorld = curWorld
        }

        if (confirm) {
            // command confirmed, go ahead with it
            if (trimWorld.isEmpty()) {
                sendErrorAndHelp(sender, "You must first use this command successfully without confirming.")
                return
            }

            if (player != null) {
                Config.log("Trimming world beyond border at the command of player \"" + player.name + "\".")
            }

            var ticks = 1
            var repeats = 1
            if (trimFrequency > 20) {
                repeats = trimFrequency / 20
            } else {
                ticks = 20 / trimFrequency
            }

            Config.trimTask = WorldTrimTask(Bukkit.getServer(), player, trimWorld, trimPadding, repeats)
            val task = Config.trimTask!!
            if (task.valid()) {
                task.setTask(Sched.runRepeating(ticks.toLong(), ticks.toLong()) { task.run() })
                sender.msg("WorldBorder map trimming task for world \"" + trimWorld + "\" started.")
            } else {
                sender.msg(C_ERR + "The world map trimming task failed to start.")
            }

            trimDefaults()
        } else {
            if (trimWorld.isEmpty()) {
                sendErrorAndHelp(sender, "You must first specify a valid world.")
                return
            }

            sender.msg(C_HEAD + "World trimming task is ready for world \"" + trimWorld + "\", attempting to process up to " + trimFrequency + " chunks per second (default 20). The map will be trimmed past " + trimPadding + " blocks beyond the border (default " + defaultPadding + ").")
            sender.msg(C_HEAD + "This process can take a very long time depending on the world's overall size. Also, depending on the chunk processing rate, players may experience lag for the duration.")
            sender.msg(C_DESC + "You should now use " + cmd + "confirm" + C_DESC + " to start the process.")
            sender.msg(C_DESC + "You can cancel at any time with " + cmd + "cancel" + C_DESC + ", or pause/unpause with " + cmd + "pause" + C_DESC + ".")
        }
    }

    private fun trimDefaults() {
        trimWorld = ""
        trimFrequency = 5000
        trimPadding = defaultPadding
    }

    private fun makeSureTrimIsRunning(sender: CommandSender): Boolean {
        if (Config.trimTask?.valid() == true) {
            return true
        }
        sendErrorAndHelp(sender, "The world map trimming task is not currently running.")
        return false
    }
}
