package com.example.utopia.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.utopia.data.models.AgentRuntime
import com.example.utopia.data.models.Structure
import com.example.utopia.data.models.TileType
import com.example.utopia.data.models.PropInstance
import com.example.utopia.debug.PathDebugOverlay
import com.example.utopia.domain.NavGrid
import com.example.utopia.util.Constants
import kotlin.math.floor

@Composable
fun NavGridDebugOverlay(
    navGrid: NavGrid,
    cameraOffset: Offset,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val (worldX, worldY) = -cameraOffset
        val navGridTileSize = Constants.TILE_SIZE

        val startX = floor(worldX / navGridTileSize).toInt().coerceIn(0, navGrid.width - 1)
        val startY = floor(worldY / navGridTileSize).toInt().coerceIn(0, navGrid.height - 1)
        val endX = (startX + size.width / navGridTileSize + 2).toInt().coerceIn(0, navGrid.width)
        val endY = (startY + size.height / navGridTileSize + 2).toInt().coerceIn(0, navGrid.height)

        for (x in startX until endX) {
            for (y in startY until endY) {
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
                        topLeft = Offset(x * navGridTileSize + cameraOffset.x, y * navGridTileSize + cameraOffset.y),
                        size = Size(navGridTileSize, navGridTileSize)
                    )
                }

                // New: Visualize Clearance Field (Heatmap)
                val clearance = navGrid.clearanceGrid[x * navGrid.height + y]
                if (clearance < 10 && tileValue > 0) {
                    val alpha = (0.3f * (1f - clearance / 10f)).coerceIn(0f, 0.3f)
                    drawRect(
                        color = Color.Blue.copy(alpha = alpha),
                        topLeft = Offset(x * navGridTileSize + cameraOffset.x, y * navGridTileSize + cameraOffset.y),
                        size = Size(navGridTileSize, navGridTileSize)
                    )
                }
            }
        }

        // Show validation error in red if it exists
        navGrid.lastValidationError?.let {
             // In a real app we'd draw text here, but for now we'll tint the screen red
             drawRect(Color.Red.copy(alpha = 0.1f), topLeft = Offset.Zero, size = size)
        }
    }
}

@Composable
fun LotDebugOverlay(
    tiles: Array<Array<TileType>>,
    cameraOffset: Offset,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val (worldX, worldY) = -cameraOffset
        val tileSize = Constants.TILE_SIZE

        val startX = floor(worldX / tileSize).toInt().coerceIn(0, Constants.MAP_TILES_W - 1)
        val startY = floor(worldY / tileSize).toInt().coerceIn(0, Constants.MAP_TILES_H - 1)
        val endX = (startX + size.width / tileSize + 2).toInt().coerceIn(0, Constants.MAP_TILES_W)
        val endY = (startY + size.height / tileSize + 2).toInt().coerceIn(0, Constants.MAP_TILES_H)

        for (x in startX until endX) {
            for (y in startY until endY) {
                val tileType = tiles.getOrNull(x)?.getOrNull(y)
                if (tileType == TileType.BUILDING_LOT) {
                    drawRect(
                        color = Color.Magenta.copy(alpha = 0.5f),
                        topLeft = Offset(x * tileSize + cameraOffset.x, y * tileSize + cameraOffset.y),
                        size = Size(tileSize, tileSize)
                    )
                }
            }
        }
    }
}

