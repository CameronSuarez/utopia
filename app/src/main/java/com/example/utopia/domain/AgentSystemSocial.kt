package com.example.utopia.domain

import com.example.utopia.data.models.AgentProfile
import com.example.utopia.data.models.AgentRuntime
import com.example.utopia.data.models.AgentState
import com.example.utopia.data.models.DayPhase
import com.example.utopia.data.models.SocialMemoryEntry
import com.example.utopia.data.models.StructureType
import com.example.utopia.data.models.WorldState
import com.example.utopia.util.Constants
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

// --- RELATIONSHIP BANDS ---

enum class RelationshipBand { ENEMY, NEUTRAL, FRIEND }

internal const val REL_FRIEND_THRESHOLD = 25
internal const val REL_ENEMY_THRESHOLD = -25
internal const val REL_MIN = -100
internal const val REL_MAX = 100

fun isFriend(score: Int): Boolean = score >= REL_FRIEND_THRESHOLD
fun isEnemy(score: Int): Boolean = score <= REL_ENEMY_THRESHOLD

// --- QUERY HELPERS ---

fun AgentSystem.getFriendsOf(agent: AgentRuntime, state: WorldState): List<AgentRuntime> {
    return state.agents.filter { other ->
        if (other.id == agent.id) return@filter false
        val key = getRelKey(agent.shortId, other.shortId)
        val score = state.relationships[key]?.toInt() ?: 0
        isFriend(score)
    }
}

fun AgentSystem.pickTopFriend(agent: AgentRuntime, state: WorldState): AgentRuntime? {
    val friends = getFriendsOf(agent, state)
    if (friends.isEmpty()) return null
    
    return friends.minWithOrNull(compareByDescending<AgentRuntime> { other ->
        val key = getRelKey(agent.shortId, other.shortId)
        state.relationships[key]?.toInt() ?: 0
    }.thenBy { other ->
        val dx = other.x - agent.x
        val dy = other.y - agent.y
        dx * dx + dy * dy
    })
}

// --- SOCIAL TRIGGERS ---

