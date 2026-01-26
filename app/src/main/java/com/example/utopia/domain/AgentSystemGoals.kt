package com.example.utopia.domain

import android.util.Log
import androidx.compose.ui.geometry.Offset
import com.example.utopia.data.models.*
import com.example.utopia.util.Constants
import kotlin.math.*
import kotlin.random.Random

private const val WORK_SHIFT_DURATION_MS = 60000L // 1 minute for a full work shift
private const val EXCURSION_CHANCE_PER_TICK = 0.05f // 5% chance each AI tick to consider an excursion
private const val JOB_SEARCH_RETRY_MS = 20000L // 20 seconds between job search attempts

// --- PRIMARY GOAL PLANNING ---

/**
 * Main entry point for Goal logic.
 * Decides if an agent needs a new high-level intent or a local micro-action.
 */
internal fun AgentSystem.updateGoal(agent: AgentRuntime, state: WorldState, nowMs: Long, phase: DayPhase, cycleIndex: Int) {
    // 1. Check if the primary work shift has ended (must run even while traveling/socializing)
    if (agent.primaryGoal == PrimaryGoal.WORK_SHIFT && nowMs >= agent.primaryGoalEndsMs) {
        agent.primaryGoal = PrimaryGoal.OFF_DUTY
        // Shift end is a hard override, not a task completion.
        // Prevents side effects like clearing returnIntent or reservation cleanup.
        agent.returnIntent = null
        agent.goalIntentType = GoalIntentType.IDLE
        agent.goalIntentEndsMs = 0L
        agent.goalIntentTargetId = null
    }

    // 2. Check if non-navigational intents have expired. (must run even while traveling/socializing)
    checkIntentExpiration(agent, nowMs)

    if (agent.state == AgentState.SOCIALIZING || agent.state == AgentState.SLEEPING || agent.state == AgentState.TRAVELING) return

    // goalBlockedUntilMs is used for both excursion cooldowns and job search cooldowns
    if (agent.dwellTimerMs > 0 || nowMs < agent.goalBlockedUntilMs) return

    // 2.5. Periodic job search for unemployed agents during work hours (MORNING/AFTERNOON)
    // This allows re-entry to job market even if the agent is on a non-critical intent like WANDER_NEAR_HOME.
    if (agent.jobId == null && agent.primaryGoal == PrimaryGoal.OFF_DUTY) {
        if (phase == DayPhase.MORNING || phase == DayPhase.AFTERNOON) {
            // findAndAssignJob handles the cooldown setting (agent.goalBlockedUntilMs) upon failure.
            if (findAndAssignJob(agent, state, nowMs)) {
                return // Job found, new GO_WORK intent started. Exit.
            }
        }
    }

    // 3. Consider taking an excursion if at work
    if (agent.primaryGoal == PrimaryGoal.WORK_SHIFT && agent.goalIntentType == GoalIntentType.BUSY_AT_WORK) {
        if (random.nextFloat() < EXCURSION_CHANCE_PER_TICK) {
            suspendWorkAndPlanExcursion(agent, state, nowMs)
        }
    }

    if (agent.goalIntentType == GoalIntentType.IDLE) {
        planNextGoalIntent(agent, phase, nowMs, state)
    }

    applyGoalIntent(agent, state, nowMs)
}

private fun GoalIntentType.isExcursion(): Boolean {
    return this == GoalIntentType.VISIT_TAVERN ||
           this == GoalIntentType.VISIT_PLAZA ||
           this == GoalIntentType.VISIT_STORE ||
           this == GoalIntentType.VISIT_FRIEND
}

private fun AgentSystem.checkIntentExpiration(agent: AgentRuntime, nowMs: Long) {
    val type = agent.goalIntentType
    if (type.isExcursion() || type == GoalIntentType.WANDER_NEAR_HOME) {
        if (nowMs >= agent.goalIntentEndsMs && agent.goalIntentEndsMs != 0L) {
            completeGoal(agent, nowMs, 0)
        }
    }
}

/**
 * Logic to decide the current navigation goal based on intent.
 */
