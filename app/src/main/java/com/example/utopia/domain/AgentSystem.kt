package com.example.utopia.domain

import com.example.utopia.data.models.*
import com.example.utopia.util.Constants
import kotlin.random.Random

/**
 * AgentSystem enforces a single-writer update pipeline and canonical villager contract.
 * Implements soft steering avoidance, lane bias, and frequent shift excursions.
 */
@Suppress("PropertyName")
class AgentSystem(
    internal val worldManager: WorldManager,
    internal val random: Random,
    internal val navGrid: NavGrid // Added NavGrid
) {
    internal var aiTickTimer = 0L

    internal val telemetry = AgentTelemetry()

    var numSocialBlockedByCooldown = 0
    var numSocialBlockedByPhase = 0
    var numSocialBlockedByState = 0
    var numBumps = 0
    var numSocialAttempted = 0
    var numSocialTriggers = 0
    var pathfindCountThisSecond = 0
    var roadPathsCountThisSecond = 0
    var offRoadFallbacksCountThisSecond = 0
    var numFriendChasesStarted = 0
    var numFriendChasesAborted = 0

    internal var pathfindCountThisFrame = 0
    internal var lastDebugLogMs = 0L

    // Caches
    internal val spatialHash = mutableMapOf<Int, MutableList<AgentRuntime>>()
    internal val interactedThisTick = mutableSetOf<String>()
    internal val agentLookup = mutableMapOf<String, AgentRuntime>()

    // Hotspot Tracking
    internal val tavernOccupancy = mutableMapOf<Long, Int>()
    internal val tavernReserved = mutableMapOf<Long, Int>()
    internal val agentCurrentTavernId = mutableMapOf<String, Long>()

    internal val plazaOccupancy = mutableMapOf<Long, Int>()
    internal val plazaReserved = mutableMapOf<Long, Int>()
    internal val agentCurrentPlazaId = mutableMapOf<String, Long>()

    internal var agentIndexById = IntArray(0)
    internal var agentIndexByIdCapacity = 0
    internal var lastAgentCountForMapping = -1
    internal var lastMaxShortIdForMapping = -1
    internal val INVALID_INDEX = -1

    // Social constants
    internal val SOCIAL_DURATION_MS = 5000L
    internal val BUMP_DURATION_MS = 1000L
    internal val BUMP_COOLDOWN_MS = 3000L

    // Excursion Timing Constants
    internal val EXC_MIN_WAIT = 10000L
    internal val EXC_MAX_WAIT = 45000L
    internal val EXC_POST_RETURN_COOLDOWN = 8000L

    internal val emojiMatrix = mapOf(
        (Personality.FRIENDLY to Personality.FRIENDLY) to listOf("😊", "👋", "✨"),
        (Personality.FRIENDLY to Personality.GRUMPY) to listOf("😟", "👋", "😅"),
        (Personality.GRUMPY to Personality.GRUMPY) to listOf("😠", "💢", "😒"),
        (Personality.SHY to Personality.SHY) to listOf("😶", "😳", "🍃"),
        (Personality.ENERGETIC to Personality.ENERGETIC) to listOf("⚡", "🔥", "🏃")
    )
    internal val genericEmojis = listOf("👋", "😊", "👍", "🤔", "✨")
    internal val hostileEmojis = listOf("😠", "💢", "😒", "🙄", "😤")
    internal val friendlyEmojis = listOf("😊", "👋", "✨", "🥰", "🙌")

    var townMood: Float = 0f

    // Lifecycle Guards
    internal var lastMorningDriftCycle = -1L
    internal var lastPhase: DayPhase? = null

    val workActionCounts = mutableMapOf<WorkActionType, Int>()

    fun update(deltaTimeMs: Long) {
        updateInternal(deltaTimeMs)
    }

    internal fun computePhaseContext(currentTimeSec: Float): PhaseContext {
        val simTimeMs = (currentTimeSec * 1000).toLong()
        val cycleIndex = simTimeMs / Constants.TOTAL_CYCLE_MS

        val normalizedTime = ((currentTimeSec % Constants.TOTAL_CYCLE_SEC) + Constants.TOTAL_CYCLE_SEC) % Constants.TOTAL_CYCLE_SEC
        val phaseIdx = (normalizedTime / Constants.PHASE_DURATION_SEC).toInt().coerceIn(0, 3)
        val phase = DayPhase.entries[phaseIdx]

        return PhaseContext(phase = phase, normalizedTime = normalizedTime, cycleIndex = cycleIndex)
    }
}

internal data class PhaseContext(
    val phase: DayPhase,
    val normalizedTime: Float,
    val cycleIndex: Long
)

data class VisibleBounds(
    val startX: Int,
    val startY: Int,
    val endX: Int,
    val endY: Int
)
