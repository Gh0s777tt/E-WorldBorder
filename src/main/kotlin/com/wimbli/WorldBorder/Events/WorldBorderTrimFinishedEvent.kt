package com.wimbli.WorldBorder.Events

import org.bukkit.World
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

class WorldBorderTrimFinishedEvent(val world: World, val totalChunks: Long) : Event() {

    override fun getHandlers(): HandlerList = handlerList

    companion object {
        private val handlerList = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = handlerList
    }
}
