package com.example.utopia.domain

import android.util.Log
import com.example.utopia.data.models.AgentRuntime
import com.example.utopia.data.models.AgentState
import com.example.utopia.data.models.DayPhase
import com.example.utopia.data.models.Structure
import com.example.utopia.data.models.VisitBehavior
import com.example.utopia.data.models.WorldState
import com.example.utopia.util.Constants

internal fun AgentSystem.shouldBeMoving(agent: AgentRuntime): Boolean {
    return agent.goalPos != null
}

internal fun AgentSystem.updateAgentAnimation(agent: AgentRuntime, deltaTimeMs: Long) {
    val isStationary = agent.dwellTimerMs > 0 || !shouldBeMoving(agent)
    val speed = if (isStationary) 0f else 1.0f * Constants.OFF_ROAD_SPEED_MULT
    agent.animationSpeed = speed

    if (speed <= 0f) {
        agent.animTimerMs = 0
        agent.animFrame = 0
        return
    }

    val frameDurationMs = 150L
    agent.animTimerMs += (deltaTimeMs * speed).toLong()
    while (agent.animTimerMs >= frameDurationMs) {
        agent.animTimerMs -= frameDurationMs
        agent.animFrame = (agent.animFrame + 1) % 4
    }
}