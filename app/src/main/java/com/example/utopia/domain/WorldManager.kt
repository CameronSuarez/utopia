package com.example.utopia.domain

import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.example.utopia.data.models.*
import com.example.utopia.ui.generateAppearanceSpec
import com.example.utopia.util.Constants
import java.util.UUID
import kotlin.random.Random

/**
 * Manages the world state, including tiles, structures, and agents.
 * This is the single source of truth for world mutation.
 */
private const val WORLD_SEED = 12345L
// Defines the margin (in tiles) around a building's physical footprint to determine its influence/ownership area.
private const val OWNERSHIP_MARGIN_X = 2 // Horizontal margin
private const val OWNERSHIP_MARGIN_Y = 3 // Vertical margin (larger to ensure space at the front/back)


class WorldManager(private val navGrid: NavGrid) {
    val random = Random(WORLD_SEED)
    private val maleNames = listOf(
        "Alaric", "Cedric", "Eldred", "Godwin", "Ivor", "Kenric", "Merrick", "Osric", "Stig", "Ulf",
        "Wulf", "Zoric", "Bram", "Ewan", "Quinn"
    )
    private val femaleNames = listOf(
        "Beatrice", "Drusilla", "Faye", "Hilda", "Joan", "Lulu", "Nell", "Pippa", "Rowena", "Tilda",
        "Vera", "Xenia", "Yrsa", "Cora", "Dara"
    )

    private val initialTiles = Array(Constants.MAP_TILES_W) { x ->
        Array(Constants.MAP_TILES_H) { y -> sampleGrassType(x, y) }
    }
    private val initialProps = generateInitialProps(initialTiles)

    private val _worldState = mutableStateOf(
        WorldState(
            tiles = initialTiles,
            structures = emptyList(),
            agents = emptyList(),
            props = initialProps,
            timeOfDay = Constants.PHASE_DURATION_SEC // Start at MORNING (120s)
        )
    )
    val worldState: State<WorldState> = _worldState

    var staticLayerId by mutableIntStateOf(0)
        private set

    // Dirty Rect Tracking for NavGrid optimization
    private var pendingDirtyRect: Rect? = null

    private fun unionDirtyRect(rect: Rect) {
        pendingDirtyRect = pendingDirtyRect?.let { a ->
            Rect(
                left = minOf(a.left, rect.left),
                top = minOf(a.top, rect.top),
                right = maxOf(a.right, rect.right),
                bottom = maxOf(a.bottom, rect.bottom)
            )
        } ?: rect
    }

    /**
     * Consumes and returns the accumulated dirty rect, resetting it to null.
     */
    fun consumeDirtyRect(): Rect? {
        val rect = pendingDirtyRect
        pendingDirtyRect = null
        return rect
    }

    init {
        val tiles = _worldState.value.tiles
        assert(tiles.size == Constants.MAP_TILES_W) { "Tiles array width must match MAP_TILES_W" }
        assert(tiles[0].size == Constants.MAP_TILES_H) { "Tiles array height must match MAP_TILES_H" }
    }

    internal fun setWorldState(newState: WorldState) {
        _worldState.value = newState
    }
    
    fun toData(): WorldStateData {
        val state = _worldState.value
        return WorldStateData(
            structures = state.structures,
            agents = state.agents.map { it.toAgent() },
            relationships = state.relationships,
            timeOfDay = state.timeOfDay,
            nextAgentId = state.nextAgentId,
            tiles = state.tiles.map { it.toList() }
        )
    }

    fun loadData(data: WorldStateData) {
        val loadedTiles = Array(Constants.MAP_TILES_W) { x ->
            Array(Constants.MAP_TILES_H) { y -> data.tiles.getOrNull(x)?.getOrNull(y) ?: sampleGrassType(x, y) }
        }
        val loadedProps = generateInitialProps(loadedTiles)

        _worldState.value = WorldState(
            tiles = loadedTiles,
            structures = data.structures,
            agents = data.agents.map { it.toRuntime() },
            props = loadedProps,
            relationships = data.relationships.toMutableMap(),
            timeOfDay = data.timeOfDay,
            nextAgentId = data.nextAgentId,
            pois = generatePOIs(WorldState(structures = data.structures, tiles = loadedTiles))
        )
        staticLayerId++
        pendingDirtyRect = null // Force full NavGrid rebuild on load
    }

