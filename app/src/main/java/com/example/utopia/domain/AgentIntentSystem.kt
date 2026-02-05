package com.example.utopia.domain

import com.example.utopia.data.models.AgentIntent
import com.example.utopia.data.models.AgentRuntime
import com.example.utopia.data.models.ResourceType
import com.example.utopia.data.models.Structure
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
            val constructionSite = findConstructionSiteNeedingResource(worldState, resourceType)
            if (constructionSite != null) {
                pressures[AgentIntent.StoreResource(constructionSite.id)] = 1.0f
                return pressures // Early exit for high priority task
            }

            // 2. Find a transformer/consumer that needs this resource
            val consumer = findConsumerNeedingResource(worldState, resourceType)
            if (consumer != null) {
                pressures[AgentIntent.StoreResource(consumer.id)] = 1.0f
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
            val workplace = worldState.structures.find { it.id == agent.workplaceId }
            if (workplace != null && isWorkPossible(workplace)) {
                pressures[AgentIntent.Work] = 0.8f // High, but not overwhelming pressure
            }
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
            val constructionSiteNeedingResources = findAnyConstructionSiteNeedingResources(worldState)

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
                val consumerNeedingResources = findAnyConsumerNeedingResources(worldState)

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
        val readyConstructionSite = findReadyConstructionSite(worldState)
        if (readyConstructionSite != null) {
            pressures[AgentIntent.Construct(readyConstructionSite.id)] = 0.85f
        }

        return pressures.filter { it.value > 0.05f } // Ignore trivial pressures
    }

    private fun findSourceForResource(worldState: WorldState, resource: ResourceType): Structure? {
        val index = worldState.poiIndex
        if (index.structureRevision == worldState.structureRevision) {
            return index.sourcesByResource[resource]?.firstOrNull()
        }
        return worldState.structures.find {
            (it.inventory[resource] ?: 0) > 0 && it.spec.produces.containsKey(resource)
        }
    }

    private fun findConstructionSiteNeedingResource(worldState: WorldState, resource: ResourceType): Structure? {
        val index = worldState.poiIndex
        if (index.structureRevision == worldState.structureRevision) {
            return index.constructionSitesNeeding[resource]?.firstOrNull()
        }
        return worldState.structures.find {
            !it.isComplete && (it.spec.buildCost[resource] ?: 0) > (it.inventory[resource] ?: 0)
        }
    }

    private fun findAnyConstructionSiteNeedingResources(worldState: WorldState): Structure? {
        val index = worldState.poiIndex
        if (index.structureRevision == worldState.structureRevision) {
            return index.constructionSitesNeeding.values.firstOrNull { it.isNotEmpty() }?.firstOrNull()
        }
        return worldState.structures
            .filter { !it.isComplete }
            .firstOrNull { site ->
                site.spec.buildCost.any { (resource, amount) ->
                    (site.inventory[resource] ?: 0) < amount
                }
            }
    }

    private fun findConsumerNeedingResource(worldState: WorldState, resource: ResourceType): Structure? {
        val index = worldState.poiIndex
        if (index.structureRevision == worldState.structureRevision) {
            return index.sinksByResource[resource]?.firstOrNull()
        }
        return worldState.structures.find {
            it.spec.consumes.containsKey(resource) &&
                    (it.inventory[resource] ?: 0) < (it.spec.inventoryCapacity[resource] ?: 0)
        }
    }

    private fun findAnyConsumerNeedingResources(worldState: WorldState): Structure? {
        val index = worldState.poiIndex
        if (index.structureRevision == worldState.structureRevision) {
            return index.sinksByResource.values.firstOrNull { it.isNotEmpty() }?.firstOrNull()
        }
        return worldState.structures.firstOrNull { s ->
            s.spec.consumes.isNotEmpty() && s.spec.consumes.any { (resource, _) ->
                (s.inventory[resource] ?: 0) < (s.spec.inventoryCapacity[resource] ?: 0)
            }
        }
    }

    private fun findReadyConstructionSite(worldState: WorldState): Structure? {
        val index = worldState.poiIndex
        if (index.structureRevision == worldState.structureRevision) {
            return index.readyConstructionSites.firstOrNull()
        }
        return worldState.structures.find { 
            !it.isComplete && it.spec.buildCost.all { (res, amount) -> (it.inventory[res] ?: 0) >= amount } 
        }
    }

    private fun isWorkPossible(structure: com.example.utopia.data.models.Structure): Boolean {
        if (!structure.isComplete) return false
        val produces = structure.spec.produces
        val consumes = structure.spec.consumes
        if (produces.isEmpty() && consumes.isEmpty()) return false

        // Production-only buildings
        if (produces.isNotEmpty() && consumes.isEmpty()) {
            return produces.all { (type, amount) ->
                val current = structure.inventory[type] ?: 0
                val capacity = structure.spec.inventoryCapacity[type] ?: 0
                current + amount <= capacity
            }
        }

        // Transformation buildings (inputs + outputs)
        if (produces.isNotEmpty() && consumes.isNotEmpty()) {
            val outputType = produces.keys.first()
            val outputAmount = produces.values.first()
            val currentOutput = structure.inventory[outputType] ?: 0
            val outputCapacity = structure.spec.inventoryCapacity[outputType] ?: 0
            if (currentOutput + outputAmount > outputCapacity) return false

            return consumes.all { (type, amount) ->
                (structure.inventory[type] ?: 0) >= amount
            }
        }

        return false
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
        val timeSinceStart = nowMs - agent.intentStartTimeMs
        val currentIntent = agent.currentIntent

        val adjustedPressures = agent.transientPressures
            .mapValues { it.value.coerceIn(0f, 1f) }
            .toMutableMap()

        if (currentIntent !is AgentIntent.Idle && currentIntent !is AgentIntent.Wandering) {
            if (timeSinceStart < Constants.INTENT_COMMITMENT_MS) {
                val boosted = (adjustedPressures[currentIntent] ?: 0f) + Constants.INTENT_COMMITMENT_WEIGHT
                adjustedPressures[currentIntent] = boosted.coerceIn(0f, 1f)
            }
        }

        if (adjustedPressures.isEmpty()) {
            return AgentIntent.Idle
        }

        val strongestPressure = adjustedPressures.maxByOrNull { it.value }

        return strongestPressure?.key ?: AgentIntent.Wandering
    }
}
