package com.example.utopia.domain

import com.example.utopia.data.models.TileType
import com.example.utopia.util.Constants

/**
 * Single source of truth for how agents interact with the world geometry.
 * 
 * DESIGN PRINCIPLE:
 * Tiles do not know about structures.
 * This authority only reasons about INHERENT terrain properties.
 */
object TraversalRules {

    /**
     * Authority on inherent tile blocking.
     * Only blocks tiles that are impassable by their nature (e.g., Walls, Water).
     * Does NOT account for structures or props.
     */
    fun isInherentlyBlocked(tile: TileType): Boolean {
        return when (tile) {
            TileType.WALL -> true
            TileType.PROP_BLOCKED -> true
            else -> false
        }
    }

    /**
     * Authority on inherent tile costs.
     */
    fun getBaseCost(tile: TileType): Int {
        return when (tile) {
            TileType.ROAD -> Constants.ROAD_COST
            else -> Constants.GRASS_COST
        }
    }
}
