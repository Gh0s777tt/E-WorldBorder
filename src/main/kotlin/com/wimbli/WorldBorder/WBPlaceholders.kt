package com.wimbli.WorldBorder

import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player

/**
 * PlaceholderAPI expansion. Only loaded/registered when PlaceholderAPI is installed.
 *
 * Placeholders (resolved against the player's current world border):
 *   %worldborder_radius%   %worldborder_radiusx%  %worldborder_radiusz%
 *   %worldborder_centerx%  %worldborder_centerz%
 *   %worldborder_shape%    %worldborder_wrapping% %worldborder_world%
 *   %worldborder_bypassing%
 */
class WBPlaceholders : PlaceholderExpansion() {

    override fun getIdentifier(): String = "worldborder"
    override fun getAuthor(): String = "Brettflan"
    override fun getVersion(): String = "2.0.0"
    override fun persist(): Boolean = true   // keep registered across PlaceholderAPI reloads

    override fun onRequest(player: OfflinePlayer?, params: String): String? {
        val online = player as? Player
        val worldName = online?.world?.name
        val border = worldName?.let { Config.border(it) }

        return when (params.lowercase()) {
            "bypassing" -> (online != null && Config.isPlayerBypassing(online.uniqueId)).toString()
            "world" -> worldName ?: ""
            "radius" -> border?.let { ((it.radiusX + it.radiusZ) / 2).toString() } ?: ""
            "radiusx" -> border?.radiusX?.toString() ?: ""
            "radiusz" -> border?.radiusZ?.toString() ?: ""
            "centerx" -> border?.let { Config.coord.format(it.x) } ?: ""
            "centerz" -> border?.let { Config.coord.format(it.z) } ?: ""
            "shape" -> if (border == null) "" else Config.shapeName(border.shape ?: Config.shapeRound)
            "wrapping" -> border?.wrapping?.toString() ?: ""
            else -> null   // null = unknown placeholder, lets PlaceholderAPI fall through
        }
    }
}
