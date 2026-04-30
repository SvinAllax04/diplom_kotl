package ru.vsu.cs.diplom_kotl.domain.placement

import ru.vsu.cs.diplom_kotl.data.catalog.FurnitureItem

data class RoomBounds(
    val widthM: Float,
    val depthM: Float,
    val freeAreaM2: Float
)

class PlacementPlanner {
    fun canFit(roomBounds: RoomBounds, item: FurnitureItem): Boolean {
        val footprint = item.widthM * item.depthM
        val freeEnough = footprint <= roomBounds.freeAreaM2 * 0.75f
        val dimensionsEnough = item.widthM <= roomBounds.widthM && item.depthM <= roomBounds.depthM
        return freeEnough && dimensionsEnough
    }
}
