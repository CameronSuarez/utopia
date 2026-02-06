package com.example.utopia.domain

import com.example.utopia.data.models.AgentIntent
import com.example.utopia.data.models.AgentRuntime
import com.example.utopia.data.models.ResourceType
import com.example.utopia.data.models.Structure
import com.example.utopia.data.models.WorldState
import com.example.utopia.util.Constants

object AgentDecisionSystem : SimulationSystem {
    private const val NO_SINK_COOLDOWN_MS = 1_000L

    override fun update(state: WorldState, deltaTimeMs: Long, nowMs: Long): WorldState {
        val mutableAgents = state.agents.toMutableList()
        val mutableStructures = state.structures.toMutableList()
        val structureIndexById = mutableStructures.withIndex().associate { it.value.id to it.index }
        val assignedCounts = mutableMapOf<String, Int>().apply {
            mutableStructures.forEach { structure ->
                if (structure.spec.capacity > 0) {
                    this[structure.id] = structure.workers.size
                }
            }
        }
        var agentsChanged = false
        var structuresChanged = false
        for (i in mutableAgents.indices) {
            val agent = mutableAgents[i]
            val workplaceId = agent.workplaceId ?: continue
            val workplaceIdx = structureIndexById[workplaceId]
            val workplace = workplaceIdx?.let { mutableStructures[it] }
            if (workplace == null || !canWorkAt(workplace)) {
                if (workplaceIdx != null && workplace != null) {
                    mutableStructures[workplaceIdx] = workplace.copy(workers = workplace.workers - agent.id)
                    val nextCount = (assignedCounts[workplace.id] ?: workplace.workers.size) - 1
                    assignedCounts[workplace.id] = maxOf(0, nextCount)
                    structuresChanged = true
                }
                mutableAgents[i] = agent.copy(workplaceId = null)
                agentsChanged = true
            }
        }

        val cachedHaulingIntent = findHaulingIntent(state)
        val cachedReadyConstructionSite = findReadyConstructionSite(state)

        data class AvailableWorkplace(val structIndex: Int, var remaining: Int)
        val availableWorkplaces = mutableListOf<AvailableWorkplace>()
        for (i in mutableStructures.indices) {
            val structure = mutableStructures[i]
            if (isAvailableWorkplace(structure, assignedCounts)) {
                val assigned = assignedCounts[structure.id] ?: structure.workers.size
                val remaining = structure.spec.capacity - assigned
                if (remaining > 0) {
                    availableWorkplaces.add(AvailableWorkplace(i, remaining))
                }
            }
        }
        val hasAnyAvailableWorkplace = availableWorkplaces.isNotEmpty()
        var nextWorkplaceIndex = 0

        fun takeAvailableWorkplace(): Structure? {
            if (availableWorkplaces.isEmpty()) return null
            var checked = 0
            while (checked < availableWorkplaces.size) {
                val entry = availableWorkplaces[nextWorkplaceIndex]
                nextWorkplaceIndex = (nextWorkplaceIndex + 1) % availableWorkplaces.size
                if (entry.remaining > 0) {
                    entry.remaining -= 1
                    return mutableStructures[entry.structIndex]
                }
                checked++
            }
            return null
        }

        val agentCount = mutableAgents.size
        val startIndex = if (agentCount == 0) 0 else ((nowMs / 16L).toInt() % agentCount).coerceAtLeast(0)
        for (step in 0 until agentCount) {
            val i = (startIndex + step) % agentCount
            val originalAgent = mutableAgents[i]
            var agent = originalAgent

            val scores = calculateScores(
                agent = agent,
                state = state,
                hasAvailableWorkplace = hasAnyAvailableWorkplace,
                nowMs = nowMs,
                haulingIntent = cachedHaulingIntent,
                readyConstructionSite = cachedReadyConstructionSite
            )
            val nextIntent = selectIntent(agent.copy(transientPressures = scores), nowMs)
            val intentChanged = nextIntent != agent.currentIntent

            agent = agent.copy(
                transientPressures = scores,
                currentIntent = nextIntent,
                intentStartTimeMs = if (intentChanged) nowMs else agent.intentStartTimeMs
            )
            if (agent != originalAgent) {
                agentsChanged = true
            }

            if (agent.carriedItem != null &&
                scores.keys.none { it is AgentIntent.StoreResource } &&
                agent.lastFailedSinkUntilMs <= nowMs
            ) {
                agent = agent.copy(
                    lastFailedSinkId = null,
                    lastFailedSinkUntilMs = nowMs + NO_SINK_COOLDOWN_MS
                )
                agentsChanged = true
            }

            if (agent.currentIntent == AgentIntent.Work && agent.workplaceId == null) {
                val availableWorkplace = takeAvailableWorkplace()
                if (availableWorkplace != null) {
                    val structIdx = structureIndexById[availableWorkplace.id] ?: -1
                    if (structIdx != -1) {
                        mutableStructures[structIdx] = availableWorkplace.copy(workers = availableWorkplace.workers + agent.id)
                    }
                    assignedCounts[availableWorkplace.id] = (assignedCounts[availableWorkplace.id] ?: 0) + 1
                    agent = agent.copy(workplaceId = availableWorkplace.id)
                    if (agent != mutableAgents[i]) {
                        mutableAgents[i] = agent
                        agentsChanged = true
                    } else {
                        mutableAgents[i] = agent
                    }
                    structuresChanged = true
                } else {
                    agent = agent.copy(currentIntent = AgentIntent.Idle, intentStartTimeMs = 0L)
                    if (agent != mutableAgents[i]) {
                        mutableAgents[i] = agent
                        agentsChanged = true
                    } else {
                        mutableAgents[i] = agent
                    }
                }
            } else {
                if (agent != mutableAgents[i]) {
                    mutableAgents[i] = agent
                    agentsChanged = true
                }
            }
        }

        return when {
            agentsChanged && structuresChanged -> state.copy(agents = mutableAgents, structures = mutableStructures)
            agentsChanged -> state.copy(agents = mutableAgents)
            structuresChanged -> state.copy(structures = mutableStructures)
            else -> state
        }
    }

