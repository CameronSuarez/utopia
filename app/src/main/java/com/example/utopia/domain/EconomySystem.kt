package com.example.utopia.domain

import com.example.utopia.data.models.AgentIntent
import com.example.utopia.data.models.AgentRuntime
import com.example.utopia.data.models.AgentState
import com.example.utopia.data.models.InventoryItem
import com.example.utopia.data.models.Structure
import com.example.utopia.data.models.WorldState

internal object EconomySystem {
    private const val CARRY_CAPACITY = 5
    private const val SINK_RETARGET_COOLDOWN_MS = 1_000L

    private data class EconomyStepResult(
        val state: WorldState,
        val inventoryChanged: Boolean
    )

    /**
     * Entry point for the economy phase.
     * Logic order is preserved to allow resource flow (Prod -> Haul -> Trans/Const) in a single tick.
     */
    fun processEconomy(worldState: WorldState, deltaTimeMs: Long, nowMs: Long): WorldState {
        var inventoryChanged = false

        val haulResult = updateHauling(worldState, nowMs)
        inventoryChanged = inventoryChanged || haulResult.inventoryChanged

        val productionResult = updateProduction(haulResult.state, deltaTimeMs)
        inventoryChanged = inventoryChanged || productionResult.inventoryChanged

        val transformResult = updateTransformation(productionResult.state)
        inventoryChanged = inventoryChanged || transformResult.inventoryChanged

        val constructionResult = updateConstruction(transformResult.state)
        inventoryChanged = inventoryChanged || constructionResult.inventoryChanged

        return if (inventoryChanged) {
            constructionResult.state.copy(
                inventoryRevision = constructionResult.state.inventoryRevision + 1
            )
        } else {
            constructionResult.state
        }
    }

    private fun updateProduction(state: WorldState, deltaTimeMs: Long): EconomyStepResult {
        val producingStructures = state.structures.filter {
            it.isComplete && it.spec.produces.isNotEmpty() && it.spec.consumes.isEmpty()
        }
        if (producingStructures.isEmpty()) return EconomyStepResult(state, inventoryChanged = false)

        val newStructures = state.structures.toMutableList()
        var changed = false

        for (structure in producingStructures) {
            val structureIndex = newStructures.indexOfFirst { it.id == structure.id }
            if (structureIndex == -1) continue

            // 1. Presence-based Worker Counting
            val activeWorkersPresent = state.agents.count { agent ->
                agent.workplaceId == structure.id &&
                        state.getInfluencingStructure(agent.position.toOffset())?.id == structure.id
            }

            if (activeWorkersPresent == 0) {
                // Decay accumulator slightly or just leave it? We'll leave it for now.
                continue
            }

            // 2. Worker Scaling with Cap
            val effectiveWorkers = minOf(activeWorkersPresent, structure.spec.maxEffectiveWorkers ?: activeWorkersPresent)
            
            // 3. Accumulate Time
            var currentAcc = structure.productionAccMs + (deltaTimeMs * effectiveWorkers)
            val interval = structure.spec.productionIntervalMs
            
            if (interval <= 0L) continue // Invalid config

            val updatedInventory = structure.inventory.toMutableMap()
            var localChanged = false

            while (currentAcc >= interval) {
                // Check all products for capacity
                val canProduceAll = structure.spec.produces.all { (type, amount) ->
                    val currentAmount = updatedInventory[type] ?: 0
                    val capacity = structure.spec.inventoryCapacity[type] ?: 0
                    currentAmount + amount <= capacity
                }

                if (canProduceAll) {
                    structure.spec.produces.forEach { (type, amount) ->
                        updatedInventory[type] = (updatedInventory[type] ?: 0) + amount
                    }
                    currentAcc -= interval
                    localChanged = true
                } else {
                    // Capped - preserve accumulator but stop producing
                    currentAcc = minOf(currentAcc, interval) 
                    break
                }
            }

            if (localChanged || currentAcc != structure.productionAccMs) {
                newStructures[structureIndex] = structure.copy(
                    inventory = updatedInventory,
                    productionAccMs = currentAcc
                )
                changed = true
            }
        }

        return if (changed) {
            EconomyStepResult(state.copy(structures = newStructures), inventoryChanged = true)
        } else {
            EconomyStepResult(state, inventoryChanged = false)
        }
    }


