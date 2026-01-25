package com.example.utopia.domain

import android.util.Log
import androidx.compose.ui.geometry.Offset
import com.example.utopia.data.models.*
import com.example.utopia.util.Constants
import kotlin.math.*
import kotlin.random.Random

// --- PRIMARY GOAL PLANNING ---

/**
 * Main entry point for Goal logic.
 * Decides if an agent needs a new high-level intent or a local micro-action.
 */
internal fun AgentSystem.updateGoal(agent: AgentRuntime, state: WorldState, nowMs: Long, phase: DayPhase, cycleIndex: Int) {
    if (agent.state == AgentState.SOCIALIZING || agent.state == AgentState.SLEEPING) return
    if (agent.dwellTimerMs > 0 || nowMs < agent.goalBlockedUntilMs) return

    agent.goalIntentSlotIndex = agent.goalIntentSlotIndex.coerceAtLeast(0)

    val dailyIntent = resolveIntent(phase, agent.jobId != null)
    if (dailyIntent != agent.currentIntent) {
        releaseReservationIfNeeded(agent)
        applyIntentTransition(agent, dailyIntent)
        agent.goalIntentSlotIndex = 0
        agent.goalIntentType = GoalIntentType.IDLE
    }

    if (agent.goalIntentType == GoalIntentType.IDLE) {
        planNextGoalIntent(agent, phase, agent.jobId != null, nowMs, state)
    }

    applyGoalIntent(agent, state, nowMs)
}

/**
 * Logic to decide the current navigation goal based on intent.
 */
private fun AgentSystem.applyGoalIntent(agent: AgentRuntime, state: WorldState, nowMs: Long) {
    var targetPos: Offset? = null
    val baseArrivalRadius = max(6f, Constants.TILE_SIZE * 0.25f)
    var reachedDistance = baseArrivalRadius

    val context = computePhaseContext(state.timeOfDay)

    when (agent.goalIntentType) {
        GoalIntentType.GO_HOME -> {
            val home = structureById(agent.homeId)
            targetPos = home?.let { getBuildingTarget(agent, it) }
        }
        GoalIntentType.GO_WORK -> {
            val job = structureById(agent.jobId)
            targetPos = job?.let { getBuildingTarget(agent, it) }
        }
        GoalIntentType.BUSY_AT_WORK -> {
            if (nowMs >= agent.goalIntentEndsMs) {
                completeGoal(agent, nowMs, 0)
                return
            }
            if (agent.goalPos == null) {
                targetPos = pickWorkMicroTarget(agent)
            } else {
                targetPos = agent.goalPos
            }
        }
        GoalIntentType.VISIT_TAVERN, GoalIntentType.VISIT_PLAZA, GoalIntentType.VISIT_STORE -> {
            val target = structureById(agent.goalIntentTargetId)
            targetPos = target?.let { getBuildingTarget(agent, it) }
        }
        GoalIntentType.VISIT_FRIEND -> {
            val friend = agentLookup[agent.goalIntentTargetId ?: ""]
            targetPos = friend?.let { Offset(it.x, it.y) }
            reachedDistance = baseArrivalRadius * 3f // Scaled relative tolerance for moving targets
        }
        GoalIntentType.WANDER_NEAR_HOME -> {
            if (nowMs >= agent.goalIntentEndsMs) {
                completeGoal(agent, nowMs, 0)
                return
            }
            if (agent.goalPos == null) {
                val home = structureById(agent.homeId)
                val anchor = home?.let { Offset(it.x, it.y) } ?: Offset(agent.x, agent.y)
                targetPos = pickWanderTarget(agent, anchor, 5 * Constants.TILE_SIZE)
            } else {
                targetPos = agent.goalPos
            }
        }
        else -> Unit
    }

    if (targetPos != null) {
        if (hasReached(agent, targetPos, reachedDistance)) {
            handleArrival(agent, nowMs, context)
        } else {
            requestNavigation(agent, targetPos, NavReason.APPLY_GOAL_INTENT)
        }
    } else if (agent.goalIntentType != GoalIntentType.IDLE) {
        fallback(agent)
    }
}

