package com.example.utopia.domain

import com.example.utopia.data.models.SocialField
import com.example.utopia.data.models.WorldState
import java.util.UUID
import kotlin.math.log2

object AgentSocialSystem {

    private const val SOCIAL_FIELD_CREATION_DISTANCE = 50f // Max distance to start a social field
    private const val BASE_RADIUS = 30f
    private const val GROWTH_FACTOR = 10f
    private const val ENERGY_DECAY_PER_SECOND = 10f // 100 energy / 10s = 10 per second

    fun updateSocialFields(worldState: WorldState, deltaSeconds: Float): WorldState {
        val newFields = mutableListOf<SocialField>()
        val agents = worldState.agents
        val existingFields = worldState.socialFields

        // 1. Update existing fields and check for termination
        val updatedFields = existingFields.mapNotNull { field ->
            if (field.participants.size < 2) {
                return@mapNotNull null
            }

            val newEnergy = field.energy - (ENERGY_DECAY_PER_SECOND * deltaSeconds)
            if (newEnergy <= 0f) {
                return@mapNotNull null
            }

            val newRadius = BASE_RADIUS + GROWTH_FACTOR * log2(field.participants.size.toFloat())
            field.copy(energy = newEnergy, radius = newRadius)
        }
        newFields.addAll(updatedFields)

        // 2. Creation of new fields
        val agentsNotInFields = agents.filter { agent ->
            updatedFields.none { field -> field.participants.contains(agent.id) }
        }

        val candidates = agentsNotInFields.filter { 
            it.currentIntent == "Wandering" || it.currentIntent == "seek_social" 
        }

        val checkedAgents = mutableSetOf<String>()

        for (agentA in candidates) {
            if (checkedAgents.contains(agentA.id)) continue

            val nearbyAgents = mutableListOf(agentA)
            for (agentB in candidates) {
                if (agentA.id == agentB.id || checkedAgents.contains(agentB.id)) continue

                val distance = agentA.position.toOffset().minus(agentB.position.toOffset()).getDistance()
                if (distance < SOCIAL_FIELD_CREATION_DISTANCE) {
                    nearbyAgents.add(agentB)
                }
            }

            if (nearbyAgents.size >= 2) {
                val participants = nearbyAgents.map { it.id }.toMutableList()
                val centerX = nearbyAgents.map { it.position.x }.average().toFloat()
                val centerY = nearbyAgents.map { it.position.y }.average().toFloat()

                val newField = SocialField(
                    id = UUID.randomUUID().toString(),
                    center = com.example.utopia.data.models.SerializableOffset(centerX, centerY),
                    radius = BASE_RADIUS + GROWTH_FACTOR * log2(participants.size.toFloat()),
                    energy = 100f, // Initial energy
                    participants = participants
                )
                newFields.add(newField)
                checkedAgents.addAll(participants)
            }
        }

        // 3. Update Agent States based on field membership
        val updatedAgents = agents.map { agent ->
            val activeField = newFields.find { it.participants.contains(agent.id) }
            
            if (activeField != null) {
                // Face the center of the social field
                val center = activeField.center.toOffset()
                val shouldFaceLeft = agent.x > center.x
                
                agent.copy(
                    state = com.example.utopia.data.models.AgentState.SOCIALIZING,
                    facingLeft = shouldFaceLeft
                )
            } else if (agent.state == com.example.utopia.data.models.AgentState.SOCIALIZING) {
                agent.copy(state = com.example.utopia.data.models.AgentState.IDLE)
            } else {
                agent
            }
        }

        return worldState.copy(socialFields = newFields, agents = updatedAgents)
    }
}
