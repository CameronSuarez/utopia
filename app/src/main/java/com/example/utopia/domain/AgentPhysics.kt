package com.example.utopia.domain

import androidx.compose.ui.geometry.Offset
import com.example.utopia.data.models.AgentIntent
import com.example.utopia.data.models.AgentRuntime
import com.example.utopia.data.models.AgentState
import com.example.utopia.data.models.PoiType
import com.example.utopia.data.models.SerializableOffset
import com.example.utopia.data.models.StructureType
import com.example.utopia.data.models.TileType
import com.example.utopia.data.models.WorldState
import com.example.utopia.util.Constants
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * DESIGN PRINCIPLE: THE DUMB EXECUTOR
 *
 * This system is responsible ONLY for the mechanics of movement, collision, and animation.
 * It does NOT decide where agents go or why.
 *
 * Responsibilities:
 * 1. Physics: Solving collisions and sliding against obstacles.
 * 2. Animation: Updating visual state based on movement.
 * 3. Separation: Keeping agents from overlapping.
 */

private const val ANIM_TICK_MS = 150L
private const val DAMPING = 0.985f // Dimensionless damping factor (0.98-0.99 is ideal for 60fps)

internal fun updateAgents(
    agents: List<AgentRuntime>,
    worldState: WorldState,
    navGrid: NavGrid,
    deltaTimeMs: Long,
    nowMs: Long
): List<AgentRuntime> {
    return agents.map { agent ->
        updateAgentTick(agent, worldState, navGrid, deltaTimeMs, nowMs)
    }
}

private fun updateAgentTick(
    agent: AgentRuntime,
    worldState: WorldState,
    navGrid: NavGrid,
    deltaTimeMs: Long,
    nowMs: Long
): AgentRuntime {
    if (agent.state == AgentState.SOCIALIZING) {
        return agent.copy(
            velocity = SerializableOffset(0f, 0f),
            animFrame = 0,
            animTimerMs = 0
        )
    }

    val deltaSeconds = deltaTimeMs / 1000f
    
    // 1. CALCULATE FORCES (Time-independent)
    val intentForce = calculateIntentForce(agent, worldState)
    val separationForce = calculateSeparationForce(agent, worldState.agents)
    val wanderForce = calculateWanderForce(agent)

    // 2. COMBINE AND CLAMP FORCES (Step 3)
    val totalForce = intentForce.plus(separationForce).plus(wanderForce)
    
    // Scale user constants (Tiles -> Pixels) to match world units
    val scaledMaxForce = Constants.MAX_FORCE * Constants.TILE_SIZE
    val clampedForce = totalForce.clampMagnitude(scaledMaxForce)

    // 3. INTEGRATE ACCELERATION -> VELOCITY (dt ONCE)
    val oldVelocity = agent.velocity.toOffset()
    var newVelocity = oldVelocity.plus(clampedForce.times(deltaSeconds))

    // 4. APPLY DAMPING (dimensionless)
    newVelocity = newVelocity.times(DAMPING)

    // 5. SPEED LIMIT (Step 4 - CRITICAL)
    val speedMult = if (worldState.tiles.getOrNull(agent.gridX)?.getOrNull(agent.gridY) == TileType.ROAD) 
        Constants.ON_ROAD_SPEED_MULT else Constants.OFF_ROAD_SPEED_MULT
    
    val scaledMaxSpeed = Constants.MAX_SPEED * Constants.TILE_SIZE * speedMult
    newVelocity = newVelocity.clampMagnitude(scaledMaxSpeed)

    // 6. INTEGRATE VELOCITY -> POSITION (dt ONCE)
    val step = newVelocity.times(deltaSeconds)
    val newPosition = tryMove(agent, step.x, step.y, navGrid)

    // 7. UPDATE ANIMATION STATE
    val isMoving = step.getDistance() > 0.01f
    val nextAnimFrame = if (isMoving) {
        val totalAnimTime = agent.animTimerMs + deltaTimeMs
        if (totalAnimTime >= ANIM_TICK_MS) (agent.animFrame + 1) % 4 else agent.animFrame
    } else 0
    val nextAnimTimer = if (isMoving) (agent.animTimerMs + deltaTimeMs) % ANIM_TICK_MS else 0L
    
    val nextFacingLeft = if (abs(step.x) > 0.01f) step.x < 0 else agent.facingLeft

    return agent.copy(
        position = newPosition,
        velocity = SerializableOffset(newVelocity.x, newVelocity.y),
        lastPosX = agent.x,
        lastPosY = agent.y,
        animFrame = nextAnimFrame,
        animTimerMs = nextAnimTimer,
        facingLeft = nextFacingLeft,
        state = if (isMoving) AgentState.TRAVELING else AgentState.IDLE
    )
}