    private fun generateInitialProps(tiles: Array<Array<TileType>>): List<PropInstance> {
        val props = mutableListOf<PropInstance>()
        val tilesize = Constants.TILE_SIZE

        for (x in 0 until Constants.MAP_TILES_W) {
            for (y in 0 until Constants.MAP_TILES_H) {
                if (!tiles[x][y].isGrass) continue

                val seed = (x * 73856093) xor (y * 19349663) xor WORLD_SEED.toInt()
                val tileRng = Random(seed.toLong())

                val propType = when (tileRng.nextFloat()) {
                    in 0.0f..0.01f -> PropType.TREE_1
                    else -> null
                }

                if (propType != null) {
                    val anchorX = (x + 0.5f) * tilesize
                    val anchorY = (y + 1.0f) * tilesize

                    props.add(
                        PropInstance(
                            id = UUID.randomUUID().toString(),
                            type = propType,
                            homeTileX = x,
                            homeTileY = y,
                            anchorX = anchorX,
                            anchorY = anchorY
                        )
                    )
                }
            }
        }
        return props
    }

    private fun sampleGrassType(x: Int, y: Int): TileType {
        val scale = 0.08f
        val n = noise2D(x * scale, y * scale)
        val isLight = n > 0.5f

        if (isLight) return TileType.GRASS_LIGHT

        val seed = (x * 73856093) xor (y * 19349663)
        val rng = Random(seed.toLong())
        return if (rng.nextFloat() < 0.05f) TileType.GRASS_DARK_TUFT else TileType.GRASS_DARK
    }

    private fun noise2D(x: Float, y: Float): Float {
        val ix = x.toInt()
        val iy = y.toInt()
        val fx = x - ix
        val fy = y - iy

        fun hash(ix: Int, iy: Int): Float {
            val seed = (ix * 73856093) xor (iy * 19349663)
            return Random(seed.toLong()).nextFloat()
        }

        val v00 = hash(ix, iy)
        val v10 = hash(ix + 1, iy)
        val v01 = hash(ix, iy + 1)
        val v11 = hash(ix + 1, iy + 1)

        val nx0 = v00 * (1 - fx) + v10 * fx
        val nx1 = v01 * (1 - fx) + v11 * fy
        return nx0 * (1 - fy) + nx1 * fy
    }

    fun incrementVersion() {
        val currentState = _worldState.value
        _worldState.value = currentState.copy(version = currentState.version + 1)
        staticLayerId++
    }

    /**
     * The authoritative function for adding a stamp-based structure to the world.
     * This function enforces the architectural contract for world mutation.
     */
    private fun bakeStructureToWorld(
        currentState: WorldState,
        structure: Structure
    ): WorldState {
        // Mark the new physical footprint area as dirty for NavGrid update.
        unionDirtyRect(Rect(offset = Offset(structure.x, structure.y - structure.type.worldHeight), size = Size(structure.type.worldWidth, structure.type.worldHeight)))

        val newTiles = currentState.copyTiles()

        // --- Step 1: Define Influence Area (Lot) using Margins ---
        val footprintRect = Rect(structure.x, structure.y - structure.type.worldHeight, structure.x + structure.type.worldWidth, structure.y)
        val influenceRect = Rect(
            left = footprintRect.left - OWNERSHIP_MARGIN_X * Constants.TILE_SIZE,
            top = footprintRect.top - OWNERSHIP_MARGIN_Y * Constants.TILE_SIZE,
            right = footprintRect.right + OWNERSHIP_MARGIN_X * Constants.TILE_SIZE,
            bottom = footprintRect.bottom + OWNERSHIP_MARGIN_Y * Constants.TILE_SIZE
        )
        val lotMinX = (influenceRect.left / Constants.TILE_SIZE).toInt()
        val lotMinY = (influenceRect.top / Constants.TILE_SIZE).toInt()
        val lotMaxX = (influenceRect.right / Constants.TILE_SIZE).toInt()
        val lotMaxY = (influenceRect.bottom / Constants.TILE_SIZE).toInt()


        // --- Step 2: Clear Props within Influence Area ---
        val newProps = currentState.props.filterNot { prop ->
            getPropFootprintTiles(prop).any { (propTileX, propTileY) ->
                val isInside = propTileX in lotMinX..lotMaxX && propTileY in lotMinY..lotMaxY
                if (isInside) {
                    unionDirtyRect(Rect(
                        propTileX * Constants.TILE_SIZE,
                        propTileY * Constants.TILE_SIZE,
                        (propTileX + 1) * Constants.TILE_SIZE,
                        (propTileY + 1) * Constants.TILE_SIZE
                    ))
                }
                isInside
            }
        }

        // --- Step 3: Claim Lot Tiles and Apply Physical Footprint ---
        markLot(newTiles, lotMinX, lotMinY, lotMaxX, lotMaxY)
        val footprintTile = if (structure.type == StructureType.PLAZA) TileType.PLAZA else TileType.BUILDING_SOLID
        markFootprint(newTiles, structure, footprintTile)

        // --- Final Step: Update World State ---
        return currentState.copy(
            structures = currentState.structures + structure,
            tiles = newTiles,
            props = newProps,
            structureRevision = currentState.structureRevision + 1,
            version = currentState.version + 1
        )
    }