internal fun AgentSystem.handleSocialTrigger(
    agent: AgentRuntime,
    matched: MutableSet<String>,
    nowMs: Long,
    normTime: Float,
    phase: DayPhase
) {
    val myIdx = agentIndexFromShortId(agent.shortId)
    if (myIdx == INVALID_INDEX) return

    val myTavernIdHash = agentCurrentTavernId[agent.id]
    val isInTavernInEvening = phase == DayPhase.EVENING && myTavernIdHash != null

    val effectiveCooldown = if (isInTavernInEvening) {
        (Constants.SOCIAL_COOLDOWN_MS * Constants.TAVERN_SOCIAL_COOLDOWN_MULT).toLong()
    } else {
        Constants.SOCIAL_COOLDOWN_MS
    }

    if (nowMs - agent.lastSocialTime < effectiveCooldown) {
        numSocialBlockedByCooldown++
        return
    }

    if ((Constants.PHASE_DURATION_SEC - (normTime % Constants.PHASE_DURATION_SEC)) < 10f) {
        numSocialBlockedByPhase++
        return
    }

    val cX = (agent.x / (Constants.TILE_SIZE * Constants.SPATIAL_HASH_CELL_SIZE)).toInt()
    val cY = (agent.y / (Constants.TILE_SIZE * Constants.SPATIAL_HASH_CELL_SIZE)).toInt()

    val nearPlaza = worldManager.worldState.value.structures.any {
        it.type == StructureType.PLAZA &&
            abs(it.x + it.type.worldWidth / 2 - agent.x) < 120f &&
            abs(it.y + it.type.worldHeight / 2 - agent.y) < 120f
    }

    val baseTriggerDistSq = (if (nearPlaza) (32f + 16f).pow(2) else 1024f) * 2.25f

    for (dx in -1..1) for (dy in -1..1) {
        spatialHash[((cX + dx) shl 16) or ((cY + dy) and 0xFFFF)]?.forEach { other ->
            if (other.id != agent.id && !matched.contains(other.id)) {
                val otherIdx = agentIndexFromShortId(other.shortId)
                if (otherIdx == INVALID_INDEX) return@forEach

                if (other.state == AgentState.SOCIALIZING || other.state == AgentState.SLEEPING) {
                    numSocialBlockedByState++
                    return@forEach
                }

                val otherTavernIdHash = agentCurrentTavernId[other.id]
                val bothInSameTavern = isInTavernInEvening && otherTavernIdHash == myTavernIdHash

                val otherEffectiveCooldown = if (bothInSameTavern) {
                    (Constants.SOCIAL_COOLDOWN_MS * Constants.TAVERN_SOCIAL_COOLDOWN_MULT).toLong()
                } else {
                    Constants.SOCIAL_COOLDOWN_MS
                }

                if (nowMs - other.lastSocialTime < otherEffectiveCooldown) {
                    numSocialBlockedByCooldown++
                    return@forEach
                }

                val dxD = other.x - agent.x
                val dyD = other.y - agent.y
                val dSq = dxD * dxD + dyD * dyD

                val triggerDistSq = if (bothInSameTavern) {
                    baseTriggerDistSq * Constants.TAVERN_SOCIAL_RANGE_MULT.pow(2)
                } else {
                    baseTriggerDistSq
                }

                if (dSq < triggerDistSq) {
                    val relKey = getRelKey(agent.shortId, other.shortId)
                    val rel = worldManager.worldState.value.relationships[relKey]?.toInt() ?: 0

                    var triggerChance = 0.5f
                    if (isFriend(rel)) triggerChance *= 1.25f
                    else if (isEnemy(rel)) triggerChance *= 0.5f

                    if (bothInSameTavern) {
                        triggerChance = (triggerChance + Constants.TAVERN_SOCIAL_TRIGGER_BONUS_PROB).coerceIn(0f, 1f)
                    }

                    val myPlazaIdHash = agentCurrentPlazaId[agent.id]
                    val otherPlazaIdHash = agentCurrentPlazaId[other.id]
                    val bothInSamePlaza = myPlazaIdHash != null && myPlazaIdHash == otherPlazaIdHash

                    val effectiveBumpCooldown = if (bothInSamePlaza) {
                        (BUMP_COOLDOWN_MS * Constants.PLAZA_BUMP_COOLDOWN_MULT).toLong()
                    } else {
                        BUMP_COOLDOWN_MS
                    }

                    val isCongested = dSq < 400f
                    val canBump = nowMs - agent.lastBumpTimeMs > effectiveBumpCooldown &&
                        nowMs - other.lastBumpTimeMs > effectiveBumpCooldown

                    if (isCongested && canBump) {
                        startSocial(agent, other, nowMs, BUMP_DURATION_MS)
                        agent.lastBumpTimeMs = nowMs
                        other.lastBumpTimeMs = nowMs
                        numBumps++
                        matched.add(agent.id)
                        matched.add(other.id)
                        return
                    } else if (dSq >= 400f) {
                        numSocialAttempted++
                        if (random.nextFloat() < triggerChance) {
                            val duration = if (bothInSameTavern) {
                                SOCIAL_DURATION_MS + Constants.TAVERN_SOCIAL_DURATION_BONUS_MS
                            } else {
                                SOCIAL_DURATION_MS
                            }
                            startSocial(agent, other, nowMs, duration)
                            numSocialTriggers++
                            matched.add(agent.id)
                            matched.add(other.id)
                            return
                        }
                    }
                }
            }
        }
    }
}

