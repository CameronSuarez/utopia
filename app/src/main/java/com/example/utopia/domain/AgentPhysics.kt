package com.example.utopia.domain

import android.util.Log
import androidx.compose.ui.geometry.Offset
import com.example.utopia.data.models.AgentRuntime
import com.example.utopia.data.models.AgentState
import com.example.utopia.data.models.TileType
import com.example.utopia.data.models.WorldState
import com.example.utopia.util.Constants
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * DESIGN PRINCIPLE: THE DUMB EXECUTOR
 *
 * This system is responsible ONLY for the mechanics of movement, collision, and animation.
 * It does NOT decide where agents go or why. It consumes paths provided by the Intent/Planning layer.
 *
 * Responsibilities:
 * 1. Path Following: Stepping through waypoints.
 * 2. Physics: Solving collisions and sliding against obstacles via [tryMove].
 * 3. Animation: Updating visual state based on movement.
 * 4. Recovery: Detecting "stuck" states and requesting path refreshes.
 */

private const val WAYPOINT_RADIUS = 2f
private const val MIN_PROGRESS_EPSILON = 0.001f
private const val ANIM_TICK_MS = 150L

internal enum class NavReason {
    FALLBACK,
    CLEAR_PATH
}

internal fun updateAgents(
    agents: List<AgentRuntime>,
    worldState: WorldState,
    navGrid: NavGrid,
    deltaTimeMs: Long,
    nowMs: Long
) {
    for (agent in agents) {
        updateAgentTick(agent, worldState, navGrid, deltaTimeMs, nowMs)
    }
}

private fun updateAgentTick(
    agent: AgentRuntime,
    worldState: WorldState,
    navGrid: NavGrid,
    deltaTimeMs: Long,
    nowMs: Long
) {
    // Save position snapshot for animation logic
    agent.lastPosX = agent.x
    agent.lastPosY = agent.y
    
    // 1. STATE-DRIVEN ACTION
    when (agent.state) {
        AgentState.TRAVELING -> {
            updateTraveling(agent, worldState, deltaTimeMs, nowMs, navGrid)
        }
        else -> {
            // No action
        }
    }

    // 2. ANIMATION
    updateAgentAnimation(agent, deltaTimeMs) 

    // 3. Dwell timer
    if (agent.dwellTimerMs > 0) {
        agent.dwellTimerMs = (agent.dwellTimerMs - deltaTimeMs).coerceAtLeast(0L)
    }
}