    fun tryPlaceStamp(type: StructureType, x: Float, y: Float, existingStructure: Structure? = null): String? {
        if (!canPlaceInternal(_worldState.value, type, x, y, isMoving = existingStructure != null)) return null

        val newStructure = existingStructure?.copy(x = x, y = y) ?: Structure(UUID.randomUUID().toString(), type, x, y)
        
        val newState = bakeStructureToWorld(_worldState.value, newStructure)

        _worldState.value = newState.copy(pois = generatePOIs(newState))
        staticLayerId++

        if (type == StructureType.HOUSE && existingStructure == null) {
            spawnVillagersForHouse(newStructure)
        }

        if (type.jobSlots > 0) {
            assignJobs()
        }

        validateInvariants()
        return newStructure.id
    }

    private fun getPropFootprintTiles(prop: PropInstance): List<Pair<Int, Int>> {
        val x = prop.homeTileX
        val y = prop.homeTileY
        return listOf(
            x to y,
            x to y - 1
        ).filter { it.second >= 0 }
    }

    private fun spawnVillagersForHouse(house: Structure) {
        val count = Constants.HOUSE_CAPACITY
        // Spawn agents just outside the bottom-center of the house's footprint.
        val spawnX = house.x + house.type.worldWidth / 2
        val spawnY = house.y + Constants.TILE_SIZE * 2f // Use house.y (bottom) as the reference.

        var firstVillagerName: String? = null

        repeat(count) {
            val gender = if (random.nextFloat() < 0.5f) Gender.MALE else Gender.FEMALE
            val namePool = if (gender == Gender.MALE) maleNames else femaleNames
            val name = namePool.random(random)
            if (it == 0) firstVillagerName = name

            val jx = spawnX + (random.nextFloat() * 20f - 10f)
            val jy = spawnY + (random.nextFloat() * 10f)
            spawnAgentWithExplicitName(house, jx, jy, name, gender)
        }

        firstVillagerName?.let {
            house.customName = "${it}'s House"
        }
    }

    private fun spawnAgentWithExplicitName(home: Structure, wx: Float, wy: Float, explicitName: String, gender: Gender) {
        val currentState = _worldState.value
        if (currentState.agents.size >= Constants.MAX_AGENTS) return

        var finalWx = wx
        var finalWy = wy
        var gx = (wx / Constants.TILE_SIZE).toInt().coerceIn(0, Constants.MAP_TILES_W - 1)
        var gy = (wy / Constants.TILE_SIZE).toInt().coerceIn(0, Constants.MAP_TILES_H - 1)

        if (!navGrid.isWalkable(gx, gy)) {
            val nudgedPos = Pathfinding.nudgeOutOfObstacle(gx, gy, navGrid)
            if (nudgedPos != null) {
                gx = nudgedPos.first
                gy = nudgedPos.second
                finalWx = (gx + 0.5f) * Constants.TILE_SIZE
                finalWy = (gy + 0.5f) * Constants.TILE_SIZE
            } else {
                Log.e("WorldManager", "Could not find a walkable spawn point for agent $explicitName")
                return
            }
        }

        val personality = Personality.entries.random(random)
        val appearance = generateAppearanceSpec(gender, random)
        val profile = AgentProfile(gender = gender, appearance = appearance)

        val now = System.currentTimeMillis()
        val agent = AgentRuntime(
            id = UUID.randomUUID().toString(),
            shortId = currentState.nextAgentId,
            profile = profile,
            name = explicitName,
            personality = personality,
            x = finalWx,
            y = finalWy,
            gridX = gx,
            gridY = gy,
            homeId = home.id,
            phaseStaggerMs = ((random.nextFloat() * 2f - 1f) * Constants.PHASE_STAGGER_MAX_MS).toLong(),
            lastSocialTime = now - (Constants.SOCIAL_COOLDOWN_MS - 10000L)
        )

        val newStructures = currentState.structures.map { s ->
            if (s.id == home.id) s.copy(residents = s.residents + agent.id) else s
        }

        _worldState.value = currentState.copy(
            agents = currentState.agents + agent,
            structures = newStructures,
            nextAgentId = currentState.nextAgentId + 1
        )
        staticLayerId++
        assignJobs()
    }

