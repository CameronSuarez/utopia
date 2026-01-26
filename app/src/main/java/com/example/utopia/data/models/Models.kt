package com.example.utopia.data.models

import androidx.compose.ui.geometry.Offset
import com.example.utopia.util.Constants
import kotlinx.serialization.Serializable

// Constants
private const val TILE_PIXEL_SIZE = 16f // The dimension of one tile in pixels, for converting sprite art to world units.

@Serializable
enum class TileType {
    GRASS_LIGHT, GRASS_DARK, GRASS_DARK_TUFT, ROAD, WALL, BUILDING_FOOTPRINT, PLAZA, PROP_BLOCKED, BUILDING_LOT, BUILDING_SOLID;

    val isGrass: Boolean get() = this == GRASS_LIGHT || this == GRASS_DARK || this == GRASS_DARK_TUFT
}

@Serializable
enum class PlacementBehavior { STAMP, STROKE }

@Serializable
data class GridOffset(val x: Int, val y: Int)

/**
 * Defines the static properties of a structure type.
 *
 * ARCHITECTURAL CONTRACT:
 * - Anchor: A structure's (x, y) position is its BOTTOM-LEFT corner in world space.
 * - Footprint Authority: `spriteWidthPx` and `spriteHeightPx` define the authoritative physical
 *   footprint of the structure, in PIXELS. This data must correspond to the full visual bounds of the sprite asset.
 *   This is the rectangle used for NavGrid blocking and physical interaction.
 * - Influence Area: The area a building claims for spacing is defined at the placement layer (WorldManager)
 *   and is intentionally larger than the physical footprint.
 */
@Serializable
enum class StructureType(
    val behavior: PlacementBehavior,
    // TODO: A developer must replace these placeholder values with the TRUE pixel dimensions from the sprite assets.
    val spriteWidthPx: Float,
    val spriteHeightPx: Float,
    val blocksNavigation: Boolean,
    val jobSlots: Int = 0,
    val capacity: Int = 0,
    val isHotspot: Boolean = false,
    val baselineTileY: Int,
    val hitRadiusWorld: Float = 0f,
    val hitOffsetXWorld: Float = 0f,
    val hitOffsetYWorld: Float = 0f
) {
    ROAD(PlacementBehavior.STROKE, TILE_PIXEL_SIZE, TILE_PIXEL_SIZE, blocksNavigation = false, baselineTileY = 0),
    WALL(PlacementBehavior.STROKE, TILE_PIXEL_SIZE, TILE_PIXEL_SIZE, blocksNavigation = true, baselineTileY = 0),
    HOUSE(PlacementBehavior.STAMP, 52f, 40f, blocksNavigation = true, capacity = Constants.HOUSE_CAPACITY, baselineTileY = 2),
    STORE(PlacementBehavior.STAMP, 44f, 41f, blocksNavigation = true, jobSlots = 2, baselineTileY = 2),
    WORKSHOP(PlacementBehavior.STAMP, 50f, 42f, blocksNavigation = true, jobSlots = 2, baselineTileY = 2),
    CASTLE(PlacementBehavior.STAMP, 72f, 76f, blocksNavigation = true, jobSlots = 4, baselineTileY = 4),
    PLAZA(PlacementBehavior.STAMP, 48f, 32f, blocksNavigation = true, baselineTileY = 3),
    TAVERN(PlacementBehavior.STAMP, 56f, 50f, blocksNavigation = true, jobSlots = 2, isHotspot = true, capacity = 4, baselineTileY = 3);

    /** The physical width of the structure's footprint in world units. Used for collision and NavGrid baking. */
    val worldWidth: Float
        get() = (spriteWidthPx / TILE_PIXEL_SIZE) * Constants.TILE_SIZE * Constants.WORLD_SCALE

    /** The physical height of the structure's footprint in world units. Used for collision and NavGrid baking. */
    val worldHeight: Float
        get() = (spriteHeightPx / TILE_PIXEL_SIZE) * Constants.TILE_SIZE * Constants.WORLD_SCALE

    val baselineWorld: Float
        get() = baselineTileY * Constants.TILE_SIZE * Constants.WORLD_SCALE
}

