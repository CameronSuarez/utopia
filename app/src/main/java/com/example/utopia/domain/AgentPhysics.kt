package com.example.utopia.domain

import androidx.compose.ui.geometry.Offset
import com.example.utopia.data.models.AgentRuntime
import com.example.utopia.data.models.AgentState
import com.example.utopia.data.models.SerializableOffset
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
    if (intent == "Idle" || intent == "Wandering" || intent == "IDLE") return Offset.Zero

    val targetType = when(intent) {
        "seek_sleep", "seek_stability" -> com.example.utopia.data.models.PoiType.HOUSE
        "seek_social" -> com.example.utopia.data.models.PoiType.PLAZA
        "seek_fun" -> com.example.utopia.data.models.PoiType.TAVERN
        "seek_stimulation" -> com.example.utopia.data.models.PoiType.STORE
        else -> return Offset.Zero
    }

    val targetPoi = worldState.pois
        .filter { it.type == targetType }
        .minByOrNull { it.pos.toOffset().minus(agent.position.toOffset()).getDistanceSquared() }
        ?: return Offset.Zero

    val toTarget = targetPoi.pos.toOffset().minus(agent.position.toOffset())
    val dist = toTarget.getDistance()
    
    // Step 2: SCALE INTENT FORCE (Tiles/sec^2 -> Pixels/sec^2)
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
        val xOnlyGX = ((agent.x + dx) / Constants.TILE_SIZE).toInt()
        var finalX = agent.x
        if (navGrid.isWalkable(xOnlyGX, agent.gridY)) {
            finalX = targetX
        }

        val yOnlyGY = ((agent.y + dy) / Constants.TILE_SIZE).toInt()
        var finalY = agent.y
        if (navGrid.isWalkable(agent.gridX, yOnlyGY)) {
            finalY = targetY
        }
        SerializableOffset(finalX, finalY)
    }
}

private fun Offset.plus(other: Offset) = Offset(x + other.x, y + other.y)
private fun Offset.times(scalar: Float) = Offset(x * scalar, y * scalar)
private fun Offset.div(scalar: Float) = Offset(x / scalar, y / scalar)
private fun Offset.clampMagnitude(max: Float): Offset {
    val mag = getDistance()
    return if (mag > max) this.div(mag).times(max) else this
}