    /** Parallel authority for stroke-based structures (Roads, Walls). */
    private fun bakeStrokeSegmentToWorld(currentState: WorldState, structure: Structure): WorldState {
        val gx = structure.topLeftGridX
        val gy = structure.topLeftGridY
        
        val newTiles = currentState.copyTiles()
        
        // 1. Clear props on the single tile.
        val newProps = currentState.props.filterNot { prop ->
            val isInside = prop.homeTileX == gx && (prop.homeTileY == gy || prop.homeTileY == gy + 1)
            if (isInside) {
                unionDirtyRect(Rect(
                    (prop.homeTileX * Constants.TILE_SIZE),
                    (prop.homeTileY * Constants.TILE_SIZE),
                    ((prop.homeTileX + 1) * Constants.TILE_SIZE),
                    ((prop.homeTileY + 1) * Constants.TILE_SIZE)
                ))
            }
            isInside
        }

        // 2. Apply the tile type.
        applyStructureToTiles(newTiles, structure)

        // 3. Mark dirty for NavGrid update.
        unionDirtyRect(Rect(structure.x, structure.y - structure.type.worldHeight, structure.x + structure.type.worldWidth, structure.y))

        return currentState.copy(
            structures = currentState.structures + structure,
            tiles = newTiles,
            props = newProps
        )
    }

    fun tryPlaceStroke(type: StructureType, tiles: List<Pair<Int, Int>>): List<String> {
        if (tiles.isEmpty()) return emptyList()

        val addedIds = mutableListOf<String>()
        var currentState = _worldState.value
        
        var changed = false
        for ((gx, gy) in tiles) {
            val wx = (gx * Constants.TILE_SIZE)
            // Anchor is bottom-left, so we add height to get the Y for the tile grid.
            val wy = ((gy + 1) * Constants.TILE_SIZE)

            if (isAlreadyOccupiedBySameType(currentState.tiles, type, gx, gy)) continue

            if (canPlaceInternal(currentState, type, wx, wy)) {
                val newStructure = Structure(UUID.randomUUID().toString(), type, wx, wy)
                currentState = bakeStrokeSegmentToWorld(currentState, newStructure)
                addedIds.add(newStructure.id)
                changed = true
            } else if (type == StructureType.WALL) {
                // Stop placing wall segments if one is blocked, to ensure contiguous walls.
                break
            }
        }

        if (changed) {
            // Batch update revisions and POIs after all segments are placed.
            val roadChanged = type == StructureType.ROAD
            val structChanged = type != StructureType.ROAD

            val newState = currentState.copy(
                roadRevision = if (roadChanged) currentState.roadRevision + 1 else currentState.roadRevision,
                structureRevision = if (structChanged) currentState.structureRevision + 1 else currentState.structureRevision
            )
            _worldState.value = newState.copy(pois = generatePOIs(newState))
            staticLayerId++
        }

        validateInvariants()
        return addedIds
    }


    private fun applyStructureToTiles(tiles: Array<Array<TileType>>, s: Structure) {
        val tileType = when (s.type) {
            StructureType.ROAD -> TileType.ROAD
            StructureType.WALL -> TileType.WALL
            else -> TileType.BUILDING_SOLID
        }
        val minX = s.topLeftGridX
        val minY = s.topLeftGridY
        val maxX = ((s.x + s.type.worldWidth - 1f) / Constants.TILE_SIZE).toInt()
        val maxY = ((s.y - 1f) / Constants.TILE_SIZE).toInt()

        for (ix in minX..maxX) {
            for (iy in minY..maxY) {
                if (ix in 0 until Constants.MAP_TILES_W && iy in 0 until Constants.MAP_TILES_H) {
                    tiles[ix][iy] = tileType
                }
            }
        }
    }

