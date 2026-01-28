package com.example.utopia.domain

import com.example.utopia.data.models.AgentRuntime

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
    fun calculatePressures(agent: AgentRuntime): Map<String, Float> {
        val pressures = mutableMapOf<String, Float>()
        val needs = agent.needs

        // --- Homeostatic Pressures (Desire to return to a baseline) ---

        // Pressure to sleep is highest when need is lowest.
        pressures["seek_sleep"] = 1.0f - (needs.sleep / 100f)

        // Pressure for social activity is highest when need is lowest.
        pressures["seek_social"] = 1.0f - (needs.social / 100f)

        // Pressure for fun is highest when need is lowest.
        pressures["seek_fun"] = 1.0f - (needs.`fun` / 100f)
        
        // Pressure for stability (e.g., go home) is highest when need is lowest.
        pressures["seek_stability"] = 1.0f - (needs.stability / 100f)

        // --- Destabilizing Pressures (Desire for novelty) ---

        // Pressure for stimulation grows as the need increases.
        pressures["seek_stimulation"] = needs.stimulation / 100f

        return pressures.filter { it.value > 0.05f } // Ignore trivial pressures
    }

    /**
     * Selects a single intent for the agent based on the highest current pressure.
     * This is the "winner-take-all" model.
     *
     * @param agent The agent to evaluate.
     * @return The name of the selected intent (e.g., "seek_sleep").
     */
    fun selectIntent(agent: AgentRuntime): String {
        val pressures = agent.transientPressures
        if (pressures.isEmpty()) {
            return "Idle"
        }

        // Find the pressure with the highest value
        val strongestPressure = pressures.maxByOrNull { it.value }

        return strongestPressure?.key ?: "Wandering"
    }
}
