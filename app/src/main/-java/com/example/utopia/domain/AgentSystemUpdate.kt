package com.example.utopia.domain

import com.example.utopia.data.models.AgentRuntime
import com.example.utopia.data.models.AgentState
import com.example.utopia.data.models.DayPhase
import com.example.utopia.data.models.WorldState
import com.example.utopia.util.Constants

internal fun AgentSystem.updateInternal(deltaTimeMs: Long) {
    val state = worldManager.worldState.value
    val context = computePhaseContext(state.timeOfDay)
    val nowMs = System.currentTimeMillis()

    handlePhaseChange(context.phase, state, nowMs)
    val runAiTick = advanceAiTick(deltaTimeMs)

    interactedThisTick.clear()

    val agents = state.agents
    ensureAgentIndexMapping(agents)
    updateSpatialHash(agents)

    if (runAiTick) {
        updateHotspotData(state)
    }

    if (context.phase == DayPhase.MORNING && lastMorningDriftCycle != context.cycleIndex) {
        wakeSleepers(state, nowMs)
        resetCycleTelemetry()
        lastMorningDriftCycle = context.cycleIndex
    }

    for (agent in agents) {
        updateAgentTick(
            agent = agent,
            state = state,
            nowMs = nowMs,
            context = context,
            runAiTick = runAiTick,
            deltaTimeMs = deltaTimeMs
        )
    }

    if (runAiTick && nowMs - lastDebugLogMs > 1000) {
        logDebugStats(context.phase)
        lastDebugLogMs = nowMs
    }
}

private fun AgentSystem.handlePhaseChange(phase: DayPhase, state: WorldState, nowMs: Long) {
    if (phase == lastPhase) return
    lastPhase = phase
}

private fun AgentSystem.advanceAiTick(deltaTimeMs: Long): Boolean {
    aiTickTimer += deltaTimeMs
    val runAiTick = aiTickTimer >= Constants.AI_TICK_MS
    if (runAiTick) aiTimer = 0L
    pathfindCountThisFrame = 0
    return runAiTick
}

private fun AgentSystem.updateAgentTick(
    agent: AgentRuntime,
    state: WorldState,
    nowMs: Long,
    context: PhaseContext,
    runAiTick: Boolean,
    deltaTimeMs: Long
) {
    updateAgentAnimation(agent, deltaTimeMs)
    clearExpiredEmoji(agent, nowMs)

    if (runAiTick) {
        // GOALS OWNERSHIP: updateGoal is the single source of truth for planning
        updateGoal(agent, state, nowMs, context.phase, context.cycleIndex.toInt())

        val isSocialPaused = agent.state == AgentState.SOCIALIZING
        if (!isSocialPaused && agent.state != AgentState.SLEEPING) {
            handleSocialTrigger(agent, interactedThisTick, nowMs, context.normalizedTime, context.phase)
        }
    }

    // STATE-DRIVEN ACTION
    when (agent.state) {
        AgentState.TRAVELING -> {
            updateTraveling(agent, state, deltaTimeMs, nowMs, context)
        }
        AgentState.SOCIALIZING -> {
            updateSocializing(agent, nowMs, context.phase)
        }
        // IDLE, AT_WORK, SLEEPING etc. don't have per-frame updates.
        else -> {
            // No specific per-frame action needed for these states.
            // They are primarily managed by the AI tick logic in updateGoal.
        }
    }

    // Dwell timer is independent of state
    if (agent.dwellTimerMs > 0) {
        agent.dwellTimerMs = (agent.dwellTimerMs - deltaTimeMs).coerceAtLeast(0L)
    }
}

private fun AgentSystem.clearExpiredEmoji(agent: AgentRuntime, nowMs: Long) {
    if (agent.state != AgentState.SOCIALIZING && agent.emoji != null && nowMs >= agent.socialEmojiUntilMs) {
        agent.emoji = null
    }
}