@Composable
fun AgentClearanceDebugOverlay(
    agents: List<AgentRuntime>,
    cameraOffset: Offset,
    showClearance: Boolean,
    showVectors: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        agents.forEach { agent ->
            val center = Offset(agent.x + cameraOffset.x, agent.y + cameraOffset.y)

            if (showClearance) {
                // 1. Collision Radius (Solid Cyan)
                drawCircle(
                    color = Color.Cyan.copy(alpha = 0.6f),
                    center = center,
                    radius = agent.collisionRadius,
                    style = Stroke(width = 1.dp.toPx())
                )

                // 2. Clearance Radius (Translucent Dash-style simulated circle)
                // We use 1.5x as a visual indicator of the "influence" zone
                drawCircle(
                    color = Color.Cyan.copy(alpha = 0.2f),
                    center = center,
                    radius = agent.collisionRadius * 1.5f,
                    style = Stroke(width = 1.dp.toPx())
                )
            }

            if (showVectors) {
                // Desired Step (Green)
                agent.debugDesiredStep?.let { drawDebugVector(center, it, Color.Green, 20f) }
                // Separation Step (Orange)
                agent.debugSeparationStep?.let { drawDebugVector(center, it, Color(0xFFFFA500), 20f) }
                // Final Adjusted Step (White)
                agent.debugAdjustedStep?.let { drawDebugVector(center, it, Color.White, 20f) }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDebugVector(
    start: Offset,
    vector: Offset,
    color: Color,
    scale: Float
) {
    if (vector == Offset.Zero) return
    val end = start + (vector * scale)
    drawLine(color = color, start = start, end = end, strokeWidth = 2.dp.toPx())
    
    // Tiny arrow head
    drawCircle(color = color, center = end, radius = 2.dp.toPx())
}

@Composable
fun EntityHitboxDebugOverlay(
    agents: List<AgentRuntime>,
    structures: List<Structure>,
    props: List<PropInstance>,
    cameraOffset: Offset,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        structures.forEach { structure ->
            val size = Size(structure.type.worldWidth, structure.type.worldHeight)
            drawRect(
                color = Color.Yellow.copy(alpha = 0.5f),
                topLeft = Offset(structure.x + cameraOffset.x, structure.y + cameraOffset.y),
                size = size,
                style = Stroke(width = 2.dp.toPx())
            )
        }

        agents.forEach { agent ->
            val center = Offset(agent.x + cameraOffset.x, agent.y + cameraOffset.y)
            drawCircle(
                color = Color.White,
                center = center,
                radius = 2.dp.toPx()
            )
        }

        props.forEach { prop ->
            val anchorWorldX = prop.anchorX
            val anchorWorldY = prop.anchorY
            val center = Offset(anchorWorldX + cameraOffset.x, anchorWorldY + cameraOffset.y)

            drawCircle(
                color = Color.Green.copy(alpha = 0.8f),
                center = center,
                radius = 4.dp.toPx()
            )
        }
    }
}

@Composable
fun DebugPanel(
    viewModel: GameViewModel,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.background(Color.Black.copy(alpha = 0.5f)).padding(4.dp)) {
        Button(onClick = { viewModel.showNavGridDebug = !viewModel.showNavGridDebug }) {
            Text(if (viewModel.showNavGridDebug) "Hide NavGrid" else "Show NavGrid")
        }
        Button(onClick = { viewModel.showHitboxesDebug = !viewModel.showHitboxesDebug }) {
            Text(if (viewModel.showHitboxesDebug) "Hide Hitboxes" else "Show Hitboxes")
        }
        Button(onClick = { viewModel.showLotDebug = !viewModel.showLotDebug }) {
            Text(if (viewModel.showLotDebug) "Hide Lot" else "Show Lot")
        }
        Button(onClick = { viewModel.showAgentPaths = !viewModel.showAgentPaths }) {
            Text(if (viewModel.showAgentPaths) "Hide Paths" else "Show Paths")
        }
        Button(onClick = { viewModel.showAgentClearanceDebug = !viewModel.showAgentClearanceDebug }) {
            Text(if (viewModel.showAgentClearanceDebug) "Hide Clearance" else "Show Clearance")
        }
        Button(onClick = { viewModel.showAgentVectorsDebug = !viewModel.showAgentVectorsDebug }) {
            Text(if (viewModel.showAgentVectorsDebug) "Hide Vectors" else "Show Vectors")
        }
    }
}
