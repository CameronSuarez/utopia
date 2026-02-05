package com.example.utopia.domain

import com.example.utopia.data.models.POI
import com.example.utopia.data.models.PoiIndex
import com.example.utopia.data.models.PoiType
import com.example.utopia.data.models.ResourceType
import com.example.utopia.data.models.Structure
import com.example.utopia.data.models.WorldState
import com.example.utopia.data.models.SerializableOffset

object PoiSystem : SimulationSystem {
    override fun update(state: WorldState, deltaTimeMs: Long, nowMs: Long): WorldState {
        if (state.poiIndex.structureRevision == state.structureRevision &&
            state.poiIndex.inventoryRevision == state.inventoryRevision) {
            return state
        }
        return recompute(state)
    }

    fun recompute(state: WorldState): WorldState {
        val index = buildIndex(state)
        return state.copy(
            pois = index.pois,
            poiIndex = index
        )
    }

    private fun buildIndex(state: WorldState): PoiIndex {
        val incompleteStructures = state.structures.filter { !it.isComplete }
        val readyConstructionSites = incompleteStructures.filter { structure ->
            structure.spec.buildCost.all { (res, amount) ->
                (structure.inventory[res] ?: 0) >= amount
            }
        }

        val sourcesByResource = mutableMapOf<ResourceType, MutableList<Structure>>()
        val sinksByResource = mutableMapOf<ResourceType, MutableList<Structure>>()
        val constructionSitesNeeding = mutableMapOf<ResourceType, MutableList<Structure>>()

        for (structure in state.structures) {
            for ((resource, _) in structure.spec.produces) {
                val current = structure.inventory[resource] ?: 0
                if (current > 0) {
                    sourcesByResource.getOrPut(resource) { mutableListOf() }.add(structure)
                }
            }

            for ((resource, _) in structure.spec.consumes) {
                val current = structure.inventory[resource] ?: 0
                val capacity = structure.spec.inventoryCapacity[resource] ?: 0
                if (current < capacity) {
                    sinksByResource.getOrPut(resource) { mutableListOf() }.add(structure)
                }
            }
        }

        for (structure in incompleteStructures) {
            for ((resource, amount) in structure.spec.buildCost) {
                val current = structure.inventory[resource] ?: 0
                if (current < amount) {
                    constructionSitesNeeding.getOrPut(resource) { mutableListOf() }.add(structure)
                }
            }
        }

        val pois = state.structures.mapNotNull { structure ->
            try {
                val poiType = PoiType.valueOf(structure.spec.id)
                POI(
                    structure.id,
                    poiType,
                    SerializableOffset(
                        structure.x + structure.spec.worldWidth / 2,
                        structure.y - structure.spec.worldHeight / 2
                    )
                )
            } catch (_: IllegalArgumentException) {
                null
            }
        }

        return PoiIndex(
            structureRevision = state.structureRevision,
            inventoryRevision = state.inventoryRevision,
            pois = pois,
            incompleteStructures = incompleteStructures,
            readyConstructionSites = readyConstructionSites,
            sourcesByResource = sourcesByResource,
            sinksByResource = sinksByResource,
            constructionSitesNeeding = constructionSitesNeeding
        )
    }
}
