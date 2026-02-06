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
        val poiIndex = systems.indexOf(PoiSystem)
        val decisionIndex = systems.indexOf(AgentDecisionSystem)

        require(poiIndex != -1 && decisionIndex != -1 && poiIndex < decisionIndex) {
            "Simulation pipeline order invariant violated: PoiSystem must run before AgentDecisionSystem."
        }
    }
}
