package com.example.utopia.data.models

import androidx.compose.ui.geometry.Offset
import com.example.utopia.util.Constants
import kotlinx.serialization.Serializable
import androidx.compose.ui.geometry.Rect
import java.util.UUID

// Constants (DEPRECATED - now in Constants.kt)
// private const val TILE_PIXEL_SIZE = 16f 

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
 * Defines the static properties of a a structure type.
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
    ROAD(PlacementBehavior.STROKE, Constants.SPRITE_TILE_SIZE, Constants.SPRITE_TILE_SIZE, blocksNavigation = false, baselineTileY = 0),
    WALL(PlacementBehavior.STROKE, Constants.SPRITE_TILE_SIZE, Constants.SPRITE_TILE_SIZE, blocksNavigation = true, baselineTileY = 0),
    HOUSE(PlacementBehavior.STAMP, 80.5f, 62.5f, blocksNavigation = true, capacity = Constants.HOUSE_CAPACITY, baselineTileY = 2),
    STORE(PlacementBehavior.STAMP, 60.4f, 46.9f, blocksNavigation = true, baselineTileY = 2),
    WORKSHOP(PlacementBehavior.STAMP, 92.6f, 62.5f, blocksNavigation = true, baselineTileY = 2),
    CASTLE(PlacementBehavior.STAMP, 72f, 76f, blocksNavigation = true, capacity = 4, baselineTileY = 4),
    PLAZA(PlacementBehavior.STAMP, 80.5f, 62.5f, blocksNavigation = true, baselineTileY = 3),
    TAVERN(PlacementBehavior.STAMP, 92.6f, 62.5f, blocksNavigation = true, capacity = 4, baselineTileY = 3), // Removed isHotspot here
    ;

    val providesSleep: Boolean get() = this == HOUSE || this == CASTLE
    val providesSocial: Boolean get() = false // Deprecated: Social is now an emergent behavior
    val providesFun: Boolean get() = this == TAVERN || this == PLAZA
    val providesStability: Boolean get() = this == STORE || this == WORKSHOP
    val providesStimulation: Boolean get() = this == STORE || this == WORKSHOP || this == CASTLE

    /** The physical width of the structure's footprint in world units. Used for collision and NavGrid baking. */
    val worldWidth: Float
        get() = (spriteWidthPx / Constants.SPRITE_TILE_SIZE) * Constants.TILE_SIZE * Constants.WORLD_SCALE

    /** The physical height of the structure's footprint in world units. Used for collision and NavGrid baking. */
    val worldHeight: Float
        get() = (spriteHeightPx / Constants.SPRITE_TILE_SIZE) * Constants.TILE_SIZE * Constants.WORLD_SCALE

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
    val customName: String? = null
) {
    fun getWorldFootprint(): Rect {
        return Rect(
            offset = Offset(x, y - type.worldHeight),
            size = androidx.compose.ui.geometry.Size(type.worldWidth, type.worldHeight)
        )
    }
}

@Serializable
enum class Gender { MALE, FEMALE }
@Serializable
data class AppearanceSpec(val skinToneId: Int, val hairColorId: Int, val tunicColorId: Int, val hairStyleId: Int, val bodyWidthMod: Float, val bodyHeightMod: Float, val hasBeard: Boolean, val hasHood: Boolean)
@Serializable
data class AgentProfile(val gender: Gender = Gender.MALE, val appearance: AppearanceSpec? = null)

@Serializable
enum class AppearanceVariant { DEFAULT } 

@Serializable
enum class AgentState { IDLE, TRAVELING, SLEEPING, SOCIALIZING, WORKING, HAVING_FUN, TRADING }
@Serializable
enum class PoiType { HOUSE, STORE, WORKSHOP, CASTLE, PLAZA, TAVERN }
@Serializable
data class POI(val id: String, val type: PoiType, val pos: SerializableOffset)
@Serializable
data class SerializableOffset(var x: Float, var y: Float) {
    fun toOffset() = Offset(x, y)
}