    private fun markLot(tiles: Array<Array<TileType>>, minX: Int, minY: Int, maxX: Int, maxY: Int) {
        for (ix in minX..maxX) {
            for (iy in minY..maxY) {
                if (ix in 0 until Constants.MAP_TILES_W && iy in 0 until Constants.MAP_TILES_H) {
                    val current = tiles[ix][iy]
                    if (current != TileType.ROAD && current != TileType.WALL && current != TileType.PROP_BLOCKED) {
                        tiles[ix][iy] = TileType.BUILDING_LOT
                    }
                }
            }
        }
    }

    private fun markFootprint(tiles: Array<Array<TileType>>, s: Structure, tileType: TileType) {
        val minX = s.topLeftGridX
        val minY = s.topLeftGridY
        val maxX = ((s.x + s.type.worldWidth - 1f) / Constants.TILE_SIZE).toInt()
        val maxY = ((s.y - 1f) / Constants.TILE_SIZE).toInt()

        for (ix in minX..maxX) {
            for (iy in minY..maxY) {
                if (ix in 0 until Constants.MAP_TILES_W && iy in 0 until Constants.MAP_TILES_H) {
                    tiles[ix][iy] = tileType
                }
            }
        }
    }

    private fun isAlreadyOccupiedBySameType(tiles: Array<Array<TileType>>, type: StructureType, gx: Int, gy: Int): Boolean {
        val targetType = when (type) {
            StructureType.ROAD -> TileType.ROAD
            StructureType.WALL -> TileType.WALL
            else -> return false
        }
        if (gx in 0 until Constants.MAP_TILES_W && gy in 0 until Constants.MAP_TILES_H) {
            if (tiles[gx][gy] != targetType) return false
        }
        return true
    }

    fun canPlace(type: StructureType, x: Float, y: Float): Boolean {
        return canPlaceInternal(_worldState.value, type, x, y)
    }

    private fun canPlaceInternal(state: WorldState, type: StructureType, x: Float, y: Float, isMoving: Boolean = false): Boolean {
        val footprintRect = Rect(x, y - type.worldHeight, x + type.worldWidth, y)
        if (footprintRect.left < 0 || footprintRect.top < 0 || footprintRect.right > Constants.WORLD_W_PX ||
            footprintRect.bottom > Constants.WORLD_H_PX) return false

        if (type == StructureType.TAVERN && !isMoving) {
            val tavernCount = state.structures.count { it.type == StructureType.TAVERN }
            if (tavernCount >= Constants.MAX_TAVERNS_PER_TOWN) {
                return false
            }
        }
        
        if (type == StructureType.ROAD || type == StructureType.WALL) {
            return true
        }

        for (s in state.structures) {
            if (s.type == StructureType.ROAD || s.type == StructureType.WALL) continue
            val otherFootprint = Rect(s.x, s.y - s.type.worldHeight, s.x + s.type.worldWidth, s.y)
            if (footprintRect.overlaps(otherFootprint)) {
                return false
            }
        }

        val minGX = (footprintRect.left / Constants.TILE_SIZE).toInt()
        val minGY = (footprintRect.top / Constants.TILE_SIZE).toInt()
        val maxGX = (footprintRect.right / Constants.TILE_SIZE).toInt()
        val maxGY = (footprintRect.bottom / Constants.TILE_SIZE).toInt()

        for (gx in minGX..maxGX) {
            for (gy in minGY..maxGY) {
                if (gx in 0 until Constants.MAP_TILES_W && gy in 0 until Constants.MAP_TILES_H) {
                    val t = state.tiles[gx][gy]
                    if (t != TileType.GRASS_LIGHT && t != TileType.GRASS_DARK && t != TileType.GRASS_DARK_TUFT && t != TileType.BUILDING_LOT) return false
                }
            }
        }

        return true
    }

