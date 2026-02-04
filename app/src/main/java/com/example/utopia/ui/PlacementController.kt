package com.example.utopia.ui

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import com.example.utopia.data.StructureRegistry
import com.example.utopia.data.models.PlacementBehavior
import com.example.utopia.data.models.Structure
import com.example.utopia.data.models.StructureSpec
import com.example.utopia.domain.WorldManager
import com.example.utopia.util.WorldGridMath
import com.example.utopia.util.IntOffset
import com.example.utopia.util.Constants
import kotlin.math.abs

enum class PlacementState {
    IDLE,
    DRAGGING_GHOST,
    ARMED_STROKE,
    BRUSHING
}

class PlacementController(
    private val worldManager: WorldManager,
    private val onWorldChanged: () -> Unit
) {
    var state by mutableStateOf(PlacementState.IDLE)
        private set

    var activeTool by mutableStateOf<StructureSpec?>(null)
        private set

    var worldPos by mutableStateOf<Offset?>(null)
        private set

    var isValid by mutableStateOf(false)
        private set

    var onTilesPlaced: ((Int) -> Unit)? = null

    private val liveRoadTilesInternal = mutableStateListOf<IntOffset>()
    val liveRoadTiles: List<IntOffset> get() = liveRoadTilesInternal

    // For repositioning
    var movingStructure by mutableStateOf<Structure?>(null)
        private set
    private var originalPosition: Offset? = null

    private var lastPlacedTile: IntOffset? = null
    private val currentStrokeIds = mutableListOf<String>()

    private fun getToolCost(spec: StructureSpec): Int = when(spec.id) {
        "ROAD" -> 2
        "WALL" -> 5
        "HOUSE" -> 50
        "STORE" -> 100
        "WORKSHOP" -> 150
        "CASTLE" -> 500
        "PLAZA" -> 200
        "TAVERN" -> 180
        else -> 0
    }

    fun beginPointer(tool: StructureSpec, screenPos: Offset, cameraOffset: Offset) {
        val camera = Camera2D(cameraOffset)
        currentStrokeIds.clear()
        lastPlacedTile = null
        liveRoadTilesInternal.clear() 

        activeTool = tool
        
        val pos: Offset
        val rawWorldPos = screenToWorld(screenPos, camera)
        val tileOffsetNudge = Constants.TILE_SIZE * 0.5f

        if (tool.behavior == PlacementBehavior.STAMP) {
            val anchoredScreenPos = screenPos - Offset(tool.worldWidth / 2f, tool.worldHeight)
            pos = screenToWorld(anchoredScreenPos, camera)
            worldPos = pos

            state = PlacementState.DRAGGING_GHOST
            isValid = worldManager.canPlace(tool.id, pos.x, pos.y)
        } else { // STROKE: Road or Wall
            worldPos = rawWorldPos
            pos = rawWorldPos + Offset(tileOffsetNudge, tileOffsetNudge)
            
            state = PlacementState.BRUSHING
            val tile = WorldGridMath.worldToTile(pos)
            lastPlacedTile = tile
            val ids = worldManager.tryPlaceStroke(tool.id, listOf(tile.x to tile.y))
            if (ids.isNotEmpty()) {
                currentStrokeIds.addAll(ids)
                onTilesPlaced?.invoke(getToolCost(tool) * ids.size)
            }
            if (tool.id == "ROAD") {
                liveRoadTilesInternal.add(tile)
            }
            isValid = true
        }
    }

    fun movePointer(screenPos: Offset, cameraOffset: Offset) {
        if (state == PlacementState.IDLE) return
        val tool = activeTool ?: return
        val camera = Camera2D(cameraOffset)
        
        val newWorldPos: Offset
        val rawWorldPos = screenToWorld(screenPos, camera)
        val tileOffsetNudge = Constants.TILE_SIZE * 0.5f

        if (tool.behavior == PlacementBehavior.STAMP) {
            val anchoredScreenPos = screenPos - Offset(tool.worldWidth / 2f, tool.worldHeight)
            newWorldPos = screenToWorld(anchoredScreenPos, camera)
            worldPos = newWorldPos
        } else { // STROKE
            newWorldPos = rawWorldPos + Offset(tileOffsetNudge, tileOffsetNudge)
            worldPos = rawWorldPos
        }

        when (state) {
            PlacementState.DRAGGING_GHOST -> {
                isValid = worldManager.canPlace(tool.id, newWorldPos.x, newWorldPos.y)
            }
            PlacementState.BRUSHING -> {
                val currentTile = WorldGridMath.worldToTile(newWorldPos)
                val oldTilePos = lastPlacedTile
                if (oldTilePos != null && currentTile != oldTilePos) {
                    processStroke(currentTile, oldTilePos)
                }
            }
            else -> {}
        }
    }

    fun endPointer(wasCancelled: Boolean = false) {
        if (wasCancelled) {
            cancel()
            return
        }

        var changed = false
        when (state) {
            PlacementState.DRAGGING_GHOST -> {
                val tool = activeTool
                val pos = worldPos
                val moving = movingStructure
                if (tool != null && pos != null && isValid) {
                    worldManager.tryPlaceStamp(tool.id, pos.x, pos.y, existingStructure = moving)
                    if (moving == null) onTilesPlaced?.invoke(getToolCost(tool))
                    changed = true
                    state = PlacementState.ARMED_STROKE
                    worldPos = null
                    movingStructure = null
                    originalPosition = null
                } else if (moving != null) {
                    cancelMove() // Will call onWorldChanged
                } else {
                    state = PlacementState.ARMED_STROKE
                }
            }
            PlacementState.BRUSHING -> {
                if (currentStrokeIds.isNotEmpty()) {
                    worldManager.incrementVersion()
                    changed = true
                }
                state = PlacementState.ARMED_STROKE
                lastPlacedTile = null
            }
            else -> {}
        }
        if (changed) onWorldChanged()
    }

    fun selectTool(spec: StructureSpec) {
        cancel()
        activeTool = spec
        state = PlacementState.ARMED_STROKE
    }

    fun startMove(structure: Structure) {
        cancel()
        movingStructure = structure
        originalPosition = Offset(structure.x, structure.y)
        activeTool = structure.spec
        worldPos = Offset(structure.x, structure.y)
        state = PlacementState.DRAGGING_GHOST
        worldManager.removeStructureAt(structure.x, structure.y, isMoving = true)
        onWorldChanged()
    }

    private fun processStroke(currentTile: IntOffset, lastTile: IntOffset) {
        val tool = activeTool ?: return
        val line = getLine(lastTile, currentTile)
        val tiles = line.map { it.x to it.y }
        val ids = worldManager.tryPlaceStroke(tool.id, tiles)
        if (ids.isNotEmpty()) {
            currentStrokeIds.addAll(ids)
            onTilesPlaced?.invoke(getToolCost(tool) * ids.size)
        }
        if (tool.id == "ROAD") {
            liveRoadTilesInternal.addAll(line)
        }
        lastPlacedTile = currentTile
    }

    fun cancel() {
        if (movingStructure != null) {
            cancelMove()
        } else {
            state = PlacementState.IDLE
            activeTool = null
            worldPos = null
            isValid = false
            lastPlacedTile = null
            currentStrokeIds.clear()
            movingStructure = null
            originalPosition = null
            liveRoadTilesInternal.clear()
        }
    }

    private fun cancelMove() {
        val tool = activeTool ?: return
        val pos = originalPosition ?: return
        val moving = movingStructure ?: return
        worldManager.tryPlaceStamp(tool.id, pos.x, pos.y, existingStructure = moving)
        state = PlacementState.IDLE
        activeTool = null
        worldPos = null
        movingStructure = null
        originalPosition = null
        liveRoadTilesInternal.clear()
        onWorldChanged()
    }

    private fun getLine(start: IntOffset, end: IntOffset): List<IntOffset> {
        val line = mutableListOf<IntOffset>()
        var x = start.x
        var y = start.y
        val dx = abs(end.x - start.x)
        val dy = abs(end.y - start.y)
        val sx = if (start.x < end.x) 1 else -1
        val sy = if (start.y < end.y) 1 else -1
        var err = dx - dy
        while (true) {
            line.add(IntOffset(x, y))
            if (x == end.x && y == end.y) break
            val e2 = 2 * err
            if (e2 > -dy) {
                err -= dy
                x += sx
            }
            if (e2 < dx) {
                err += dx
                y += sy
            }
        }
        return line
    }
}
