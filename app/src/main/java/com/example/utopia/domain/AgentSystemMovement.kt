package com.example.utopia.domain

import com.example.utopia.data.models.AgentRuntime
import com.example.utopia.data.models.AgentState
import com.example.utopia.data.models.TileType
import com.example.utopia.data.models.WorldState
import com.example.utopia.util.Constants
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

private const val WAYPOINT_RADIUS = 2f
private const val MIN_PROGRESS_EPSILON = 0.001f

internal fun AgentSystem.updateTraveling(
    agent: AgentRuntime,
    state: WorldState,
    deltaTimeMs: Long,
    nowMs: Long,
    context: PhaseContext
) {
    val target = agent.pathTiles.getOrNull(agent.pathIndex)?.let {
        val x = (it ushr 16) * Constants.TILE_SIZE + Constants.TILE_SIZE / 2f
        val y = (it and 0xFFFF) * Constants.TILE_SIZE + Constants.TILE_SIZE / 2f
        x to y
    } ?: agent.goalPos?.let { it.x to it.y }

    if (target == null) {
        if (agent.state == AgentState.TRAVELING || agent.state == AgentState.AT_WORK) {
            handleArrival(agent, nowMs, context)
        }
        return
    }

    val (targetX, targetY) = target

    if (nowMs < agent.yieldUntilMs || agent.dwellTimerMs > 0) {
        return
    }

    if (agent.noProgressMs > 2500L && agent.goalPos != null) {
        agent.noProgressMs = 0
        requestNavigation(agent, agent.goalPos, NavReason.FALLBACK)
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
            handleArrival(agent, nowMs, context)
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
    tryMove(agent, desiredStepX, desiredStepY)

    val deltaX = agent.x - lastX
    val deltaY = agent.y - lastY
    val forwardProgress = deltaX * (dx / dist) + deltaY * (dy / dist)

    if (forwardProgress > MIN_PROGRESS_EPSILON) {
        agent.noProgressMs = 0
    } else if (desiredStepX != 0f || desiredStepY != 0f) {
        agent.noProgressMs += deltaTimeMs
    }
}

private fun AgentSystem.tryMove(
    agent: AgentRuntime,
    desiredStepX: Float,
    desiredStepY: Float
) {
    var appliedStepX = 0f
    var appliedStepY = 0f

    val targetX = agent.x + desiredStepX
    val targetY = agent.y + desiredStepY

    val targetGX = (targetX / Constants.TILE_SIZE).toInt()
    val targetGY = (targetY / Constants.TILE_SIZE).toInt()

    if (this.navGrid.isWalkable(targetGX, targetGY)) {
        appliedStepX = desiredStepX
        appliedStepY = desiredStepY
    } else {
        val xOnlyGX = ((agent.x + desiredStepX) / Constants.TILE_SIZE).toInt()
        if (this.navGrid.isWalkable(xOnlyGX, agent.gridY)) {
            appliedStepX = desiredStepX
        }

        val yOnlyGY = ((agent.y + desiredStepY) / Constants.TILE_SIZE).toInt()
        if (this.navGrid.isWalkable(agent.gridX, yOnlyGY)) {
            appliedStepY = desiredStepY
        }
    }

    agent.x += appliedStepX
    agent.y += appliedStepY
    agent.gridX = (agent.x / Constants.TILE_SIZE).toInt()
    agent.gridY = (agent.y / Constants.TILE_SIZE).toInt()
}
