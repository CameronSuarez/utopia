package com.example.utopia.domain

import com.example.utopia.data.models.AgentRuntime
import com.example.utopia.data.models.AgentState
import com.example.utopia.util.Constants
import kotlin.math.abs

private const val ANIM_TICK_MS = 150L

internal fun AgentSystem.updateAgentAnimation(
    agent: AgentRuntime,
    deltaTimeMs: Long
) {
    val isMoving = agent.state == AgentState.TRAVELING && (abs(agent.x - agent.lastPosX) > 0.01f || abs(agent.y - agent.lastPosY) > 0.01f)

    if (isMoving) {
        agent.animTimerMs += deltaTimeMs
        if (agent.animTimerMs >= ANIM_TICK_MS) {
            agent.animTimerMs = 0L
            agent.animFrame = (agent.animFrame + 1) % 4 // Assuming 4 frames of walk animation
        }
    } else {
        // Not moving, reset to a standing frame
        agent.animTimerMs = 0L
        agent.animFrame = 0
    }
}