    internal fun hasAvailableWorkplace(state: WorldState): Boolean {
        return state.structures.any { isAvailableWorkplace(it, emptyMap()) }
    }

    private fun hasAvailableWorkplace(structures: List<Structure>, assignedCounts: Map<String, Int>): Boolean {
        return structures.any { isAvailableWorkplace(it, assignedCounts) }
    }

    internal fun canWorkAt(structure: Structure): Boolean = isWorkPossible(structure)

    internal fun findStoreTargetForResource(
        state: WorldState,
        resource: ResourceType,
        excludeId: String? = null,
        requireCapacity: Boolean = true,
        skipSinkId: String? = null
    ): Structure? {
        val index = state.poiIndex
        if (isPoiIndexCurrent(state)) {
            val constructionTarget = index.constructionSitesNeeding[resource]?.firstOrNull { it.id != excludeId && it.id != skipSinkId }
            if (constructionTarget != null) return constructionTarget
            if (requireCapacity) {
                return index.sinksByResource[resource]?.firstOrNull { it.id != excludeId && it.id != skipSinkId }
            }
            return index.sinksByResource[resource]?.firstOrNull { it.id != excludeId && it.id != skipSinkId }
                ?: state.structures.firstOrNull { it.id != excludeId && it.id != skipSinkId && it.spec.consumes.containsKey(resource) }
        }

        val constructionTarget = state.structures.firstOrNull { s ->
            s.id != excludeId && s.id != skipSinkId && !s.isComplete && (s.spec.buildCost[resource] ?: 0) > (s.inventory[resource] ?: 0)
        }
        if (constructionTarget != null) return constructionTarget

        return if (requireCapacity) {
            state.structures.firstOrNull { s ->
                s.id != excludeId &&
                    s.id != skipSinkId &&
                    s.spec.consumes.containsKey(resource) &&
                    (s.inventory[resource] ?: 0) < (s.spec.inventoryCapacity[resource] ?: 0)
            }
        } else {
            state.structures.firstOrNull { s -> s.id != excludeId && s.id != skipSinkId && s.spec.consumes.containsKey(resource) }
        }
    }

