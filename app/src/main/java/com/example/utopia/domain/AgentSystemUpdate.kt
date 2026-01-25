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
        wakeSleepers(state)
        resetCycleTelemetry()
        lastMorningDriftCycle = context.cycleIndex
    }

    for (agent in agents) {
        updateAgentTick(
            agent = agent,
            state = state,
            nowMs = nowMs,
            phase = context.phase,
            normalizedTime = context.normalizedTime,
            cycleIndex = context.cycleIndex,
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
    if (runAiTick) aiTickTimer = 0L
    pathfindCountThisFrame = 0
    return runAiTick
}

private fun AgentSystem.updateAgentTick(
    agent: AgentRuntime,
    state: WorldState,
    nowMs: Long,
    phase: DayPhase,
    normalizedTime: Float,
    cycleIndex: Long,
    runAiTick: Boolean,
    deltaTimeMs: Long
) {
    updateAgentAnimation(agent, deltaTimeMs)

    decayMoodBiasIfNeeded(agent, phase, cycleIndex)
    clearExpiredEmoji(agent, nowMs)

    val isSocialPaused = updateSocialIfNeeded(agent, nowMs, phase)

    if (runAiTick) {
        // GOALS OWNERSHIP: updateGoal is the single source of truth for planning
        updateGoal(agent, state, nowMs, phase, cycleIndex.toInt())

        if (!isSocialPaused && agent.state != AgentState.SLEEPING) {
            handleSocialTrigger(agent, interactedThisTick, nowMs, normalizedTime, phase)
        }
    }

    if (agent.state != AgentState.SOCIALIZING && agent.goalPos != null) {
        SimpleNav.updateAgent(this, agent, state, deltaTimeMs, nowMs)
    }
}

private fun AgentSystem.decayMoodBiasIfNeeded(agent: AgentRuntime, phase: DayPhase, cycleIndex: Long) {
    if (phase != DayPhase.MORNING) return
    if (agent.memory.lastDecayDay == cycleIndex.toInt()) return

    if (agent.memory.recentMoodBias > 0) agent.memory.recentMoodBias--
    else if (agent.memory.recentMoodBias < 0) agent.memory.recentMoodBias++
    agent.memory.lastDecayDay = cycleIndex.toInt()
}

private fun AgentSystem.clearExpiredEmoji(agent: AgentRuntime, nowMs: Long) {
    if (agent.state != AgentState.SOCIALIZING && agent.emoji != null && nowMs >= agent.socialEmojiUntilMs) {
        agent.emoji = null
    }
}

private fun AgentSystem.updateSocialIfNeeded(agent: AgentRuntime, nowMs: Long, phase: DayPhase): Boolean {
    if (agent.state != AgentState.SOCIALIZING) return false
    updateSocializing(agent, nowMs, phase)
    return agent.state == AgentState.SOCIALIZING
}
