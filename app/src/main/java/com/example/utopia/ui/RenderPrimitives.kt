package com.example.utopia.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.example.utopia.data.models.AgentRuntime 
import com.example.utopia.data.models.Structure
import com.example.utopia.data.models.StructureType
// REMOVED: import com.example.utopia.domain.VisibleBounds
import com.example.utopia.util.Constants
import kotlin.math.floor


data class VisibleBounds(
    val startX: Int,
    val endX: Int,
    val startY: Int,
    val endY: Int
)

/**
 * The single authoritative implementation for drawing a road tile.
 * This is used by RoadCache for baking and by PlacementController for live previews.
 */
fun DrawScope.drawRoadTileInternal(pos: Offset, tileSize: Float, seed: Long) {
    val roadColor = Color(0xFFBCAAA4)
    val detailColor = Color(0xFF8D6E63).copy(alpha = 0.3f)

    drawRect(roadColor, pos, Size(tileSize, tileSize))

    val rng = java.util.Random(seed)
    repeat(6) {
        val rx = pos.x + rng.nextFloat() * (tileSize * 0.7f) + (tileSize * 0.15f)
        val ry = pos.y + rng.nextFloat() * (tileSize * 0.7f) + (tileSize * 0.15f)
        val rw = 2f + rng.nextFloat() * 6f
        val rh = 1f + rng.nextFloat() * 4f
        drawRoundRect(detailColor, Offset(rx, ry), Size(rw, rh), CornerRadius(1f, 1f))
    }
}

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

fun worldSizeToScreen(worldSize: Size, camera: Camera2D): Size {
    return worldSize * camera.zoom
}

/**
 * Camera-aware transforms: Screen -> World
 */
fun screenToWorld(screenPos: Offset, camera: Camera2D): Offset {
    return (screenPos / camera.zoom) - camera.offset
}

fun Camera2D.computeVisibleBounds(screenSize: Size): VisibleBounds {
    val tileSize = Constants.TILE_SIZE
    val (camX, camY) = -offset
    val worldX = camX / zoom
    val worldY = camY / zoom

    val startX = floor(worldX / tileSize).toInt().coerceAtLeast(0)
    val startY = floor(worldY / tileSize).toInt().coerceAtLeast(0)
    
    // Calculate how many tiles fit on screen
    val tilesW = (screenSize.width / (tileSize * zoom)).toInt() + 2
    val tilesH = (screenSize.height / (tileSize * zoom)).toInt() + 2
    
    // We don't have map size here easily without passing it, but coercion happens in usage
    return VisibleBounds(
        startX = startX,
        startY = startY,
        endX = startX + tilesW,
        endY = startY + tilesH
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
 * Agent Hit Bounds (World) - A small rectangle at the agent's feet.
 * Used for click-selection.
 */
fun agentHitBoundsWorld(agent: AgentRuntime): Rect {
    val tileSize = Constants.TILE_SIZE
    val width = tileSize * 1.5f  // Increased width for easier clicking
    val height = tileSize * 2.0f // Increased height to cover the agent's body

    // Agent's world position (agent.x, agent.y) is assumed to be the center-ish of the base.
    // We expand the box upwards to cover the whole agent.
    val left = agent.x - width / 2f
    val top = agent.y - height * 0.8f // Shifted up to cover the sprite better
    val size = Size(width, height)

    return Rect(Offset(left, top), size)
}