private fun startSocial(a: AgentRuntime, b: AgentRuntime, nowMs: Long, dur: Long) {
    if (a.activeWorkAction != null) {
        a.pausedWorkActionType = a.activeWorkAction
        a.pausedWorkActionRemainingMs = max(0, a.workActionEndsAtMs - nowMs)
        a.pausedNextWorkDecisionDeltaMs = max(0, a.nextWorkDecisionAtMs - nowMs)
    }
    if (b.activeWorkAction != null) {
        b.pausedWorkActionType = b.activeWorkAction
        b.pausedWorkActionRemainingMs = max(0, b.workActionEndsAtMs - nowMs)
        b.pausedNextWorkDecisionDeltaMs = max(0, b.nextWorkDecisionAtMs - nowMs)
    }

    a.previousState = a.state; b.previousState = b.state
    a.state = AgentState.SOCIALIZING; b.state = AgentState.SOCIALIZING
    a.socialPartnerId = b.id; b.socialPartnerId = a.id
    a.socialStartMs = nowMs; b.socialStartMs = nowMs
    a.socialEndMs = nowMs + dur; b.socialEndMs = nowMs + dur
    a.lastSocialTime = nowMs; b.lastSocialTime = nowMs
    a.emoji = null; b.emoji = null
    a.socialEmojiUntilMs = 0; b.socialEmojiUntilMs = 0
}

internal fun AgentSystem.updateSocializing(agent: AgentRuntime, nowMs: Long, phase: DayPhase) {
    if (nowMs >= agent.socialEndMs) {
        endSocial(agent, nowMs, phase)
        return
    }
    val partner = agentLookup[agent.socialPartnerId ?: ""] ?: return

    val dxToPartner = partner.x - agent.x
    if (abs(dxToPartner) > 0.1f) {
        agent.facingLeft = dxToPartner < 0f
    }

    val elapsed = nowMs - agent.socialStartMs
    val isBump = (agent.socialEndMs - agent.socialStartMs) <= BUMP_DURATION_MS

    if (isBump) {
        if (agent.emoji == null) {
            agent.emoji = "🚶"
            agent.socialEmojiUntilMs = agent.socialEndMs
        }
        return
    }

    val turnMs = 2000L
    val turnIdx = (elapsed / turnMs).toInt()
    val msInTurn = elapsed % turnMs

    val amISpeakerA = agent.shortId < partner.shortId
    val isMyTurn = if (amISpeakerA) turnIdx % 2 == 0 else turnIdx % 2 != 0

    if (isMyTurn && msInTurn < 1000L) {
        if (agent.emoji == null) {
            agent.emoji = pickInteractionEmoji(agent, partner, turnIdx)
            agent.socialEmojiUntilMs = nowMs + 1000L
        }
    } else if (nowMs >= agent.socialEmojiUntilMs) {
        agent.emoji = null
    }
}

