package com.example.utopia.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.utopia.data.models.AgentRuntime
import com.example.utopia.data.models.PropInstance
import com.example.utopia.data.models.Structure
import com.example.utopia.data.models.TileType
import com.example.utopia.domain.NavGrid
import com.example.utopia.domain.VisibleBounds
import com.example.utopia.util.Constants
import kotlin.math.floor

/**
 * Single-pass debug renderer.
 * Optimized with shared bounds calculation and world-to-screen geometry.
 */
fun DrawScope.drawDebugLayers(
    viewModel: GameViewModel,
    agents: List<AgentRuntime>,
    structures: List<Structure>,
    props: List<PropInstance>,
    tiles: Array<Array<TileType>>,
    camera: Camera2D
) {
    val bounds = camera.computeVisibleBounds(size)

    if (viewModel.showNavGridDebug) {
        drawNavGridOverlay(viewModel.navGrid, camera, bounds)
    }

    if (viewModel.showLotDebug) {
        drawLotOverlay(tiles, camera, bounds)
    }

    if (viewModel.showHitboxesDebug) {
        drawHitboxOverlay(agents, structures, props, camera)
    }

    if (viewModel.showAgentPaths) {
        drawPathOverlay(agents, camera)
    }

    if (viewModel.showAgentClearanceDebug || viewModel.showAgentVectorsDebug) {
        drawAgentClearanceOverlay(
            agents,
            camera,
            viewModel.showAgentClearanceDebug,
            viewModel.showAgentVectorsDebug
        )
    }
}

private fun DrawScope.drawNavGridOverlay(navGrid: NavGrid, camera: Camera2D, bounds: VisibleBounds) {
    val tileSize = Constants.TILE_SIZE

    for (x in bounds.startX until bounds.endX) {
        for (y in bounds.startY until bounds.endY) {
            val tileValue = navGrid.grid[x][y]
            val color = when (tileValue.toInt()) {
                0 -> Color.Red.copy(alpha = 0.4f)       // Blocked
                1 -> Color.Green.copy(alpha = 0.4f)     // Walkable
                2 -> Color.Blue.copy(alpha = 0.4f)      // Road
                else -> Color.Transparent
            }
            if (color != Color.Transparent) {
                drawRect(
                    color = color,
                    topLeft = Offset(x * tileSize * camera.zoom + camera.offset.x, y * tileSize * camera.zoom + camera.offset.y),
                    size = Size(tileSize * camera.zoom, tileSize * camera.zoom)
                )
            }

            val clearance = navGrid.clearanceGrid[x * navGrid.height + y]
            if (clearance < 10 && tileValue > 0) {
                val alpha = (0.3f * (1f - clearance / 10f)).coerceIn(0f, 0.3f)
                drawRect(
                    color = Color.Blue.copy(alpha = alpha),
                    topLeft = Offset(x * tileSize * camera.zoom + camera.offset.x, y * tileSize * camera.zoom + camera.offset.y),
                    size = Size(tileSize * camera.zoom, tileSize * camera.zoom)
                )
            }
        }
    }

    navGrid.lastValidationError?.let {
        drawRect(Color.Red.copy(alpha = 0.1f), topLeft = Offset.Zero, size = size)
    }
}

private fun DrawScope.drawLotOverlay(tiles: Array<Array<TileType>>, camera: Camera2D, bounds: VisibleBounds) {
    val tileSize = Constants.TILE_SIZE

    for (x in bounds.startX until bounds.endX) {
        for (y in bounds.startY until bounds.endY) {
            val tileType = tiles.getOrNull(x)?.getOrNull(y)
            if (tileType == TileType.BUILDING_LOT) {
                drawRect(
                    color = Color.Magenta.copy(alpha = 0.5f),
                    topLeft = Offset(x * tileSize * camera.zoom + camera.offset.x, y * tileSize * camera.zoom + camera.offset.y),
                    size = Size(tileSize * camera.zoom, tileSize * camera.zoom)
                )
            }
        }
    }
}

