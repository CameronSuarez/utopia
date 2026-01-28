package com.example.utopia.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import com.example.utopia.data.models.AgentRuntime
import com.example.utopia.data.models.PropInstance
import com.example.utopia.data.models.Structure
import com.example.utopia.data.models.StructureType
import com.example.utopia.data.models.WorldState
import com.example.utopia.domain.NavGrid
import com.example.utopia.util.IntOffset as UtilIntOffset // Alias utility class
import com.example.utopia.util.Constants
import kotlin.math.floor
import kotlin.math.roundToInt // Import added to resolve unresolved reference

/**
 * RENDER LAYER INVARIANTS:
 * 1. GroundCache draws: natural terrain (grass) and building footprints ONLY.
 * 2. RoadCache draws: permanent roads ONLY.
 * 3. StructureRenderer draws: structures, agents, props, and FX. It NEVER draws roads or cached terrain.
 * 4. Live Preview (liveRoadTiles): UI-only brushing preview. It is never baked into a permanent cache.
 *
 * Draw Order: Ground -> Roads -> Live Preview -> [Structures + Props + Agents + FX (Y-Sorted)]
 */

/**
 * The master renderer for the city.
 * Orchestrates the draw order of all layers.
 */
fun DrawScope.drawCity(
    worldState: WorldState,
    camera: Camera2D,
    groundBitmap: ImageBitmap?,
    roadBitmap: ImageBitmap?,
    roadAsset: ImageBitmap, // Added road asset for live preview
    structureAssets: StructureAssets,
    propAssets: PropAssets,
    emojiFxAssets: EmojiFxAssets,
    agentNameById: Map<String, String>,
    timeMs: Long,
    liveRoadTiles: List<UtilIntOffset> = emptyList(), // Changed import of IntOffset to avoid conflict
    navGrid: NavGrid? = null,
    showNavGrid: Boolean = false
) {
    // Calculate visible world bounds for culling
    val visibleWorldWidth = size.width / camera.zoom
    val visibleWorldHeight = size.height / camera.zoom
    val worldL = -camera.offset.x / camera.zoom
    val worldT = -camera.offset.y / camera.zoom
    val worldR = worldL + visibleWorldWidth
    val worldB = worldT + visibleWorldHeight

    // 1. Ground Layer (Natural terrain)
    drawGround(camera, groundBitmap)

    // 2. Road Layer (Cached bitmap)
    roadBitmap?.let { drawRoadBitmap(it, camera) }

    // Draw live road tiles (brushing preview) above cached roads
    if (liveRoadTiles.isNotEmpty()) {
        drawLiveRoads(liveRoadTiles, camera, roadAsset) // Pass roadAsset
    }

    // 3. Unified World Render List (Structures + Agents + Props + FX)
    val renderItems = buildList {
        // STRUCTURES
        worldState.structures
            .asSequence()
            .filterNot { it.type == StructureType.ROAD }
            // Simple culling for structures based on a generous view area
            .filter { structure ->
                structure.x + structure.type.worldWidth >= worldL &&
                        structure.x <= worldR &&
                        structure.y + structure.type.worldHeight >= worldT &&
                        structure.y <= worldB
            }
            .forEach { structure ->
                add(RenderItem(
                    layer = RenderLayer.STRUCTURES,
                    depthY = structureBaselineY(structure),
                    depthX = structure.x + structure.type.worldWidth / 2f,
                    tieBreak = structure.id.hashCode()
                ) {
                    drawStructureItem(structure, camera, structureAssets, agentNameById)
                })
            }

        // PROPS - Culling is critical here for performance
        worldState.props
            .asSequence()
            .forEach { prop ->
                add(RenderItem(
                    layer = RenderLayer.PROPS,
                    depthY = propBaselineY(prop),
                    depthX = prop.anchorX,
                    tieBreak = prop.id.hashCode()
                ) {
                    drawPropItem(prop, camera, propAssets)
                })
            }

        // AGENTS and FX
        worldState.agents.forEach { agent ->
            // Culling agents based on their world position
            if (agent.x in worldL..worldR && agent.y in worldT..worldB) {
                add(RenderItem(
                    layer = RenderLayer.AGENTS,
                    depthY = agentFootpointY(agent),
                    depthX = agent.x,
                    tieBreak = agent.shortId
                ) {
                    drawAgentItem(agent, camera)
                })

                add(RenderItem(
                    layer = RenderLayer.FX,
                    depthY = agentFootpointY(agent),
                    depthX = agent.x,
                    tieBreak = agent.shortId
                ) {
                    drawAgentEmojiFxItem(agent, camera, emojiFxAssets, timeMs)
                })
            }
        }
    }

    // Sort by depthY (primary) -> depthX (tie-breaker) -> tieBreak (deterministic ID)
    renderItems
        .sortedWith(compareBy<RenderItem> { it.depthY }.thenBy { it.depthX }.thenBy { it.tieBreak })
        .forEach { it.draw(this) }

    // 4. Debug NavGrid Overlay
    if (showNavGrid && navGrid != null) {
        drawNavGridOverlay(navGrid, camera)
    }
}

