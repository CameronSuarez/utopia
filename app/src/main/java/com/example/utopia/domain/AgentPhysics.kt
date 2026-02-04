package com.example.utopia.domain

import androidx.compose.ui.geometry.Offset
import com.example.utopia.data.models.AgentIntent
import com.example.utopia.data.models.AgentRuntime
import com.example.utopia.data.models.AgentState
import com.example.utopia.data.models.PoiType
import com.example.utopia.data.models.SerializableOffset
import com.example.utopia.data.models.TileType
import com.example.utopia.data.models.WorldState
import com.example.utopia.util.Constants
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

private const val DAMPING = 0.985f
private const val STRIDE_PX = 32f

internal fun updateAgents(
    agents: List<AgentRuntime>,
    worldState: WorldState,
    navGrid: NavGrid,
    deltaTimeMs: Long
): List<AgentRuntime> {
    return agents.map { agent ->
        updateAgentTick(agent, worldState, navGrid, deltaTimeMs)
    }
}

private fun updateAgentTick(
    agent: AgentRuntime,
    worldState: WorldState,
    navGrid: NavGrid,
    deltaTimeMs: Long
): AgentRuntime {
    if (agent.state == AgentState.SOCIALIZING) {
        return agent.copy(
            velocity = SerializableOffset(0f, 0f),
            animFrame = 0
        )
    }

    if (agent.state == AgentState.SLEEPING || agent.state == AgentState.WORKING) {
        val intentSatisfied = isIntentSatisfied(agent, worldState)
        if (intentSatisfied) {
            return agent.copy(
                velocity = SerializableOffset(0f, 0f),
                animFrame = 0
            )
        }
    }


    val deltaSeconds = deltaTimeMs / 1000f

    val intentForce = calculateIntentForce(agent, worldState)
    val separationForce = calculateSeparationForce(agent, worldState.agents)
    val wanderForce = calculateWanderForce(agent)

    val totalForce = intentForce.plus(separationForce).plus(wanderForce)

    val scaledMaxForce = Constants.MAX_FORCE * Constants.TILE_SIZE
    val clampedForce = totalForce.clampMagnitude(scaledMaxForce)

    val oldVelocity = agent.velocity.toOffset()
    var newVelocity = oldVelocity.plus(clampedForce.times(deltaSeconds))

    newVelocity = newVelocity.times(DAMPING)

    val speedMult = if (worldState.tiles.getOrNull(agent.gridX)?.getOrNull(agent.gridY) == TileType.ROAD)
        Constants.ON_ROAD_SPEED_MULT else Constants.OFF_ROAD_SPEED_MULT

    val scaledMaxSpeed = Constants.MAX_SPEED * Constants.TILE_SIZE * speedMult
    newVelocity = newVelocity.clampMagnitude(scaledMaxSpeed)

    val step = newVelocity.times(deltaSeconds)
    val newPosition = tryMove(agent, step.x, step.y, navGrid)

    val moveSpeedPxPerSec = newVelocity.getDistance()
    val nextAnimAcc = agent.animAcc + (moveSpeedPxPerSec / STRIDE_PX) * deltaSeconds
    val nextAnimFrame = (nextAnimAcc * 4f).toInt() and 3

    val nextFacingLeft = if (abs(step.x) > 0.01f) step.x < 0 else agent.facingLeft

    val structure = worldState.getInfluencingStructure(agent.position.toOffset())
    val intentSatisfiedState = when (val intent = agent.currentIntent) {
        AgentIntent.SeekSleep -> if (structure?.spec?.providesSleep == true) AgentState.SLEEPING else null
        AgentIntent.SeekFun -> if (structure?.spec?.providesFun == true) AgentState.HAVING_FUN else null
        AgentIntent.SeekStability -> if (structure?.spec?.id == "STORE") AgentState.TRADING else null
        AgentIntent.SeekStimulation -> if (structure?.spec?.id == "STORE") AgentState.TRADING else null
        is AgentIntent.Construct -> if (structure?.id == intent.targetId) AgentState.WORKING else null
        is AgentIntent.GetResource -> if (structure?.id == intent.targetId) AgentState.WORKING else null
        is AgentIntent.StoreResource -> if (structure?.id == intent.targetId) AgentState.WORKING else null
        AgentIntent.Work -> {
            // An agent is only truly WORKING if they have the Work intent AND are at their assigned workplace.
            if (agent.workplaceId != null && structure?.id == agent.workplaceId) {
                AgentState.WORKING
            } else {
                null
            }
        }
        else -> null
    }

    val isMoving = newVelocity.getDistanceSquared() > 0.1f
    val nextState = intentSatisfiedState ?: if (isMoving) AgentState.TRAVELING else AgentState.IDLE

    return agent.copy(
        position = newPosition,
        velocity = SerializableOffset(newVelocity.x, newVelocity.y),
        lastPosX = agent.x,
        lastPosY = agent.y,
        animAcc = nextAnimAcc,
        animFrame = nextAnimFrame,
        facingLeft = nextFacingLeft,
        state = nextState
    )
}

