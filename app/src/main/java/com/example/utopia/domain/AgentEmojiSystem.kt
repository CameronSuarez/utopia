package com.example.utopia.domain

import com.example.utopia.data.models.AgentRuntime
import com.example.utopia.data.models.AgentState
import com.example.utopia.data.models.EmojiSignal
import com.example.utopia.data.models.SerializableOffset
import com.example.utopia.data.models.SocialField
import com.example.utopia.data.models.WorldState
import java.util.UUID
import kotlin.random.Random

/**
 * VII. EMOJI SYSTEM (VOTING MODEL)
 */
object AgentEmojiSystem {
    private const val BASE_EMISSION_RATE = 0.3f // Increased from 0.2f for more frequent chatter

    // Emoji table based on personality and mood
    private val MOOD_POSITIVE_EMOJIS = listOf("😊", "😄", "✨", "☀️", "🥧", "🍻")
    private val MOOD_NEGATIVE_EMOJIS = listOf("😟", "😴", "💢", "🌫️", "💧", "⛈️", "🕸️")

    private val WARM_EMOJIS = listOf("👋", "❤️", "🌻", "🕊️", "🤗")
    private val PLAYFUL_EMOJIS = listOf("\uD83E\uDD21", "\uD83D\uDC51", "🎭", "🎲", "🌟", "\uD83D\uDC14", "\uD83C\uDF1E", "🎠")
    private val CONTEMPLATIVE_EMOJIS = listOf("🤔", "👀", "🥀", "💀", "⛓️", "🧐")
    private val TRADING_EMOJIS = listOf("💰", "⚖️", "💎", "\uD83E\uDE99", "📜", "\uD83E\uDDF5", "\uD83C\uDF56", "\uD83E\uDDC0")

    /**
     * Emission Gate: chance = baseRate × Expressiveness × FieldEnergy
     */
    fun checkEmissionGate(agent: AgentRuntime, field: SocialField): Boolean {
        // Expressiveness is [-1..1]. We'll map it to [0.2..1.0] for the probability to ensure even shy agents talk.
        val expressivenessFactor = ((agent.personality.expressiveness + 1f) / 2f).coerceIn(0.2f, 1.0f)
        val fieldEnergyFactor = (field.energy / 100f).coerceIn(0.1f, 1.0f)

        val chance = BASE_EMISSION_RATE * expressivenessFactor * fieldEnergyFactor
        return Random.nextFloat() < chance
    }

    fun selectEmoji(agent: AgentRuntime): String {
        val mood = calculateMood(agent)
        val p = agent.personality

        // Use a map to store emoji -> weight
        val weightedPool = mutableMapOf<String, Float>()

        // 1. Add baseline emojis based on mood
        val baseEmojis = if (mood > 0.5f) MOOD_POSITIVE_EMOJIS else MOOD_NEGATIVE_EMOJIS
        baseEmojis.forEach { weightedPool[it] = 1.0f }

        // 2. Add personality-based weights
        // Personality scores are [-1, 1]. Map to [0, 2] for weighting.
        val warmthWeight = (p.warmth + 1f)
        val playfulWeight = (p.playfulness + 1f)
        val sensitiveWeight = (p.sensitivity + 1f)
        val positiveWeight = (p.positivity + 1f)

        WARM_EMOJIS.forEach { emoji ->
            weightedPool[emoji] = (weightedPool[emoji] ?: 0f) + warmthWeight
        }
        PLAYFUL_EMOJIS.forEach { emoji ->
            weightedPool[emoji] = (weightedPool[emoji] ?: 0f) + playfulWeight
        }
        CONTEMPLATIVE_EMOJIS.forEach { emoji ->
            weightedPool[emoji] = (weightedPool[emoji] ?: 0f) + sensitiveWeight
        }
        // Boost positive emojis if the agent has a positive personality
        if (mood > 0.5f) {
            MOOD_POSITIVE_EMOJIS.forEach { emoji ->
                weightedPool[emoji] = (weightedPool[emoji] ?: 0f) + positiveWeight
            }
        }

        // 3. Select an emoji from the weighted pool
        val totalWeight = weightedPool.values.sum()
        if (totalWeight <= 0f) {
            // Fallback if no emojis are available
            return if (mood > 0.5f) "😊" else "😟"
        }

        var randomValue = Random.nextFloat() * totalWeight
        for ((emoji, weight) in weightedPool) {
            if (randomValue < weight) {
                return emoji
            }
            randomValue -= weight
        }

        // Fallback to the last emoji in case of floating point inaccuracies
        return weightedPool.keys.last()
    }

