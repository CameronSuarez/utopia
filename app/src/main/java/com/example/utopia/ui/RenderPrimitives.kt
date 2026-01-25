package com.example.utopia.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.example.utopia.data.models.AgentRuntime // ADDED: Required for agentHitBoundsWorld
import com.example.utopia.data.models.Structure
import com.example.utopia.data.models.StructureType // Add this import
import com.example.utopia.ui.StructureAssets
import com.example.utopia.util.Constants


data class Camera2D(
    val offset: Offset,
    val zoom: Float = 1.0f
)

@Composable
fun pxToDp(px: Float): Dp = with(LocalDensity.current) { px.toDp() }

/**
 * Camera-aware transforms: World -> Screen
 */
fun worldToScreen(worldPos: Offset, camera: Camera2D): Offset {
    return (worldPos + camera.offset) * camera.zoom
}

/**
 * Camera-aware transforms: Screen -> World
 */
fun screenToWorld(screenPos: Offset, camera: Camera2D): Offset {
    return (screenPos / camera.zoom) - camera.offset
}

fun worldSizeToScreen(worldSize: Size, camera: Camera2D): Size {
    return worldSize * camera.zoom
}

fun worldRectToScreenRect(worldRect: Rect, camera: Camera2D): Rect {
    return Rect(
        offset = worldToScreen(worldRect.topLeft, camera),
        size = worldSizeToScreen(worldRect.size, camera)
    )
}

fun structureHitBoundsWorld(structure: Structure): Rect {
    val type = structure.type
    val radius = type.hitRadiusWorld
    val center = Offset(
        structure.x + type.hitOffsetXWorld,
        structure.y + type.hitOffsetYWorld
    )
    val tileSize = Constants.TILE_SIZE

    return if (radius > 0f) {
        Rect(center - Offset(radius, radius), Size(radius * 2f, radius * 2f))
    } else {
        val halfTile = tileSize / 2f

        // Special case for House: 4x1 tile area, shifted 1 tile right, vertically centered on hitOffsetYWorld
        if (type == StructureType.HOUSE) {
            val width = tileSize * 4f
            val height = tileSize * 1f
            val topLeft = Offset(
                (center.x - width / 2f) + tileSize, // Shifted right by 1 tile
                center.y - height / 2f
            )
            return Rect(topLeft, Size(width, height))
        }

        // Special case for Plaza: 1 tile high, full world width, located one tile up from the bottom row
        if (type == StructureType.PLAZA) {
            val width = type.worldWidth
            val height = tileSize
            val topLeft = Offset(
                structure.x,
                structure.y + type.worldHeight - height - tileSize // Moved up 1 tile (subtract tileSize)
            )
            return Rect(topLeft, Size(width, height))
        }

        // Special case for Tavern: 3x2 tile area, horizontally centered, and bottom-aligned
        if (type == StructureType.TAVERN) {
            val width = tileSize * 3f
            val height = tileSize * 2f
            
            // Horizontal center based on hitOffsetXWorld
            val left = center.x - width / 2f
            
            // Bottom aligned with the structure's world height
            val top = structure.y + type.worldHeight - height
            
            return Rect(Offset(left, top), Size(width, height))
        }
        
        // Special case for Workshop: 2x2 tile area, horizontally centered, and bottom-aligned
        if (type == StructureType.WORKSHOP) {
            val width = tileSize * 2f
            val height = tileSize * 2f
            
            // Horizontal center based on hitOffsetXWorld
            val left = center.x - width / 2f
            
            // Bottom aligned with the structure's world height
            val top = structure.y + type.worldHeight - height
            
            return Rect(Offset(left, top), Size(width, height))
        }

        // Special case for Store: 3x1 tile area, aligned with structure's left edge, bottom-aligned
        if (type == StructureType.STORE) {
            val width = tileSize * 3f // Increased width to 3 tiles
            val height = tileSize * 1f
            val topLeft = Offset(
                structure.x, // Aligned with structure's left edge
                structure.y + type.worldHeight - height // Moved down 1 tile (was -tileSize)
            )
            return Rect(topLeft, Size(width, height))
        }


        // Tighten the bounds to a single tile centered on the hit offset for other rectangular structures
        val topLeft = center - Offset(halfTile, halfTile)
        return Rect(topLeft, Size(tileSize, tileSize))
    }
}

/**
 * Logical Bounds (Owned Area) - Expanded by 2 tiles in all directions.
 * Used for house territory, job assignment, etc.
 */
fun structureLogicalBoundsWorld(structure: Structure): Rect {
    val type = structure.type
    val tileSize = Constants.TILE_SIZE
    val expansion = tileSize * 2f // Expanded to 2 tiles based on user request to "Expand"
    
    // Base bounds for the structure's physical space (using the visual size as a base)
    val baseWorldRect = Rect(
        left = structure.x,
        top = structure.y,
        right = structure.x + type.worldWidth,
        bottom = structure.y + type.worldHeight
    )
    
    // Expansion by 2 tiles in all directions
    return Rect(
        left = baseWorldRect.left - expansion,
        top = baseWorldRect.top - expansion,
        right = baseWorldRect.right + expansion,
        bottom = baseWorldRect.bottom + expansion
    )
}

/**
 * Agent Hit Bounds (World) - A small rectangle at the agent's feet.
 * Used for click-selection.
 */
fun agentHitBoundsWorld(agent: AgentRuntime): Rect {
    val tileSize = Constants.TILE_SIZE
    val width = tileSize / 2f // Width reduced by 50%
    val height = tileSize / 4f // 1/4 tile high (a thin strip at the feet)

    // Agent's world position (agent.x, agent.y) is assumed to be the bottom center of the sprite.
    // The top-left corner is calculated to place the bottom of the box at agent.y, centered on agent.x
    val left = agent.x - width / 2f
    val top = agent.y - height
    val size = Size(width, height)

    return Rect(Offset(left, top), size)
}