package com.example.utopia.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
// REMOVED: import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.utopia.data.models.AgentRuntime
import com.example.utopia.data.models.Structure
import com.example.utopia.data.models.TileType
import com.example.utopia.data.models.PropInstance
// REMOVED: import com.example.utopia.debug.PathDebugOverlay
import com.example.utopia.domain.NavGrid
import com.example.utopia.util.Constants
import kotlin.math.floor

/**
 * [RED/GREEN/BLUE] Visualizes the NavGrid: the AI's understanding of the world for pathfinding.
 * - RED: Blocked tiles. Agents cannot path through these.
 * - GREEN: Walkable tiles.
 * - BLUE: Road tiles (walkable, but with a different pathfinding cost).
 * This overlay should align perfectly with the physical footprints shown in EntityHitboxDebugOverlay.
 */
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
                val tileValue = navGrid.grid.getOrNull(x)?.getOrNull(y) ?: continue
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
                val clearanceIndex = x * navGrid.height + y
                if (clearanceIndex < navGrid.clearanceGrid.size) {
                    val clearance = navGrid.clearanceGrid[clearanceIndex]
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
        }

        // Show validation error in red if it exists
        navGrid.lastValidationError?.let {
             // In a real app we'd draw text here, but for now we'll tint the screen red
             drawRect(Color.Red.copy(alpha = 0.1f), topLeft = Offset.Zero, size = size)
        }
    }
}

/**
 * [MAGENTA] Visualizes the "Influence Area" or "Zoning" layer.
 * This shows which tiles are designated as `BUILDING_LOT`. It represents the potential of the
 * land and is used for placement rules, spacing, and clearing props. It is intentionally
 * larger than the physical building footprint.
 */
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

// REMOVED: AgentClearanceDebugOverlay
// REMOVED: drawDebugVector

/**
 * [YELLOW] Visualizes the "Physical Footprint" or "Hitbox" of entities.
 * This represents the solid foundation of structures used for collision, interaction, and as
 * the source for NavGrid baking. It is allowed to be smaller than the visual sprite to
 * permit aesthetic overhangs.
 */
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
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Default.BugReport,
                contentDescription = "Debug Menu",
                tint = Color.White
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text(if (viewModel.showNavGridDebug) "Hide NavGrid" else "Show NavGrid") },
                onClick = { 
                    viewModel.showNavGridDebug = !viewModel.showNavGridDebug
                    expanded = false
                }
            )
            DropdownMenuItem(
                text = { Text(if (viewModel.showHitboxesDebug) "Hide Hitboxes" else "Show Hitboxes") },
                onClick = { 
                    viewModel.showHitboxesDebug = !viewModel.showHitboxesDebug
                    expanded = false
                }
            )
            DropdownMenuItem(
                text = { Text(if (viewModel.showLotDebug) "Hide Lot" else "Show Lot") },
                onClick = { 
                    viewModel.showLotDebug = !viewModel.showLotDebug
                    expanded = false
                }
            )
            DropdownMenuItem(
                text = { Text(if (viewModel.showAgentPaths) "Hide Paths" else "Show Paths") },
                onClick = { 
                    viewModel.showAgentPaths = !viewModel.showAgentPaths
                    expanded = false
                }
            )
            DropdownMenuItem(
                text = { Text(if (viewModel.showAgentLabels) "Hide Labels" else "Show Labels") },
                onClick = { 
                    viewModel.showAgentLabels = !viewModel.showAgentLabels
                    expanded = false
                }
            )
            // REMOVED: AgentClearanceDebug menu item
            // REMOVED: AgentVectorsDebug menu item
        }
    }
}
