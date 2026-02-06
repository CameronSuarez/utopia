package com.example.utopia.domain

import com.example.utopia.data.models.AgentIntent
import com.example.utopia.data.models.WorldState

object WorldAnalysisSystem : SimulationSystem {
    override fun update(state: WorldState, deltaTimeMs: Long, nowMs: Long): WorldState {
        return state
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

object EconomySystemWrapper : SimulationSystem {
    override fun update(state: WorldState, deltaTimeMs: Long, nowMs: Long): WorldState {
        return EconomySystem.processEconomy(state, deltaTimeMs, nowMs)
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