private fun isIntentSatisfied(agent: AgentRuntime, worldState: WorldState): Boolean {
    val structure = worldState.getInfluencingStructure(agent.position.toOffset())
    return when (val intent = agent.currentIntent) {
        AgentIntent.Work -> structure?.id == agent.workplaceId && agent.workplaceId != null
        AgentIntent.SeekSleep -> {
            if (agent.homeId != null) {
                structure?.id == agent.homeId
            } else {
                structure?.spec?.providesSleep == true
            }
        }
        AgentIntent.SeekFun -> structure?.spec?.providesFun == true
        AgentIntent.SeekStability -> structure?.spec?.id == "STORE" || structure?.spec?.id == "WORKSHOP" || structure?.spec?.id == "LUMBERJACK_HUT"
        AgentIntent.SeekStimulation -> structure?.spec?.providesStimulation == true
        is AgentIntent.GetResource, is AgentIntent.StoreResource -> {
            val targetId = if (intent is AgentIntent.GetResource) intent.targetId else (intent as AgentIntent.StoreResource).targetId
            structure?.id == targetId
        }
        is AgentIntent.Construct -> structure?.id == intent.targetId
        else -> false
    }
}

private fun calculateIntentForce(agent: AgentRuntime, worldState: WorldState): Offset {
    val intent = agent.currentIntent
    if (intent is AgentIntent.Idle || intent is AgentIntent.Wandering || isIntentSatisfied(agent, worldState)) {
        return Offset.Zero
    }

    val pos = agent.position.toOffset()

    val targetStructure = when (intent) {
        is AgentIntent.GetResource -> worldState.structures.find { it.id == intent.targetId }
        is AgentIntent.StoreResource -> worldState.structures.find { it.id == intent.targetId }
        is AgentIntent.Construct -> worldState.structures.find { it.id == intent.targetId }
        AgentIntent.Work -> worldState.structures.find { it.id == agent.workplaceId }
        AgentIntent.SeekSleep -> agent.homeId?.let { homeId -> worldState.structures.find { it.id == homeId } }
        else -> null
    }

    if (targetStructure != null) {
        val targetPos = Offset(targetStructure.x + targetStructure.spec.worldWidth / 2, targetStructure.y - targetStructure.spec.worldHeight / 2)
        val toTarget = targetPos.minus(pos)
        val dist = toTarget.getDistance()
        return if (dist > 5f) toTarget.div(dist).times(Constants.INTENT_FORCE * Constants.TILE_SIZE) else Offset.Zero
    }

    val targetPoi = worldState.pois
        .filter { poi ->
            when (intent) {
                AgentIntent.SeekSleep -> poi.type == PoiType.HOUSE || poi.type == PoiType.CASTLE
                AgentIntent.SeekFun -> poi.type == PoiType.TAVERN || poi.type == PoiType.PLAZA
                AgentIntent.SeekStimulation -> poi.type == PoiType.STORE || poi.type == PoiType.WORKSHOP || poi.type == PoiType.CASTLE || poi.type == PoiType.LUMBERJACK_HUT
                AgentIntent.SeekStability -> poi.type == PoiType.STORE || poi.type == PoiType.WORKSHOP || poi.type == PoiType.LUMBERJACK_HUT
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
    val isIdleWandering = agent.state == AgentState.IDLE &&
            (agent.currentIntent == AgentIntent.Idle || agent.currentIntent == AgentIntent.Wandering)
    val isFunWandering = agent.state == AgentState.HAVING_FUN && agent.currentIntent == AgentIntent.SeekFun
    val isTradingWandering = agent.state == AgentState.TRADING

    if (isIdleWandering) {
        val seed = agent.id.hashCode().toLong() + (System.currentTimeMillis() / 2000)
        val rng = Random(seed)
        return Offset(rng.nextFloat() * 2 - 1, rng.nextFloat() * 2 - 1)
            .times(Constants.WANDER_FORCE * Constants.TILE_SIZE)
    }

    if (isFunWandering || isTradingWandering) {
        val timeBlock = System.currentTimeMillis() / 1500L
        val seed = agent.id.hashCode().toLong() + timeBlock
        val rng = Random(seed)
        val shouldMove = rng.nextFloat() < 0.33f

        return if (!shouldMove) {
            Offset.Zero
        } else {
            Offset(rng.nextFloat() * 2 - 1, rng.nextFloat() * 2 - 1)
                .times(Constants.WANDER_FORCE * Constants.TILE_SIZE)
        }
    }
    return Offset.Zero
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
        
        if (finalX == agent.x && finalY == agent.y) {
            val nudge = Pathfinding.nudgeOutOfObstacle(targetGX, targetGY, navGrid)
            if (nudge != null) {
                val nudgeX = (nudge.first + 0.5f) * Constants.TILE_SIZE
                val nudgeY = (nudge.second + 0.5f) * Constants.TILE_SIZE
                val toNudge = Offset(nudgeX - agent.x, nudgeY - agent.y)
                val step = toNudge.div(toNudge.getDistance()).times(2f)
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
