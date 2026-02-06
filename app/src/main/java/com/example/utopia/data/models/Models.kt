package com.example.utopia.data.models

import androidx.compose.ui.geometry.Offset
import com.example.utopia.util.Constants
import kotlinx.serialization.Serializable
import androidx.compose.ui.geometry.Rect
import com.example.utopia.data.StructureRegistry
import kotlinx.serialization.Transient
import java.util.UUID

@Serializable
enum class ResourceType {
    WOOD,
    PLANKS
}

@Serializable
data class InventoryItem(val type: ResourceType, val quantity: Int)


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
 * Defines the static properties of a structure type. This is the data-driven replacement
 * for the old StructureType enum.
 */
@Serializable
data class StructureSpec(
    val id: String,
    val behavior: PlacementBehavior,
    val spriteWidthPx: Float,
    val spriteHeightPx: Float,
    val blocksNavigation: Boolean,
    val capacity: Int = 0,
    val baselineTileY: Int,
    val hitRadiusWorld: Float = 0f,
    val hitOffsetXWorld: Float = 0f,
    val hitOffsetYWorld: Float = 0f,
    val providesSleep: Boolean = false,
    val providesSocial: Boolean = false, // Deprecated: Social is now an emergent behavior
    val providesFun: Boolean = false,
    val providesStability: Boolean = false,
    val productionIntervalMs: Long = 0L,
    val maxEffectiveWorkers: Int? = null,
    val produces: Map<ResourceType, Int> = emptyMap(),
    val consumes: Map<ResourceType, Int> = emptyMap(),
    val inventoryCapacity: Map<ResourceType, Int> = emptyMap(),
    val buildCost: Map<ResourceType, Int> = emptyMap()
) {
    /** The physical width of the structure's footprint in world units. Used for collision and NavGrid baking. */
    @Transient
    val worldWidth: Float = (spriteWidthPx / Constants.SPRITE_TILE_SIZE) * Constants.TILE_SIZE

    /** The physical height of the structure's footprint in world units. Used for collision and NavGrid baking. */
    @Transient
    val worldHeight: Float = (spriteHeightPx / Constants.SPRITE_TILE_SIZE) * Constants.TILE_SIZE

    @Transient
    val baselineWorld: Float = baselineTileY * Constants.TILE_SIZE * Constants.WORLD_SCALE
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
    val typeId: String,
    val x: Float,
    val y: Float,
    val residents: List<String> = emptyList(),
    val workers: List<String> = emptyList(),
    val customName: String? = null,
    val inventory: Map<ResourceType, Int> = emptyMap(),
    val productionAccMs: Long = 0L,
    val buildProgress: Float = 0f,
    val isComplete: Boolean = true,
    val buildStarted: Boolean = false
) {
    val spec: StructureSpec by lazy { StructureRegistry.get(typeId) }

    fun getWorldFootprint(): Rect {
        return Rect(
            offset = Offset(x, y - spec.worldHeight),
            size = androidx.compose.ui.geometry.Size(spec.worldWidth, spec.worldHeight)
        )
    }

    /**
     * Returns the structure's influence area (lot) using the standard ownership margins (2x3 tiles).
     */
    fun getInfluenceRect(): Rect {
        val footprintRect = getWorldFootprint()
        return Rect(
            left = footprintRect.left - 2 * Constants.TILE_SIZE,
            top = footprintRect.top - 3 * Constants.TILE_SIZE,
            right = footprintRect.right + 2 * Constants.TILE_SIZE,
            bottom = footprintRect.bottom + 3 * Constants.TILE_SIZE
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
enum class AgentState { IDLE, TRAVELING, SLEEPING, SOCIALIZING, WORKING, HAVING_FUN }
@Serializable
enum class PoiType { HOUSE, SAWMILL, CASTLE, PLAZA, TAVERN, LUMBERJACK_HUT }
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
    val `fun`: Float         // [0, 100]
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

data class PoiIndex(
    val structureRevision: Int = -1,
    val inventoryRevision: Int = -1,
    val pois: List<POI> = emptyList(),
    val incompleteStructures: List<Structure> = emptyList(),
    val readyConstructionSites: List<Structure> = emptyList(),
    val sourcesByResource: Map<ResourceType, List<Structure>> = emptyMap(),
    val sinksByResource: Map<ResourceType, List<Structure>> = emptyMap(),
    val constructionSitesNeeding: Map<ResourceType, List<Structure>> = emptyMap()
)

data class WorldState(
    val tiles: Array<Array<TileType>>,
    val structures: List<Structure> = emptyList(),
    @Transient val structureGrid: Array<Array<String?>> = Array(Constants.MAP_TILES_W) { Array(Constants.MAP_TILES_H) { null } },
    val agents: List<AgentRuntime> = emptyList(),
    val props: List<PropInstance> = emptyList(),
    val pois: List<POI> = emptyList(),
    @Transient val poiIndex: PoiIndex = PoiIndex(),
    val socialFields: List<SocialField> = emptyList(),
    val emojiSignals: List<EmojiSignal> = emptyList(),
    val nextAgentId: Int = 0,
    val roadRevision: Int = 0,
    val structureRevision: Int = 0,
    val inventoryRevision: Int = 0,
    val version: Int = 15
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
    /**
     * Returns the structure whose influence area (lot) contains the given world position.
     * Uses the standard ownership margins (2x3 tiles).
     */
    fun getInfluencingStructure(pos: Offset): Structure? {
        return structures.firstOrNull { s ->
            s.getInfluenceRect().contains(pos)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WorldState) return false

        if (!tiles.contentDeepEquals(other.tiles)) return false
        if (!structureGrid.contentDeepEquals(other.structureGrid)) return false
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
        result = 31 * result + structureGrid.contentDeepHashCode()
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
    val version: Int = 15,
    val tiles: List<List<TileType>>,
    val structures: List<Structure>,
    val agents: List<AgentRuntime>,
    val props: List<PropInstance> = emptyList(),
    val pois: List<POI> = emptyList(),
    val socialFields: List<SocialField> = emptyList(),
    val emojiSignals: List<EmojiSignal> = emptyList(),
    val nextAgentId: Int = 0,
    val roadRevision: Int = 0,
    val structureRevision: Int = 0,
    val inventoryRevision: Int = 0
)