    fun undoStructures(ids: List<String>) {
        if (ids.isEmpty()) return

        var currentState = _worldState.value
        val structuresToRemove = currentState.structures.filter { it.id in ids }
        if (structuresToRemove.isEmpty()) return

        for (s in structuresToRemove) {
            currentState = if (s.type.behavior == PlacementBehavior.STROKE) {
                unbakeStrokeSegmentFromWorld(currentState, s)
            } else {
                unbakeStructureFromWorld(currentState, s)
            }
        }

        val roadChanged = structuresToRemove.any { it.type == StructureType.ROAD }
        val structChanged = structuresToRemove.any { it.type != StructureType.ROAD }
        val newState = currentState.copy(
            roadRevision = if (roadChanged) currentState.roadRevision + 1 else currentState.roadRevision,
            structureRevision = if (structChanged) currentState.structureRevision + 1 else currentState.structureRevision,
            version = currentState.version + 1
        )
        _worldState.value = newState.copy(pois = generatePOIs(newState))
        staticLayerId++
    }

    fun removeStructureAt(x: Float, y: Float, isMoving: Boolean = false) {
        val currentState = _worldState.value
        val structure = currentState.structures.find { s ->
            val footprint = Rect(s.x, s.y - s.type.worldHeight, s.x + s.type.worldWidth, s.y)
            footprint.contains(Offset(x, y))
        } ?: return

        var finalState = if (structure.type.behavior == PlacementBehavior.STROKE) {
            unbakeStrokeSegmentFromWorld(currentState, structure)
        } else {
            unbakeStructureFromWorld(currentState, structure)
        }

        if (!isMoving) {
            // Dissociate agents from the removed structure.
            val newAgents = finalState.agents.map { agent ->
                if (agent.homeId == structure.id) {
                    agent.homeId = null
                }
                if (agent.jobId == structure.id) {
                    agent.jobId = null
                    agent.appearance = AppearanceVariant.DEFAULT
                }
                agent
            }
            finalState = finalState.copy(agents = newAgents)
        }

        val roadChanged = structure.type == StructureType.ROAD
        val structChanged = structure.type != StructureType.ROAD
        val newState = finalState.copy(
            roadRevision = if (roadChanged) currentState.roadRevision + 1 else currentState.roadRevision,
            structureRevision = if (structChanged) currentState.structureRevision + 1 else currentState.structureRevision,
            version = currentState.version + 1
        )
        _worldState.value = newState.copy(pois = generatePOIs(newState))
        staticLayerId++
    }


    /** The symmetric inverse of bakeStructureToWorld. */
    private fun unbakeStructureFromWorld(currentState: WorldState, structure: Structure): WorldState {
        val newTiles = currentState.copyTiles()
        
        // Define the same influence area used during baking.
        val footprintRect = Rect(structure.x, structure.y - structure.type.worldHeight, structure.x + structure.type.worldWidth, structure.y)
        val influenceRect = Rect(
            left = footprintRect.left - OWNERSHIP_MARGIN_X * Constants.TILE_SIZE,
            top = footprintRect.top - OWNERSHIP_MARGIN_Y * Constants.TILE_SIZE,
            right = footprintRect.right + OWNERSHIP_MARGIN_X * Constants.TILE_SIZE,
            bottom = footprintRect.bottom + OWNERSHIP_MARGIN_Y * Constants.TILE_SIZE
        )
        
        // Mark the entire influence area as dirty for NavGrid update.
        unionDirtyRect(influenceRect)

        // Revert all tiles within the influence area to their natural state.
        val minX = (influenceRect.left / Constants.TILE_SIZE).toInt()
        val minY = (influenceRect.top / Constants.TILE_SIZE).toInt()
        val maxX = (influenceRect.right / Constants.TILE_SIZE).toInt()
        val maxY = (influenceRect.bottom / Constants.TILE_SIZE).toInt()
        
        for (ix in minX..maxX) {
            for (iy in minY..maxY) {
                if (ix in 0 until Constants.MAP_TILES_W && iy in 0 until Constants.MAP_TILES_H) {
                    newTiles[ix][iy] = sampleGrassType(ix, iy)
                }
            }
        }
        
        return currentState.copy(
            structures = currentState.structures.filter { it.id != structure.id },
            tiles = newTiles
        )
    }

