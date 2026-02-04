package com.example.utopia.domain

import com.example.utopia.data.models.WorldState

class SimulationPipeline(
    private val systems: List<SimulationSystem>
) {
    init {
        validateOrderInvariants()
    }

    fun run(state: WorldState, deltaTimeMs: Long, nowMs: Long): WorldState {
        var current = state
        for (system in systems) {
            current = system.update(current, deltaTimeMs, nowMs)
        }
        return current
    }

    private fun validateOrderInvariants() {
        val worldAnalysisIndex = systems.indexOf(WorldAnalysisSystem)
        val agentIntentIndex = systems.indexOf(AgentIntentSystemWrapper)

        require(worldAnalysisIndex != -1 && agentIntentIndex != -1 && worldAnalysisIndex < agentIntentIndex) {
            "Simulation pipeline order invariant violated: WorldAnalysisSystem must run before AgentIntentSystemWrapper."
        }
    }
}
