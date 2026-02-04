package com.example.utopia.domain

import com.example.utopia.data.models.TileType
import com.example.utopia.data.models.WorldState
import com.example.utopia.data.models.AgentRuntime

/**
 * Handles the decay and fulfillment of agent needs based on their location in the world.
 */
object AgentNeedsSystem {

    // Manifest: 1 tick = 1 second. Day length = 420 seconds.
    private const val SECONDS_PER_DAY = 420f

    // Rates are defined as "points per day"
    private const val SLEEP_DECAY_PER_DAY = 100f // An agent gets fully tired in one day
    private const val SLEEP_GAIN_PER_DAY = 400f // Fills sleep 4x faster than decay

    private const val SOCIAL_DECAY_PER_DAY = 60f
    private const val SOCIAL_GAIN_PER_DAY = 300f

    private const val FUN_DECAY_PER_DAY = 50f
    private const val FUN_GAIN_PER_DAY = 250f

    private const val STABILITY_DECAY_PER_DAY = 40f
    private const val STABILITY_GAIN_PER_DAY = 200f

    private const val STIMULATION_GAIN_PER_DAY = 70f // Stimulation grows when not in a stimulating environment
    private const val STIMULATION_DECAY_PER_DAY = 300f // Satisfied quickly

    fun updateNeeds(agent: AgentRuntime, deltaTime: Float, worldState: WorldState): AgentRuntime {
        val needs = agent.needs
        val decayFactor = deltaTime / SECONDS_PER_DAY

        val pos = agent.position.toOffset()
        val tile = worldState.getTileAtWorld(pos)
        val structure = worldState.getInfluencingStructure(pos)

        val onRoad = tile == TileType.ROAD
        val hasInfluencingStructure = structure != null

        // Fulfillments based on presence (Semantic Tiles and Structures)
        // We use hasInfluencingStructure instead of tile type because roads can be built over lots.
        val fulfillsSleep = hasInfluencingStructure && structure!!.spec.providesSleep
        // Social is fulfilled ONLY by being in a SocialField (interaction with other agents)
        val fulfillsSocial = worldState.socialFields.any { it.participants.contains(agent.id) }

        val fulfillsFun = hasInfluencingStructure && structure!!.spec.providesFun
        val fulfillsStability = hasInfluencingStructure && structure!!.spec.providesStability
        val fulfillsStimulation = (hasInfluencingStructure && structure!!.spec.providesStimulation) || onRoad

        val updatedNeeds = needs.copy(
            sleep = calculateNeed(needs.sleep, fulfillsSleep, SLEEP_GAIN_PER_DAY, SLEEP_DECAY_PER_DAY, decayFactor),
            social = calculateNeed(needs.social, fulfillsSocial, SOCIAL_GAIN_PER_DAY, SOCIAL_DECAY_PER_DAY, decayFactor),
            `fun` = calculateNeed(needs.`fun`, fulfillsFun, FUN_GAIN_PER_DAY, FUN_DECAY_PER_DAY, decayFactor),
            stability = calculateNeed(needs.stability, fulfillsStability, STABILITY_GAIN_PER_DAY, STABILITY_DECAY_PER_DAY, decayFactor),
            stimulation = calculateNeed(needs.stimulation, !fulfillsStimulation, STIMULATION_GAIN_PER_DAY, STIMULATION_DECAY_PER_DAY, decayFactor)
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
