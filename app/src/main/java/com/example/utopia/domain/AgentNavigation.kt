package com.example.utopia.domain

import android.util.Log
import androidx.compose.ui.geometry.Offset
import com.example.utopia.data.models.AgentRuntime
import com.example.utopia.util.Constants
import kotlin.random.Random

internal enum class NavReason {
    APPLY_GOAL_INTENT,
    INTENT_TRANSITION,
    FALLBACK,
    GOAL_COMPLETE,
    DURATION_ARRIVAL,
    MANUAL_DEBUG,
    INVALIDATED_PATH
}

internal fun AgentSystem.requestNavigation(
    agent: AgentRuntime,
    target: Offset?,
    reason: NavReason
) {
    agent.pathTiles = emptyList()
    agent.pathIndex = 0
    agent.noProgressMs = 0L

    agent.goalPos = target

    if (target == null) {
        // Explicit idle request — no pathfinding
        return
    }

    telemetry.pathRequests++
    if (reason == NavReason.FALLBACK) {
        telemetry.goalRetries++
    }

    val goalLabel = target.let { "(${"%.1f".format(it.x)}, ${"%.1f".format(it.y)})" }
    Log.d("AgentGoal", "id=${agent.id} state=${agent.state} goal=$goalLabel source=$reason")

    val worldState = worldManager.worldState.value
    val route = Pathfinding.planRoute(
        startPos = Offset(agent.x, agent.y),
        targetStructureId = agent.goalIntentTargetId,
        targetWorldPos = target,
        navGrid = this.navGrid,
        structures = worldState.structures,
        requiredClearance = agent.collisionRadius
    )
    val path = route.first

    if (path.isEmpty()) {
        agent.repathCooldownUntilMs = System.currentTimeMillis() + Random.nextLong(1000L, 3000L)
        agent.pathTiles = emptyList()
    } else {
        val tiles = path.map { pos ->
            val gx = (pos.x / Constants.TILE_SIZE).toInt().coerceIn(0, Constants.MAP_TILES_W - 1)
            val gy = (pos.y / Constants.TILE_SIZE).toInt().coerceIn(0, Constants.MAP_TILES_H - 1)
            (gx shl 16) or (gy and 0xFFFF)
        }
        agent.pathTiles = tiles
    }
    agent.pathIndex = 0
    agent.noProgressMs = 0
}
