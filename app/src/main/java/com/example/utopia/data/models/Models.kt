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
    val capacity: Int = 0,
    // Removed: isHotspot
    val baselineTileY: Int,
    val hitRadiusWorld: Float = 0f,
    val hitOffsetXWorld: Float = 0f,
    val hitOffsetYWorld: Float = 0f
) {
    ROAD(PlacementBehavior.STROKE, TILE_PIXEL_SIZE, TILE_PIXEL_SIZE, blocksNavigation = false, baselineTileY = 0),
    WALL(PlacementBehavior.STROKE, TILE_PIXEL_SIZE, TILE_PIXEL_SIZE, blocksNavigation = true, baselineTileY = 0),
    HOUSE(PlacementBehavior.STAMP, 52f, 40f, blocksNavigation = true, capacity = Constants.HOUSE_CAPACITY, baselineTileY = 2),
    STORE(PlacementBehavior.STAMP, 44f, 41f, blocksNavigation = true, baselineTileY = 2),
    WORKSHOP(PlacementBehavior.STAMP, 50f, 42f, blocksNavigation = true, baselineTileY = 2),
    CASTLE(PlacementBehavior.STAMP, 72f, 76f, blocksNavigation = true, capacity = 4, baselineTileY = 4),
    PLAZA(PlacementBehavior.STAMP, 48f, 32f, blocksNavigation = true, baselineTileY = 3),
    TAVERN(PlacementBehavior.STAMP, 56f, 50f, blocksNavigation = true, capacity = 4, baselineTileY = 3); // Removed isHotspot here

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
    var customName: String? = null
)

// Removed: @Serializable enum class DayPhase { NIGHT, MORNING, AFTERNOON, EVENING }
// Removed: @Serializable enum class Personality
@Serializable
enum class Gender { MALE, FEMALE }
@Serializable
data class AppearanceSpec(val skinToneId: Int, val hairColorId: Int, val tunicColorId: Int, val hairStyleId: Int, val bodyWidthMod: Float, val bodyHeightMod: Float, val hasBeard: Boolean, val hasHood: Boolean)
// Removed: @Serializable data class SocialMemoryEntry
// Removed: @Serializable data class AgentProfile
@Serializable
data class AgentProfile(val gender: Gender = Gender.MALE, var appearance: AppearanceSpec? = null)

// Simplified appearance variants (unused workers variants removed)
@Serializable
enum class AppearanceVariant { DEFAULT } 

// Simplified Agent State: only transport/idle/sleep remain
@Serializable
enum class AgentState { IDLE, TRAVELING, SLEEPING }
@Serializable
enum class PoiType { HOUSE, STORE, WORKSHOP, CASTLE, PLAZA, TAVERN }
@Serializable
data class POI(val id: String, val type: PoiType, val pos: SerializableOffset)
@Serializable
data class SerializableOffset(val x: Float, val y: Float) {
    fun toOffset() = Offset(x, y)
}
// Simplified SoftMemory (now a regular class)
@Serializable
class SoftMemory()

class AgentRuntime(
    val id: String,
    val shortId: Int,
    var profile: AgentProfile,
    var x: Float,
    var y: Float,
    var gridX: Int,
    var gridY: Int,
    var name: String = "Villager",
    // Removed Personality
    var appearance: AppearanceVariant = AppearanceVariant.DEFAULT,
    var state: AgentState = AgentState.IDLE,
    var previousState: AgentState = AgentState.IDLE,
    var homeId: String? = null,
    var emoji: String? = null,
    var lastPoiId: String? = null,
    var goalPos: Offset? = null,
    var pathTiles: List<Int> = emptyList(),
    var pathIndex: Int = 0,
    var repathCooldownUntilMs: Long = 0,
    var noProgressMs: Long = 0,
    var lastPosX: Float = 0f,
    var lastPosY: Float = 0f,
    // Removed: blockedByAgent
    var facingLeft: Boolean = false,
    var animFrame: Int = 0,
    var animTimerMs: Long = 0,
    // Removed: animationSpeed
    var phaseStaggerMs: Long = 0,
    var dwellTimerMs: Long = 0,
    // Removed: workAnimKind
    var workAnimEndTimeMs: Long = 0,
    var goalBlockedUntilMs: Long = 0,
    var yieldUntilMs: Long = 0,
    val collisionRadius: Float = Constants.AGENT_COLLISION_RADIUS,
    var reservedSlot: GridOffset? = null,
    var reservedStructureId: String? = null,
    var detourPos: Offset? = null,
    var resumePos: Offset? = null,
    var avoidanceSide: Float = 0f,
    var avoidanceCommitUntilMs: Long = 0,
    var memory: SoftMemory = SoftMemory()
)

