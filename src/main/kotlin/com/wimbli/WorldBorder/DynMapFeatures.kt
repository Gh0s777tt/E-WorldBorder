package com.wimbli.WorldBorder

import org.bukkit.Bukkit
import org.dynmap.DynmapAPI
import org.dynmap.markers.AreaMarker
import org.dynmap.markers.CircleMarker
import org.dynmap.markers.MarkerAPI
import org.dynmap.markers.MarkerSet

object DynMapFeatures {
    private var api: DynmapAPI? = null
    private var markApi: MarkerAPI? = null
    private var markSet: MarkerSet? = null
    private const val lineWeight = 3
    private const val lineOpacity = 1.0
    private const val lineColor = 0xFF0000

    private val roundBorders = HashMap<String, CircleMarker>()
    private val squareBorders = HashMap<String, AreaMarker>()

    // Whether re-rendering functionality is available
    fun renderEnabled(): Boolean = api != null

    // Whether circular border markers are available
    fun borderEnabled(): Boolean = markApi != null

    fun setup() {
        val test = Bukkit.getServer().pluginManager.getPlugin("dynmap")
        if (test == null || !test.isEnabled) return

        val dynmap = test as DynmapAPI
        api = dynmap

        // make sure DynMap version is new enough to include circular markers
        try {
            Class.forName("org.dynmap.markers.CircleMarker")
            // for version 0.35 of DynMap, CircleMarkers were bugged (center always 0,0)
            if (dynmap.dynmapVersion.startsWith("0.35-"))
                throw ClassNotFoundException()
        } catch (ex: ClassNotFoundException) {
            Config.logConfig("DynMap is available, but border display is currently disabled: you need DynMap v0.36 or newer.")
            return
        } catch (ex: NullPointerException) {
            Config.logConfig("DynMap is present, but an NPE (type 1) was encountered while trying to integrate. Border display disabled.")
            return
        }

        try {
            val ma = dynmap.markerAPI ?: return
            markApi = ma
        } catch (ex: NullPointerException) {
            Config.logConfig("DynMap is present, but an NPE (type 2) was encountered while trying to integrate. Border display disabled.")
            return
        }

        // go ahead and show borders for all worlds
        showAllBorders()

        Config.logConfig("Successfully hooked into DynMap for the ability to display borders.")
    }

    /*
     * Re-rendering methods, used for updating trimmed chunks to show them as gone.
     * (Currently unused/not working, retained for completeness.)
     */

    fun renderRegion(worldName: String, coord: CoordXZ) {
        if (!renderEnabled()) return
        val world = Bukkit.getWorld(worldName)
        val y = world?.maxHeight ?: 255
        val x = CoordXZ.regionToBlock(coord.x)
        val z = CoordXZ.regionToBlock(coord.z)
        api?.triggerRenderOfVolume(worldName, x, 0, z, x + 511, y, z + 511)
    }

    fun renderChunks(worldName: String, coords: List<CoordXZ>) {
        if (!renderEnabled()) return
        val world = Bukkit.getWorld(worldName)
        val y = world?.maxHeight ?: 255
        for (coord in coords) renderChunk(worldName, coord, y)
    }

    fun renderChunk(worldName: String, coord: CoordXZ, maxY: Int) {
        if (!renderEnabled()) return
        val x = CoordXZ.chunkToBlock(coord.x)
        val z = CoordXZ.chunkToBlock(coord.z)
        api?.triggerRenderOfVolume(worldName, x, 0, z, x + 15, maxY, z + 15)
    }

    /*
     * Methods for displaying our borders on DynMap's world maps
     */

    fun showAllBorders() {
        val markApi = this.markApi ?: return

        // in case any borders are already shown
        removeAllBorders()

        if (!Config.dynmapEnabled) {
            // don't show the marker set in DynMap if our integration is disabled
            markSet?.deleteMarkerSet()
            markSet = null
            return
        }

        // make sure the marker set is initialized
        var set = markApi.getMarkerSet("worldborder.markerset")
        if (set == null) {
            set = markApi.createMarkerSet("worldborder.markerset", "WorldBorder", null, false) ?: return
        } else {
            set.markerSetLabel = "WorldBorder"
        }
        markSet = set
        set.layerPriority = Config.dynmapPriority
        set.hideByDefault = Config.dynmapHideByDefault

        for ((worldName, border) in Config.getBorders()) {
            showBorder(worldName, border)
        }
    }

    fun showBorder(worldName: String, border: BorderData) {
        if (!borderEnabled()) return
        if (!Config.dynmapEnabled) return

        val round = border.shape ?: Config.shapeRound
        if (round) showRoundBorder(worldName, border) else showSquareBorder(worldName, border)
    }

    private fun showRoundBorder(worldName: String, border: BorderData) {
        if (squareBorders.containsKey(worldName)) removeBorder(worldName)

        val world = Bukkit.getWorld(worldName)
        val y = (world?.maxHeight ?: 255).toDouble()

        val existing = roundBorders[worldName]
        if (existing == null) {
            val set = markSet ?: return
            val marker = set.createCircleMarker(
                "worldborder_$worldName", Config.dynmapMessage, false, worldName,
                border.x, y, border.z, border.radiusX.toDouble(), border.radiusZ.toDouble(), true
            ) ?: return
            marker.setLineStyle(lineWeight, lineOpacity, lineColor)
            marker.setFillStyle(0.0, 0x000000)
            roundBorders[worldName] = marker
        } else {
            existing.setCenter(worldName, border.x, y, border.z)
            existing.setRadius(border.radiusX.toDouble(), border.radiusZ.toDouble())
        }
    }

    private fun showSquareBorder(worldName: String, border: BorderData) {
        if (roundBorders.containsKey(worldName)) removeBorder(worldName)

        // corners of the square border
        val xVals = doubleArrayOf(border.x - border.radiusX, border.x + border.radiusX)
        val zVals = doubleArrayOf(border.z - border.radiusZ, border.z + border.radiusZ)

        val existing = squareBorders[worldName]
        if (existing == null) {
            val set = markSet ?: return
            val marker = set.createAreaMarker(
                "worldborder_$worldName", Config.dynmapMessage, false, worldName, xVals, zVals, true
            ) ?: return
            marker.setLineStyle(3, 1.0, 0xFF0000)
            marker.setFillStyle(0.0, 0x000000)
            squareBorders[worldName] = marker
        } else {
            existing.setCornerLocations(xVals, zVals)
        }
    }

    fun removeAllBorders() {
        if (!borderEnabled()) return

        for (marker in roundBorders.values) marker.deleteMarker()
        roundBorders.clear()

        for (marker in squareBorders.values) marker.deleteMarker()
        squareBorders.clear()
    }

    fun removeBorder(worldName: String) {
        if (!borderEnabled()) return

        roundBorders.remove(worldName)?.deleteMarker()
        squareBorders.remove(worldName)?.deleteMarker()
    }
}
