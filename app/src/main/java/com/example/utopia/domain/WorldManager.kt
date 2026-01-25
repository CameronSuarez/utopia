package com.example.utopia.domain

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
private const val LOT_CLEARANCE_PAD = 2 // Moderate pad for lot area and prop clearance.

class WorldManager {
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
            timeOfDay = Constants.PHASE_DURATION_SEC // Start at MORNING (300s)
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
                    in 0.0f..0.02f -> PropType.TREE_1
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

    fun tryPlaceStamp(type: StructureType, x: Float, y: Float, existingStructure: Structure? = null): String? {
        if (!canPlaceInternal(_worldState.value, type, x, y, isMoving = existingStructure != null)) return null

        val newStructure = existingStructure?.copy(x = x, y = y) ?: Structure(UUID.randomUUID().toString(), type, x, y)
        val id = newStructure.id

        // Mark the new placement area as dirty
        unionDirtyRect(Rect(offset = Offset(x, y), size = Size(type.worldWidth, type.worldHeight)))

        val currentState = _worldState.value
        val newTiles = currentState.copyTiles()

        val newProps = if (type.behavior == PlacementBehavior.STAMP) {
            val pad = LOT_CLEARANCE_PAD
            val structGX = newStructure.gridX
            val structGY = newStructure.gridY
            val structureWidth = newStructure.type.width
            val structureHeight = newStructure.type.height

            val lotMinX = structGX - pad
            val lotMinY = structGY - pad
            val lotMaxX = structGX + structureWidth - 1 + pad
            val lotMaxY = structGY + structureHeight - 1 + pad

            currentState.props.filterNot { prop ->
                getPropFootprintTiles(prop).any { (propTileX, propTileY) ->
                    val isInside = propTileX in lotMinX..lotMaxX && propTileY in lotMinY..lotMaxY
                    if (isInside) {
                        // Mark removed prop area as dirty (conservatively using a full tile)
                        unionDirtyRect(Rect(
                            propTileX * Constants.TILE_SIZE.toFloat(),
                            propTileY * Constants.TILE_SIZE.toFloat(),
                            (propTileX + 1) * Constants.TILE_SIZE.toFloat(),
                            (propTileY + 1) * Constants.TILE_SIZE.toFloat()
                        ))
                    }
                    isInside
                }
            }
        } else {
            val gx = newStructure.gridX
            val gy = newStructure.gridY
            currentState.props.filterNot { prop ->
                val isInside = prop.homeTileX == gx && (prop.homeTileY == gy || prop.homeTileY == gy + 1)
                if (isInside) {
                    unionDirtyRect(Rect(
                        prop.homeTileX * Constants.TILE_SIZE.toFloat(),
                        prop.homeTileY * Constants.TILE_SIZE.toFloat(),
                        (prop.homeTileX + 1) * Constants.TILE_SIZE.toFloat(),
                        (prop.homeTileY + 1) * Constants.TILE_SIZE.toFloat()
                    ))
                }
                isInside
            }
        }

        var newRoadRevision = currentState.roadRevision
        var newStructRevision = currentState.structureRevision

        when (type) {
            StructureType.ROAD -> {
                applyStructureToTiles(newTiles, newStructure)
                newRoadRevision++
            }
            StructureType.WALL -> {
                applyStructureToTiles(newTiles, newStructure)
                newStructRevision++
            }
            StructureType.PLAZA -> {
                markFootprint(newTiles, newStructure, TileType.PLAZA)
                newStructRevision++
            }
            else -> {
                markLot(newTiles, newStructure)
                newStructRevision++
            }
        }

        val newState = currentState.copy(
            structures = currentState.structures + newStructure,
            tiles = newTiles,
            props = newProps,
            roadRevision = newRoadRevision,
            structureRevision = newStructRevision,
            version = currentState.version + 1
        )

        _worldState.value = newState.copy(pois = generatePOIs(newState))
        staticLayerId++

        if (type == StructureType.HOUSE && existingStructure == null) {
            spawnVillagersForHouse(newStructure)
        }

        if (type.jobSlots > 0) {
            assignJobs()
        }

        validateInvariants()
        return id
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
        val spawnX = house.x + house.type.worldWidth / 2
        val spawnY = house.y + house.type.worldHeight

        var firstVillagerName: String? = null

        repeat(count) {
            val gender = if (random.nextFloat() < 0.5f) Gender.MALE else Gender.FEMALE
            val namePool = if (gender == Gender.MALE) maleNames else femaleNames
            val name = namePool.random(random)
            if (it == 0) firstVillagerName = name

            val jx = spawnX + (random.nextFloat() * 20f - 10f)
            val jy = spawnY + (random.nextFloat() * 20f - 10f)
            spawnAgentWithExplicitName(house, jx, jy, name, gender)
        }

        firstVillagerName?.let {
            house.customName = "${it}'s House"
        }
    }

