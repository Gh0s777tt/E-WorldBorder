package com.wimbli.WorldBorder

import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import org.bukkit.Bukkit
import org.bukkit.entity.Entity

/**
 * Thin wrapper over Paper's modern schedulers (GlobalRegionScheduler / AsyncScheduler / EntityScheduler).
 * These exist on regular Paper too, so the same code runs on both Paper and Folia — the plugin is
 * Folia-compatible without a separate code path (no use of the legacy single-threaded BukkitScheduler).
 */
object Sched {
    private val plugin get() = WorldBorder.plugin

    /** Repeating task on the global region (e.g. the border-check timer, fill/trim loops). */
    fun runRepeating(initialDelayTicks: Long, periodTicks: Long, task: () -> Unit): ScheduledTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(
        plugin,
        { task() },
        initialDelayTicks.coerceAtLeast(1L),
        periodTicks.coerceAtLeast(1L),
    )

    /** Run once on the global region thread (next tick). */
    fun runGlobal(task: () -> Unit) {
        Bukkit.getGlobalRegionScheduler().run(plugin) { task() }
    }

    /** Run off the main/region threads (for blocking I/O such as Mojang lookups). */
    fun runAsync(task: () -> Unit) {
        Bukkit.getAsyncScheduler().runNow(plugin) { task() }
    }

    /** Run a task on a specific entity's region thread (required on Folia for e.g. teleporting it). */
    fun runForEntity(entity: Entity, task: () -> Unit) {
        entity.scheduler.run(plugin, { task() }, null)
    }

    /** Delayed task on a specific entity's region thread (e.g. remounting a vehicle). */
    fun runForEntityDelayed(entity: Entity, delayTicks: Long, task: () -> Unit) {
        entity.scheduler.runDelayed(plugin, { task() }, null, delayTicks.coerceAtLeast(1L))
    }
}