private fun calculateIntentForce(agent: AgentRuntime, worldState: WorldState): Offset {
    val intent = agent.currentIntent
    if (intent is AgentIntent.Idle || intent is AgentIntent.Wandering) return Offset.Zero

    val pos = agent.position.toOffset()

    // 1. Check if we are already in a zone that satisfies this intent.
    // This prevents agents from trying to "push" into the center of a building footprint.
    val tile = worldState.getTileAtWorld(pos)
    val structure = worldState.getInfluencingStructure(pos)
    val onLot = tile == TileType.BUILDING_LOT || tile == TileType.PLAZA

    val isCurrentlySatisfied = when (intent) {
        AgentIntent.SeekSleep -> onLot && structure?.type?.providesSleep == true
        AgentIntent.SeekStability -> onLot && structure?.type?.providesStability == true
        AgentIntent.SeekFun -> onLot && structure?.type?.providesFun == true
        AgentIntent.SeekStimulation -> (onLot && structure?.type?.providesStimulation == true) || tile == TileType.ROAD
        else -> false
    }

    if (isCurrentlySatisfied) return Offset.Zero

    // 2. Find the closest POI that can satisfy this intent.
    val targetPoi = worldState.pois
        .filter { poi ->
            when (intent) {
                AgentIntent.SeekSleep -> poi.type == PoiType.HOUSE || poi.type == PoiType.CASTLE
                AgentIntent.SeekFun -> poi.type == PoiType.TAVERN || poi.type == PoiType.PLAZA
                AgentIntent.SeekStimulation -> poi.type == PoiType.STORE || poi.type == PoiType.WORKSHOP || poi.type == PoiType.CASTLE
                AgentIntent.SeekStability -> poi.type == PoiType.STORE || poi.type == PoiType.WORKSHOP
                else -> false
            }
        }
        .minByOrNull { it.pos.toOffset().minus(pos).getDistanceSquared() }
        ?: return Offset.Zero

    val toTarget = targetPoi.pos.toOffset().minus(pos)
    val dist = toTarget.getDistance()

    return if (dist > 5f) {
        toTarget.div(dist).times(Constants.INTENT_FORCE * Constants.TILE_SIZE)
    } else {
        Offset.Zero
    }
}

private fun calculateSeparationForce(agent: AgentRuntime, allAgents: List<AgentRuntime>): Offset {
    val radius = Constants.AGENT_COLLISION_RADIUS
    var push = Offset.Zero
    
    for (other in allAgents) {
        if (other.id == agent.id) continue
        val toAgent = agent.position.toOffset().minus(other.position.toOffset())
        val distSq = toAgent.getDistanceSquared()
        
        if (distSq < radius * radius && distSq > 0.01f) {
            val dist = sqrt(distSq)
            val forceMagnitude = (radius - dist) / radius
            push = push.plus(toAgent.div(dist).times(forceMagnitude * Constants.SEPARATION_FORCE * Constants.TILE_SIZE))
        }
    }
    return push
}

private fun calculateWanderForce(agent: AgentRuntime): Offset {
    if (agent.state != AgentState.IDLE) return Offset.Zero
    val seed = agent.id.hashCode().toLong() + (System.currentTimeMillis() / 2000)
    val rng = Random(seed)
    return Offset(rng.nextFloat() * 2 - 1, rng.nextFloat() * 2 - 1)
        .times(Constants.WANDER_FORCE * Constants.TILE_SIZE)
}

private fun tryMove(
    agent: AgentRuntime,
    dx: Float,
    dy: Float,
    navGrid: NavGrid
): SerializableOffset {
    val targetX = agent.x + dx
    val targetY = agent.y + dy

    val targetGX = (targetX / Constants.TILE_SIZE).toInt()
    val targetGY = (targetY / Constants.TILE_SIZE).toInt()

    return if (navGrid.isWalkable(targetGX, targetGY)) {
        SerializableOffset(targetX, targetY)
    } else {
        // --- Obstacle Avoidance (Force field logic) ---
        // If the direct path is blocked, we try to slide along the obstacle
        val currentGX = (agent.x / Constants.TILE_SIZE).toInt()
        val currentGY = (agent.y / Constants.TILE_SIZE).toInt()
        
        val xOnlyGX = ((agent.x + dx) / Constants.TILE_SIZE).toInt()
        var finalX = agent.x
        if (navGrid.isWalkable(xOnlyGX, currentGY)) {
            finalX = targetX
        }

        val yOnlyGY = ((agent.y + dy) / Constants.TILE_SIZE).toInt()
        var finalY = agent.y
        if (navGrid.isWalkable(currentGX, yOnlyGY)) {
            finalY = targetY
        }
        
        // If still blocked on both axes (corner case), check if there's a nearby walkable tile to "bounce" towards
        if (finalX == agent.x && finalY == agent.y) {
            val nudge = Pathfinding.nudgeOutOfObstacle(targetGX, targetGY, navGrid)
            if (nudge != null) {
                val nudgeX = (nudge.first + 0.5f) * Constants.TILE_SIZE
                val nudgeY = (nudge.second + 0.5f) * Constants.TILE_SIZE
                val toNudge = Offset(nudgeX - agent.x, nudgeY - agent.y)
                val step = toNudge.div(toNudge.getDistance()).times(2f) // Small step toward opening
                return SerializableOffset(agent.x + step.x, agent.y + step.y)
            }
        }

        SerializableOffset(finalX, finalY)
    }
}

private fun Offset.clampMagnitude(max: Float): Offset {
    val mag = getDistance()
    return if (mag > max) this.div(mag).times(max) else this
}
