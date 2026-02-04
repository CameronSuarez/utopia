package com.example.utopia.ui

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.example.utopia.data.models.AgentRuntime
import com.example.utopia.data.models.PropInstance
import com.example.utopia.data.models.Structure
import com.example.utopia.domain.NavGrid
import com.example.utopia.util.Constants
import kotlin.math.floor

private const val SPATIAL_CELL_SIZE = 256f

class SnapshotSpatialIndex(private val cellSize: Float) {
    private val grid = mutableMapOf<Pair<Int, Int>, MutableList<SpatialEntry>>()

    data class SpatialEntry(val item: RenderItemRef, val bounds: Rect)

    fun insert(item: RenderItemRef, bounds: Rect) {
        val startX = floor(bounds.left / cellSize).toInt()
        val endX = floor(bounds.right / cellSize).toInt()
        val startY = floor(bounds.top / cellSize).toInt()
        val endY = floor(bounds.bottom / cellSize).toInt()

        val entry = SpatialEntry(item, bounds)
        for (ix in startX..endX) {
            for (iy in startY..endY) {
                grid.getOrPut(ix to iy) { mutableListOf() }.add(entry)
            }
        }
    }

    fun query(viewport: Rect): List<RenderItemRef> {
        val startX = floor(viewport.left / cellSize).toInt()
        val endX = floor(viewport.right / cellSize).toInt()
        val startY = floor(viewport.top / cellSize).toInt()
        val endY = floor(viewport.bottom / cellSize).toInt()

        val candidates = mutableSetOf<SpatialEntry>()
        for (ix in startX..endX) {
            for (iy in startY..endY) {
                grid[ix to iy]?.let { candidates.addAll(it) }
            }
        }
        
        return candidates
            .filter { it.bounds.overlaps(viewport) }
            .map { it.item }
    }
}

fun buildVisibleWorldObjects(
    context: RenderContext,
    snapshot: SceneSnapshot,
    viewportSize: Size
): List<RenderItemRef> {
    val worldState = snapshot.worldState
    val camera = context.camera
    
    val index = SnapshotSpatialIndex(SPATIAL_CELL_SIZE)
    
    worldState.structures.forEach { structure ->
        val spec = structure.spec
        if (spec.id != "ROAD") {
            val bounds = Rect(
                structure.x,
                structure.y - spec.worldHeight,
                structure.x + spec.worldWidth,
                structure.y + spec.worldHeight
            )
            val item = RenderItemRef(
                kind = RenderItemKind.STRUCTURE,
                item = structure,
                depthY = structureBaselineY(structure),
                depthX = structure.x + spec.worldWidth / 2f,
                tieBreak = structure.id.hashCode()
            )
            index.insert(item, bounds)
        }
    }

    worldState.props.forEach { prop ->
        val w = 6f * Constants.TILE_SIZE
        val h = 8f * Constants.TILE_SIZE
        val bounds = Rect(
            prop.anchorX - w/2f,
            prop.anchorY - h,
            prop.anchorX + w/2f,
            prop.anchorY
        )
        val item = RenderItemRef(
            kind = RenderItemKind.PROP,
            item = prop,
            depthY = propBaselineY(prop),
            depthX = prop.anchorX,
            tieBreak = prop.id.hashCode()
        )
        index.insert(item, bounds)
    }

    worldState.agents.forEach { agent ->
        val bounds = agentHitBoundsWorld(agent)
        val item = RenderItemRef(
            kind = RenderItemKind.AGENT,
            item = agent,
            depthY = agentFootpointY(agent),
            depthX = agent.x,
            tieBreak = agent.shortId
        )
        index.insert(item, bounds)

        val fxItem = RenderItemRef(
            kind = RenderItemKind.EMOJI_FX,
            item = agent,
            depthY = agentFootpointY(agent),
            depthX = agent.x,
            tieBreak = agent.shortId
        )
        index.insert(fxItem, bounds)
    }

    val margin = 200f
    val worldL = -camera.offset.x / camera.zoom - margin
    val worldT = -camera.offset.y / camera.zoom - margin
    val worldR = worldL + (viewportSize.width / camera.zoom) + margin * 2
    val worldB = worldT + (viewportSize.height / camera.zoom) + margin * 2
    val viewportRect = Rect(worldL, worldT, worldR, worldB)

    return index.query(viewportRect)
        .sortedWith(compareBy { it.depthY })
}

class GroundLayer : RenderLayer {
    override fun DrawScope.draw(context: RenderContext, snapshot: SceneSnapshot) {
        drawGround(context.camera, context.groundBitmap)
    }
}

class RoadLayer : RenderLayer {
    override fun DrawScope.draw(context: RenderContext, snapshot: SceneSnapshot) {
        context.roadBitmap?.let { drawRoadBitmap(it, context.camera) }
        if (snapshot.liveRoadTiles.isNotEmpty()) {
            drawLiveRoads(snapshot.liveRoadTiles, context.camera)
        }
    }
}

class WorldObjectLayer : RenderLayer {
    override fun DrawScope.draw(context: RenderContext, snapshot: SceneSnapshot) {
        snapshot.visibleWorldObjectsYSorted.forEach { ref ->
            when (ref.kind) {
                RenderItemKind.STRUCTURE -> {
                    val structure = ref.item as Structure
                    drawStructureItem(structure, context.camera, context.structureAssets, context.agentNameById)
                }
                RenderItemKind.PROP -> {
                    val prop = ref.item as PropInstance
                    drawPropItem(prop, context.camera, context.propAssets)
                }
                RenderItemKind.AGENT -> {
                    val agent = ref.item as AgentRuntime
                    drawAgentItem(agent, context.camera)
                }
                RenderItemKind.EMOJI_FX -> {
                    val agent = ref.item as AgentRuntime
                    drawAgentEmojiFxItem(agent, snapshot.worldState, context.camera, context.emojiFxAssets, context.timeMs)
                }
            }
        }
    }
}

class DebugOverlayLayer : RenderLayer {
    override fun DrawScope.draw(context: RenderContext, snapshot: SceneSnapshot) {
        val navGrid = context.navGrid
        if (context.showNavGrid && navGrid is NavGrid) {
            drawNavGridOverlay(navGrid, context.camera)
        }
    }
}
