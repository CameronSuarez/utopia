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
import com.example.utopia.util.Constants
import kotlin.math.floor

data class VisibleBounds(
    val startX: Int,
    val endX: Int,
    val startY: Int,
    val endY: Int
)

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

fun worldToScreen(worldPos: Offset, camera: Camera2D): Offset {
    return (worldPos + camera.offset) * camera.zoom
}

fun worldSizeToScreen(worldSize: Size, camera: Camera2D): Size {
    return worldSize * camera.zoom
}

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
    
    val tilesW = (screenSize.width / (tileSize * zoom)).toInt() + 2
    val tilesH = (screenSize.height / (tileSize * zoom)).toInt() + 2
    
    return VisibleBounds(
        startX = startX,
        startY = startY,
        endX = startX + tilesW,
        endY = startY + tilesH
    )
}

fun structureHitBoundsWorld(structure: Structure): Rect {
    val spec = structure.spec
    val radius = spec.hitRadiusWorld
    val center = Offset(
        structure.x + spec.hitOffsetXWorld,
        structure.y + spec.hitOffsetYWorld
    )
    val tileSize = Constants.TILE_SIZE

    return if (radius > 0f) {
        Rect(center - Offset(radius, radius), Size(radius * 2f, radius * 2f))
    } else {
        val halfTile = tileSize / 2f

        if (spec.id == "HOUSE") {
            val width = tileSize * 4f
            val height = tileSize * 1f
            val topLeft = Offset(
                (center.x - width / 2f) + tileSize,
                center.y - height / 2f
            )
            return Rect(topLeft, Size(width, height))
        }

        if (spec.id == "PLAZA") {
            val width = spec.worldWidth
            val height = tileSize
            val topLeft = Offset(
                structure.x,
                structure.y + spec.worldHeight - height - tileSize
            )
            return Rect(topLeft, Size(width, height))
        }

        if (spec.id == "TAVERN") {
            val width = tileSize * 3f
            val height = tileSize * 2f
            val left = center.x - width / 2f
            val top = structure.y + spec.worldHeight - height
            return Rect(Offset(left, top), Size(width, height))
        }
        
        if (spec.id == "SAWMILL") {
            val width = tileSize * 2f
            val height = tileSize * 2f
            val left = center.x - width / 2f
            val top = structure.y + spec.worldHeight - height
            return Rect(Offset(left, top), Size(width, height))
        }

        val topLeft = center - Offset(halfTile, halfTile)
        return Rect(topLeft, Size(tileSize, tileSize))
    }
}

fun agentHitBoundsWorld(agent: AgentRuntime): Rect {
    val tileSize = Constants.TILE_SIZE
    val width = tileSize * 1.5f
    val height = tileSize * 2.0f
    val left = agent.x - width / 2f
    val top = agent.y - height * 0.8f
    val size = Size(width, height)

    return Rect(Offset(left, top), size)
}
