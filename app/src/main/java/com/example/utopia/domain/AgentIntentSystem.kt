package com.example.utopia.domain

import com.example.utopia.data.models.AgentIntent
import com.example.utopia.data.models.AgentRuntime
import com.example.utopia.util.Constants

/**
 * The beginning of the Agent's "Brain".
 *
 * This system is responsible for translating an agent's internal state (primarily needs)
 * into a set of "pressures" that will drive behavior.
 *
 * As per the manifest: "Needs create pressure fields, not tasks."
 * And for Intent: "Winner-take-all"
 */
object AgentIntentSystem {

    /**
     * Calculates the transient pressures for a given agent.
     * These pressures are ephemeral and recalculated each tick.
     *
     * @param agent The agent to evaluate.
     * @return A map of pressure names to their strength [0, 1].
     */
    fun calculatePressures(agent: AgentRuntime): Map<AgentIntent, Float> {
        val pressures = mutableMapOf<AgentIntent, Float>()
        val needs = agent.needs

        // --- Homeostatic Pressures (Desire to return to a baseline) ---

        // Pressure to sleep is only active when need is critically low (< 10%).
        pressures[AgentIntent.SeekSleep] = if (needs.sleep < 10f) 1.0f - (needs.sleep / 100f) else 0f

        // REMOVED: Social is no longer a driven intent.
        // pressures["seek_social"] = 1.0f - (needs.social / 100f)

        // Pressure for fun is highest when need is lowest.
        pressures[AgentIntent.SeekFun] = 1.0f - (needs.`fun` / 100f)
        
        // Pressure for stability (e.g., go home) is highest when need is lowest.
        pressures[AgentIntent.SeekStability] = 1.0f - (needs.stability / 100f)

        // --- Destabilizing Pressures (Desire for novelty) ---

        // Pressure for stimulation grows as the need increases.
        pressures[AgentIntent.SeekStimulation] = needs.stimulation / 100f

        // --- Momentum Bias (Fix C) ---
        // Boost the current intent slightly so agents don't flip-flop between two close needs.
        val current = agent.currentIntent
        if (current !is AgentIntent.Idle && current !is AgentIntent.Wandering) {
            pressures[current] = (pressures[current] ?: 0f) + Constants.MOMENTUM_BIAS
        }

        return pressures.filter { it.value > 0.05f } // Ignore trivial pressures
    }

    /**
     * Selects a single intent for the agent based on the highest current pressure.
     * This is the "winner-take-all" model.
     *
     * @param agent The agent to evaluate.
     * @param nowMs The current world time in milliseconds.
     * @return The name of the selected intent (e.g., "seek_sleep").
     */
    fun selectIntent(agent: AgentRuntime, nowMs: Long): AgentIntent {
        // --- Action Commitment Window (Fix A) ---
        // If we recently started an intent, stick to it unless it's gone completely.
        val timeSinceStart = nowMs - agent.intentStartTimeMs
        val isLocked = timeSinceStart < Constants.INTENT_COMMITMENT_MS
        
        val pressures = agent.transientPressures
        val currentIntent = agent.currentIntent

        if (isLocked && currentIntent !is AgentIntent.Idle && currentIntent !is AgentIntent.Wandering) {
            // Only stick to it if it still has *some* pressure (hasn't been fully satisfied)
            if ((pressures[currentIntent] ?: 0f) > 0.1f) {
                return currentIntent
            }
        }

        if (pressures.isEmpty()) {
            return AgentIntent.Idle
        }

        // Find the pressure with the highest value
        val strongestPressure = pressures.maxByOrNull { it.value }

        return strongestPressure?.key ?: AgentIntent.Wandering
    }
}