@Serializable
data class PersonalityVector(
    val expressiveness: Float, // [-1, 1]
    val positivity: Float,     // [-1, 1]
    val playfulness: Float,    // [-1, 1]
    val warmth: Float,         // [-1, 1]
    val sensitivity: Float     // [-1, 1]
)

@Serializable
data class Needs(
    // Homeostatic
    val sleep: Float,       // [0, 100]
    val stability: Float,   // [0, 100]
    val social: Float,      // [0, 100]
    val `fun`: Float,         // [0, 100]

    // Destabilizing
    val stimulation: Float  // [0, 100]
)

@Serializable
data class SocialMemory(
    val affinity: MutableMap<String, Float> = mutableMapOf() // AgentID -> Affinity Score [-100, 100]
)

@Serializable
data class SocialField(
    val id: String,
    val center: SerializableOffset,
    val radius: Float,
    val energy: Float,
    val participants: List<String>
)


@Serializable
data class EmojiSignal(
    val id: String = UUID.randomUUID().toString(),
    val senderId: String,
    val emojiType: String, // e.g. "HAPPY", "ANGRY", "WAVE"
    val targetAgentId: String? = null, // Used for gossip to show a portrait
    val position: SerializableOffset,
    val timestamp: Long,
    val lifeTime: Int = 2 // Ticks it lasts for visibility/processing
)

data class WorldState(
    val tiles: Array<Array<TileType>>,
    val structures: List<Structure> = emptyList(),
    val agents: List<AgentRuntime> = emptyList(),
    val props: List<PropInstance> = emptyList(),
    val pois: List<POI> = emptyList(),
    val socialFields: List<SocialField> = emptyList(),
    val emojiSignals: List<EmojiSignal> = emptyList(),
    val nextAgentId: Int = 0,
    val roadRevision: Int = 0,
    val structureRevision: Int = 0,
    val version: Int = 14
) {
    fun copyTiles(): Array<Array<TileType>> = Array(tiles.size) { x -> tiles[x].copyOf() }

    fun getTileAtWorld(pos: Offset): TileType {
        val gx = (pos.x / Constants.TILE_SIZE).toInt()
        val gy = (pos.y / Constants.TILE_SIZE).toInt()
        return if (gx in 0 until Constants.MAP_TILES_W && gy in 0 until Constants.MAP_TILES_H) {
            tiles[gx][gy]
        } else {
            TileType.GRASS_LIGHT
        }
    }
    fun getStructureAt(pos: Offset): Structure? {
        return structures.firstOrNull {
            val r = it.getWorldFootprint()
            pos.x >= r.left &&
                    pos.x <= r.right &&
                    pos.y >= r.top &&
                    pos.y <= r.bottom
        }
    }
    /**
     * Returns the structure whose influence area (lot) contains the given world position.
     * Uses the standard ownership margins (2x3 tiles).
     */
    fun getInfluencingStructure(pos: Offset): Structure? {
        return structures.firstOrNull { s ->
            val footprint = s.getWorldFootprint()
            val influenceRect = Rect(
                left = footprint.left - 2 * Constants.TILE_SIZE,
                top = footprint.top - 3 * Constants.TILE_SIZE,
                right = footprint.right + 2 * Constants.TILE_SIZE,
                bottom = footprint.bottom + 3 * Constants.TILE_SIZE
            )
            influenceRect.contains(pos)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WorldState) return false

        if (!tiles.contentDeepEquals(other.tiles)) return false
        if (structures != other.structures) return false
        if (agents != other.agents) return false
        if (props != other.props) return false
        if (pois != other.pois) return false
        if (socialFields != other.socialFields) return false
        if (emojiSignals != other.emojiSignals) return false
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
        result = 31 * result + socialFields.hashCode()
        result = 31 * result + emojiSignals.hashCode()
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
    val agents: List<AgentRuntime>,
    val props: List<PropInstance> = emptyList(),
    val pois: List<POI> = emptyList(),
    val socialFields: List<SocialField> = emptyList(),
    val emojiSignals: List<EmojiSignal> = emptyList(),
    val nextAgentId: Int = 0,
    val roadRevision: Int = 0,
    val structureRevision: Int = 0
)
