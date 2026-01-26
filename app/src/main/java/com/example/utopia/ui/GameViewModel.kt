package com.example.utopia.ui

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.utopia.domain.NavGrid
import com.example.utopia.domain.Pathfinding
import com.example.utopia.domain.WorldManager
import com.example.utopia.domain.updateAgents
import com.example.utopia.util.Constants
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class GameViewModel : ViewModel() {
    val navGrid = NavGrid()
    val worldManager = WorldManager(navGrid)

    // Note: placementController now doesn't strictly need to trigger updateNavGrid immediately,
    // as the game loop will catch the dirty rect and update it batch-style.
    val placementController = PlacementController(worldManager, ::updateNavGrid)

    var cameraOffset by mutableStateOf(Offset(0f, 0f))

    // Selection State
    var selectedAgentId by mutableStateOf<String?>(null)
    var selectedBuildingId by mutableStateOf<String?>(null)

    // Town Stats
    var totalPopulation by mutableIntStateOf(0)
    var buildingCount by mutableIntStateOf(0)

    // Debug
    var showNavGridDebug by mutableStateOf(false)
    var showHitboxesDebug by mutableStateOf(false)
    var showLotDebug by mutableStateOf(false) // Added for lot overlay
    var showAgentPaths by mutableStateOf(false)
    var showAgentLabels by mutableStateOf(false)
    // REMOVED: var showAgentClearanceDebug by mutableStateOf(false)
    // REMOVED: var showAgentVectorsDebug by mutableStateOf(false)

    var fps by mutableIntStateOf(0)
    var frameTimeMs by mutableLongStateOf(0L)

    // Time State (Now locally managed)
    var currentPhaseName by mutableStateOf("Day/Night") // Hardcode simplified time state
    var timeInCycle by mutableFloatStateOf(0f)
    var roadCount by mutableIntStateOf(0)

    init {
        Log.d("StartupHeartbeat", "GameViewModel.init - START")
        updateNavGrid() // Initial bake
        startGameLoop()
        Log.d("StartupHeartbeat", "GameViewModel.init - END")
    }

    private fun updateNavGrid(dirtyRect: Rect? = null) {
        val worldState = worldManager.worldState.value
        navGrid.update(worldState.tiles, worldState.structures, worldState.props, dirtyRect)
        Pathfinding.clearCache()
        Log.d("NavGrid", "NavGrid updated (dirty: ${dirtyRect != null}) and path cache cleared.")
    }

    private fun startGameLoop() {
        Log.d("StartupHeartbeat", "GameViewModel.startGameLoop - Launching")
        viewModelScope.launch {
            delay(1000)

            var lastTime = System.currentTimeMillis()
            var frameCount = 0
            var lastFpsUpdate = System.currentTimeMillis()
            var timeMs = 0L

            // Local time state to replace WorldState.timeOfDay
            var localTimeOfDay = 0f
            val totalCycleSec = 480f // Replaced Constants.PHASE_DURATION_SEC * 4
            val phaseDurationSec = 120f // Replaced Constants.PHASE_DURATION_SEC 

            while (isActive) {
                val currentTime = System.currentTimeMillis()
                val deltaTime = currentTime - lastTime
                lastTime = currentTime
                frameTimeMs = deltaTime
                timeMs += deltaTime

                val deltaSeconds = deltaTime / 1000f

                // Manual Time Update
                localTimeOfDay = (localTimeOfDay + deltaSeconds) % totalCycleSec

                // Agent Update (Only Movement/Animation)
                updateAgents(
                    agents = worldManager.worldState.value.agents,
                    worldState = worldManager.worldState.value,
                    navGrid = navGrid,
                    deltaTimeMs = deltaTime,
                    nowMs = currentTime
                )

                // Batch NavGrid Update
                val dirtyRect = worldManager.consumeDirtyRect()
                if (dirtyRect != null) {
                    updateNavGrid(dirtyRect)
                }

                // Update Debug Info
                val worldState = worldManager.worldState.value
                timeInCycle = localTimeOfDay
                
                // Simple Phase Calculation for minimal UI display
                val phaseIdx = (localTimeOfDay / phaseDurationSec).toInt().coerceIn(0, 3)
                currentPhaseName = when(phaseIdx) {
                    0 -> "Night"
                    1 -> "Morning"
                    2 -> "Afternoon"
                    3 -> "Evening"
                    else -> "Error"
                }

                // FPS and Stats Calculation
                frameCount++
                if (currentTime - lastFpsUpdate >= 1000) {
                    fps = frameCount
                    frameCount = 0
                    lastFpsUpdate = currentTime

                    val agents = worldState.agents
                    totalPopulation = agents.size
                    buildingCount = worldState.structures.size

                    // REMOVED: Relationship/Mood calculation

                    var roads = 0
                    for (x in 0 until Constants.MAP_TILES_W) {
                        for (y in 0 until Constants.MAP_TILES_H) {
                            if (worldState.tiles[x][y] == com.example.utopia.data.models.TileType.ROAD) roads++
                        }
                    }
                    roadCount = roads
                }

                delay(16)
            }
        }
    }

    fun onMoveCamera(delta: Offset) {
        cameraOffset += delta
    }

    fun selectAgent(id: String) {
        selectedAgentId = id
        selectedBuildingId = null
    }

    fun selectBuilding(id: String) {
        selectedBuildingId = id
        selectedAgentId = null
    }

    fun clearSelection() {
        selectedAgentId = null
        selectedBuildingId = null
    }
}
