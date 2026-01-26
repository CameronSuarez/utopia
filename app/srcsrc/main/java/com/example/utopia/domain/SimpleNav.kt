package com.example.utopia.domain

import androidx.compose.ui.geometry.Offset
import com.example.utopia.data.models.AgentRuntime
import com.example.utopia.data.models.TileType
import com.example.utopia.data.models.WorldState
import com.example.utopia.util.Constants
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

private class SimpleNavState(val agent: AgentRuntime) {
    var goalPos: Offset? by agent::goalPos
    var pathTiles: List<Int> by agent::pathTiles // FIX: Changed type from IntArray to List<Int>
    var pathIndex: Int by agent::pathIndex
    var repathCooldownUntilMs: Long by agent::repathCooldownUntilMs
    var noProgressMs: Long by agent::noProgressMs
    var lastPosX: Float by agent::lastPosX
    var lastPosY: Float by agent::lastPosY
    var blockedByAgent: Boolean by agent::blockedByAgent
    var dwellTimerMs: Long by agent::dwellTimerMs
    var detourPos: Offset? by agent::detourPos
}

private enum class SimpleNavTargetType { GOAL, WAYPOINT, DETOUR }

private data class SimpleNavTarget(val pos: Offset, val type: SimpleNavTargetType)

private data class SimpleNavMoveResult(
    val appliedStepX: Float,
    val appliedStepY: Float,
    val blocked: Boolean
)

object SimpleNav {
    private val WAYPOINT_RADIUS: Float
        get() = max(6f, Constants.TILE_SIZE * 0.25f)

    private const val MIN_PROGRESS_EPSILON = 0.05f

    private fun calculateJitter(agentId: Int): Offset {
        val hash = agentId.hashCode()
        val jitterX = (hash % 7 - 3) * 0.05f * Constants.TILE_SIZE
        val jitterY = ((hash / 7) % 7 - 3) * 0.05f * Constants.TILE_SIZE
        return Offset(jitterX, jitterY)
    }

    fun updateAgent(
        agentSystem: AgentSystem,
        agent: AgentRuntime,
        state: WorldState,
        deltaTimeMs: Long,
        nowMs: Long
    ) {
        val navState = SimpleNavState(agent)

        if (navState.dwellTimerMs > 0) {
            navState.dwellTimerMs = (navState.dwellTimerMs - deltaTimeMs).coerceAtLeast(0L)
        }

        if (navState.lastPosX == 0f && navState.lastPosY == 0f) {
            navState.lastPosX = agent.x
            navState.lastPosY = agent.y
        }

        val target = resolveTarget(navState)
        if (target == null || nowMs < agent.yieldUntilMs || navState.dwellTimerMs > 0) {
            return
        }

        // Stuck detection: if we've been trying to move for a while with no progress, force a replan
        if (navState.noProgressMs > 2500L && agent.goalPos != null) {
            navState.noProgressMs = 0
            agentSystem.requestNavigation(agent, agent.goalPos, NavReason.FALLBACK)
            return
        }

        val dx = target.pos.x - agent.x
        val dy = target.pos.y - agent.y

        // Improved arrival logic: be more tolerant for the final goal to account for collision/size
        val arrivalThreshold = if (target.type == SimpleNavTargetType.GOAL) {
            WAYPOINT_RADIUS + agent.collisionRadius
        } else {
            WAYPOINT_RADIUS
        }

        val distSq = dx * dx + dy * dy
        if (distSq <= arrivalThreshold * arrivalThreshold) {
            onTargetArrived(navState)
            return
        }

        val speedMult = if (state.tiles[agent.gridX][agent.gridY] == TileType.ROAD) Constants.ON_ROAD_SPEED_MULT else Constants.OFF_ROAD_SPEED_MULT
        val baseSpeed = Constants.AGENT_BASE_SPEED_FACTOR * speedMult
        val maxMovementMag = baseSpeed * deltaTimeMs * Constants.AGENT_MOVEMENT_SCALAR

        val dist = sqrt(distSq)
        var desiredStepX = (dx / dist) * maxMovementMag
        var desiredStepY = (dy / dist) * maxMovementMag

        val desiredMag = sqrt(desiredStepX * desiredStepX + desiredStepY * desiredStepY)
        if (desiredMag > dist) {
            desiredStepX = dx
            desiredStepY = dy
        }
        
        if (abs(desiredStepX) > 0.01f) agent.facingLeft = desiredStepX < 0

        tryMove(agent, agentSystem.navGrid, desiredStepX, desiredStepY)

        val deltaX = agent.x - navState.lastPosX
        val deltaY = agent.y - navState.lastPosY
        val forwardProgress = deltaX * (dx / dist) + deltaY * (dy / dist)

        if (forwardProgress > MIN_PROGRESS_EPSILON) {
            navState.noProgressMs = 0
            navState.lastPosX = agent.x
            navState.lastPosY = agent.y
        } else if (desiredStepX != 0f || desiredStepY != 0f) {
            navState.noProgressMs += deltaTimeMs
            navState.lastPosX = agent.x
            navState.lastPosY = agent.y
        }
    }

    private fun resolveTarget(navState: SimpleNavState): SimpleNavTarget? {
        val detour = navState.detourPos
        if (detour != null) return SimpleNavTarget(detour, SimpleNavTargetType.DETOUR)

        val tiles = navState.pathTiles
        val index = navState.pathIndex
        if (tiles.isNotEmpty() && index in tiles.indices) {
            val packed = tiles[index]
            val gx = packed shr 16
            val gy = packed and 0xFFFF
            val jitter = calculateJitter(navState.agent.shortId)
            val waypointPos = Offset((gx + 0.5f) * Constants.TILE_SIZE, (gy + 0.5f) * Constants.TILE_SIZE) + jitter
            return SimpleNavTarget(waypointPos, SimpleNavTargetType.WAYPOINT)
        }
        return navState.goalPos?.let { SimpleNavTarget(it, SimpleNavTargetType.GOAL) }
    }

    private fun onTargetArrived(navState: SimpleNavState) {
        val detour = navState.detourPos
        if (detour != null) {
            navState.detourPos = null
            return
        }

        if (navState.pathTiles.isNotEmpty()) {
            navState.pathIndex += 1
            if (navState.pathIndex < navState.pathTiles.size) {
                return
            }
        }

        // Final arrival at goal or end of path
        navState.pathTiles = emptyList()
        navState.pathIndex = 0
        navState.goalPos = null

        if (navState.agent.state == com.example.utopia.data.models.AgentState.TRAVELING) {
            navState.agent.state = com.example.utopia.data.models.AgentState.IDLE
        }
    }

    private fun tryMove(
        agent: AgentRuntime,
        navGrid: NavGrid,
        desiredStepX: Float,
        desiredStepY: Float
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
}