internal fun requestNavigation(
    agent: AgentRuntime,
    target: Offset?,
    reason: NavReason,
    navGrid: NavGrid,
    worldState: WorldState
) {
    Log.d("NavStart", "agent=${agent.id} state=${agent.state} pos=(${agent.x}, ${agent.y})")

    if (target == null) {
        agent.pathTiles = emptyList()
        agent.pathIndex = 0
        agent.noProgressMs = 0L
        agent.goalPos = null

        if (reason != NavReason.CLEAR_PATH) {
            if (agent.state == AgentState.TRAVELING) {
                agent.state = AgentState.IDLE
            }
        }
        return
    }

    agent.state = AgentState.TRAVELING

    val startPos = Offset(agent.x, agent.y)
    val route = Pathfinding.planRoute(
        startPos = startPos,
        targetStructureId = null,
        targetWorldPos = target,
        navGrid = navGrid,
        structures = worldState.structures,
        requiredClearance = agent.collisionRadius
    )
    val path = route.first

    if (path.isEmpty()) {
        agent.repathCooldownUntilMs = System.currentTimeMillis() + Random.nextLong(1000L, 3000L)
        agent.pathTiles = emptyList()
        agent.state = AgentState.IDLE
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
    agent.goalPos = target
}

internal fun updateTraveling(
    agent: AgentRuntime,
    state: WorldState,
    deltaTimeMs: Long,
    nowMs: Long,
    navGrid: NavGrid
) {
    val target = agent.pathTiles.getOrNull(agent.pathIndex)?.let {
        val x = (it ushr 16) * Constants.TILE_SIZE + Constants.TILE_SIZE / 2f
        val y = (it and 0xFFFF) * Constants.TILE_SIZE + Constants.TILE_SIZE / 2f
        x to y
    } ?: agent.goalPos?.let { it.x to it.y }

    if (target == null) {
        agent.state = AgentState.IDLE
        requestNavigation(agent, null, NavReason.CLEAR_PATH, navGrid, state)
        return
    }

    val (targetX, targetY) = target

    if (nowMs < agent.yieldUntilMs || agent.dwellTimerMs > 0) {
        return
    }

    if (agent.noProgressMs > 2500L && agent.goalPos != null) {
        agent.noProgressMs = 0
        requestNavigation(agent, agent.goalPos, NavReason.FALLBACK, navGrid, state)
        return
    }

    val dx = targetX - agent.x
    val dy = targetY - agent.y
    val distSq = dx * dx + dy * dy

    val isFinalSegment = agent.pathTiles.isEmpty() || agent.pathIndex >= agent.pathTiles.lastIndex

    val arrivalRadius = if (isFinalSegment) {
        WAYPOINT_RADIUS + agent.collisionRadius
    } else {
        WAYPOINT_RADIUS
    }
    val arrivalRadiusSq = arrivalRadius * arrivalRadius

    if (distSq <= arrivalRadiusSq) {
        if (isFinalSegment) {
            agent.state = AgentState.IDLE
            requestNavigation(agent, null, NavReason.CLEAR_PATH, navGrid, state)
        } else {
            agent.pathIndex++
        }
        return
    }

    val speedMult = if (state.tiles[agent.gridX][agent.gridY] == TileType.ROAD) Constants.ON_ROAD_SPEED_MULT else Constants.OFF_ROAD_SPEED_MULT
    val baseSpeed = Constants.AGENT_BASE_SPEED_FACTOR * speedMult
    val movementScalar = 2.0f
    val maxMovementMag = baseSpeed * deltaTimeMs * movementScalar

    val dist = sqrt(distSq)
    val actualStep = min(maxMovementMag, dist)

    val desiredStepX = (dx / dist) * actualStep
    val desiredStepY = (dy / dist) * actualStep

    if (abs(desiredStepX) > 0.01f) agent.facingLeft = desiredStepX < 0

    val lastX = agent.x
    val lastY = agent.y
    tryMove(agent, desiredStepX, desiredStepY, navGrid)

    val deltaX = agent.x - lastX
    val deltaY = agent.y - lastY
    val forwardProgress = deltaX * (dx / dist) + deltaY * (dy / dist)

    if (forwardProgress > MIN_PROGRESS_EPSILON) {
        agent.noProgressMs = 0
    } else if (desiredStepX != 0f || desiredStepY != 0f) {
        agent.noProgressMs += deltaTimeMs
    }
}

private fun tryMove(
    agent: AgentRuntime,
    desiredStepX: Float,
    desiredStepY: Float,
    navGrid: NavGrid
) {
    var appliedStepX = 0f
    var appliedStepY = 0f

    val targetX = agent.x + desiredStepX
    val targetY = agent.y + desiredStepY

    val targetGX = (targetX / Constants.TILE_SIZE).toInt()
    val targetGY = (targetY / Constants.TILE_SIZE).toInt()

    if (navGrid.isWalkable(targetGX, targetGY)) {
        appliedStepX = desiredStepX
        appliedStepY = desiredStepY
    } else {
        val xOnlyGX = ((agent.x + desiredStepX) / Constants.TILE_SIZE).toInt()
        if (navGrid.isWalkable(xOnlyGX, agent.gridY)) {
            appliedStepX = desiredStepX
        }

        val yOnlyGY = ((agent.y + desiredStepY) / Constants.TILE_SIZE).toInt()
        if (navGrid.isWalkable(agent.gridX, yOnlyGY)) {
            appliedStepY = desiredStepY
        }
    }

    agent.x += appliedStepX
    agent.y += appliedStepY
    agent.gridX = (agent.x / Constants.TILE_SIZE).toInt()
    agent.gridY = (agent.y / Constants.TILE_SIZE).toInt()
}

internal fun updateAgentAnimation(
    agent: AgentRuntime,
    deltaTimeMs: Long
) {
    val isMoving = agent.state == AgentState.TRAVELING && (abs(agent.x - agent.lastPosX) > 0.01f || abs(agent.y - agent.lastPosY) > 0.01f)

    if (isMoving) {
        agent.animTimerMs += deltaTimeMs
        if (agent.animTimerMs >= ANIM_TICK_MS) {
            agent.animTimerMs = 0L
            agent.animFrame = (agent.animFrame + 1) % 4
        }
    } else {
        agent.animTimerMs = 0L
        agent.animFrame = 0
    }
}