    private fun updateTransformation(state: WorldState): EconomyStepResult {
        val workingAgents = state.agents.filter {
            it.state == AgentState.WORKING && it.workplaceId != null
        }
        if (workingAgents.isEmpty()) return EconomyStepResult(state, inventoryChanged = false)
    
        val newStructures = state.structures.toMutableList()
        var changed = false
    
        for (agent in workingAgents) {
            val workplaceIndex = newStructures.indexOfFirst { it.id == agent.workplaceId }
            if (workplaceIndex == -1) continue
    
            val workplace = newStructures[workplaceIndex]
            val consumesSpec = workplace.spec.consumes
            val producesSpec = workplace.spec.produces

            // Generic Transformation: Has both inputs and outputs
            if (consumesSpec.isEmpty() || producesSpec.isEmpty()) continue
    
            // Guardrail 1: Check if output capacity is full BEFORE consuming inputs
            val outputResourceType = producesSpec.keys.first()
            val outputAmount = producesSpec.values.first()
            val currentOutputAmount = workplace.inventory[outputResourceType] ?: 0
            val outputCapacity = workplace.spec.inventoryCapacity[outputResourceType] ?: 0

            if (currentOutputAmount + outputAmount > outputCapacity) continue

            val canConsume = consumesSpec.all { (resource, amount) ->
                (workplace.inventory[resource] ?: 0) >= amount
            }

            if (canConsume) {
                val newInventory = workplace.inventory.toMutableMap()

                consumesSpec.forEach { (resource, amount) ->
                    newInventory[resource] = (newInventory[resource] ?: 0) - amount
                }

                newInventory[outputResourceType] = currentOutputAmount + outputAmount
                
                newStructures[workplaceIndex] = workplace.copy(inventory = newInventory)
                changed = true
            }
        }
    
        return if (changed) {
            EconomyStepResult(state.copy(structures = newStructures), inventoryChanged = true)
        } else {
            EconomyStepResult(state, inventoryChanged = false)
        }
    }

    private fun updateHauling(worldState: WorldState, nowMs: Long): EconomyStepResult {
        var changed = false
        val newAgents = worldState.agents.toMutableList()
        val newStructures = worldState.structures.toMutableList()
        val structureById = worldState.structures.associateBy { it.id }

        for ((agentIndex, agent) in worldState.agents.withIndex()) {
            val intent = agent.currentIntent
            // Note: EconomySystem does not know about movement. It checks the high-level state
            // or preconditions provided by the snapshot.
            if (!isIntentSatisfied(agent, structureById)) continue

            when (intent) {
                is AgentIntent.GetResource -> {
                    val sourceIndex = newStructures.indexOfFirst { it.id == intent.targetId }
                    if (sourceIndex == -1) continue
                    val source = newStructures[sourceIndex]

                    val currentAmount = source.inventory[intent.resource] ?: 0
                    // Invariant: Source must have stock AND agent must be empty
                    if (currentAmount > 0 && agent.carriedItem == null) {
                        println("ECONOMY_TRACE: Agent ${agent.name} GET ${intent.resource} from ${source.spec.id}")
                        val takeAmount = minOf(CARRY_CAPACITY, currentAmount)
                        val newInventory = source.inventory.toMutableMap()
                        newInventory[intent.resource] = currentAmount - takeAmount
                        newStructures[sourceIndex] = source.copy(inventory = newInventory)

                        newAgents[agentIndex] = agent.copy(
                            carriedItem = InventoryItem(intent.resource, takeAmount),
                            currentIntent = AgentIntent.Idle,
                            intentStartTimeMs = 0L // Reset to allow immediate re-decision next tick
                        )
                        changed = true
                    }
                }
                is AgentIntent.StoreResource -> {
                    val carried = agent.carriedItem ?: continue
                    // Invariant: Agent must have item to store
                    
                    val sinkIndex = newStructures.indexOfFirst { it.id == intent.targetId }
                    if (sinkIndex == -1) continue
                    val sink = newStructures[sinkIndex]

                    val resourceType = carried.type
                    val currentAmount = sink.inventory[resourceType] ?: 0
                    val capacity = if (!sink.isComplete) {
                        sink.spec.buildCost[resourceType] ?: 0
                    } else {
                        sink.spec.inventoryCapacity[resourceType] ?: 0
                    }

                    // Invariant: Sink must have capacity
                    if (currentAmount < capacity) {
                        println("ECONOMY_TRACE: Agent ${agent.name} STORE ${carried.type} to ${sink.spec.id}")
                        val space = capacity - currentAmount
                        val depositAmount = minOf(space, carried.quantity)
                        val newInventory = sink.inventory.toMutableMap()
                        newInventory[resourceType] = currentAmount + depositAmount
                        newStructures[sinkIndex] = sink.copy(inventory = newInventory)

                        val remaining = carried.quantity - depositAmount
                        if (remaining <= 0) {
                            newAgents[agentIndex] = agent.copy(
                                carriedItem = null,
                                currentIntent = AgentIntent.Idle,
                                intentStartTimeMs = 0L,
                                lastFailedSinkId = null,
                                lastFailedSinkUntilMs = 0L
                            )
                        } else {
                            val nextSink = AgentDecisionSystem.findStoreTargetForResource(
                                worldState,
                                resourceType,
                                excludeId = sink.id,
                                requireCapacity = true
                            )
                            val nextIntent = nextSink?.let { AgentIntent.StoreResource(it.id) } ?: intent
                            newAgents[agentIndex] = agent.copy(
                                carriedItem = InventoryItem(resourceType, remaining),
                                currentIntent = nextIntent,
                                intentStartTimeMs = if (nextIntent != intent) 0L else agent.intentStartTimeMs,
                                lastFailedSinkId = null,
                                lastFailedSinkUntilMs = 0L
                            )
                        }
                        changed = true
                    } else {
                        val nextSink = AgentDecisionSystem.findStoreTargetForResource(
                            worldState,
                            resourceType,
                            excludeId = sink.id,
                            requireCapacity = true
                        )
                        if (nextSink != null) {
                            newAgents[agentIndex] = agent.copy(
                                currentIntent = AgentIntent.StoreResource(nextSink.id),
                                intentStartTimeMs = 0L,
                                lastFailedSinkId = sink.id,
                                lastFailedSinkUntilMs = nowMs + SINK_RETARGET_COOLDOWN_MS
                            )
                            changed = true
                        } else {
                            newAgents[agentIndex] = agent.copy(
                                lastFailedSinkId = sink.id,
                                lastFailedSinkUntilMs = nowMs + SINK_RETARGET_COOLDOWN_MS
                            )
                            changed = true
                        }
                    }
                }
                else -> {}
            }
        }

        return if (changed) {
            EconomyStepResult(worldState.copy(agents = newAgents, structures = newStructures), inventoryChanged = true)
        } else {
            EconomyStepResult(worldState, inventoryChanged = false)
        }
    }

