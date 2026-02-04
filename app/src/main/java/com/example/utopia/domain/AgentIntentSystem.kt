package com.example.utopia.domain

import com.example.utopia.data.models.AgentIntent
import com.example.utopia.data.models.AgentRuntime
import com.example.utopia.data.models.ResourceType
import com.example.utopia.data.models.WorldState
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
    fun calculatePressures(agent: AgentRuntime, worldState: WorldState): Map<AgentIntent, Float> {
        val pressures = mutableMapOf<AgentIntent, Float>()

        // Overwhelming priority: if holding an item, store it.
        if (agent.carriedItem != null) {
            val resourceType = agent.carriedItem.type
            // 1. Find a construction site that needs this resource
            val constructionSite = worldState.structures.find {
                !it.isComplete && (it.spec.buildCost[resourceType] ?: 0) > (it.inventory[resourceType] ?: 0)
            }
            if (constructionSite != null) {
                pressures[AgentIntent.StoreResource(constructionSite.id)] = 2.0f
                return pressures // Early exit for high priority task
            }

            // 2. Find a transformer/consumer that needs this resource
            val consumer = worldState.structures.find {
                it.spec.consumes.containsKey(resourceType) &&
                        (it.inventory[resourceType] ?: 0) < (it.spec.inventoryCapacity[resourceType] ?: 0)
            }
            if (consumer != null) {
                pressures[AgentIntent.StoreResource(consumer.id)] = 2.0f
                return pressures // Early exit for high priority task
            }
        }

        val needs = agent.needs

        // --- Homeostatic Pressures (Desire to return to a baseline) ---
        pressures[AgentIntent.SeekSleep] = if (needs.sleep < 10f) 1.0f - (needs.sleep / 100f) else 0f
        pressures[AgentIntent.SeekFun] = 1.0f - (needs.`fun` / 100f)
        pressures[AgentIntent.SeekStability] = 1.0f - (needs.stability / 100f)

        // --- Destabilizing Pressures (Desire for novelty) ---
        pressures[AgentIntent.SeekStimulation] = needs.stimulation / 100f

        // --- Work Pressure ---
        if (agent.workplaceId != null) {
            pressures[AgentIntent.Work] = 0.8f // High, but not overwhelming pressure
        } else {
            // Pressure to seek work if unemployed and a job is available
            if (worldState.transient_hasAvailableWorkplace) {
                pressures[AgentIntent.Work] = 0.9f // HIGH pressure to find a job
            }
        }

        // --- Hauling Pressure (Generic) ---
        if (agent.carriedItem == null) {
            // Find a need, then find a source to satisfy it.
            // Priority: Construction > Transformation
            
            // 1. Check for construction needs
            val constructionSiteNeedingResources = worldState.structures
                .filter { !it.isComplete }
                .firstOrNull { site ->
                    site.spec.buildCost.any { (resource, amount) ->
                        (site.inventory[resource] ?: 0) < amount
                    }
                }

            if (constructionSiteNeedingResources != null) {
                for ((resource, requiredAmount) in constructionSiteNeedingResources.spec.buildCost) {
                    if ((constructionSiteNeedingResources.inventory[resource] ?: 0) < requiredAmount) {
                        val source = findSourceForResource(worldState, resource)
                        if (source != null) {
                            pressures[AgentIntent.GetResource(source.id, resource)] = 0.75f
                            // Found a hauling job for construction, can stop searching for now
                            break 
                        }
                    }
                }
            }

            // 2. If no construction hauling, check for transformation needs
            if (pressures.keys.none { it is AgentIntent.GetResource }) {
                 val consumerNeedingResources = worldState.structures.firstOrNull { s ->
                    s.spec.consumes.isNotEmpty() && s.spec.consumes.any { (resource, _) ->
                        (s.inventory[resource] ?: 0) < (s.spec.inventoryCapacity[resource] ?: 0)
                    }
                }

                if (consumerNeedingResources != null) {
                    for ((resource, _) in consumerNeedingResources.spec.consumes) {
                        if ((consumerNeedingResources.inventory[resource] ?: 0) < (consumerNeedingResources.spec.inventoryCapacity[resource] ?: 0)) {
                             val source = findSourceForResource(worldState, resource)
                             if (source != null) {
                                 pressures[AgentIntent.GetResource(source.id, resource)] = 0.7f
                                 break
                             }
                        }
                    }
                }
            }
        }
        
        // --- Construction Pressure ---
        // An agent should only go to construct if the site has the resources it needs.
        val readyConstructionSite = worldState.structures.find { 
            !it.isComplete && it.spec.buildCost.all { (res, amount) -> (it.inventory[res] ?: 0) >= amount } 
        }
        if (readyConstructionSite != null) {
            pressures[AgentIntent.Construct(readyConstructionSite.id)] = 0.85f
        }

        // --- Momentum Bias (Fix C) ---
        val current = agent.currentIntent
        if (current !is AgentIntent.Idle && current !is AgentIntent.Wandering) {
            pressures[current] = (pressures[current] ?: 0f) + Constants.MOMENTUM_BIAS
        }

        return pressures.filter { it.value > 0.05f } // Ignore trivial pressures
    }

    private fun findSourceForResource(worldState: WorldState, resource: ResourceType) =
        worldState.structures.find {
            (it.inventory[resource] ?: 0) > 0 && it.spec.produces.containsKey(resource)
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
        val timeSinceStart = nowMs - agent.intentStartTimeMs
        val isLocked = timeSinceStart < Constants.INTENT_COMMITMENT_MS
        
        val pressures = agent.transientPressures
        val currentIntent = agent.currentIntent

        if (isLocked && currentIntent !is AgentIntent.Idle && currentIntent !is AgentIntent.Wandering) {
            if ((pressures[currentIntent] ?: 0f) > 0.1f) {
                return currentIntent
            }
        }

        if (pressures.isEmpty()) {
            return AgentIntent.Idle
        }

        val strongestPressure = pressures.maxByOrNull { it.value }

        return strongestPressure?.key ?: AgentIntent.Wandering
    }
}