/**
 * Represents an instance of a structure in the world.
 *
 * ANCHOR CONTRACT: The (x, y) coordinate represents the BOTTOM-LEFT anchor of the
 * structure's sprite in world space. All physical and visual bounds are calculated
 * relative to this point.
 */
@Serializable
data class Structure(
    val id: String,
    val type: StructureType,
    val x: Float,
    val y: Float,
    val residents: List<String> = emptyList(),
    val workers: List<String> = emptyList(),
    var customName: String? = null
)

@Serializable
enum class DayPhase { NIGHT, MORNING, AFTERNOON, EVENING }
@Serializable
enum class Personality(val id: Int) { FRIENDLY(0), GRUMPY(1), SHY(2), ENERGETIC(3), CALM(4), QUIRKY(5), LONELY(6), SNOBBY(7), BRAVE(8), CLUMSY(9), ROMANTIC(10), MISCHIEVOUS(11) }
@Serializable
enum class Gender { MALE, FEMALE }
@Serializable
data class AppearanceSpec(val skinToneId: Int, val hairColorId: Int, val tunicColorId: Int, val hairStyleId: Int, val bodyWidthMod: Float, val bodyHeightMod: Float, val hasBeard: Boolean, val hasHood: Boolean)
@Serializable
data class SocialMemoryEntry(val otherAgentId: String, val firstInteractionTick: Long, val lastInteractionTick: Long)
@Serializable
data class AgentProfile(val gender: Gender = Gender.MALE, var appearance: AppearanceSpec? = null, val socialMemory: List<SocialMemoryEntry> = emptyList())
@Serializable
enum class AppearanceVariant { DEFAULT, STORE_WORKER, WORKSHOP_WORKER, CASTLE_GUARD, TAVERN_KEEPER }
@Serializable
enum class DailyIntent { WANDER, GO_WORK, GO_HOME }
@Serializable
enum class GoalIntentType { IDLE, GO_HOME, GO_WORK, BUSY_AT_WORK, VISIT_FRIEND, VISIT_PLAZA, VISIT_TAVERN, VISIT_STORE, WANDER_NEAR_HOME }
@Serializable
enum class AgentState { IDLE, TRAVELING, AT_WORK, SLEEPING, SOCIALIZING, EXCURSION_VISITING }
@Serializable
enum class PoiType { HOUSE, STORE, WORKSHOP, CASTLE, PLAZA, TAVERN }
@Serializable
enum class WorkActionType { WANDER_NEARBY, VISIT_PLAZA, VISIT_OTHER_STORE }
@Serializable
enum class PrimaryGoal { WORK_SHIFT, OFF_DUTY }
@Serializable
data class POI(val id: String, val type: PoiType, val pos: SerializableOffset)
@Serializable
data class SerializableOffset(val x: Float, val y: Float) {
    fun toOffset() = Offset(x, y)
}
@Serializable
data class SoftMemory(var lastSocialPartnerId: String? = null, var lastVisitedBuildingId: String? = null, var recentMoodBias: Int = 0, var lastDecayDay: Int = -1, var lastTavernVisitCycle: Int = -1)