    private fun updateConstruction(worldState: WorldState): EconomyStepResult {
        var changed = false
        val newStructures = worldState.structures.toMutableList()

        for ((structureIndex, structure) in worldState.structures.withIndex()) {
            if (structure.isComplete) continue

            val builders = worldState.agents.filter {
                val intent = it.currentIntent
                intent is AgentIntent.Construct && intent.targetId == structure.id
            }

            var updatedStructure = structure

            // Consume-all-at-start logic
            if (!structure.buildStarted) {
                val requiredResources = structure.spec.buildCost
                val hasAllResources = requiredResources.all { (resource, amount) ->
                    (structure.inventory[resource] ?: 0) >= amount
                }

                if (hasAllResources) {
                    val newInventory = structure.inventory.toMutableMap()
                    requiredResources.forEach { (resource, amount) ->
                        newInventory[resource] = (newInventory[resource] ?: 0) - amount
                    }
                    updatedStructure = structure.copy(
                        inventory = newInventory,
                        buildStarted = true
                    )
                } else {
                    // Cannot start construction yet
                    continue
                }
            }

            if (builders.isNotEmpty()) {
                // Progress logic
                val newProgress = updatedStructure.buildProgress + builders.size * 0.1f // arbitrary progress rate
                if (newProgress >= 100f) {
                    newStructures[structureIndex] = updatedStructure.copy(buildProgress = 100f, isComplete = true)
                } else {
                    newStructures[structureIndex] = updatedStructure.copy(buildProgress = newProgress)
                }
                changed = true
            } else if (updatedStructure.buildStarted) {
                // If resources are delivered but no builders are present, complete immediately.
                newStructures[structureIndex] = updatedStructure.copy(buildProgress = 100f, isComplete = true)
                changed = true
            }
        }

        return if (changed) {
            EconomyStepResult(worldState.copy(structures = newStructures), inventoryChanged = true)
        } else {
            EconomyStepResult(worldState, inventoryChanged = false)
        }
    }

    internal fun isOutputCapped(structure: Structure): Boolean {
        val producesSpec = structure.spec.produces
        val consumesSpec = structure.spec.consumes
        if (producesSpec.isEmpty()) return false

        if (consumesSpec.isEmpty()) {
            return producesSpec.any { (type, amount) ->
                val currentAmount = structure.inventory[type] ?: 0
                val capacity = structure.spec.inventoryCapacity[type] ?: 0
                currentAmount + amount > capacity
            }
        }

        val outputType = producesSpec.keys.first()
        val outputAmount = producesSpec.values.first()
        val currentOutputAmount = structure.inventory[outputType] ?: 0
        val outputCapacity = structure.spec.inventoryCapacity[outputType] ?: 0
        return currentOutputAmount + outputAmount > outputCapacity
    }

    internal fun isMissingInputs(structure: Structure): Boolean {
        val consumesSpec = structure.spec.consumes
        if (consumesSpec.isEmpty()) return false
        return consumesSpec.any { (type, amount) ->
            (structure.inventory[type] ?: 0) < amount
        }
    }

    /**
     * Logic check for whether an agent is positioned correctly to perform an economic action.
     * Duplicate logic from AgentPhysics for now to avoid circular dependencies, 
     * though ideally this would be part of a shared Perception/Spatial layer.
     */
    private fun isIntentSatisfied(
        agent: AgentRuntime,
        structureById: Map<String, Structure>
    ): Boolean {
        val targetId = when (val intent = agent.currentIntent) {
            is AgentIntent.GetResource -> intent.targetId
            is AgentIntent.StoreResource -> intent.targetId
            is AgentIntent.Construct -> intent.targetId
            else -> return false
        }
        val target = structureById[targetId] ?: return false
        return target.getInfluenceRect().contains(agent.position.toOffset())
    }
}
