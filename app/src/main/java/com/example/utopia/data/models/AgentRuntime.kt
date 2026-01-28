package com.example.utopia.data.models

import androidx.compose.ui.geometry.Offset
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * PURE DATA - Authoritative State
 * No logic. Strictly immutable for deterministic simulation.
 */
@Serializable
data class AgentRuntime(
    // Core State
    val id: String,
    val name: String,
    val profile: AgentProfile,
    val position: SerializableOffset,
    val velocity: SerializableOffset,

    // Intent & Goals
    val currentIntent: String, // Example: "Wandering", "SeekingSocial", "Working"

    // Intrinsic Traits
    val personality: PersonalityVector,
    val needs: Needs,

    // Social State
    val socialMemory: SocialMemory,

    // Situation-dependent State
    @Transient
    val transientPressures: Map<String, Float> = emptyMap(),

    // Physical/Visual Helpers (Non-authoritative, but kept in state for continuity)
    @Transient val lastPosX: Float = 0f,
    @Transient val lastPosY: Float = 0f,
    @Transient val animTimerMs: Long = 0L,
    @Transient val animFrame: Int = 0,
    @Transient val facingLeft: Boolean = false,
    @Transient val dwellTimerMs: Long = 0L,
    @Transient val yieldUntilMs: Long = 0L,
    @Transient val pathTiles: List<Int> = emptyList(),
    @Transient val pathIndex: Int = 0,
    @Transient val goalPos: Offset? = null,
    @Transient val noProgressMs: Long = 0L,
    @Transient val repathCooldownUntilMs: Long = 0L,
    @Transient val intentStartTimeMs: Long = 0L,
    @Transient val lastAffinityDelta: Float = 0f,
    @Transient val affinityDeltaTimerMs: Long = 0L,
    @Transient val state: AgentState = AgentState.IDLE
) {
    val x: Float get() = position.x
    val y: Float get() = position.y
    val shortId: Int get() = id.hashCode()
    val gridX: Int get() = (x / 32f).toInt()
    val gridY: Int get() = (y / 32f).toInt()
    val collisionRadius: Float get() = 12f
}