private fun AgentSystem.handleArrival(agent: AgentRuntime, nowMs: Long, context: PhaseContext) {
    val type = agent.goalIntentType
    val targetId = agent.goalIntentTargetId

    when (type) {
        GoalIntentType.GO_HOME -> completeGoal(agent, nowMs, 15000L)
        GoalIntentType.GO_WORK -> {
            agent.state = AgentState.AT_WORK
            completeGoal(agent, nowMs, 0)
        }
        GoalIntentType.VISIT_TAVERN -> {
            targetId?.let { id ->
                val hsKey = id.hashCode().toLong()
                tavernOccupancy[hsKey] = ((tavernOccupancy[hsKey] ?: 0) + 1).coerceAtMost(Constants.MAX_TAVERN_OCCUPANTS)
                tavernReserved[hsKey] = ((tavernReserved[hsKey] ?: 0) - 1).coerceAtLeast(0)
                agent.memory.lastTavernVisitCycle = context.cycleIndex.toInt()
            }
            completeGoal(agent, nowMs, 10000L + random.nextInt(10000))
        }
        GoalIntentType.VISIT_PLAZA -> {
            targetId?.let { id ->
                val hsKey = id.hashCode().toLong()
                plazaOccupancy[hsKey] = ((plazaOccupancy[hsKey] ?: 0) + 1).coerceAtMost(Constants.MAX_PLAZA_OCCUPANTS)
                plazaReserved[hsKey] = ((plazaReserved[hsKey] ?: 0) - 1).coerceAtLeast(0)
            }
            completeGoal(agent, nowMs, 10000L + random.nextInt(10000))
        }
        GoalIntentType.VISIT_STORE -> completeGoal(agent, nowMs, 8000L)
        GoalIntentType.VISIT_FRIEND -> completeGoal(agent, nowMs, 5000L)
        GoalIntentType.BUSY_AT_WORK, GoalIntentType.WANDER_NEAR_HOME -> {
            requestNavigation(agent, null, NavReason.DURATION_ARRIVAL)
            agent.dwellTimerMs = 2000L + random.nextInt(3000)
        }
        else -> completeGoal(agent, nowMs, 0)
    }
}