    private fun spawnAgentWithExplicitName(home: Structure, wx: Float, wy: Float, explicitName: String, gender: Gender) {
        val currentState = _worldState.value
        if (currentState.agents.size >= Constants.MAX_AGENTS) return

        val personality = Personality.entries.random(random)
        val appearance = generateAppearanceSpec(gender, random)
        val profile = AgentProfile(gender = gender, appearance = appearance)

        var currentWX = wx
        var currentWY = wy
        var gx = (currentWX / Constants.TILE_SIZE).toInt().coerceIn(0, Constants.MAP_TILES_W - 1)
        var gy = (currentWY / Constants.TILE_SIZE).toInt().coerceIn(0, Constants.MAP_TILES_H - 1)

        val spawnTile = currentState.tiles[gx][gy]
        
        // Use NavGrid for authority instead of raw TileType semantics
        val tempNavGrid = NavGrid().apply { update(currentState.tiles, currentState.structures, currentState.props) }
        
        if (!tempNavGrid.isWalkable(gx, gy)) {
            val nudged = Pathfinding.nudgeOutOfObstacle(gx, gy, tempNavGrid)
            if (nudged != null) {
                currentWX = (nudged.first + 0.5f) * Constants.TILE_SIZE
                currentWY = (nudged.second + 0.5f) * Constants.TILE_SIZE
                gx = nudged.first
                gy = nudged.second
            }
        }

        val now = System.currentTimeMillis()
        val agent = AgentRuntime(
            id = UUID.randomUUID().toString(),
            shortId = currentState.nextAgentId,
            profile = profile,
            name = explicitName,
            personality = personality,
            x = currentWX,
            y = currentWY,
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

    fun tryPlaceStroke(type: StructureType, tiles: List<Pair<Int, Int>>): List<String> {
        if (tiles.isEmpty()) return emptyList()

        val currentState = _worldState.value
        val newTiles = currentState.copyTiles()
        val newStructures = currentState.structures.toMutableList()
        val newProps = currentState.props.toMutableList()
        val addedIds = mutableListOf<String>()

        var changed = false
        var roadChanged = false
        var structChanged = false
        for ((gx, gy) in tiles) {
            val wx = gx * Constants.TILE_SIZE
            val wy = gy * Constants.TILE_SIZE

            if (isAlreadyOccupiedBySameType(newTiles, type, gx, gy)) continue

            if (canPlaceInternal(currentState, type, wx, wy)) {
                newProps.removeAll { prop -> 
                    val isInside = prop.homeTileX == gx && (prop.homeTileY == gy || prop.homeTileY == gy + 1)
                    if (isInside) {
                         unionDirtyRect(Rect(
                            prop.homeTileX * Constants.TILE_SIZE.toFloat(),
                            prop.homeTileY * Constants.TILE_SIZE.toFloat(),
                            (prop.homeTileX + 1) * Constants.TILE_SIZE.toFloat(),
                            (prop.homeTileY + 1) * Constants.TILE_SIZE.toFloat()
                        ))
                    }
                    isInside
                }

                val id = UUID.randomUUID().toString()
                val s = Structure(id, type, wx, wy)
                newStructures.add(s)
                applyStructureToTiles(newTiles, s)
                addedIds.add(id)
                changed = true
                if (type == StructureType.ROAD) roadChanged = true
                else structChanged = true
                
                // Mark placed stroke tile as dirty
                unionDirtyRect(Rect(wx, wy, wx + Constants.TILE_SIZE, wy + Constants.TILE_SIZE))
            } else if (type == StructureType.WALL) {
                break
            }
        }

        if (changed) {
            val newState = currentState.copy(
                structures = newStructures,
                tiles = newTiles,
                props = newProps,
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
        val gx = s.gridX
        val gy = s.gridY
        for (ix in gx until gx + s.type.width) {
            for (iy in gy until gy + s.type.height) {
                if (ix in 0 until Constants.MAP_TILES_W && iy in 0 until Constants.MAP_TILES_H) {
                    tiles[ix][iy] = tileType
                }
            }
        }
    }

    private fun markLot(tiles: Array<Array<TileType>>, s: Structure) {
        val pad = LOT_CLEARANCE_PAD
        val minX = (s.x / Constants.TILE_SIZE).toInt() - pad
        val minY = (s.y / Constants.TILE_SIZE).toInt() - pad
        val maxX = ((s.x + s.type.worldWidth - 1f) / Constants.TILE_SIZE).toInt() + pad
        val maxY = ((s.y + s.type.worldHeight - 1f) / Constants.TILE_SIZE).toInt() + pad

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
        val minX = (s.x / Constants.TILE_SIZE).toInt()
        val minY = (s.y / Constants.TILE_SIZE).toInt()
        val maxX = ((s.x + s.type.worldWidth - 1f) / Constants.TILE_SIZE).toInt()
        val maxY = ((s.y + s.type.worldHeight - 1f) / Constants.TILE_SIZE).toInt()

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

        for (ix in gx until gx + type.width) {
            for (iy in gy until gy + type.height) {
                if (ix in 0 until Constants.MAP_TILES_W && iy in 0 until Constants.MAP_TILES_H) {
                    if (tiles[ix][iy] != targetType) return false
                }
            }
        }
        return true
    }

    fun canPlace(type: StructureType, x: Float, y: Float): Boolean {
        return canPlaceInternal(_worldState.value, type, x, y)
    }

    private fun canPlaceInternal(state: WorldState, type: StructureType, x: Float, y: Float, isMoving: Boolean = false): Boolean {
        if (x < 0 || y < 0 || x + type.worldWidth > Constants.WORLD_W_PX ||
            y + type.worldHeight > Constants.WORLD_H_PX) return false

        if (type == StructureType.TAVERN && !isMoving) {
            val tavernCount = state.structures.count { it.type == StructureType.TAVERN }
            if (tavernCount >= Constants.MAX_TAVERNS_PER_TOWN) {
                return false
            }
        }
        
        if (type == StructureType.ROAD || type == StructureType.WALL) {
            return true
        }

        val newAABBR = x + type.worldWidth
        val newAABBB = y + type.worldHeight

        for (s in state.structures) {
            if (s.type == StructureType.ROAD || s.type == StructureType.WALL) continue
            
            val otherL = s.x
            val otherR = s.x + s.type.worldWidth
            val otherT = s.y
            val otherB = s.y + s.type.worldHeight

            if (x < otherR && newAABBR > otherL && y < otherB && newAABBB > otherT) {
                return false
            }
        }

        val minGX = (x / Constants.TILE_SIZE).toInt()
        val minGY = (y / Constants.TILE_SIZE).toInt()
        val maxGX = ((x + type.worldWidth - 1f) / Constants.TILE_SIZE).toInt()
        val maxGY = ((y + type.worldHeight - 1f) / Constants.TILE_SIZE).toInt()

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
        val currentState = _worldState.value
        val structuresToRemove = currentState.structures.filter { it.id in ids }
        if (structuresToRemove.isEmpty()) return

        val newStructures = currentState.structures.filter { it.id !in ids }
        val newTiles = currentState.copyTiles()
        
        val newProps = currentState.props.toMutableList()
        
        var roadChanged = false
        var structChanged = false

        for (s in structuresToRemove) {
            if (s.type == StructureType.ROAD) roadChanged = true
            else structChanged = true

            // Mark undone area as dirty
            unionDirtyRect(Rect(offset = Offset(s.x, s.y), size = Size(s.type.worldWidth, s.type.worldHeight)))

            val minX = (s.x / Constants.TILE_SIZE).toInt()
            val minY = (s.y / Constants.TILE_SIZE).toInt()
            val maxX = ((s.x + s.type.worldWidth - 1f) / Constants.TILE_SIZE).toInt()
            val maxY = ((s.y + s.type.worldHeight - 1f) / Constants.TILE_SIZE).toInt()
            
            for (ix in minX..maxX) {
                for (iy in minY..maxY) {
                    if (ix in 0 until Constants.MAP_TILES_W && iy in 0 until Constants.MAP_TILES_H) {
                        newTiles[ix][iy] = sampleGrassType(ix, iy)
                    }
                }
            }
        }

        val newState = currentState.copy(
            structures = newStructures,
            tiles = newTiles,
            props = newProps,
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
            x >= s.x && x < s.x + s.type.worldWidth &&
            y >= s.y && y < s.y + s.type.worldHeight
        } ?: return

        val newStructures = currentState.structures.filter { it.id != structure.id }
        val newTiles = currentState.copyTiles()
        val newProps = currentState.props.toMutableList()

        var roadChanged = false
        var structChanged = false

        // Mark removed area as dirty
        unionDirtyRect(Rect(offset = Offset(structure.x, structure.y), size = Size(structure.type.worldWidth, structure.type.worldHeight)))

        val minX = (structure.x / Constants.TILE_SIZE).toInt()
        val minY = (structure.y / Constants.TILE_SIZE).toInt()
        val maxX = ((structure.x + structure.type.worldWidth - 1f) / Constants.TILE_SIZE).toInt()
        val maxY = ((structure.y + structure.type.worldHeight - 1f) / Constants.TILE_SIZE).toInt()
        
        if (structure.type == StructureType.ROAD) roadChanged = true
        else structChanged = true
        
        for (ix in minX..maxX) {
            for (iy in minY..maxY) {
                if (ix in 0 until Constants.MAP_TILES_W && iy in 0 until Constants.MAP_TILES_H) {
                    newTiles[ix][iy] = sampleGrassType(ix, iy)
                }
            }
        }

        if (!isMoving) {
            for (agent in currentState.agents) {
                if (agent.homeId == structure.id) agent.homeId = null
                if (agent.jobId == structure.id) {
                    agent.jobId = null
                    agent.appearance = AppearanceVariant.DEFAULT
                }
            }
        }

        val newState = currentState.copy(
            structures = newStructures,
            tiles = newTiles,
            props = newProps,
            roadRevision = if (roadChanged) currentState.roadRevision + 1 else currentState.roadRevision,
            structureRevision = if (structChanged) currentState.structureRevision + 1 else currentState.structureRevision,
            version = currentState.version + 1
        )
        _worldState.value = newState.copy(pois = generatePOIs(newState))
        staticLayerId++
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
                    SerializableOffset(s.x + s.type.worldWidth / 2, s.y + s.type.worldHeight / 2)
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
                val gx = (s.x / Constants.TILE_SIZE).toInt()
                val gy = (s.y / Constants.TILE_SIZE).toInt()
                if (state.tiles[gx][gy] != expectedTile && state.tiles[gx][gy] != TileType.BUILDING_FOOTPRINT) {
                    // Log.e("WorldManager", "Invariant broken: Structure ${s.id} at $gx,$gy not on footprint (expected $expectedTile, got ${state.tiles[gx][gy]})")
                }
            }
        }
    }
}