    private fun calculateMood(agent: AgentRuntime): Float {
        return (agent.needs.sleep + agent.needs.stability + agent.needs.social + agent.needs.`fun`) / 400f
    }

    /**
     * Emission Weight: W = Preference × Mood × Personality × Context × Noise
     */
    fun calculateEmissionWeight(agent: AgentRuntime): Float {
        val mood = calculateMood(agent)
        val personalityFactor = (agent.personality.warmth + agent.personality.positivity + 2f) / 4f
        val noise = Random.nextFloat() * 0.2f + 0.9f // [0.9, 1.1]
        return mood * personalityFactor * noise
    }

    fun updateEmojiSignals(worldState: WorldState, nowMs: Long): List<EmojiSignal> {
        val nextSignals = worldState.emojiSignals.filter { signal ->
            nowMs - signal.timestamp < (signal.lifeTime * 1000L)
        }.toMutableList()

        // 1. Process active Social Fields to see who emits
        worldState.socialFields.forEach { field ->
            // PERFORMANCE/ORDER: If anyone in this field is already speaking, don't start a new emoji.
            // This prevents "blasting" and ensures one speaker at a time.
            val someoneSpeaking = nextSignals.any { it.senderId in field.participants }
            if (someoneSpeaking) return@forEach

            val potentialSpeakers = field.participants.mapNotNull { agentId ->
                worldState.agents.find { it.id == agentId }
            }.filter { checkEmissionGate(it, field) }

            if (potentialSpeakers.isNotEmpty()) {
                // Pick exactly one speaker from the pool of those who passed the gate
                val speaker = potentialSpeakers.random()
                val emoji = selectEmoji(speaker)
                nextSignals.add(
                    EmojiSignal(
                        id = UUID.randomUUID().toString(),
                        senderId = speaker.id,
                        emojiType = emoji,
                        position = SerializableOffset(speaker.x, speaker.y - 48f), // Moved up from -20f to be above head
                        timestamp = nowMs,
                        lifeTime = 2 // Reduced from 3 to allow faster turn-taking
                    )
                )
            }
        }

        // 2. Process non-social, state-based emoji emissions (e.g., Trading)
        worldState.agents.forEach { agent ->
            if (agent.state == AgentState.TRADING) {
                // Don't show a trading emoji if the agent is already part of a social conversation
                if (nextSignals.any { it.senderId == agent.id }) return@forEach

                // Use the same time-blocking logic as the "stop-and-go" wandering to decide
                // when to show an emoji. This makes it feel more natural.
                val timeBlock = nowMs / 3000L
                val seed = agent.id.hashCode().toLong() + timeBlock
                val rng = Random(seed)
                val shouldShowEmoji = rng.nextFloat() < 0.2f // 20% chance per time block

                if (shouldShowEmoji) {
                    val emoji = TRADING_EMOJIS.random(rng)
                    nextSignals.add(
                        EmojiSignal(
                            id = UUID.randomUUID().toString(),
                            senderId = agent.id,
                            emojiType = emoji,
                            position = SerializableOffset(agent.x, agent.y - 48f),
                            timestamp = nowMs,
                            lifeTime = 2
                        )
                    )
                }
            }
        }


        return nextSignals
    }
}