private fun AgentSystem.planNextGoalIntent(agent: AgentRuntime, phase: DayPhase, employed: Boolean, nowMs: Long, state: WorldState) {
    if (phase == DayPhase.NIGHT) {
        agent.goalIntentType = GoalIntentType.GO_HOME
        return
    }

    val slots = if (employed) {
        when (phase) {
            DayPhase.MORNING, DayPhase.AFTERNOON -> listOf(GoalIntentType.GO_WORK, GoalIntentType.BUSY_AT_WORK, GoalIntentType.VISIT_FRIEND, GoalIntentType.VISIT_PLAZA, GoalIntentType.GO_WORK)
            DayPhase.EVENING -> listOf(GoalIntentType.VISIT_TAVERN, GoalIntentType.VISIT_FRIEND, GoalIntentType.VISIT_PLAZA)
            else -> listOf(GoalIntentType.GO_HOME)
        }
    } else {
        when (phase) {
            DayPhase.MORNING, DayPhase.AFTERNOON -> listOf(GoalIntentType.WANDER_NEAR_HOME, GoalIntentType.VISIT_FRIEND, GoalIntentType.VISIT_STORE, GoalIntentType.VISIT_PLAZA)
            DayPhase.EVENING -> listOf(GoalIntentType.WANDER_NEAR_HOME, GoalIntentType.VISIT_FRIEND, GoalIntentType.VISIT_TAVERN, GoalIntentType.VISIT_PLAZA)
            else -> listOf(GoalIntentType.GO_HOME)
        }
    }

    val size = slots.size
    if (size == 0) {
        agent.goalIntentType = GoalIntentType.IDLE
        return
    }

    val idx = ((agent.goalIntentSlotIndex % size) + size) % size
    val nextType = slots[idx]

    when (nextType) {
        GoalIntentType.VISIT_TAVERN -> {
            val tavern = findBestHotspot(agent, state, StructureType.TAVERN, tavernOccupancy, tavernReserved, Constants.MAX_TAVERN_OCCUPANTS, 0.7f)
            if (tavern != null) { startVisit(agent, tavern, nextType) }
            else { fallback(agent) }
        }
        GoalIntentType.VISIT_PLAZA -> {
            val plaza = findBestHotspot(agent, state, StructureType.PLAZA, plazaOccupancy, plazaReserved, Constants.MAX_PLAZA_OCCUPANTS, 0.7f)
            if (plaza != null) { startVisit(agent, plaza, nextType) }
            else { fallback(agent) }
        }
        GoalIntentType.VISIT_STORE -> {
            val store = state.structures.filter { it.type == StructureType.STORE }.randomOrNull(random)
            if (store != null) { startVisit(agent, store, nextType) }
            else { fallback(agent) }
        }
        GoalIntentType.VISIT_FRIEND -> {
            val friend = pickTopFriend(agent, state)
            if (friend != null && nowMs > agent.goalIntentFriendCooldownUntilMs) {
                agent.goalIntentType = nextType
                agent.goalIntentTargetId = friend.id
                agent.goalIntentFriendCooldownUntilMs = nowMs + 30000L
            } else { fallback(agent) }
        }
        GoalIntentType.BUSY_AT_WORK -> {
            agent.goalIntentType = nextType
            agent.goalIntentEndsMs = nowMs + 10000L
            agent.goalIntentFailCount = 0
        }
        GoalIntentType.WANDER_NEAR_HOME -> {
            agent.goalIntentType = nextType
            agent.goalIntentEndsMs = nowMs + 10000L
            agent.goalIntentFailCount = 0
        }
        else -> {
            agent.goalIntentType = nextType
            agent.goalIntentEndsMs = 0L
            agent.goalIntentFailCount = 0
        }
    }
}

private fun AgentSystem.completeGoal(agent: AgentRuntime, nowMs: Long, dwellMs: Long) {
    releaseReservationIfNeeded(agent)
    agent.goalIntentType = GoalIntentType.IDLE
    agent.goalIntentSlotIndex++
    agent.goalIntentEndsMs = 0L
    agent.goalIntentTargetId = null
    agent.dwellTimerMs = dwellMs
    agent.goalIntentFailCount = 0
    requestNavigation(agent, null, NavReason.GOAL_COMPLETE)
}

private fun AgentSystem.fallback(agent: AgentRuntime) {
    agent.goalIntentSlotIndex++
    agent.goalIntentType = GoalIntentType.IDLE
    agent.goalIntentEndsMs = 0L
    agent.goalIntentTargetId = null
    agent.goalIntentFailCount = 0
    requestNavigation(agent, null, NavReason.FALLBACK)
}

private fun AgentSystem.startVisit(agent: AgentRuntime, target: Structure, type: GoalIntentType) {
    agent.goalIntentType = type
    agent.goalIntentTargetId = target.id
    agent.goalIntentFailCount = 0

    val hsKey = target.id.hashCode().toLong()
    if (target.type == StructureType.TAVERN) tavernReserved[hsKey] = (tavernReserved[hsKey] ?: 0) + 1
    if (target.type == StructureType.PLAZA) plazaReserved[hsKey] = (plazaReserved[hsKey] ?: 0) + 1
}

private fun AgentSystem.releaseReservationIfNeeded(agent: AgentRuntime) {
    val targetId = agent.goalIntentTargetId ?: return
    val hsKey = targetId.hashCode().toLong()

    when (agent.goalIntentType) {
        GoalIntentType.VISIT_TAVERN -> {
            tavernReserved[hsKey] = ((tavernReserved[hsKey] ?: 0) - 1).coerceAtLeast(0)
        }
        GoalIntentType.VISIT_PLAZA -> {
            plazaReserved[hsKey] = ((plazaReserved[hsKey] ?: 0) - 1).coerceAtLeast(0)
        }
        else -> Unit
    }
}