class AgentRuntime(
    val id: String,
    val shortId: Int,
    var profile: AgentProfile,
    var x: Float,
    var y: Float,
    var gridX: Int,
    var gridY: Int,
    var name: String = "Villager",
    var personality: Personality = Personality.CALM,
    var appearance: AppearanceVariant = AppearanceVariant.DEFAULT,
    var state: AgentState = AgentState.IDLE,
    var previousState: AgentState = AgentState.IDLE,
    var homeId: String? = null,
    var jobId: String? = null,
    var emoji: String? = null,
    var lastSocialTime: Long = 0,
    var lastBumpTimeMs: Long = 0,
    var lastPoiId: String? = null,
    var currentIntent: DailyIntent = DailyIntent.WANDER,
    var goalPos: Offset? = null,
    var pathTiles: List<Int> = emptyList(),
    var pathIndex: Int = 0,
    var repathCooldownUntilMs: Long = 0,
    var noProgressMs: Long = 0,
    var lastPosX: Float = 0f,
    var lastPosY: Float = 0f,
    var blockedByAgent: Boolean = false,
    var facingLeft: Boolean = false,
    var animFrame: Int = 0,
    var animTimerMs: Long = 0,
    var animationSpeed: Float = 0f,
    var phaseStaggerMs: Long = 0,
    var dwellTimerMs: Long = 0,
    var socialStartMs: Long = 0,
    var socialEndMs: Long = 0,
    var socialPartnerId: String? = null,
    var socialNextEmojiAtMs: Long = 0,
    var socialEmojiUntilMs: Long = 0,
    var nextExcursionAtMs: Long = 0,
    var excursionEndsAtMs: Long = 0,
    var excursionTargetBuildingId: String? = null,
    var excursionReservedType: String = "NONE",
    var excursionReservedBuildingId: String? = null,
    var lastAffinityDelta: Byte = 0,
    var affinityDeltaUiUntilMs: Long = 0L,
    var memory: SoftMemory = SoftMemory(),
    var workAnimKind: Int = (shortId % 4),
    var nextWorkMoveAtMs: Long = 0,
    var workAnimEndTimeMs: Long = 0,
    var workAnchorPos: Offset? = null,
    var goalBlockedUntilMs: Long = 0,
    var isAtWorkAnchor: Boolean = false,
    var activeWorkAction: WorkActionType? = null,
    var workActionEndsAtMs: Long = 0,
    var nextWorkDecisionAtMs: Long = 0,
    var pausedWorkActionRemainingMs: Long = 0,
    var pausedWorkActionType: WorkActionType? = null,
    var pausedWasHidden: Boolean = false,
    var pausedNextWorkDecisionDeltaMs: Long = 0,
    var yieldUntilMs: Long = 0,
    val collisionRadius: Float = Constants.AGENT_COLLISION_RADIUS,
    var reservedSlot: GridOffset? = null,
    var reservedStructureId: String? = null,
    var detourPos: Offset? = null,
    var resumePos: Offset? = null,
    var avoidanceSide: Float = 0f, // New: 0f (uncommitted), 1f or -1f
    var avoidanceCommitUntilMs: Long = 0, // New: Time until side is mutable again
    // --- New Goal Architecture ---
    var primaryGoal: PrimaryGoal = PrimaryGoal.OFF_DUTY,
    var primaryGoalEndsMs: Long = 0L,
    var returnIntent: GoalIntentType? = null,
    // Unified Intent State (Goals only)
    var goalIntentType: GoalIntentType = GoalIntentType.IDLE,
    var goalIntentEndsMs: Long = 0L,
    var goalIntentTargetId: String? = null,
    var goalIntentFailCount: Int = 0,
    var goalIntentSlotIndex: Int = -1,
    var goalIntentFriendCooldownUntilMs: Long = 0L,
    // Debugging fields
    var debugDesiredStep: Offset? = null,
    var debugSeparationStep: Offset? = null,
    var debugAdjustedStep: Offset? = null
)

