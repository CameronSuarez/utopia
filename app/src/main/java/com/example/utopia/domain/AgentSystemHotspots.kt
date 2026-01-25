package com.example.utopia.domain

import com.example.utopia.data.models.AgentRuntime
import com.example.utopia.data.models.StructureType
import com.example.utopia.data.models.WorldState
import com.example.utopia.util.Constants

internal fun AgentSystem.ensureAgentIndexMapping(agents: List<AgentRuntime>) {
    val currentAgentCount = agents.size
    val maxShortId = agents.maxOfOrNull { it.shortId } ?: -1

    if (currentAgentCount != lastAgentCountForMapping || maxShortId != lastMaxShortIdForMapping) {
        lastAgentCountForMapping = currentAgentCount
        lastMaxShortIdForMapping = maxShortId
        agentIndexByIdCapacity = maxShortId + 1

        if (agentIndexById.size < agentIndexByIdCapacity) {
            agentIndexById = IntArray(agentIndexByIdCapacity) { INVALID_INDEX }
        } else {
            agentIndexById.fill(INVALID_INDEX, 0, agentIndexById.size)
        }

        for (i in agents.indices) {
            val agent = agents[i]
            if (agent.shortId in 0 until agentIndexByIdCapacity) {
                agentIndexById[agent.shortId] = i
            }
        }
    }
}

internal fun AgentSystem.updateSpatialHash(agents: List<AgentRuntime>) {
    agentLookup.clear()
    spatialHash.values.forEach { it.clear() }
    for (agent in agents) {
        agentLookup[agent.id] = agent
        val cellX = (agent.x / (Constants.TILE_SIZE * Constants.SPATIAL_HASH_CELL_SIZE)).toInt()
        val cellY = (agent.y / (Constants.TILE_SIZE * Constants.SPATIAL_HASH_CELL_SIZE)).toInt()
        val hash = (cellX shl 16) or (cellY and 0xFFFF)
        spatialHash.getOrPut(hash) { mutableListOf() }.add(agent)
    }
}

internal fun AgentSystem.updateHotspotData(state: WorldState) {
    tavernOccupancy.clear()
    agentCurrentTavernId.clear()

    plazaOccupancy.clear()
    agentCurrentPlazaId.clear()

    val hotspots = state.structures.filter { it.type.isHotspot }
    if (hotspots.isEmpty()) return

    for (hs in hotspots) {
        var occupancyCount = 0

        val left = hs.x - 10f
        val right = hs.x + hs.type.worldWidth + 10f
        val top = hs.y - 10f
        val bottom = hs.y + hs.type.worldHeight + 10f

        val hsKey = hs.id.hashCode().toLong()

        for (agent in state.agents) {
            val isDwellingAtHs = agent.dwellTimerMs > 0 && agent.goalIntentTargetId == hs.id
            val isNear = agent.x in left..right && agent.y in top..bottom
            val isIntentHs = agent.goalIntentTargetId == hs.id

            if (isDwellingAtHs || (isNear && (isIntentHs || agent.jobId == hs.id))) {
                occupancyCount++
                if (hs.type == StructureType.TAVERN) {
                    agentCurrentTavernId[agent.id] = hsKey
                } else if (hs.type == StructureType.PLAZA) {
                    agentCurrentPlazaId[agent.id] = hsKey
                }
            }
        }

        if (hs.type == StructureType.TAVERN) {
            tavernOccupancy[hsKey] = occupancyCount
        } else if (hs.type == StructureType.PLAZA) {
            plazaOccupancy[hsKey] = occupancyCount
        }
    }
}

internal fun AgentSystem.agentIndexFromShortId(shortId: Int): Int {
    if (shortId !in 0 until agentIndexByIdCapacity) return INVALID_INDEX
    return agentIndexById[shortId]
}
