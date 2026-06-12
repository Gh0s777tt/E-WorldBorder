package com.wimbli.WorldBorder

import org.bukkit.Bukkit
import kotlin.math.max
import kotlin.math.min

/**
 * Optional integration with Minecraft's built-in (client-side) world border, so players actually SEE a
 * red wall / screen overlay at the edge.
 *
 * The vanilla border is always square, so for elliptic or rectangular borders this is a square bounding-box
 * approximation; the plugin's own knockback still enforces the real shape. Vanilla border damage is disabled
 * (purely visual), so it never fights the plugin and never hurts bypassing players.
 */
object VanillaBorder {
    // Minecraft's maximum world border diameter
    private const val MAX_SIZE = 59_999_968.0

    // apply to every world that currently has a plugin border
    fun applyAll() {
        if (!Config.vanillaBorder) return
        for ((worldName, border) in Config.getBorders()) {
            apply(worldName, border)
        }
    }

    // mirror a plugin border onto the world's vanilla border (no-op if the feature is disabled).
    // animateSeconds > 0 makes the client smoothly grow/shrink to the new size (used on user changes).
    @JvmOverloads
    fun apply(worldName: String, border: BorderData, animateSeconds: Long = 0L) {
        if (!Config.vanillaBorder) return
        val world = Bukkit.getWorld(worldName) ?: return
        val wb = world.worldBorder
        wb.setCenter(border.x, border.z)
        // square bounding box of the (possibly elliptic/rectangular) plugin border
        val targetSize = min(2.0 * max(border.radiusX, border.radiusZ), MAX_SIZE)
        if (animateSeconds > 0L) {
            wb.setSize(targetSize, animateSeconds)
        } else {
            wb.size = targetSize
        }
        wb.damageAmount = 0.0 // visual only; the plugin handles enforcement
        wb.warningDistance = 16 // show the red overlay when getting close
    }

    // reset a single world's vanilla border back to the default
    fun remove(worldName: String) {
        Bukkit.getWorld(worldName)?.worldBorder?.reset()
    }

    // reset every world's vanilla border back to the default
    fun removeAll() {
        for (world in Bukkit.getWorlds()) {
            world.worldBorder.reset()
        }
    }
}