private fun DrawScope.drawHitboxOverlay(
    agents: List<AgentRuntime>,
    structures: List<Structure>,
    props: List<PropInstance>,
    camera: Camera2D
) {
    structures.forEach { structure ->
        val width = structure.type.worldWidth * camera.zoom
        val height = structure.type.worldHeight * camera.zoom
        // ANCHOR FIX: Subtract worldHeight because structure.y is the bottom anchor.
        val drawY = (structure.y - structure.type.worldHeight) * camera.zoom + camera.offset.y
        drawRect(
            color = Color.Yellow.copy(alpha = 0.5f),
            topLeft = Offset(structure.x * camera.zoom + camera.offset.x, drawY),
            size = Size(width, height),
            style = Stroke(width = 2.dp.toPx())
        )
    }

    agents.forEach { agent ->
        val center = Offset(agent.x * camera.zoom + camera.offset.x, agent.y * camera.zoom + camera.offset.y)
        drawCircle(
            color = Color.White,
            center = center,
            radius = 2.dp.toPx()
        )
    }

    props.forEach { prop ->
        val center = Offset(prop.anchorX * camera.zoom + camera.offset.x, prop.anchorY * camera.zoom + camera.offset.y)
        drawCircle(
            color = Color.Green.copy(alpha = 0.8f),
            center = center,
            radius = 4.dp.toPx()
        )
    }
}

private fun DrawScope.drawPathOverlay(agents: List<AgentRuntime>, camera: Camera2D) {
    agents.forEach { agent ->
        val path = agent.pathTiles
        if (path.size < 2) return@forEach

        var prev: Offset? = null
        for (i in agent.pathIndex until path.size) {
            val packed = path[i]
            val gx = packed shr 16
            val gy = packed and 0xFFFF

            val worldPos = Offset(
                (gx * Constants.TILE_SIZE + Constants.TILE_SIZE * 0.5f) * camera.zoom + camera.offset.x,
                (gy * Constants.TILE_SIZE + Constants.TILE_SIZE * 0.5f) * camera.zoom + camera.offset.y
            )

            prev?.let {
                drawLine(
                    color = if (i == agent.pathIndex) Color.Green else Color.Yellow,
                    start = it,
                    end = worldPos,
                    strokeWidth = 2.dp.toPx()
                )
            }
            prev = worldPos
        }
    }
}

private fun DrawScope.drawAgentClearanceOverlay(
    agents: List<AgentRuntime>,
    camera: Camera2D,
    showClearance: Boolean,
    showVectors: Boolean
) {
    agents.forEach { agent ->
        val center = Offset(agent.x * camera.zoom + camera.offset.x, agent.y * camera.zoom + camera.offset.y)

        if (showClearance) {
            drawCircle(
                color = Color.Cyan.copy(alpha = 0.6f),
                center = center,
                radius = agent.collisionRadius * camera.zoom,
                style = Stroke(width = 1.dp.toPx())
            )
            drawCircle(
                color = Color.Cyan.copy(alpha = 0.2f),
                center = center,
                radius = agent.collisionRadius * 1.5f * camera.zoom,
                style = Stroke(width = 1.dp.toPx())
            )
        }

        if (showVectors) {
            agent.debugDesiredStep?.let { drawDebugVector(center, it, Color.Green, 20f * camera.zoom) }
            agent.debugSeparationStep?.let { drawDebugVector(center, it, Color(0xFFFFA500), 20f * camera.zoom) }
            agent.debugAdjustedStep?.let { drawDebugVector(center, it, Color.White, 20f * camera.zoom) }
        }
    }
}

private fun DrawScope.drawDebugVector(
    start: Offset,
    vector: Offset,
    color: Color,
    scale: Float
) {
    if (vector == Offset.Zero) return
    val end = start + (vector * scale)
    drawLine(color = color, start = start, end = end, strokeWidth = 2.dp.toPx())
    drawCircle(color = color, center = end, radius = 2.dp.toPx())
}
