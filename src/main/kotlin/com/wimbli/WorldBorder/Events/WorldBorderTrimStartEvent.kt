package com.wimbli.WorldBorder.Events

import com.wimbli.WorldBorder.WorldTrimTask
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

class WorldBorderTrimStartEvent(val trimTask: WorldTrimTask) : Event() {

    override fun getHandlers(): HandlerList = handlerList

    companion object {
        private val handlerList = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = handlerList
    }
}
