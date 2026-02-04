package com.example.utopia.domain

import com.example.utopia.data.models.AgentRuntime
import com.example.utopia.data.models.EmojiSignal
import com.example.utopia.data.models.SerializableOffset
import com.example.utopia.data.models.WorldState
import com.example.utopia.util.Constants
import java.util.UUID
import kotlin.random.Random

/**
 * IX. GOSSIP
 * Triggered by strong reactions in groups ≥2.
 * Third-party affinity shifts via trust-weighted spillover.
 */
object AgentGossipSystem {

    fun processGossip(worldState: WorldState, nowMs: Long): WorldState {
        val agents = worldState.agents
        val fields = worldState.socialFields
        val newEmojiSignals = worldState.emojiSignals.toMutableList()

        // DEEP COPY agents and their social memory maps to maintain immutability
        val updatedAgents = agents.map { 
            it.copy(socialMemory = it.socialMemory.copy(affinity = it.socialMemory.affinity.toMutableMap())) 
        }.toMutableList()

        for (field in fields) {
            if (field.participants.size < 2) continue

            for (listenerId in field.participants) {
                if (Random.nextFloat() > Constants.GOSSIP_CHANCE) continue

                val listenerIndex = updatedAgents.indexOfFirst { it.id == listenerId }
                val listener = updatedAgents.getOrNull(listenerIndex) ?: continue
                val potentialSpeakers = field.participants.filter { it != listenerId }
                if (potentialSpeakers.isEmpty()) continue

                val speakerId = potentialSpeakers.random()
                val speaker = updatedAgents.find { it.id == speakerId } ?: continue

                val knownBySpeaker = speaker.socialMemory.affinity.keys.filter {
                    it != listenerId && !field.participants.contains(it)
                }
                if (knownBySpeaker.isEmpty()) continue

                val subjectId = knownBySpeaker.random()
                val speakerOpinion = speaker.socialMemory.affinity[subjectId] ?: 0f

                val trustValue = listener.socialMemory.affinity[speakerId] ?: 0f
                val trustWeight = (trustValue / 100f).coerceIn(-1.0f, 1.0f)

                val delta = speakerOpinion * trustWeight * Constants.GOSSIP_SPILLOVER_FACTOR

                val currentAffinityForSubject = listener.socialMemory.affinity[subjectId] ?: 0f
                listener.socialMemory.affinity[subjectId] = (currentAffinityForSubject + delta).coerceIn(-100f, 100f)
                updatedAgents[listenerIndex] = listener

                // EMIT GOSSIP SIGNAL
                // Only emit if no one in the field is already speaking (to respect turn-taking)
                val someoneSpeaking = newEmojiSignals.any { it.senderId in field.participants }
                if (!someoneSpeaking) {
                    newEmojiSignals.add(
                        EmojiSignal(
                            id = UUID.randomUUID().toString(),
                            senderId = speakerId,
                            emojiType = "GOSSIP", // Indicator for renderer
                            targetAgentId = subjectId,
                            position = SerializableOffset(speaker.x, speaker.y - (Constants.TILE_SIZE * 1.5f)),
                            timestamp = nowMs,
                            lifeTime = 2
                        )
                    )
                }
            }
        }

        return worldState.copy(agents = updatedAgents, emojiSignals = newEmojiSignals)
    }
}