@Serializable
data class Agent(
    val id: String,
    val shortId: Int,
    var profile: AgentProfile,
    var name: String,
    // Removed Personality
    var appearance: AppearanceVariant,
    var serPosition: SerializableOffset,
    var gridX: Int,
    var gridY: Int,
    var state: AgentState,
    var previousState: AgentState,
    var homeId: String?,
    var emoji: String?,
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
    var facingLeft: Boolean,
    var animFrame: Int,
    var memory: SoftMemory,
    // Removed: workAnimKind
    var workAnimEndTimeMs: Long,
    var goalBlockedUntilMs: Long,
    var yieldUntilMs: Long = 0,
    val collisionRadius: Float = Constants.AGENT_COLLISION_RADIUS,
    var reservedSlot: GridOffset? = null,
    var reservedStructureId: String? = null,
    var serDetourPos: SerializableOffset? = null,
    var serResumePos: SerializableOffset? = null,
    var avoidanceSide: Float = 0f,
    var avoidanceCommitUntilMs: Long = 0,
)

fun AgentRuntime.toAgent(): Agent {
    return Agent(
        id = id,
        shortId = shortId,
        profile = profile, // Profile is now minimal, no deep copy needed
        name = name,
        // Removed Personality
        appearance = appearance,
        serPosition = SerializableOffset(x, y),
        gridX = gridX,
        gridY = gridY,
        state = state,
        previousState = previousState,
        homeId = homeId,
        emoji = emoji,
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
        facingLeft = facingLeft,
        animFrame = animFrame,
        memory = memory, // SoftMemory is now SoftMemory()
        workAnimEndTimeMs = workAnimEndTimeMs,
        goalBlockedUntilMs = goalBlockedUntilMs,
        yieldUntilMs = yieldUntilMs,
        collisionRadius = collisionRadius,
        reservedSlot = reservedSlot,
        reservedStructureId = reservedStructureId,
        serDetourPos = detourPos?.let { SerializableOffset(it.x, it.y) },
        serResumePos = resumePos?.let { SerializableOffset(it.x, it.y) },
        avoidanceSide = avoidanceSide,
        avoidanceCommitUntilMs = avoidanceCommitUntilMs,
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
        // Removed Personality
        appearance = appearance,
        state = state,
        previousState = previousState,
        homeId = homeId,
        emoji = emoji,
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
        facingLeft = facingLeft,
        animFrame = animFrame,
        memory = memory, // SoftMemory is now SoftMemory()
        workAnimEndTimeMs = workAnimEndTimeMs,
        goalBlockedUntilMs = goalBlockedUntilMs,
        yieldUntilMs = yieldUntilMs,
        collisionRadius = collisionRadius,
        reservedSlot = reservedSlot,
        reservedStructureId = reservedStructureId,
        detourPos = serDetourPos?.toOffset(),
        resumePos = serResumePos?.toOffset(),
        avoidanceSide = avoidanceSide,
        avoidanceCommitUntilMs = avoidanceCommitUntilMs,
    )
}

data class WorldState(
    val tiles: Array<Array<TileType>>,
    val structures: List<Structure> = emptyList(),
    val agents: List<AgentRuntime> = emptyList(),
    val props: List<PropInstance> = emptyList(),
    val pois: List<POI> = emptyList(),
    // Removed: relationships - Keep world state completely inert
    // Removed: timeOfDay
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
        // Removed: relationships
        // Removed: timeOfDay
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
        // Removed: relationships
        // Removed: timeOfDay
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
    // Removed: relationships
    // Removed: timeOfDay
    val nextAgentId: Int = 0,
    val roadRevision: Int = 0,
    val structureRevision: Int = 0
)