    /** The symmetric inverse of bakeStrokeSegmentToWorld. */
    private fun unbakeStrokeSegmentFromWorld(currentState: WorldState, structure: Structure): WorldState {
        val newTiles = currentState.copyTiles()
        val gx = structure.topLeftGridX
        val gy = structure.topLeftGridY
        
        if (gx in 0 until Constants.MAP_TILES_W && gy in 0 until Constants.MAP_TILES_H) {
            newTiles[gx][gy] = sampleGrassType(gx, gy)
        }
        
        unionDirtyRect(Rect(structure.x, structure.y - structure.type.worldHeight, structure.x + structure.type.worldWidth, structure.y))
        
        return currentState.copy(
            structures = currentState.structures.filter { it.id != structure.id },
            tiles = newTiles
        )
    }

    fun assignJobs() {
        val currentState = _worldState.value
        val unemployed = currentState.agents.filter { it.jobId == null }
        if (unemployed.isEmpty()) return

        val workplaces = currentState.structures.filter { it.type.jobSlots > 0 }

        val currentOccupancy = mutableMapOf<String, MutableList<String>>()
        for (agent in currentState.agents) {
            agent.jobId?.let { jid ->
                currentOccupancy.getOrPut(jid) { mutableListOf() }.add(agent.id)
            }
        }

        var changed = false
        for (agent in unemployed) {
            val nearestWorkplace = workplaces
                .filter { (currentOccupancy[it.id]?.size ?: 0) < it.type.jobSlots }
                .minByOrNull {
                    val dx = it.x - agent.x
                    val dy = it.y - agent.y
                    dx * dx + dy * dy
                }

            if (nearestWorkplace != null) {
                agent.jobId = nearestWorkplace.id
                currentOccupancy.getOrPut(nearestWorkplace.id) { mutableListOf() }.add(agent.id)
                changed = true
            }
        }

        if (changed) {
            staticLayerId++
        }
    }

    private fun generatePOIs(state: WorldState): List<POI> {
        val pois = mutableListOf<POI>()
        for (s in state.structures) {
            val poiType = when (s.type) {
                StructureType.HOUSE -> PoiType.HOUSE
                StructureType.STORE -> PoiType.STORE
                StructureType.WORKSHOP -> PoiType.WORKSHOP
                StructureType.CASTLE -> PoiType.CASTLE
                StructureType.PLAZA -> PoiType.PLAZA
                StructureType.TAVERN -> PoiType.TAVERN
                else -> null
            }
            if (poiType != null) {
                pois.add(POI(
                    s.id,
                    poiType,
                    SerializableOffset(s.x + s.type.worldWidth / 2, s.y - s.type.worldHeight / 2) // Center point
                ))
            }
        }
        return pois
    }

    fun updateTime(deltaSeconds: Float) {
        val oldState = _worldState.value
        val newTime = (oldState.timeOfDay + deltaSeconds) % (Constants.PHASE_DURATION_SEC * 4)
        _worldState.value = oldState.copy(timeOfDay = newTime)
    }

    fun getBrightness(): Float {
        val time = _worldState.value.timeOfDay
        val phaseIdx = (time / Constants.PHASE_DURATION_SEC).toInt().coerceIn(0, 3)
        val phaseProgress = (time % Constants.PHASE_DURATION_SEC) / Constants.PHASE_DURATION_SEC
        val phaseBrightness = floatArrayOf(0.6f, 0.9f, 1.0f, 0.9f)
        val nextIdx = (phaseIdx + 1) % phaseBrightness.size

        return lerp(phaseBrightness[phaseIdx], phaseBrightness[nextIdx], phaseProgress)
    }

    private fun lerp(start: Float, end: Float, t: Float): Float {
        return start + (end - start) * t
    }

    private fun validateInvariants() {
        val state = _worldState.value
        state.structures.forEach { s ->
            val expectedTile = if (s.type == StructureType.PLAZA) TileType.PLAZA else TileType.BUILDING_SOLID

            if (s.type != StructureType.ROAD && s.type != StructureType.WALL) {
                val gx = s.topLeftGridX
                val gy = s.topLeftGridY
                if (gx >= 0 && gx < Constants.MAP_TILES_W && gy >= 0 && gy < Constants.MAP_TILES_H) {
                    if (state.tiles[gx][gy] != expectedTile && state.tiles[gx][gy] != TileType.BUILDING_FOOTPRINT) {
                        Log.e("WorldManager", "Invariant broken: Structure ${s.id} at $gx,$gy not on footprint (expected $expectedTile, got ${state.tiles[gx][gy]})")
                    }
                }
            }
        }
    }
}