private fun AgentSystem.pickWorkMicroTarget(agent: AgentRuntime): Offset? {
    val job = structureById(agent.jobId) ?: return null
    val anchor = getBuildingTarget(null, job) ?: Offset(agent.x, agent.y)
    return pickWanderTarget(agent, anchor, 3 * Constants.TILE_SIZE)
}

private fun AgentSystem.pickWanderTarget(agent: AgentRuntime, anchor: Offset, radius: Float): Offset? {
    repeat(5) {
        val angle = random.nextFloat() * 2 * PI.toFloat()
        val r = random.nextFloat() * radius
        val tx = anchor.x + cos(angle) * r
        val ty = anchor.y + sin(angle) * r

        val gx = (tx / Constants.TILE_SIZE).toInt()
        val gy = (ty / Constants.TILE_SIZE).toInt()
        if (navGrid.isWalkable(gx, gy)) { // Use NavGrid
            return Offset(tx, ty)
        }
    }
    return null
}

private fun hasReached(agent: AgentRuntime, target: Offset, tolerance: Float): Boolean {
    val dx = agent.x - target.x
    val dy = agent.y - target.y
    return (dx * dx + dy * dy) <= tolerance * tolerance
}

// --- CATEGORY RESOLUTION ---

internal fun AgentSystem.resolveIntent(phase: DayPhase, hasJob: Boolean): DailyIntent {
    return when (phase) {
        DayPhase.NIGHT -> DailyIntent.GO_HOME
        DayPhase.MORNING, DayPhase.AFTERNOON -> if (hasJob) DailyIntent.GO_WORK else DailyIntent.WANDER
        DayPhase.EVENING -> DailyIntent.WANDER
    }
}

internal fun AgentSystem.applyIntentTransition(agent: AgentRuntime, newIntent: DailyIntent) {
    agent.currentIntent = newIntent
    requestNavigation(agent, null, NavReason.INTENT_TRANSITION)
    agent.pathTiles = emptyList() // FIX: Changed from intArrayOf()
    agent.pathIndex = 0
    agent.dwellTimerMs = 0
    agent.workAnimEndTimeMs = 0L
    agent.state = AgentState.TRAVELING
}

internal fun AgentSystem.wakeSleepers(state: WorldState) {
    for (agent in state.agents) {
        if (agent.state == AgentState.SLEEPING) {
            agent.state = AgentState.IDLE
            agent.dwellTimerMs = 0L
        }
    }
}

// --- UTILITIES ---

internal fun AgentSystem.structureById(id: String?): Structure? {
    return id?.let { worldManager.worldState.value.structures.find { s -> s.id == it } }
}

internal fun AgentSystem.getBuildingTarget(agent: AgentRuntime?, structure: Structure): Offset? {
    return Pathfinding.pickWalkableTileForStructure(
        structure,
        agent?.let { Offset(it.x, it.y) } ?: Offset(structure.x, structure.y),
        navGrid // Use NavGrid
    )
}

private fun AgentSystem.findBestHotspot(
    agent: AgentRuntime,
    state: WorldState,
    type: StructureType,
    occupancyMap: Map<Long, Int>,
    reservedMap: Map<Long, Int>,
    maxOccupancy: Int,
    bias: Float
): Structure? {
    val candidates = state.structures.filter {
        if (it.type != type) return@filter false
        val hsKey = it.id.hashCode().toLong()
        val effective = (occupancyMap[hsKey] ?: 0) + (reservedMap[hsKey] ?: 0)
        effective < maxOccupancy
    }
    return if (candidates.isEmpty()) null else selectbyDistance(agent, candidates, bias)
}

private fun AgentSystem.selectbyDistance(agent: AgentRuntime, list: List<Structure>, biasToNearest: Float): Structure {
    if (list.size <= 1) return list.first()
    return if (random.nextFloat() < biasToNearest) {
        list.minBy { (it.x - agent.x).pow(2) + (it.y - agent.y).pow(2) }
    } else {
        list.random(random)
    }
}