@Serializable
data class Agent(
    val id: String,
    val shortId: Int,
    var profile: AgentProfile,
    var name: String,
    var personality: Personality,
    var appearance: AppearanceVariant,
    var serPosition: SerializableOffset,
    var gridX: Int,
    var gridY: Int,
    var state: AgentState,
    var previousState: AgentState,
    var homeId: String?,
    var jobId: String?,
    var emoji: String?,
    var lastSocialTime: Long,
    var lastBumpTimeMs: Long,
    var lastPoiId: String?,
    var goalPos: SerializableOffset? = null,
    var pathTiles: List<Int> = emptyList(),
    var pathIndex: Int = 0,
    var repathCooldownUntilMs: Long = 0,
    var noProgressMs: Long = 0,
    var lastPosX: Float = 0f,
    var lastPosY: Float = 0f,
    var phaseStaggerMs: Long,
    var dwellTimerMs: Long,
    var socialStartMs: Long,
    var socialEndMs: Long,
    var socialPartnerId: String?,
    var socialNextEmojiAtMs: Long,
    var socialEmojiUntilMs: Long,
    var nextExcursionAtMs: Long,
    var excursionEndsAtMs: Long,
    var excursionTargetBuildingId: String?,
    var excursionReservedType: String,
    var excursionReservedBuildingId: String?,
    var lastAffinityDelta: Byte,
    var affinityDeltaUiUntilMs: Long,
    var currentIntent: DailyIntent,
    var facingLeft: Boolean,
    var animFrame: Int,
    var memory: SoftMemory,
    var workAnimKind: Int,
    var nextWorkMoveAtMs: Long,
    var workAnimEndTimeMs: Long,
    var serWorkAnchorPos: SerializableOffset?,
    var goalBlockedUntilMs: Long,
    var isAtWorkAnchor: Boolean,
    var activeWorkAction: WorkActionType?,
    var workActionEndsAtMs: Long,
    var nextWorkDecisionAtMs: Long,
    var pausedWorkActionRemainingMs: Long,
    var pausedWorkActionType: WorkActionType?,
    var pausedWasHidden: Boolean,
    var pausedNextWorkDecisionDeltaMs: Long,
    var yieldUntilMs: Long = 0,
    val collisionRadius: Float = Constants.AGENT_COLLISION_RADIUS,
    var reservedSlot: GridOffset? = null,
    var reservedStructureId: String? = null,
    var serDetourPos: SerializableOffset? = null,
    var serResumePos: SerializableOffset? = null,
    var avoidanceSide: Float = 0f, // New: 0f (uncommitted), 1f or -1f
    var avoidanceCommitUntilMs: Long = 0, // New: Time until side is mutable again
    // --- New Goal Architecture ---
    var primaryGoal: PrimaryGoal = PrimaryGoal.OFF_DUTY,
    var primaryGoalEndsMs: Long = 0L,
    var returnIntent: GoalIntentType? = null,
    // Unified Intent State (Goals only)
    var goalIntentType: GoalIntentType = GoalIntentType.IDLE,
    var goalIntentEndsMs: Long = 0L,
    var goalIntentTargetId: String? = null,
    var goalIntentFailCount: Int = 0,
    var goalIntentSlotIndex: Int = -1,
    var goalIntentFriendCooldownUntilMs: Long = 0L,
    // Debugging fields
    var serDebugDesiredStep: SerializableOffset? = null,
    var serDebugSeparationStep: SerializableOffset? = null,
    var serDebugAdjustedStep: SerializableOffset? = null
)