    internal fun selectIntent(agent: AgentRuntime, nowMs: Long): AgentIntent {
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

    private fun calculateScores(
        agent: AgentRuntime,
        state: WorldState,
        hasAvailableWorkplace: Boolean,
        nowMs: Long,
        haulingIntent: AgentIntent?,
        readyConstructionSite: Structure?
    ): Map<AgentIntent, Float> {
        val scores = linkedMapOf<AgentIntent, Float>()

        if (agent.carriedItem != null) {
            if (agent.lastFailedSinkId == null && agent.lastFailedSinkUntilMs > nowMs) {
                return scores
            }
            val carried = agent.carriedItem
            val skipSinkId = if (agent.lastFailedSinkUntilMs > nowMs) agent.lastFailedSinkId else null
        val target = findConstructionTargetForResource(state, carried.type, skipSinkId = skipSinkId)
                ?: findConsumerTargetForResource(state, carried.type, skipSinkId = skipSinkId)
        if (target != null) {
            scores[AgentIntent.StoreResource(target.id)] = 0.8f
        }
        return scores
    }

        val needs = agent.needs

        scores[AgentIntent.SeekSleep] = if (needs.sleep < 10f) 1.0f - (needs.sleep / 100f) else 0f
        scores[AgentIntent.SeekFun] = 1.0f - (needs.`fun` / 100f)
        scores[AgentIntent.SeekStability] = 1.0f - (needs.stability / 100f)
        val assignedWorkplace = agent.workplaceId?.let { id ->
            state.structures.firstOrNull { it.id == id }
        }
        if (assignedWorkplace != null && canWorkAt(assignedWorkplace)) {
            scores[AgentIntent.Work] = 1.0f
        } else if (agent.workplaceId == null && hasAvailableWorkplace) {
            scores[AgentIntent.Work] = 1.0f
        }

        haulingIntent?.let { scores[it] = 0.8f }
        readyConstructionSite?.let { scores[AgentIntent.Construct(it.id)] = 0.8f }

        return scores.filter { it.value > 0.05f }
    }

    private fun isAvailableWorkplace(structure: Structure, assignedCounts: Map<String, Int>): Boolean {
        val assigned = assignedCounts[structure.id] ?: structure.workers.size
        return structure.isComplete &&
            (structure.spec.produces.isNotEmpty() || structure.spec.consumes.isNotEmpty()) &&
            structure.spec.capacity > 0 &&
            assigned < structure.spec.capacity &&
            canWorkAt(structure)
    }

    private fun findHaulingIntent(state: WorldState): AgentIntent? {
        val index = state.poiIndex
        if (isPoiIndexCurrent(state)) {
            val constructionSite = index.constructionSitesNeeding.values.firstOrNull { it.isNotEmpty() }?.firstOrNull()
            if (constructionSite != null) {
                for ((resource, requiredAmount) in constructionSite.spec.buildCost) {
                    if ((constructionSite.inventory[resource] ?: 0) < requiredAmount) {
                        val source = index.sourcesByResource[resource]?.firstOrNull()
                        if (source != null) {
                            return AgentIntent.GetResource(source.id, resource)
                        }
                    }
                }
            }
        } else {
            val constructionSite = state.structures
                .filter { !it.isComplete }
                .firstOrNull { site ->
                    site.spec.buildCost.any { (resource, amount) ->
                        (site.inventory[resource] ?: 0) < amount
                    }
                }
            if (constructionSite != null) {
                for ((resource, requiredAmount) in constructionSite.spec.buildCost) {
                    if ((constructionSite.inventory[resource] ?: 0) < requiredAmount) {
                        val source = findSourceForResource(state, resource)
                        if (source != null) {
                            return AgentIntent.GetResource(source.id, resource)
                        }
                    }
                }
            }
        }
        val consumer = findAnyConsumerNeedingResources(state) ?: return null
        for ((resource, _) in consumer.spec.consumes) {
            val source = findSourceForResource(state, resource)
            if (source != null) {
                return AgentIntent.GetResource(source.id, resource)
            }
        }
        return null
    }

    private fun findSourceForResource(state: WorldState, resource: ResourceType): Structure? {
        val index = state.poiIndex
        if (isPoiIndexCurrent(state)) {
            return index.sourcesByResource[resource]?.firstOrNull()
        }
        return state.structures.firstOrNull {
            (it.inventory[resource] ?: 0) > 0 && it.spec.produces.containsKey(resource)
        }
    }

    private fun findConstructionTargetForResource(
        state: WorldState,
        resource: ResourceType,
        excludeId: String? = null,
        skipSinkId: String? = null
    ): Structure? {
        val index = state.poiIndex
        if (isPoiIndexCurrent(state)) {
            return index.constructionSitesNeeding[resource]?.firstOrNull { it.id != excludeId && it.id != skipSinkId }
        }
        return state.structures.firstOrNull { s ->
            s.id != excludeId &&
                s.id != skipSinkId &&
                !s.isComplete &&
                (s.spec.buildCost[resource] ?: 0) > (s.inventory[resource] ?: 0)
        }
    }

    private fun findConsumerTargetForResource(
        state: WorldState,
        resource: ResourceType,
        excludeId: String? = null,
        skipSinkId: String? = null
    ): Structure? {
        val index = state.poiIndex
        if (isPoiIndexCurrent(state)) {
            return index.sinksByResource[resource]?.firstOrNull { candidate ->
                candidate.id != excludeId &&
                    candidate.id != skipSinkId &&
                    canAcceptInputForProduction(candidate, resource)
            }
        }
        return state.structures.firstOrNull { s ->
            s.id != excludeId &&
                s.id != skipSinkId &&
                s.spec.consumes.containsKey(resource) &&
                (s.inventory[resource] ?: 0) < (s.spec.inventoryCapacity[resource] ?: 0) &&
                canAcceptInputForProduction(s, resource)
        }
    }

    private fun findAnyConsumerNeedingResources(state: WorldState): Structure? {
        val index = state.poiIndex
        if (isPoiIndexCurrent(state)) {
            return index.sinksByResource.values.firstOrNull { list ->
                list.any { canAcceptInputForProduction(it, null) }
            }?.firstOrNull { canAcceptInputForProduction(it, null) }
        }
        return state.structures.firstOrNull { s ->
            s.spec.consumes.isNotEmpty() && s.spec.consumes.any { (resource, _) ->
                (s.inventory[resource] ?: 0) < (s.spec.inventoryCapacity[resource] ?: 0)
            } && canAcceptInputForProduction(s, null)
        }
    }

    private fun canAcceptInputForProduction(structure: Structure, resource: ResourceType?): Boolean {
        if (structure.spec.consumes.isEmpty()) return false
        if (resource != null && !structure.spec.consumes.containsKey(resource)) return false
        val produces = structure.spec.produces
        if (produces.isNotEmpty()) {
            val outputType = produces.keys.first()
            val outputAmount = produces.values.first()
            val currentOutput = structure.inventory[outputType] ?: 0
            val outputCapacity = structure.spec.inventoryCapacity[outputType] ?: 0
            if (currentOutput + outputAmount > outputCapacity) return false
        }
        return true
    }

    private fun findReadyConstructionSite(state: WorldState): Structure? {
        val index = state.poiIndex
        if (isPoiIndexCurrent(state)) {
            return index.readyConstructionSites.firstOrNull()
        }
        return state.structures.firstOrNull {
            !it.isComplete && it.spec.buildCost.all { (res, amount) -> (it.inventory[res] ?: 0) >= amount }
        }
    }

    private fun isPoiIndexCurrent(state: WorldState): Boolean {
        return state.poiIndex.structureRevision == state.structureRevision &&
            state.poiIndex.inventoryRevision == state.inventoryRevision
    }

    private fun isWorkPossible(structure: Structure): Boolean {
        if (!structure.isComplete) return false
        val produces = structure.spec.produces
        val consumes = structure.spec.consumes
        if (produces.isEmpty() && consumes.isEmpty()) return false

        if (produces.isNotEmpty() && consumes.isEmpty()) {
            return produces.all { (type, amount) ->
                val current = structure.inventory[type] ?: 0
                val capacity = structure.spec.inventoryCapacity[type] ?: 0
                current + amount <= capacity
            }
        }

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
}
