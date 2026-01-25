package com.example.utopia.domain

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.example.utopia.data.models.PropInstance
import com.example.utopia.data.models.Structure
import com.example.utopia.data.models.TileType
import com.example.utopia.util.Constants

class NavGrid(val size: Int = Constants.GRID_SIZE) {

    /**
     * The grid representing the navigation mesh.
     * Values:
     * 0 = Blocked
     * 1 = Walkable (e.g., Grass)
     * 2 = Road (higher cost to walk on than grass for pathfinding)
     */
    var grid: Array<ByteArray> = Array(size) { ByteArray(size) }
        private set

    fun isWalkable(x: Int, y: Int): Boolean {
        if (x !in 0 until size || y !in 0 until size) return false
        return (grid.getOrNull(x)?.getOrNull(y) ?: 0) > 0
    }

    fun getCost(x: Int, y: Int): Int {
        if (x !in 0 until size || y !in 0 until size) return Int.MAX_VALUE
        return when (grid[x][y]) {
            2.toByte() -> Constants.ROAD_COST
            else -> Constants.GRASS_COST
        }
    }

    fun update(
        tiles: Array<Array<TileType>>,
        structures: List<Structure>,
        props: List<PropInstance>
    ) {
        // 1. Initialize with base tile data
        for (x in 0 until size) {
            for (y in 0 until size) {
                grid[x][y] = when {
                    isTileBlocked(tiles[x][y]) -> 0 // Blocked
                    tiles[x][y] == TileType.ROAD -> 2 // Road
                    else -> 1 // Walkable
                }
            }
        }

        // 2. Rasterize structure footprints
        for (structure in structures) {
            val footprint = getStructureFootprint(structure)
            val minGX = (footprint.left / Constants.TILE_SIZE).toInt()
            val maxGX = (footprint.right / Constants.TILE_SIZE).toInt()
            val minGY = (footprint.top / Constants.TILE_SIZE).toInt()
            val maxGY = (footprint.bottom / Constants.TILE_SIZE).toInt()

            for (gx in minGX..maxGX) {
                for (gy in minGY..maxGY) {
                    if (gx in 0 until size && gy in 0 until size) {
                        grid[gx][gy] = 0 // Mark as blocked
                    }
                }
            }
        }

        // 3. Rasterize prop footprints
        for (prop in props) {
            val footprint = getPropFootprint(prop)
            val minGX = (footprint.left / Constants.TILE_SIZE).toInt()
            val maxGX = (footprint.right / Constants.TILE_SIZE).toInt()
            val minGY = (footprint.top / Constants.TILE_SIZE).toInt()
            val maxGY = (footprint.bottom / Constants.TILE_SIZE).toInt()

            for (gx in minGX..maxGX) {
                for (gy in minGY..maxGY) {
                    if (gx in 0 until size && gy in 0 until size) {
                        grid[gx][gy] = 0 // Mark as blocked
                    }
                }
            }
        }
    }

    private fun isTileBlocked(tile: TileType): Boolean {
        return tile == TileType.BUILDING_SOLID || tile == TileType.BUILDING_FOOTPRINT ||
                tile == TileType.WALL || tile == TileType.PROP_BLOCKED
    }

    private fun getStructureFootprint(structure: Structure): Rect {
        val type = structure.type
        return Rect(
            offset = Offset(structure.x, structure.y + type.worldHeight - Constants.TILE_SIZE),
            size = Size(type.worldWidth, Constants.TILE_SIZE)
        )
    }

    private fun getPropFootprint(prop: PropInstance): Rect {
        val footprintSize = Constants.TILE_SIZE * 0.4f
        return Rect(
            offset = Offset(prop.anchorX - (footprintSize / 2f), prop.anchorY - footprintSize),
            size = Size(footprintSize, footprintSize)
        )
    }
}