internal fun AgentSystem.applyGoalIntent(agent: AgentRuntime, state: WorldState, nowMs: Long) {
    if (agent.state != AgentState.IDLE) {
        return
    }

    var targetPos: Offset? = null

    when (agent.goalIntentType) {
        GoalIntentType.GO_HOME -> {
            val home = structureById(agent.homeId)
            targetPos = home?.let { getBuildingTarget(null, it) }
        }
        GoalIntentType.GO_WORK -> {
            // New logic: Check if job is still valid before navigating
            val job = structureById(agent.jobId)
            if (job == null) {
                // Cannot work today / job destroyed. Fall back to OFF_DUTY.
                agent.primaryGoal = PrimaryGoal.OFF_DUTY
                fallback(agent) // fallback sets goalIntentType to IDLE, which triggers planNextGoalIntent
                return
            }
            targetPos = job.let { getBuildingTarget(null, it) }
        }
        GoalIntentType.BUSY_AT_WORK -> {
            // BUSY_AT_WORK no longer has its own timer; it's managed by the primary goal.
            // It just generates micro-movements.
            if (agent.goalPos == null) {
                targetPos = pickWorkMicroTarget(agent)
            } else {
                targetPos = agent.goalPos
            }
        }
        GoalIntentType.VISIT_TAVERN, GoalIntentType.VISIT_PLAZA, GoalIntentType.VISIT_STORE -> {
            val target = structureById(agent.goalIntentTargetId)
            targetPos = target?.let { getBuildingTarget(null, it) }
        }
        GoalIntentType.VISIT_FRIEND -> {
            val friend = agentLookup[agent.goalIntentTargetId ?: ""]
            targetPos = friend?.let { Offset(it.x, it.y) }
        }
        GoalIntentType.WANDER_NEAR_HOME -> {
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
        requestNavigation(agent, targetPos, NavReason.APPLY_GOAL_INTENT)
    } else if (agent.goalIntentType != GoalIntentType.IDLE) {
        fallback(agent)
    }
}

internal fun AgentSystem.handleArrival(agent: AgentRuntime, nowMs: Long, context: PhaseContext) {
    val type = agent.goalIntentType
    val targetId = agent.goalIntentTargetId

    when (type) {
        GoalIntentType.GO_HOME -> {
            agent.state = AgentState.SLEEPING
            completeGoal(agent, nowMs, 0L)
        }
        GoalIntentType.GO_WORK -> {
            agent.state = AgentState.IDLE
            agent.goalIntentType = GoalIntentType.BUSY_AT_WORK
            agent.dwellTimerMs = 1000L // Dwell to prevent immediate re-planning
        }
        GoalIntentType.VISIT_TAVERN -> {
            targetId?.let { id ->
                val hsKey = id.hashCode().toLong()
                tavernOccupancy[hsKey] = ((tavernOccupancy[hsKey] ?: 0) + 1).coerceAtMost(Constants.MAX_TAVERN_OCCUPANTS)
                tavernReserved[hsKey] = ((tavernReserved[hsKey] ?: 0) - 1).coerceAtLeast(0)
                agent.memory.lastTavernVisitCycle = context.cycleIndex.toInt()
            }
            agent.state = AgentState.EXCURSION_VISITING
            agent.goalIntentEndsMs = nowMs + 10000L + random.nextInt(10000)
        }
        GoalIntentType.VISIT_PLAZA -> {
            targetId?.let { id ->
                val hsKey = id.hashCode().toLong()
                plazaOccupancy[hsKey] = ((plazaOccupancy[hsKey] ?: 0) + 1).coerceAtMost(Constants.MAX_PLAZA_OCCUPANTS)
                plazaReserved[hsKey] = ((plazaReserved[hsKey] ?: 0) - 1).coerceAtLeast(0)
            }
            agent.state = AgentState.EXCURSION_VISITING
            agent.goalIntentEndsMs = nowMs + 10000L + random.nextInt(10000)
        }
        GoalIntentType.VISIT_STORE -> {
            agent.state = AgentState.EXCURSION_VISITING
            agent.goalIntentEndsMs = nowMs + 8000L
        }
        GoalIntentType.VISIT_FRIEND -> {
            agent.state = AgentState.EXCURSION_VISITING
            agent.goalIntentEndsMs = nowMs + 5000L
        }
        GoalIntentType.BUSY_AT_WORK, GoalIntentType.WANDER_NEAR_HOME -> {
            requestNavigation(agent, null, NavReason.DURATION_ARRIVAL)
            agent.dwellTimerMs = 2000L + random.nextInt(3000)
        }
        else -> completeGoal(agent, nowMs, 0)
    }
}

private fun AgentSystem.planNextGoalIntent(agent: AgentRuntime, phase: DayPhase, nowMs: Long, state: WorldState) {

    // 1. Night-time behavior (Highest priority)
    if (phase == DayPhase.NIGHT) {
        agent.goalIntentType = GoalIntentType.GO_HOME
        return
    }

    // 2. Employed Agent Check (Daytime Only)
    if (agent.jobId != null) {
        if (agent.primaryGoal == PrimaryGoal.WORK_SHIFT) {
            // Agent is correctly on shift, continue micro-movements (FIX for perpetual GO_WORK)
            agent.goalIntentType = GoalIntentType.BUSY_AT_WORK
        } else {
            // Agent is employed but OFF_DUTY (missed wakeSleepers, or just returned from a long journey).
            // Re-enter shift deterministically, assuming the shift hasn't expired.
            agent.primaryGoal = PrimaryGoal.WORK_SHIFT
            agent.primaryGoalEndsMs = nowMs + WORK_SHIFT_DURATION_MS
            agent.goalIntentType = GoalIntentType.GO_WORK
        }
        return
    }

    // 3. Unemployed/Off-Duty Behavior (Daytime Only)
    if (agent.primaryGoal == PrimaryGoal.OFF_DUTY) {
        
        // 3.1. PRIMARY: Job seeking is now handled by a periodic check in updateGoal.
        
        // 3.2. SECONDARY: Attempt Leisure (Excursion)
        if (planExcursion(agent, state, nowMs)) {
            return // Successfully planned an excursion
        }

        // 3.3. FALLBACK: Wander near home (sink)
        agent.goalIntentType = GoalIntentType.WANDER_NEAR_HOME
        return
    }
}

private fun AgentSystem.suspendWorkAndPlanExcursion(agent: AgentRuntime, state: WorldState, nowMs: Long) {
    agent.returnIntent = GoalIntentType.BUSY_AT_WORK
    planExcursion(agent, state, nowMs)
}

private fun AgentSystem.planExcursion(agent: AgentRuntime, state: WorldState, nowMs: Long): Boolean {
    // For now, picks a random excursion. Can be made more sophisticated later.
    val possibleExcursions = listOf(GoalIntentType.VISIT_PLAZA, GoalIntentType.VISIT_TAVERN, GoalIntentType.VISIT_FRIEND)
    val nextType = possibleExcursions.random(random)

    when (nextType) {
        GoalIntentType.VISIT_TAVERN -> {
            val tavern = findBestHotspot(agent, state, StructureType.TAVERN, tavernOccupancy, tavernReserved, Constants.MAX_TAVERN_OCCUPANTS, 0.7f)
            if (tavern != null) { startVisit(agent, tavern, nextType, nowMs); return true }
        }
        GoalIntentType.VISIT_PLAZA -> {
            val plaza = findBestHotspot(agent, state, StructureType.PLAZA, plazaOccupancy, plazaReserved, Constants.MAX_PLAZA_OCCUPANTS, 0.7f)
            if (plaza != null) { startVisit(agent, plaza, nextType, nowMs); return true }
        }
        GoalIntentType.VISIT_FRIEND -> {
            val friend = pickTopFriend(agent, state)
            if (friend != null) {
                agent.goalIntentType = nextType
                agent.goalIntentTargetId = friend.id
                startVisit(agent, null, nextType, nowMs) // Call startVisit for cooldown
                return true
            }
        }
        else -> Unit
    }
    return false
}

/**
 * Attempts to find an available job and immediately assigns it and starts the WORK_SHIFT.
 * If no job is found, sets a cooldown on the agent.
 */
private fun AgentSystem.findAndAssignJob(agent: AgentRuntime, state: WorldState, nowMs: Long): Boolean {
    val workplaces = state.structures.filter { it.type.jobSlots > 0 }
    val currentOccupancy = state.agents.groupingBy { it.jobId }.eachCount()

    // Find the nearest workplace with an open slot
    val nearestWorkplace = workplaces
        .filter { (currentOccupancy[it.id] ?: 0) < it.type.jobSlots }
        .minByOrNull {
            val dx = it.x - agent.x
            val dy = it.y - agent.y
            dx * dx + dy * dy
        }

    if (nearestWorkplace != null) {
        // Assign
        agent.jobId = nearestWorkplace.id
        // Transition to work shift
        agent.primaryGoal = PrimaryGoal.WORK_SHIFT
        agent.primaryGoalEndsMs = nowMs + WORK_SHIFT_DURATION_MS
        agent.goalIntentType = GoalIntentType.GO_WORK
        return true
    } else {
        // Set cooldown on failure to prevent re-querying every tick
        agent.goalBlockedUntilMs = nowMs + JOB_SEARCH_RETRY_MS
        return false
    }
}

private fun AgentSystem.completeGoal(agent: AgentRuntime, nowMs: Long, dwellMs: Long) {
    if (agent.state == AgentState.TRAVELING) {
        agent.state = AgentState.IDLE
    }

    releaseReservationIfNeeded(agent)

    // Check if returning from an excursion
    val returnIntent = agent.returnIntent
    if (returnIntent != null) {
        agent.returnIntent = null
        agent.goalIntentType = GoalIntentType.GO_WORK // Go back to work
    } else {
        // Otherwise, standard completion
        agent.goalIntentType = GoalIntentType.IDLE
    }

    agent.goalIntentEndsMs = 0L
    agent.goalIntentTargetId = null
    agent.dwellTimerMs = dwellMs
    agent.goalIntentFailCount = 0
}

private fun AgentSystem.fallback(agent: AgentRuntime) {
    agent.goalIntentType = GoalIntentType.IDLE
    agent.dwellTimerMs = 500L + random.nextInt(500)
    requestNavigation(agent, null, NavReason.FALLBACK)
}

private fun AgentSystem.startVisit(agent: AgentRuntime, target: Structure?, type: GoalIntentType, nowMs: Long) {
    agent.goalIntentType = type
    agent.goalIntentTargetId = target?.id
    agent.goalBlockedUntilMs = nowMs + 20000L // Add cooldown for all excursions/visits

    val hsKey = target?.id?.hashCode()?.toLong() ?: return
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
    repeat(10) {
        val angle = random.nextFloat() * 2 * PI.toFloat()
        val r = random.nextFloat() * radius
        val tx = anchor.x + cos(angle) * r
        val ty = anchor.y + sin(angle) * r
        val gx = (tx / Constants.TILE_SIZE).toInt()
        val gy = (ty / Constants.TILE_SIZE).toInt()
        if (navGrid.isWalkable(gx, gy)) return Offset(tx, ty)
    }
    return null
}

internal fun AgentSystem.wakeSleepers(state: WorldState, nowMs: Long) {
    for (agent in state.agents) {
        if (agent.state == AgentState.SLEEPING) {
            agent.state = AgentState.IDLE
            agent.dwellTimerMs = 0L

            // START OF DAY LOGIC
            // 1. Check for existing job OR attempt a guaranteed morning job search
            if (agent.jobId == null && findAndAssignJob(agent, state, nowMs)) {
                // Job found! Continue as employed. findAndAssignJob handles the full shift setup.
            }
            
            if (agent.jobId != null) {
                // If job was found in morning search, it's already set up, just ensure the GO_WORK intent
                if (agent.primaryGoal != PrimaryGoal.WORK_SHIFT) {
                    agent.primaryGoal = PrimaryGoal.WORK_SHIFT
                    agent.primaryGoalEndsMs = nowMs + WORK_SHIFT_DURATION_MS
                }
                agent.goalIntentType = GoalIntentType.GO_WORK
            } else {
                // Unemployed: OFF_DUTY for the day
                agent.primaryGoal = PrimaryGoal.OFF_DUTY
                agent.primaryGoalEndsMs = 0L
                agent.goalIntentType = GoalIntentType.IDLE
            }
        }
    }
}

// --- UTILITIES ---
internal fun AgentSystem.structureById(id: String?): Structure? {
    return id?.let { worldManager.worldState.value.structures.find { s -> s.id == it } }
}
private fun AgentSystem.getBuildingTarget(agent: AgentRuntime?, structure: Structure): Offset? {
    return Pathfinding.pickWalkableTileForStructure(structure, agent?.let { Offset(it.x, it.y) } ?: Offset(structure.x, structure.y), navGrid)
}
private fun AgentSystem.findBestHotspot(agent: AgentRuntime, state: WorldState, type: StructureType, occupancyMap: Map<Long, Int>, reservedMap: Map<Long, Int>, maxOccupancy: Int, bias: Float): Structure? {
    val candidates = state.structures.filter { it.type == type && (occupancyMap[it.id.hashCode().toLong()] ?: 0) + (reservedMap[it.id.hashCode().toLong()] ?: 0) < maxOccupancy }
    return if (candidates.isEmpty()) null else selectbyDistance(agent, candidates, bias)
}
private fun AgentSystem.selectbyDistance(agent: AgentRuntime, list: List<Structure>, biasToNearest: Float): Structure {
    if (list.size <= 1) return list.first()
    return if (random.nextFloat() < biasToNearest) list.minBy { (it.x - agent.x).pow(2) + (it.y - agent.y).pow(2) }
    else list.random(random)
}