fun AgentRuntime.toAgent(): Agent {
    return Agent(
        id = id,
        shortId = shortId,
        profile = profile,
        name = name,
        personality = personality,
        appearance = appearance,
        serPosition = SerializableOffset(x, y),
        gridX = gridX,
        gridY = gridY,
        state = state,
        previousState = previousState,
        homeId = homeId,
        jobId = jobId,
        emoji = emoji,
        lastSocialTime = lastSocialTime,
        lastBumpTimeMs = lastBumpTimeMs,
        lastPoiId = lastPoiId,
        goalPos = goalPos?.let { SerializableOffset(it.x, it.y) },
        pathTiles = pathTiles,
        pathIndex = pathIndex,
        repathCooldownUntilMs = repathCooldownUntilMs,
        noProgressMs = noProgressMs,
        lastPosX = lastPosX,
        lastPosY = lastPosY,
        phaseStaggerMs = phaseStaggerMs,
        dwellTimerMs = dwellTimerMs,
        socialStartMs = socialStartMs,
        socialEndMs = socialEndMs,
        socialPartnerId = socialPartnerId,
        socialNextEmojiAtMs = socialNextEmojiAtMs,
        socialEmojiUntilMs = socialEmojiUntilMs,
        nextExcursionAtMs = nextExcursionAtMs,
        excursionEndsAtMs = excursionEndsAtMs,
        excursionTargetBuildingId = excursionTargetBuildingId,
        excursionReservedType = excursionReservedType,
        excursionReservedBuildingId = excursionReservedBuildingId,
        lastAffinityDelta = lastAffinityDelta,
        affinityDeltaUiUntilMs = affinityDeltaUiUntilMs,
        currentIntent = currentIntent,
        facingLeft = facingLeft,
        animFrame = animFrame,
        memory = memory,
        workAnimKind = workAnimKind,
        nextWorkMoveAtMs = nextWorkMoveAtMs,
        workAnimEndTimeMs = workAnimEndTimeMs,
        serWorkAnchorPos = workAnchorPos?.let { SerializableOffset(it.x, it.y) },
        goalBlockedUntilMs = goalBlockedUntilMs,
        isAtWorkAnchor = isAtWorkAnchor,
        activeWorkAction = activeWorkAction,
        workActionEndsAtMs = workActionEndsAtMs,
        nextWorkDecisionAtMs = nextWorkDecisionAtMs,
        pausedWorkActionRemainingMs = pausedWorkActionRemainingMs,
        pausedWorkActionType = pausedWorkActionType,
        pausedWasHidden = pausedWasHidden,
        pausedNextWorkDecisionDeltaMs = pausedNextWorkDecisionDeltaMs,
        yieldUntilMs = yieldUntilMs,
        collisionRadius = collisionRadius,
        reservedSlot = reservedSlot,
        reservedStructureId = reservedStructureId,
        serDetourPos = detourPos?.let { SerializableOffset(it.x, it.y) },
        serResumePos = resumePos?.let { SerializableOffset(it.x, it.y) },
        avoidanceSide = avoidanceSide,
        avoidanceCommitUntilMs = avoidanceCommitUntilMs,
        // --- New Goal Architecture ---
        primaryGoal = primaryGoal,
        primaryGoalEndsMs = primaryGoalEndsMs,
        returnIntent = returnIntent,
        // Unified Intent State (Goals only)
        goalIntentType = goalIntentType,
        goalIntentEndsMs = goalIntentEndsMs,
        goalIntentTargetId = goalIntentTargetId,
        goalIntentFailCount = goalIntentFailCount,
        goalIntentSlotIndex = goalIntentSlotIndex,
        goalIntentFriendCooldownUntilMs = goalIntentFriendCooldownUntilMs,
        // Debugging fields
        serDebugDesiredStep = debugDesiredStep?.let { SerializableOffset(it.x, it.y) },
        serDebugSeparationStep = debugSeparationStep?.let { SerializableOffset(it.x, it.y) },
        serDebugAdjustedStep = debugAdjustedStep?.let { SerializableOffset(it.x, it.y) }
    )
}

