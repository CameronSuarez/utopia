package com.example.utopia.domain

import com.example.utopia.data.models.AgentRuntime
import com.example.utopia.data.models.Needs
import kotlin.math.max

/**
 * Handles the decay of agent needs over time based on the manifest's time model.
 */
object AgentNeedsSystem {

    // Manifest: 1 tick = 1 second. Day length = 420 seconds.
    private const val SECONDS_PER_DAY = 420f

    // Decay rates are defined as "points per day"
    private const val SLEEP_DECAY_PER_DAY = 100f // An agent gets fully tired in one day
    private const val SOCIAL_DECAY_PER_DAY = 60f
    private const val FUN_DECAY_PER_DAY = 50f
    private const val STABILITY_DECAY_PER_DAY = 40f
    private const val STIMULATION_GAIN_PER_DAY = 70f // Stimulation is a destabilizing need, it grows when not met

    fun updateNeeds(agent: AgentRuntime, deltaTime: Float): AgentRuntime {
        val needs = agent.needs
        val decayFactor = deltaTime / SECONDS_PER_DAY

        val updatedNeeds = needs.copy(
            sleep = max(0f, needs.sleep - SLEEP_DECAY_PER_DAY * decayFactor),
            social = max(0f, needs.social - SOCIAL_DECAY_PER_DAY * decayFactor),
            `fun` = max(0f, needs.`fun` - FUN_DECAY_PER_DAY * decayFactor),
            stability = max(0f, needs.stability - STABILITY_DECAY_PER_DAY * decayFactor),
            stimulation = max(0f, needs.stimulation + STIMULATION_GAIN_PER_DAY * decayFactor)
        )
        return agent.copy(needs = updatedNeeds)
    }
}
