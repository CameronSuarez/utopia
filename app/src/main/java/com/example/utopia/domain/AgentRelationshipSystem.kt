package com.example.utopia.domain

import com.example.utopia.data.models.AgentRuntime
import com.example.utopia.data.models.SocialField
import com.example.utopia.data.models.WorldState
import kotlin.math.abs
import kotlin.math.sign

/**
 * VIII. RELATIONSHIPS
 */
object AgentRelationshipSystem {
    private const val AFFINITY_DECAY_RATE = 0.001f // Drastically reduced decay rate
    private const val MIN_AFFINITY = -100f
    private const val MAX_AFFINITY = 100f

    /**
     * Resolve React: How much an agent likes an emoji signal.
     * Result is [-5, 5]
     */
    fun calculatePreference(listener: AgentRuntime, emoji: String): Float {
        val p = listener.personality
        val mood = (listener.needs.sleep + listener.needs.stability + listener.needs.social + listener.needs.`fun`) / 400f
        
        val base = when (emoji) {
            "😊", "😄", "❤️", "🌻", "☀️", "🕊️" -> (p.positivity + p.warmth + mood) / 3f
            "👋", "✨", "🥧", "🍻" -> (p.expressiveness + p.warmth) / 2f
            "😟", "💧", "🥀", "⛈️", "🕸️" -> (p.sensitivity * 0.5f) - (p.positivity * 0.5f)
            "😴" -> if (listener.needs.sleep < 30f) 0.5f else -0.2f
            "💢", "🌫️", "💀", "⛓️" -> -p.warmth
            "🎉", "🎈", "🎲", "🍭", "🎡", "🎠", "🪁", "🃏", "🎭", "🌟" -> (p.playfulness + p.positivity) / 2f
            else -> 0f
        }
        return base * 5f
    }

    /**
     * Update Rule: Δ = preference × energy × sensitivity
     */
    fun processEmojiReaction(
        listener: AgentRuntime,
        senderId: String,
        emoji: String,
        fieldEnergy: Float
    ): AgentRuntime {
        val preference = calculatePreference(listener, emoji)
        val sensitivity = (listener.personality.sensitivity + 1f) / 2f // Map [-1, 1] to [0, 1]
        
        val delta = preference * (fieldEnergy / 100f) * sensitivity // Scale factor is now internal to preference

        val affinityMap = listener.socialMemory.affinity.toMutableMap()
        val currentAffinity = affinityMap.getOrDefault(senderId, 0f)
        val newAffinity = (currentAffinity + delta).coerceIn(MIN_AFFINITY, MAX_AFFINITY)
        affinityMap[senderId] = newAffinity

        return listener.copy(
            socialMemory = listener.socialMemory.copy(affinity = affinityMap),
            lastAffinityDelta = delta,
            affinityDeltaTimerMs = 2000L // Show for 2 seconds
        )
    }

    /**
     * Affinity decays slowly toward zero.
     */
    private fun decayAffinity(agent: AgentRuntime, deltaTimeMs: Long): AgentRuntime {
        val affinityMap = agent.socialMemory.affinity.toMutableMap()
        val keys = affinityMap.keys.toList()

        for (otherId in keys) {
            val current = affinityMap[otherId] ?: 0f
            if (current == 0f) continue

            val decayAmount = (AFFINITY_DECAY_RATE * (deltaTimeMs / 1000f)) * sign(current)

            val next = if (abs(current) <= abs(decayAmount)) {
                0f
            } else {
                current - decayAmount
            }

            affinityMap[otherId] = next
        }
        
        val nextTimer = (agent.affinityDeltaTimerMs - deltaTimeMs).coerceAtLeast(0L)
        
        return agent.copy(
            socialMemory = agent.socialMemory.copy(affinity = affinityMap),
            affinityDeltaTimerMs = nextTimer,
            lastAffinityDelta = if (nextTimer == 0L) 0f else agent.lastAffinityDelta
        )
    }

    fun updateRelationships(worldState: WorldState, deltaTimeMs: Long): List<AgentRuntime> {
        val agents = worldState.agents
        val emojiSignals = worldState.emojiSignals
        val socialFields = worldState.socialFields

        return agents.map { agent ->
            var updatedAgent = decayAffinity(agent, deltaTimeMs)
            
            // Process reactions to NEW emojis this tick
            // We find emojis emitted by others in the same social field
            val myField = socialFields.find { it.participants.contains(agent.id) }
            if (myField != null) {
                val nearbyEmojis = emojiSignals.filter { 
                    it.senderId != agent.id && 
                    it.senderId in myField.participants &&
                    it.timestamp >= (System.currentTimeMillis() - deltaTimeMs) // Only "new" signals
                }

                nearbyEmojis.forEach { signal ->
                    updatedAgent = processEmojiReaction(updatedAgent, signal.senderId, signal.emojiType, myField.energy)
                }
            }
            
            updatedAgent
        }
    }
}
