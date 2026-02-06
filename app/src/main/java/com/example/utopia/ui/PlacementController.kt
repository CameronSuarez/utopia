package com.example.utopia.ui

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import com.example.utopia.data.models.Structure
import com.example.utopia.data.models.StructureSpec
import com.example.utopia.domain.WorldManager
import com.example.utopia.util.IntOffset

enum class PlacementState {
    IDLE,
    DRAGGING_GHOST,
    ARMED // Simplified from ARMED_STROKE
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

    // For repositioning
    var movingStructure by mutableStateOf<Structure?>(null)
        private set
    private var originalPosition: Offset? = null

    fun beginPointer(tool: StructureSpec, screenPos: Offset, cameraOffset: Offset) {
        val camera = Camera2D(cameraOffset)
        activeTool = tool

        val anchoredScreenPos = screenPos - Offset(tool.worldWidth / 2f, tool.worldHeight)
        val pos = screenToWorld(anchoredScreenPos, camera)
        worldPos = pos

        state = PlacementState.DRAGGING_GHOST
        isValid = worldManager.canPlace(tool.id, pos.x, pos.y)
    }

    fun movePointer(screenPos: Offset, cameraOffset: Offset) {
        if (state != PlacementState.DRAGGING_GHOST) return
        val tool = activeTool ?: return
        val camera = Camera2D(cameraOffset)

        val anchoredScreenPos = screenPos - Offset(tool.worldWidth / 2f, tool.worldHeight)
        val newWorldPos = screenToWorld(anchoredScreenPos, camera)
        worldPos = newWorldPos
        isValid = worldManager.canPlace(tool.id, newWorldPos.x, newWorldPos.y)
    }

    fun endPointer(wasCancelled: Boolean = false) {
        if (wasCancelled) {
            cancel()
            return
        }

        var changed = false
        if (state == PlacementState.DRAGGING_GHOST) {
            val tool = activeTool
            val pos = worldPos
            val moving = movingStructure
            if (tool != null && pos != null && isValid) {
                worldManager.tryPlaceStamp(tool.id, pos.x, pos.y, existingStructure = moving)
                changed = true
                cancel()
            } else if (moving != null) {
                cancelMove() // Will call onWorldChanged
            } else {
                state = PlacementState.ARMED
            }
        }
        if (changed) onWorldChanged()
    }

    fun selectTool(spec: StructureSpec) {
        cancel()
        activeTool = spec
        state = PlacementState.ARMED
    }

    fun startMove(structure: Structure) {
        cancel()
        movingStructure = structure
        originalPosition = Offset(structure.x, structure.y)
        activeTool = structure.spec
        worldPos = Offset(structure.x, structure.y)
        state = PlacementState.DRAGGING_GHOST
        worldManager.removeStructureAt(structure.x, structure.y)
        onWorldChanged()
    }

    fun cancel() {
        if (movingStructure != null) {
            cancelMove()
        } else {
            state = PlacementState.IDLE
            activeTool = null
            worldPos = null
            isValid = false
            movingStructure = null
            originalPosition = null
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
        onWorldChanged()
    }
}