private fun DrawScope.drawNavGridOverlay(navGrid: NavGrid, camera: Camera2D) {
    val (camX, camY) = -camera.offset
    val worldX = camX / camera.zoom
    val worldY = camY / camera.zoom
    val tileSize = Constants.TILE_SIZE

    val startX = floor(worldX / tileSize).toInt().coerceIn(0, navGrid.width)
    val startY = floor(worldY / tileSize).toInt().coerceIn(0, navGrid.height)
    val endX = (startX + size.width / (tileSize * camera.zoom) + 2).toInt().coerceIn(0, navGrid.width)
    val endY = (startY + size.height / (tileSize * camera.zoom) + 2).toInt().coerceIn(0, navGrid.height)

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
                    topLeft = Offset(x * tileSize * camera.zoom + camera.offset.x, y * tileSize * camera.zoom + camera.offset.y),
                    size = Size(tileSize * camera.zoom, tileSize * camera.zoom)
                )
            }
        }
    }
}


private enum class RenderLayer {
    STRUCTURES,
    PROPS,
    AGENTS,
    FX
}

private data class RenderItem(
    val layer: RenderLayer,
    val depthY: Float,
    val depthX: Float, // Added for deterministic tie-breaking
    val tieBreak: Int,
    val draw: DrawScope.() -> Unit
)

// FIX: Assuming agent.y is the top anchor, the footpoint (for Y-sorting) is TILE_SIZE lower.
private fun agentFootpointY(agent: AgentRuntime): Float = agent.y + Constants.TILE_SIZE

private fun structureBaselineY(structure: Structure): Float =
    structure.y + structure.type.baselineWorld

private fun propBaselineY(prop: PropInstance): Float =
    prop.anchorY

/**
 * Extension for drawing the cached road bitmap with camera transforms.
 */
private fun DrawScope.drawRoadBitmap(bitmap: ImageBitmap, camera: Camera2D) {
    withTransform({
        translate(camera.offset.x, camera.offset.y)
        scale(camera.zoom, camera.zoom, pivot = Offset.Zero)
    }) {
        drawImage(bitmap, topLeft = Offset.Zero)
    }
}

/**
 * Draws the live road tiles (brushing preview) using the road asset.
 */
private fun DrawScope.drawLiveRoads(tiles: List<UtilIntOffset>, camera: Camera2D, roadAsset: ImageBitmap) {
    val tileSize = Constants.TILE_SIZE
    val dstSize = androidx.compose.ui.unit.IntSize(tileSize.roundToInt(), tileSize.roundToInt())

    withTransform({
        translate(camera.offset.x, camera.offset.y)
        scale(camera.zoom, camera.zoom, pivot = Offset.Zero)
    }) {
        for (tile in tiles) {
            val wx = tile.x * tileSize
            val wy = tile.y * tileSize

            drawImage(
                image = roadAsset,
                dstOffset = androidx.compose.ui.unit.IntOffset(wx.roundToInt(), wy.roundToInt()),
                dstSize = dstSize
            )
        }
    }
}
