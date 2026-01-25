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
import com.example.utopia.domain.AgentSystem
import com.example.utopia.domain.NavGrid
import com.example.utopia.domain.Pathfinding
import com.example.utopia.domain.WorldManager
import com.example.utopia.util.Constants
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class GameViewModel : ViewModel() {
    val navGrid = NavGrid()
    val worldManager = WorldManager(navGrid)
    val agentSystem = AgentSystem(worldManager, worldManager.random, navGrid)
    // Note: placementController now doesn't strictly need to trigger updateNavGrid immediately,
    // as the game loop will catch the dirty rect and update it batch-style.
    val placementController = PlacementController(worldManager, ::updateNavGrid)

    var cameraOffset by mutableStateOf(Offset(0f, 0f))

    // Selection State
    var selectedAgentId by mutableStateOf<String?>(null)
    var selectedBuildingId by mutableStateOf<String?>(null)

    // Town Stats
    var totalPopulation by mutableIntStateOf(0)
    var employedCount by mutableIntStateOf(0)
    var unemployedCount by mutableIntStateOf(0)
    var buildingCount by mutableIntStateOf(0)
    var townMood by mutableFloatStateOf(0f)
    private var moodHistory = mutableListOf<Float>()

    // Debug
    var showNavGridDebug by mutableStateOf(false)
    var showHitboxesDebug by mutableStateOf(false)
    var showLotDebug by mutableStateOf(false) // Added for lot overlay
    var showAgentPaths by mutableStateOf(false)
    var showAgentLabels by mutableStateOf(false)
    var showAgentClearanceDebug by mutableStateOf(false)
    var showAgentVectorsDebug by mutableStateOf(false)

    var fps by mutableIntStateOf(0)
    var frameTimeMs by mutableLongStateOf(0L)
    var pathRequestsPerSec by mutableIntStateOf(0)
    var roadPathsPerSec by mutableIntStateOf(0)
    var offRoadFallbacksPerSec by mutableIntStateOf(0)
    var socialTriggersPerCycle by mutableIntStateOf(0)
    var bumpsPerCycle by mutableIntStateOf(0)
    var friendChasesStarted by mutableIntStateOf(0)
    var friendChasesAborted by mutableIntStateOf(0)
    var blockedByCooldown by mutableIntStateOf(0)
    var blockedByPhase by mutableIntStateOf(0)
    var blockedByState by mutableIntStateOf(0)
    var currentPhaseName by mutableStateOf("Unknown")
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

            while (isActive) {
                val currentTime = System.currentTimeMillis()
                val deltaTime = currentTime - lastTime
                lastTime = currentTime
                frameTimeMs = deltaTime

                worldManager.updateTime(deltaTime / 1000f)
                agentSystem.update(deltaTime)

                // Batch NavGrid Update
                val dirtyRect = worldManager.consumeDirtyRect()
                if (dirtyRect != null) {
                    updateNavGrid(dirtyRect)
                }

                // Update Debug Info
                val worldState = worldManager.worldState.value
                timeInCycle = worldState.timeOfDay
                val phaseIdx = (timeInCycle / Constants.PHASE_DURATION_SEC).toInt().coerceIn(0, 3)
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

                    pathRequestsPerSec = agentSystem.pathfindCountThisSecond
                    agentSystem.pathfindCountThisSecond = 0
                    roadPathsPerSec = agentSystem.roadPathsCountThisSecond
                    agentSystem.roadPathsCountThisSecond = 0
                    offRoadFallbacksPerSec = agentSystem.offRoadFallbacksCountThisSecond
                    agentSystem.offRoadFallbacksCountThisSecond = 0
                    socialTriggersPerCycle = agentSystem.numSocialTriggers
                    bumpsPerCycle = agentSystem.numBumps
                    friendChasesStarted = agentSystem.numFriendChasesStarted
                    friendChasesAborted = agentSystem.numFriendChasesAborted
                    blockedByCooldown = agentSystem.numSocialBlockedByCooldown
                    blockedByPhase = agentSystem.numSocialBlockedByPhase
                    blockedByState = agentSystem.numSocialBlockedByState

                    val agents = worldState.agents
                    totalPopulation = agents.size
                    employedCount = agents.count { it.jobId != null }
                    unemployedCount = totalPopulation - employedCount
                    buildingCount = worldState.structures.size

                    val rels = worldState.relationships.values
                    if (rels.isNotEmpty()) {
                        val avgRel = rels.map { it.toFloat() }.average().toFloat() / 3f
                        moodHistory.add(avgRel)
                        if (moodHistory.size > 120) moodHistory.removeAt(0)
                        townMood = moodHistory.average().toFloat()
                        agentSystem.townMood = townMood
                    }

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
