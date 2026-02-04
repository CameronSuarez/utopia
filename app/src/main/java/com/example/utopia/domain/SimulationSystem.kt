package com.example.utopia.domain

import com.example.utopia.data.models.WorldState

/**
 * A discrete phase of the world simulation.
 */
interface SimulationSystem {
    /**
     * Updates the world state for this system's specific domain.
     * 
     * @param state The current world state at the start of this system's phase.
     * @param deltaTimeMs Time elapsed since the last tick in milliseconds.
     * @param nowMs Current monotonic game time in milliseconds.
     * @return The updated world state after this system's operations.
     */
    fun update(state: WorldState, deltaTimeMs: Long, nowMs: Long): WorldState
}