internal fun AgentSystem.endSocial(agent: AgentRuntime, nowMs: Long, phase: DayPhase) {
    val partner = agentLookup[agent.socialPartnerId ?: ""]
    if (partner != null) {
        if (agent.shortId < partner.shortId) {
            if (partner.socialPartnerId == agent.id && partner.state == AgentState.SOCIALIZING) {

                val isBump = (agent.socialEndMs - agent.socialStartMs) <= BUMP_DURATION_MS

                if (!isBump) {
                    agent.memory.lastSocialPartnerId = partner.id
                    partner.memory.lastSocialPartnerId = agent.id
                    recordSocialMemory(agent, partner, nowMs)

                    val key = getRelKey(agent.shortId, partner.shortId)
                    val current = worldManager.worldState.value.relationships[key]?.toInt() ?: 0

                    val valence = determineValence(agent.emoji, partner.emoji)

                    val myTavernIdHash = agentCurrentTavernId[agent.id]
                    val otherTavernIdHash = agentCurrentTavernId[partner.id]
                    val bothInSameTavern = phase == DayPhase.EVENING && myTavernIdHash != null && myTavernIdHash == otherTavernIdHash

                    val positiveProb = if (bothInSameTavern) {
                        when (valence) {
                            1 -> Constants.TAVERN_FRIENDLY_PLUS1_CHANCE
                            -1 -> 1f - Constants.TAVERN_HOSTILE_MINUS1_CHANCE
                            else -> Constants.TAVERN_MIXED_PLUS1_CHANCE
                        }
                    } else {
                        when (valence) {
                            1 -> 0.7f
                            -1 -> 0.3f
                            else -> 0.5f
                        }
                    }

                    val saturationBias = if (current > 0) -0.1f * current / REL_MAX else if (current < 0) -0.1f * current / REL_MIN else 0f
                    val finalPosProb = (positiveProb + saturationBias).coerceIn(0.1f, 0.9f)

                    val delta = if (random.nextFloat() < finalPosProb) 5 else -5

                    val outcome = applyRelationshipDelta(agent.shortId, partner.shortId, delta)
                    if (outcome.result == RelationshipDeltaResult.APPLIED) {
                        val appliedCurrent = outcome.current ?: 0
                        val next = outcome.next ?: 0

                        val affinityDelta = (next - appliedCurrent).toByte()
                        if (nowMs >= agent.affinityDeltaUiUntilMs || agent.lastAffinityDelta != affinityDelta) {
                            agent.lastAffinityDelta = affinityDelta
                            agent.affinityDeltaUiUntilMs = nowMs + 800L
                        }
                        if (nowMs >= partner.affinityDeltaUiUntilMs || partner.lastAffinityDelta != affinityDelta) {
                            partner.lastAffinityDelta = affinityDelta
                            partner.affinityDeltaUiUntilMs = nowMs + 800L
                        }

                        if (delta > 0) {
                            agent.memory.recentMoodBias = (agent.memory.recentMoodBias + 5).coerceAtMost(20)
                            partner.memory.recentMoodBias = (partner.memory.recentMoodBias + 5).coerceAtMost(20)
                        } else {
                            agent.memory.recentMoodBias = (agent.memory.recentMoodBias - 5).coerceAtLeast(-20)
                            partner.memory.recentMoodBias = (partner.memory.recentMoodBias - 5).coerceAtLeast(-20)
                        }
                    }
                }
            }
        }
    }

    agent.state = agent.previousState; agent.emoji = null; agent.socialPartnerId = null; agent.dwellTimerMs = 500L
    agent.socialEmojiUntilMs = 0

    if (agent.pausedWorkActionType != null && agent.pausedWorkActionRemainingMs > 0) {
        agent.activeWorkAction = agent.pausedWorkActionType
        agent.workActionEndsAtMs = nowMs + agent.pausedWorkActionRemainingMs
        agent.nextWorkDecisionAtMs = nowMs + agent.pausedNextWorkDecisionDeltaMs

        agent.pausedWorkActionType = null
        agent.pausedWorkActionRemainingMs = 0
        agent.pausedNextWorkDecisionDeltaMs = 0
    }
}

internal fun AgentSystem.recordSocialMemory(a: AgentRuntime, b: AgentRuntime, nowMs: Long) {
    fun updateEntry(profile: AgentProfile, otherId: String): Pair<AgentProfile, Boolean> {
        val existing = profile.socialMemory.firstOrNull { it.otherAgentId == otherId }
        val nextMemory = if (existing == null) {
            profile.socialMemory + SocialMemoryEntry(
                otherAgentId = otherId,
                firstInteractionTick = nowMs,
                lastInteractionTick = nowMs
            )
        } else if (existing.lastInteractionTick != nowMs) {
            profile.socialMemory.map { entry ->
                if (entry.otherAgentId == otherId) entry.copy(lastInteractionTick = nowMs) else entry
            }
        } else {
            profile.socialMemory
        }
        val didChange = nextMemory !== profile.socialMemory
        val nextProfile = if (didChange) profile.copy(socialMemory = nextMemory) else profile
        return nextProfile to didChange
    }

    val currentState = worldManager.worldState.value
    val agents = currentState.agents
    val aIndex = agents.indexOfFirst { it.id == a.id }
    val bIndex = agents.indexOfFirst { it.id == b.id }
    if (aIndex == -1 || bIndex == -1) return

    val (aProfile, aChanged) = updateEntry(agents[aIndex].profile, b.id)
    val (bProfile, bChanged) = updateEntry(agents[bIndex].profile, a.id)
    if (!aChanged && !bChanged) return

    if (aChanged) agents[aIndex].profile = aProfile
    if (bChanged) agents[bIndex].profile = bProfile

    worldManager.setWorldState(currentState.copy(agents = agents.toList()))
}

