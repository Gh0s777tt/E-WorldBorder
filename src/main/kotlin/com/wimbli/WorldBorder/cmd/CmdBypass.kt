package com.wimbli.WorldBorder.cmd

import com.wimbli.WorldBorder.Config
import com.wimbli.WorldBorder.Sched
import com.wimbli.WorldBorder.UUID.UUIDFetcher
import com.wimbli.WorldBorder.msg
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.util.UUID

class CmdBypass : WBCmd() {
    init {
        name = "bypass"
        permission = "bypass"
        minParams = 0
        maxParams = 2

        addCmdExample(nameEmphasized() + "{player} [on|off] - let player go beyond border.")
        helpText = "If [player] isn't specified, the command sender is used. If [on|off] isn't specified, the value " +
            "will be toggled. Once bypass is enabled, the player will not be stopped by any borders until bypass is " +
            "disabled for them again. Use the " + commandEmphasized("bypasslist") + C_DESC + "command to list all " +
            "players with bypass enabled."
    }

    override fun cmdStatus(sender: CommandSender) {
        if (sender !is Player) return
        val bypass = Config.isPlayerBypassing(sender.uniqueId)
        sender.msg(C_HEAD + "Border bypass is currently " + enabledColored(bypass) + C_HEAD + " for you.")
    }

    override fun execute(sender: CommandSender, player: Player?, params: MutableList<String>, worldName: String?) {
        if (player == null && params.isEmpty()) {
            sendErrorAndHelp(sender, "When running this command from console, you must specify a player.")
            return
        }

        val targetName = if (params.isEmpty()) player!!.name else params[0]
        // explicit on/off if provided, otherwise we toggle once the UUID is known
        val explicit: Boolean? = if (params.size > 1) strAsBool(params[1]) else null

        // resolve the UUID without blocking when possible: self, or an online player
        val directUuid: UUID? = when {
            params.isEmpty() -> player!!.uniqueId
            else -> Bukkit.getPlayerExact(targetName)?.uniqueId
        }

        if (directUuid != null) {
            applyBypass(sender, player, targetName, directUuid, explicit)
            return
        }

        // FIX: only do the (blocking) Mojang UUID lookup off the main thread, then apply back on the main thread.
        // The original ran this lookup inside a sync task, freezing the server for offline-player lookups.
        Sched.runAsync {
            val uuid: UUID? = try {
                UUIDFetcher.getUUID(targetName)
            } catch (ex: Exception) {
                sendErrorAndHelp(sender, "Failed to look up UUID for the player name you specified. ${ex.localizedMessage}")
                return@runAsync
            }
            if (uuid == null) {
                sendErrorAndHelp(sender, "Failed to look up UUID for the player name you specified; null value returned.")
                return@runAsync
            }
            Sched.runGlobal {
                applyBypass(sender, player, targetName, uuid, explicit)
            }
        }
    }

    private fun applyBypass(sender: CommandSender, player: Player?, targetName: String, uuid: UUID, explicit: Boolean?) {
        val bypassing = explicit ?: !Config.isPlayerBypassing(uuid)
        Config.setPlayerBypass(uuid, bypassing)

        val target = Bukkit.getPlayerExact(targetName)
        if (target != null && target.isOnline)
            target.msg("Border bypass is now " + enabledColored(bypassing) + ".")

        Config.log(
            "Border bypass for player \"$targetName\" is " + (if (bypassing) "enabled" else "disabled") +
                (if (player != null) " at the command of player \"${player.name}\"" else "") + "."
        )
        if (player != null && player !== target)
            sender.msg("Border bypass for player \"$targetName\" is " + enabledColored(bypassing) + ".")
    }
}