fun Agent.toRuntime(): AgentRuntime {
    return AgentRuntime(
        id = id,
        shortId = shortId,
        profile = profile,
        x = serPosition.x,
        y = serPosition.y,
        gridX = gridX,
        gridY = gridY,
        name = name,
        personality = personality,
        appearance = appearance,
        state = state,
        previousState = previousState,
        homeId = homeId,
        jobId = jobId,
        emoji = emoji,
        lastSocialTime = lastSocialTime,
        lastBumpTimeMs = lastBumpTimeMs,
        lastPoiId = lastPoiId,
        goalPos = goalPos?.toOffset(),
        pathTiles = pathTiles,
        pathIndex = pathIndex,
        repathCooldownUntilMs = repathCooldownUntilMs,
        noProgressMs = noProgressMs,
        lastPosX = lastPosX,
        lastPosY = lastPosY,
        phaseStaggerMs = phaseStaggerMs,
        dwellTimerMs = dwellTimerMs,
        socialStartMs = socialStartMs,
        socialEndMs = socialEndMs,
        socialPartnerId = socialPartnerId,
        socialNextEmojiAtMs = socialNextEmojiAtMs,
        socialEmojiUntilMs = socialEmojiUntilMs,
        nextExcursionAtMs = nextExcursionAtMs,
        excursionEndsAtMs = excursionEndsAtMs,
        excursionTargetBuildingId = excursionTargetBuildingId,
        excursionReservedType = excursionReservedType,
        excursionReservedBuildingId = excursionReservedBuildingId,
        lastAffinityDelta = lastAffinityDelta,
        affinityDeltaUiUntilMs = affinityDeltaUiUntilMs,
        currentIntent = currentIntent,
        facingLeft = facingLeft,
        animFrame = animFrame,
        memory = memory,
        workAnimKind = workAnimKind,
        nextWorkMoveAtMs = nextWorkMoveAtMs,
        workAnimEndTimeMs = workAnimEndTimeMs,
        workAnchorPos = serWorkAnchorPos?.toOffset(),
        goalBlockedUntilMs = goalBlockedUntilMs,
        isAtWorkAnchor = isAtWorkAnchor,
        activeWorkAction = activeWorkAction,
        workActionEndsAtMs = workActionEndsAtMs,
        nextWorkDecisionAtMs = nextWorkDecisionAtMs,
        pausedWorkActionRemainingMs = pausedWorkActionRemainingMs,
        pausedWorkActionType = pausedWorkActionType,
        pausedWasHidden = pausedWasHidden,
        pausedNextWorkDecisionDeltaMs = pausedNextWorkDecisionDeltaMs,
        yieldUntilMs = yieldUntilMs,
        collisionRadius = collisionRadius,
        reservedSlot = reservedSlot,
        reservedStructureId = reservedStructureId,
        detourPos = serDetourPos?.toOffset(),
        resumePos = serResumePos?.toOffset(),
        avoidanceSide = avoidanceSide,
        avoidanceCommitUntilMs = avoidanceCommitUntilMs,
        // --- New Goal Architecture ---
        primaryGoal = primaryGoal,
        primaryGoalEndsMs = primaryGoalEndsMs,
        returnIntent = returnIntent,
        // Unified Intent State (Goals only)
        goalIntentType = goalIntentType,
        goalIntentEndsMs = goalIntentEndsMs,
        goalIntentTargetId = goalIntentTargetId,
        goalIntentFailCount = goalIntentFailCount,
        goalIntentSlotIndex = goalIntentSlotIndex,
        goalIntentFriendCooldownUntilMs = goalIntentFriendCooldownUntilMs,
        // Debugging fields
        debugDesiredStep = serDebugDesiredStep?.toOffset(),
        debugSeparationStep = serDebugSeparationStep?.toOffset(),
        debugAdjustedStep = serDebugAdjustedStep?.toOffset()
    )
}

data class WorldState(
    val tiles: Array<Array<TileType>>,
    val structures: List<Structure> = emptyList(),
    val agents: List<AgentRuntime> = emptyList(),
    val props: List<PropInstance> = emptyList(),
    val pois: List<POI> = emptyList(),
    val relationships: MutableMap<Long, Byte> = mutableMapOf(),
    val timeOfDay: Float = 0.0f,
    val nextAgentId: Int = 0,
    val roadRevision: Int = 0,
    val structureRevision: Int = 0,
    val version: Int = 14
) {
    fun copyTiles(): Array<Array<TileType>> = Array(tiles.size) { x -> tiles[x].copyOf() }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WorldState) return false

        if (!tiles.contentDeepEquals(other.tiles)) return false
        if (structures != other.structures) return false
        if (agents != other.agents) return false
        if (props != other.props) return false
        if (pois != other.pois) return false
        if (relationships != other.relationships) return false
        if (timeOfDay != other.timeOfDay) return false
        if (nextAgentId != other.nextAgentId) return false
        if (roadRevision != other.roadRevision) return false
        if (structureRevision != other.structureRevision) return false
        if (version != other.version) return false

        return true
    }

    override fun hashCode(): Int {
        var result = tiles.contentDeepHashCode()
        result = 31 * result + structures.hashCode()
        result = 31 * result + agents.hashCode()
        result = 31 * result + props.hashCode()
        result = 31 * result + pois.hashCode()
        result = 31 * result + relationships.hashCode()
        result = 31 * result + timeOfDay.hashCode()
        result = 31 * result + nextAgentId
        result = 31 * result + roadRevision
        result = 31 * result + structureRevision
        result = 31 * result + version
        return result
    }
}

@Serializable
data class WorldStateData(
    val version: Int = 14,
    val tiles: List<List<TileType>>,
    val structures: List<Structure>,
    val agents: List<Agent>,
    val props: List<PropInstance> = emptyList(),
    val pois: List<POI> = emptyList(),
    val relationships: Map<Long, Byte> = emptyMap(),
    val timeOfDay: Float,
    val nextAgentId: Int = 0,
    val roadRevision: Int = 0,
    val structureRevision: Int = 0
)
