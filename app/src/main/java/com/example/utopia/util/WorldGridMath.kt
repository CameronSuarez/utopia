package com.example.utopia.util

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size

/**
 * Pure math for converting between World (pixel) space and Grid (tile) space.
 * This is "neutral" logic that does not depend on the Camera or UI state.
 */
object WorldGridMath {

    fun worldToTile(worldPos: Offset): IntOffset {
        return IntOffset(
            (worldPos.x / Constants.TILE_SIZE).toInt(),
            (worldPos.y / Constants.TILE_SIZE).toInt()
        )
    }

    fun tileToWorld(x: Int, y: Int): Offset {
        return Offset(
            x * Constants.TILE_SIZE,
            y * Constants.TILE_SIZE
        )
    }

    fun tileToWorld(tile: IntOffset): Offset {
        return tileToWorld(tile.x, tile.y)
    }

    fun tileCenterToWorld(x: Int, y: Int): Offset {
        return Offset(
            (x + 0.5f) * Constants.TILE_SIZE,
            (y + 0.5f) * Constants.TILE_SIZE
        )
    }

    fun tileCenterToWorld(tile: IntOffset): Offset {
        return tileCenterToWorld(tile.x, tile.y)
    }

    /**
     * Returns the world-space bounding box for a given tile coordinate.
     */
    fun tileToWorldRect(x: Int, y: Int): Rect {
        return Rect(
            offset = tileToWorld(x, y),
            size = Size(Constants.TILE_SIZE, Constants.TILE_SIZE)
        )
    }

    fun dist(a: Offset, b: Offset): Float {
        return (a - b).getDistance()
    }
}
