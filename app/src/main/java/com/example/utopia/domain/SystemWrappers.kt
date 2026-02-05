package com.example.utopia.domain

import com.example.utopia.data.models.AgentIntent
import com.example.utopia.data.models.WorldState

object WorldAnalysisSystem : SimulationSystem {
    override fun update(state: WorldState, deltaTimeMs: Long, nowMs: Long): WorldState {
        val hasAvailableWorkplace = state.structures.any {
            it.isComplete && (it.spec.produces.isNotEmpty() || it.spec.consumes.isNotEmpty()) &&
                    it.spec.capacity > 0 && it.workers.size < it.spec.capacity
        }
        return state.withTransientHasAvailableWorkplace(hasAvailableWorkplace)
    }
}

object AgentAssignmentSystem : SimulationSystem {
    override fun update(state: WorldState, deltaTimeMs: Long, nowMs: Long): WorldState {
        val agents = state.agents.toMutableList()
        val structures = state.structures.toMutableList()
        var changed = false

        for (i in agents.indices) {
            val agent = agents[i]
            
            // Assign Home opportunistically
            if (agent.homeId == null) {
                val availableHome = structures.find { s ->
                    s.isComplete && s.spec.providesSleep && s.residents.size < s.spec.capacity
                }
                if (availableHome != null) {
                    val structIdx = structures.indexOfFirst { it.id == availableHome.id }
                    structures[structIdx] = availableHome.copy(residents = availableHome.residents + agent.id)
                    agents[i] = agent.copy(homeId = availableHome.id)
                    changed = true
                }
            }
        }
        
        return if (changed) state.copy(agents = agents, structures = structures) else state
    }
}

object AgentNeedsSystemWrapper : SimulationSystem {
    override fun update(state: WorldState, deltaTimeMs: Long, nowMs: Long): WorldState {
        val deltaSeconds = deltaTimeMs / 1000.0f
        val agentsWithNeeds = state.agents.map { agent ->
            AgentNeedsSystem.updateNeeds(agent, deltaSeconds, state)
        }
        return state.copy(agents = agentsWithNeeds)
    }
}

object AgentSocialSystemWrapper : SimulationSystem {
    override fun update(state: WorldState, deltaTimeMs: Long, nowMs: Long): WorldState {
        val deltaSeconds = deltaTimeMs / 1000.0f
        return AgentSocialSystem.updateSocialFields(state, deltaSeconds)
    }
}

object AgentGossipSystemWrapper : SimulationSystem {
    override fun update(state: WorldState, deltaTimeMs: Long, nowMs: Long): WorldState {
        return AgentGossipSystem.processGossip(state, nowMs)
    }
}

object AgentEmojiSystemWrapper : SimulationSystem {
    override fun update(state: WorldState, deltaTimeMs: Long, nowMs: Long): WorldState {
        val updatedEmojiSignals = AgentEmojiSystem.updateEmojiSignals(state, nowMs)
        return state.copy(emojiSignals = updatedEmojiSignals)
    }
}

object AgentRelationshipSystemWrapper : SimulationSystem {
    override fun update(state: WorldState, deltaTimeMs: Long, nowMs: Long): WorldState {
        val agentsWithRelationships = AgentRelationshipSystem.updateRelationships(state, deltaTimeMs)
        return state.copy(agents = agentsWithRelationships)
    }
}

