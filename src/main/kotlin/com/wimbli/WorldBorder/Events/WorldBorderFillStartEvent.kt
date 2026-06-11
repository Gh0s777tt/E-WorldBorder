package com.wimbli.WorldBorder.Events

import com.wimbli.WorldBorder.WorldFillTask
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

class WorldBorderFillStartEvent(val fillTask: WorldFillTask) : Event() {

    override fun getHandlers(): HandlerList = handlerList

    companion object {
        private val handlerList = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = handlerList
    }
}