internal fun AgentSystem.applyRelationshipDelta(aShortId: Int, bShortId: Int, delta: Int): RelationshipDeltaOutcome {
    if (delta == 0) return RelationshipDeltaOutcome(RelationshipDeltaResult.SKIP_DELTA_ZERO)
    if (aShortId < 0 || bShortId < 0) return RelationshipDeltaOutcome(RelationshipDeltaResult.SKIP_INVALID_ID)
    if (aShortId == bShortId) return RelationshipDeltaOutcome(RelationshipDeltaResult.SKIP_SELF)

    val low = min(aShortId, bShortId)
    val high = max(aShortId, bShortId)

    val key = getRelKey(low, high)
    val currentState = worldManager.worldState.value
    val current = currentState.relationships[key]?.toInt() ?: 0
    val next = (current + delta).coerceIn(REL_MIN, REL_MAX)
    if (next == current) {
        return RelationshipDeltaOutcome(RelationshipDeltaResult.SKIP_OUT_OF_RANGE_CLAMPED_TO_ZERO, current, next)
    }
    val updatedRelationships = currentState.relationships.toMutableMap().apply {
        this[key] = next.toByte()
    }
    worldManager.setWorldState(currentState.copy(relationships = updatedRelationships))

    return RelationshipDeltaOutcome(RelationshipDeltaResult.APPLIED, current, next)
}

internal fun AgentSystem.determineValence(emojiA: String?, emojiB: String?): Int {
    val emojis = listOfNotNull(emojiA, emojiB)
    if (emojis.isEmpty()) return 0
    val friendlyCount = emojis.count { it in friendlyEmojis }
    val hostileCount = emojis.count { it in hostileEmojis }
    return if (friendlyCount > hostileCount) 1 else if (hostileCount > friendlyCount) -1 else 0
}

internal fun AgentSystem.pickInteractionEmoji(a: AgentRuntime, b: AgentRuntime, turnIdx: Int): String {
    val relKey = getRelKey(a.shortId, b.shortId)
    val rel = worldManager.worldState.value.relationships[relKey]?.toInt() ?: 0

    val moodShift = (townMood * 0.1f)
    val bias = if (random.nextFloat() < 0.5f + moodShift) 1 else -1

    val options = when {
        isFriend(rel) || (rel >= 0 && bias > 0) -> friendlyEmojis
        isEnemy(rel) || (rel <= 0 && bias < 0) -> hostileEmojis
        else -> emojiMatrix[a.personality to b.personality] ?: emojiMatrix[b.personality to a.personality]
    } ?: genericEmojis

    return options[java.util.Random((a.shortId + b.shortId + turnIdx).toLong()).nextInt(options.size)]
}

private fun getRelKey(a: Int, b: Int): Long {
    val low = min(a, b).toLong()
    val high = max(a, b).toLong()
    return (low shl 32) or high
}

internal enum class RelationshipDeltaResult {
    APPLIED,
    SKIP_DELTA_ZERO,
    SKIP_INVALID_ID,
    SKIP_SELF,
    SKIP_OUT_OF_RANGE_CLAMPED_TO_ZERO
}

internal data class RelationshipDeltaOutcome(
    val result: RelationshipDeltaResult,
    val current: Int? = null,
    val next: Int? = null
)