object AgentIntentSystemWrapper : SimulationSystem {
    override fun update(state: WorldState, deltaTimeMs: Long, nowMs: Long): WorldState {
        // 1. Standard intent selection
        val agents = state.agents.map { agent ->
            val pressures = AgentIntentSystem.calculatePressures(agent, state)
            val nextIntent = AgentIntentSystem.selectIntent(agent.copy(transientPressures = pressures), nowMs)
            val intentChanged = nextIntent != agent.currentIntent

            agent.copy(
                transientPressures = pressures,
                currentIntent = nextIntent,
                intentStartTimeMs = if (intentChanged) nowMs else agent.intentStartTimeMs
            )
        }
        
        var changed = false
        val mutableAgents = agents.toMutableList()
        val mutableStructures = state.structures.toMutableList()

        // 2. Demand-driven workplace assignment
        for (i in mutableAgents.indices) {
            val agent = mutableAgents[i]
            if (agent.currentIntent == AgentIntent.Work && agent.workplaceId == null) {
                
                val availableWorkplace = mutableStructures.find { s ->
                    s.isComplete && (s.spec.produces.isNotEmpty() || s.spec.consumes.isNotEmpty()) &&
                            s.spec.capacity > 0 && s.workers.size < s.spec.capacity
                }
                
                if (availableWorkplace != null) {
                    val structIdx = mutableStructures.indexOfFirst { it.id == availableWorkplace.id }
                    mutableStructures[structIdx] = availableWorkplace.copy(workers = availableWorkplace.workers + agent.id)
                    mutableAgents[i] = agent.copy(workplaceId = availableWorkplace.id)
                    changed = true
                } else {
                    // No job available, revert intent to prevent getting stuck
                    mutableAgents[i] = agent.copy(currentIntent = AgentIntent.Idle)
                    changed = true
                }
            }
        }
        
        return if (changed) {
            state.copy(agents = mutableAgents, structures = mutableStructures)
        } else {
            state.copy(agents = agents)
        }
    }
}

object EconomySystemWrapper : SimulationSystem {
    override fun update(state: WorldState, deltaTimeMs: Long, nowMs: Long): WorldState {
        return EconomySystem.processEconomy(state, deltaTimeMs)
    }
}

class AgentPhysicsWrapper(private val navGrid: NavGrid) : SimulationSystem {
    override fun update(state: WorldState, deltaTimeMs: Long, nowMs: Long): WorldState {
        val movedAgents = updateAgents(
            agents = state.agents,
            worldState = state,
            navGrid = navGrid,
            deltaTimeMs = deltaTimeMs,
            nowMs = nowMs
        )
        return state.copy(agents = movedAgents)
    }
}

object StaleTargetCleanupSystem : SimulationSystem {
    override fun update(state: WorldState, deltaTimeMs: Long, nowMs: Long): WorldState {
        val structureIds = state.structures.map { it.id }.toSet()
        val mutableStructures = state.structures.toMutableList()
        var structuresChanged = false

        val cleanedAgents = state.agents.map { agent ->
            var updatedAgent = agent
            
            // Cleanup workplaceId
            if (agent.workplaceId != null && !structureIds.contains(agent.workplaceId)) {
                val oldWorkplaceId = agent.workplaceId
                updatedAgent = updatedAgent.copy(workplaceId = null)

                // Also remove agent from the old workplace's worker list
                val structIdx = mutableStructures.indexOfFirst { it.id == oldWorkplaceId }
                if (structIdx != -1) {
                    val oldWorkplace = mutableStructures[structIdx]
                    mutableStructures[structIdx] = oldWorkplace.copy(workers = oldWorkplace.workers - agent.id)
                    structuresChanged = true
                }
            }

            // Cleanup homeId
            if (agent.homeId != null && !structureIds.contains(agent.homeId)) {
                val oldHomeId = agent.homeId
                updatedAgent = updatedAgent.copy(homeId = null)
                
                // Also remove agent from the old home's resident list
                val structIdx = mutableStructures.indexOfFirst { it.id == oldHomeId }
                if (structIdx != -1) {
                    val oldHome = mutableStructures[structIdx]
                    mutableStructures[structIdx] = oldHome.copy(residents = oldHome.residents - agent.id)
                    structuresChanged = true
                }
            }
            
            // Cleanup stale intents
            val isStale = when (val intent = updatedAgent.currentIntent) {
                is AgentIntent.GetResource -> !structureIds.contains(intent.targetId)
                is AgentIntent.StoreResource -> !structureIds.contains(intent.targetId)
                is AgentIntent.Construct -> !structureIds.contains(intent.targetId)
                else -> false
            }
            
            if (isStale) {
                updatedAgent = updatedAgent.copy(
                    currentIntent = AgentIntent.Idle,
                    intentStartTimeMs = 0L
                )
            }
            
            updatedAgent
        }

        return if (structuresChanged) {
            state.copy(agents = cleanedAgents, structures = mutableStructures)
        } else {
            state.copy(agents = cleanedAgents)
        }
    }
}
