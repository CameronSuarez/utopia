package com.example.utopia.domain

import com.example.utopia.data.models.WorldState
import com.example.utopia.data.models.AgentRuntime
import com.example.utopia.util.Constants

/**
 * Handles the decay and fulfillment of agent needs based on their location in the world.
 */
object AgentNeedsSystem {

    fun updateNeeds(agent: AgentRuntime, deltaTime: Float, worldState: WorldState): AgentRuntime {
        val needs = agent.needs
        val decayFactor = deltaTime / Constants.NEEDS_SECONDS_PER_DAY

        val pos = agent.position.toOffset()
        val structure = worldState.getInfluencingStructure(pos)

        val hasInfluencingStructure = structure != null

        // Fulfillments based on presence (Semantic Tiles and Structures)
        // We use hasInfluencingStructure instead of tile type because roads can be built over lots.
        val fulfillsSleep = hasInfluencingStructure && structure.spec.providesSleep
        // Social is fulfilled ONLY by being in a SocialField (interaction with other agents)
        val fulfillsSocial = worldState.socialFields.any { it.participants.contains(agent.id) }

        val fulfillsFun = hasInfluencingStructure && structure.spec.providesFun
        val fulfillsStability = hasInfluencingStructure && structure.spec.providesStability
        val updatedNeeds = needs.copy(
            sleep = calculateNeed(
                needs.sleep,
                fulfillsSleep,
                Constants.NEEDS_SLEEP_GAIN_PER_DAY,
                Constants.NEEDS_SLEEP_DECAY_PER_DAY,
                decayFactor
            ),
            social = calculateNeed(
                needs.social,
                fulfillsSocial,
                Constants.NEEDS_SOCIAL_GAIN_PER_DAY,
                Constants.NEEDS_SOCIAL_DECAY_PER_DAY,
                decayFactor
            ),
            `fun` = calculateNeed(
                needs.`fun`,
                fulfillsFun,
                Constants.NEEDS_FUN_GAIN_PER_DAY,
                Constants.NEEDS_FUN_DECAY_PER_DAY,
                decayFactor
            ),
            stability = calculateNeed(
                needs.stability,
                fulfillsStability,
                Constants.NEEDS_STABILITY_GAIN_PER_DAY,
                Constants.NEEDS_STABILITY_DECAY_PER_DAY,
                decayFactor
            )
        )
        return agent.copy(needs = updatedNeeds)
    }

    private fun calculateNeed(
        current: Float,
        isFulfilling: Boolean,
        gainRate: Float,
        decayRate: Float,
        factor: Float
    ): Float {
        val delta = if (isFulfilling) gainRate * factor else -decayRate * factor
        return (current + delta).coerceIn(0f, 100f)
    }
}
