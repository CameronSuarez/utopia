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
import com.example.utopia.data.StructureRegistry
import com.example.utopia.data.models.*
import com.example.utopia.ui.generateAppearanceSpec
import com.example.utopia.util.Constants
import java.util.UUID
import kotlin.random.Random

private const val WORLD_SEED = 12345L
private const val OWNERSHIP_MARGIN_X = 2
private const val OWNERSHIP_MARGIN_Y = 3

class WorldManager(private val navGrid: NavGrid) {
    val random = Random(WORLD_SEED)

    private val simulationPipeline = SimulationPipeline(
        listOf(
            AgentAssignmentSystem,
            AgentNeedsSystemWrapper,
            AgentSocialSystemWrapper,
            AgentGossipSystemWrapper,
            AgentEmojiSystemWrapper,
            AgentRelationshipSystemWrapper,
            PoiSystem,
            WorldAnalysisSystem,
            AgentDecisionSystem,
            AgentPhysicsWrapper(navGrid),
            EconomySystemWrapper,
            StaleTargetCleanupSystem
        )
    )

    private val maleNames = listOf("Alaric", "Cedric", "Eldred", "Godwin", "Ivor", "Kenric", "Merrick", "Osric", "Stig", "Ulf", "Wulf", "Zoric", "Bram", "Ewan", "Quinn")
    private val femaleNames = listOf("Beatrice", "Drusilla", "Faye", "Hilda", "Joan", "Lulu", "Nell", "Pippa", "Rowena", "Tilda", "Vera", "Xenia", "Yrsa", "Cora", "Dara")

    private val initialTiles = Array(Constants.MAP_TILES_W) { x ->
        Array(Constants.MAP_TILES_H) { y -> sampleGrassType(x, y) }
    }
    private val initialProps = generateInitialProps(initialTiles)

    private val _worldState = mutableStateOf(
        WorldState(
            tiles = initialTiles,
            structures = emptyList(),
            agents = emptyList(),
            props = initialProps
        )
    )
    val worldState: State<WorldState> = _worldState

    var staticLayerId by mutableIntStateOf(0)
        private set

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

    fun toData(): WorldStateData {
        val state = _worldState.value
        return WorldStateData(
            structures = state.structures,
            agents = state.agents,
            props = state.props,
            pois = state.pois,
            socialFields = state.socialFields,
            emojiSignals = state.emojiSignals,
            nextAgentId = state.nextAgentId,
            roadRevision = state.roadRevision,
            structureRevision = state.structureRevision,
            inventoryRevision = state.inventoryRevision,
            tiles = state.tiles.map { it.toList() }
        )
    }

    fun loadData(data: WorldStateData) {
        val loadedTiles = Array(Constants.MAP_TILES_W) { x ->
            Array(Constants.MAP_TILES_H) { y -> data.tiles.getOrNull(x)?.getOrNull(y) ?: sampleGrassType(x, y) }
        }

        var grid = Array(Constants.MAP_TILES_W) { Array<String?>(Constants.MAP_TILES_H) { null } }
        data.structures.forEach { structure ->
            grid = writeToGrid(grid, structure)
        }

        val baseState = WorldState(
            tiles = loadedTiles,
            structures = data.structures,
            structureGrid = grid,
            agents = data.agents,
            props = data.props,
            socialFields = data.socialFields,
            emojiSignals = data.emojiSignals,
            nextAgentId = data.nextAgentId,
            roadRevision = data.roadRevision,
            structureRevision = data.structureRevision,
            inventoryRevision = data.inventoryRevision
        )
        _worldState.value = PoiSystem.recompute(baseState)
        staticLayerId++
        pendingDirtyRect = null
    }

    fun advanceTick(deltaTimeMs: Long, nowMs: Long) {
        val oldState = _worldState.value
        val finalState = computeNextState(oldState, deltaTimeMs, nowMs)
        if (finalState !== oldState) {
            _worldState.value = finalState
        }
    }

    fun computeNextState(oldState: WorldState, deltaTimeMs: Long, nowMs: Long): WorldState {
        val newState = simulationPipeline.run(oldState, deltaTimeMs, nowMs)

        val newlyCompletedHouses = newState.structures.filter { newStructure ->
            newStructure.typeId == "HOUSE" && newStructure.isComplete &&
                (oldState.structures.find { it.id == newStructure.id }?.isComplete == false)
        }

        var finalState = newState
        if (newlyCompletedHouses.isNotEmpty()) {
            for (house in newlyCompletedHouses) {
                finalState = spawnVillagersForHouse(house, finalState)
            }
        }

        return finalState
    }

    fun applyState(newState: WorldState) {
        _worldState.value = newState
    }

    fun addAgent(agent: AgentRuntime) {
        val currentState = _worldState.value
        if (currentState.agents.size >= Constants.MAX_AGENTS) return

        _worldState.value = currentState.copy(
            agents = currentState.agents + agent,
            nextAgentId = currentState.nextAgentId + 1,
            version = currentState.version + 1
        )
        staticLayerId++
    }

    fun removeAgent(agentId: String) {
        val currentState = _worldState.value
        val updatedAgents = currentState.agents.filterNot { it.id == agentId }
        if (updatedAgents.size == currentState.agents.size) return

        val updatedStructures = currentState.structures.map { structure ->
            if (structure.residents.contains(agentId)) {
                structure.copy(residents = structure.residents.filterNot { it == agentId })
            } else {
                structure
            }
        }

        _worldState.value = currentState.copy(
            agents = updatedAgents,
            structures = updatedStructures,
            version = currentState.version + 1
        )
        staticLayerId++
    }

    fun addProp(prop: PropInstance) {
        val currentState = _worldState.value
        _worldState.value = currentState.copy(
            props = currentState.props + prop,
            version = currentState.version + 1
        )
        markPropDirty(prop)
        staticLayerId++
    }

    fun updateProp(prop: PropInstance) {
        val currentState = _worldState.value
        val existing = currentState.props.find { it.id == prop.id } ?: return
        val updatedProps = currentState.props.map { if (it.id == prop.id) prop else it }

        _worldState.value = currentState.copy(
            props = updatedProps,
            version = currentState.version + 1
        )
        markPropDirty(existing)
        markPropDirty(prop)
        staticLayerId++
    }

    fun removeProp(propId: String) {
        val currentState = _worldState.value
        val existing = currentState.props.find { it.id == propId } ?: return
        val updatedProps = currentState.props.filterNot { it.id == propId }

        _worldState.value = currentState.copy(
            props = updatedProps,
            version = currentState.version + 1
        )
        markPropDirty(existing)
        staticLayerId++
    }

    fun addStructure(structure: Structure): String? {
        val spec = structure.spec
        return if (spec.behavior == PlacementBehavior.STROKE) {
            val gx = (structure.x / Constants.TILE_SIZE).toInt()
            val gy = ((structure.y - spec.worldHeight) / Constants.TILE_SIZE).toInt()
            val ids = tryPlaceStroke(structure.typeId, listOf(gx to gy))
            ids.firstOrNull()
        } else {
            tryPlaceStamp(structure.typeId, structure.x, structure.y, existingStructure = structure)
        }
    }

    fun updateStructure(structure: Structure) {
        val currentState = _worldState.value
        val updatedStructures = currentState.structures.map { existing ->
            if (existing.id == structure.id) structure else existing
        }
        if (updatedStructures == currentState.structures) return

        _worldState.value = currentState.copy(
            structures = updatedStructures,
            version = currentState.version + 1
        )
        staticLayerId++
    }

    fun removeStructure(structureId: String) {
        undoStructures(listOf(structureId))
    }

    private fun markPropDirty(prop: PropInstance) {
        getPropFootprintTiles(prop).forEach { (tileX, tileY) ->
            unionDirtyRect(
                Rect(
                    tileX * Constants.TILE_SIZE,
                    tileY * Constants.TILE_SIZE,
                    (tileX + 1) * Constants.TILE_SIZE,
                    (tileY + 1) * Constants.TILE_SIZE
                )
            )
        }
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
        _worldState.value = currentState.copy(
            version = currentState.version + 1,
            roadRevision = currentState.roadRevision + 1,
            structureRevision = currentState.structureRevision + 1
        )
        staticLayerId++
    }

    private fun bakeStructureToWorld(
        currentState: WorldState,
        structure: Structure
    ): WorldState {
        val spec = structure.spec
        unionDirtyRect(Rect(offset = Offset(structure.x, structure.y - spec.worldHeight), size = Size(spec.worldWidth, spec.worldHeight)))

        val newTiles = currentState.copyTiles()

        val footprintRect = Rect(structure.x, structure.y - spec.worldHeight, structure.x + spec.worldWidth, structure.y)
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

        markLot(newTiles, lotMinX, lotMinY, lotMaxX, lotMaxY)
        val footprintTile = if (spec.id == "PLAZA") TileType.PLAZA else TileType.BUILDING_SOLID
        markFootprint(newTiles, structure, footprintTile)

        val newGrid = writeToGrid(currentState.structureGrid, structure)

        return currentState.copy(
            structures = currentState.structures + structure,
            tiles = newTiles,
            props = newProps,
            structureGrid = newGrid,
            structureRevision = currentState.structureRevision + 1,
            version = currentState.version + 1
        )
    }

    fun tryPlaceStamp(typeId: String, x: Float, y: Float, existingStructure: Structure? = null): String? {
        if (!canPlaceInternal(_worldState.value, typeId, x, y, isMoving = existingStructure != null)) return null

        val spec = StructureRegistry.get(typeId)

        val starterBuildings = listOf("HOUSE", "LUMBERJACK_HUT", "SAWMILL")
        val isFirstOfKind = starterBuildings.contains(typeId) && _worldState.value.structures.none { it.spec.id == typeId }
        val needsConstruction = existingStructure == null && spec.buildCost.isNotEmpty() && !isFirstOfKind

        val newStructure = existingStructure?.copy(x = x, y = y)
            ?: Structure(
                id = UUID.randomUUID().toString(),
                typeId = typeId,
                x = x,
                y = y,
                isComplete = !needsConstruction
            )

        val newState = bakeStructureToWorld(_worldState.value, newStructure)

        var finalState = PoiSystem.recompute(newState)

        if (typeId == "HOUSE" && !needsConstruction && existingStructure == null) {
            finalState = spawnVillagersForHouse(newStructure, finalState)
        }

        _worldState.value = finalState
        staticLayerId++
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

    private fun spawnVillagersForHouse(house: Structure, currentState: WorldState): WorldState {
        val count = 2
        val spawnX = house.x + house.spec.worldWidth / 2
        val spawnY = house.y + Constants.TILE_SIZE * 2f

        var workingState = currentState
        var firstVillagerName: String? = null

        repeat(count) {
            val gender = if (random.nextFloat() < 0.5f) Gender.MALE else Gender.FEMALE
            val namePool = if (gender == Gender.MALE) maleNames else femaleNames
            val name = namePool.random(random)
            if (it == 0) firstVillagerName = name

            val jx = spawnX + (random.nextFloat() * 20f - 10f)
            val jy = spawnY + (random.nextFloat() * 10f)
            workingState = spawnAgentWithExplicitName(house, jx, jy, name, gender, workingState)
        }

        return firstVillagerName?.let { name ->
            workingState.copy(
                structures = workingState.structures.map { s ->
                    if (s.id == house.id) s.copy(customName = "$name's House") else s
                }
            )
        } ?: workingState
    }

    fun spawnAgent(x: Float, y: Float) {
        val gender = if (random.nextFloat() < 0.5f) Gender.MALE else Gender.FEMALE
        val namePool = if (gender == Gender.MALE) maleNames else femaleNames
        val name = namePool.random(random)
        _worldState.value = spawnAgentWithExplicitName(null, x, y, name, gender, _worldState.value)
    }

    private fun spawnAgentWithExplicitName(
        home: Structure?,
        wx: Float,
        wy: Float,
        explicitName: String,
        gender: Gender,
        currentState: WorldState
    ): WorldState {
        if (currentState.agents.size >= Constants.MAX_AGENTS) return currentState

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
                return currentState
            }
        }

        val agentProfile = AgentProfile(
            gender = gender,
            appearance = generateAppearanceSpec(gender, random)
        )

        val workplaceId = if (home == null) {
            val workersByWorkplace = currentState.agents.filter { it.workplaceId != null }
                .groupBy { it.workplaceId!! }

            val availableWorkplace = currentState.structures.find { structure ->
                val spec = structure.spec
                val assignedWorkers = workersByWorkplace[structure.id]?.size ?: 0
                (spec.id == "LUMBERJACK_HUT" || spec.id == "SAWMILL") && spec.capacity > 0 && assignedWorkers < spec.capacity
            }
            availableWorkplace?.id
        } else {
            null
        }

        val agent = AgentRuntime(
            id = UUID.randomUUID().toString(),
            name = explicitName,
            profile = agentProfile,
            position = SerializableOffset(finalWx, finalWy),
            velocity = SerializableOffset(0f, 0f),
            currentIntent = AgentIntent.Idle,
            homeId = home?.id,
            workplaceId = workplaceId,
            personality = PersonalityVector(
                expressiveness = random.nextFloat() * 2 - 1,
                positivity = random.nextFloat() * 2 - 1,
                playfulness = random.nextFloat() * 2 - 1,
                warmth = random.nextFloat() * 2 - 1,
                sensitivity = random.nextFloat() * 2 - 1
            ),
            needs = Needs(
                sleep = random.nextFloat() * 20f + 70f,
                stability = random.nextFloat() * 20f + 70f,
                social = random.nextFloat() * 20f + 70f,
                `fun` = random.nextFloat() * 20f + 70f
            ),
            socialMemory = SocialMemory()
        )

        val newStructures = if (home != null) {
            currentState.structures.map { s ->
                if (s.id == home.id) s.copy(residents = s.residents + agent.id) else s
            }
        } else {
            currentState.structures
        }

        return currentState.copy(
            agents = currentState.agents + agent,
            structures = newStructures,
            nextAgentId = currentState.nextAgentId + 1
        )
    }

    private fun bakeStrokeSegmentToWorld(currentState: WorldState, structure: Structure): WorldState {
        val spec = structure.spec
        val gx = (structure.x / Constants.TILE_SIZE).toInt()
        val gy = ((structure.y - spec.worldHeight) / Constants.TILE_SIZE).toInt()

        val newTiles = currentState.copyTiles()

        val newProps = currentState.props.filterNot { prop ->
            getPropFootprintTiles(prop).any { (propTileX, propTileY) ->
                val isInside = propTileX == gx && propTileY == gy
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

        applyStructureToTiles(newTiles, structure)

        unionDirtyRect(Rect(structure.x, structure.y - spec.worldHeight, structure.x + spec.worldWidth, structure.y))

        val newGrid = writeToGrid(currentState.structureGrid, structure)

        return currentState.copy(
            structures = currentState.structures + structure,
            tiles = newTiles,
            props = newProps,
            structureGrid = newGrid
        )
    }

    fun tryPlaceStroke(typeId: String, tiles: List<Pair<Int, Int>>): List<String> {
        if (tiles.isEmpty()) return emptyList()

        val addedIds = mutableListOf<String>()
        val startState = _worldState.value

        val workingTiles = startState.copyTiles()
        val workingStructures = startState.structures.toMutableList()
        val workingProps = startState.props.toMutableList()
        var workingGrid = startState.structureGrid

        var changed = false
        for ((gx, gy) in tiles) {
            val wx = (gx * Constants.TILE_SIZE)
            val wy = ((gy + 1) * Constants.TILE_SIZE)

            if (isAlreadyOccupiedBySameType(workingTiles, typeId, gx, gy)) continue

            if (canPlaceInternal(startState, typeId, wx, wy)) {
                val id = UUID.randomUUID().toString()
                val newStructure = Structure(id, typeId, wx, wy)

                val propsToRemove = workingProps.filter { prop ->
                    getPropFootprintTiles(prop).any { (ptX, ptY) -> ptX == gx && ptY == gy }
                }
                if (propsToRemove.isNotEmpty()) {
                    propsToRemove.forEach { p ->
                        getPropFootprintTiles(p).forEach { (tx, ty) ->
                            unionDirtyRect(Rect(tx * Constants.TILE_SIZE, ty * Constants.TILE_SIZE, (tx + 1) * Constants.TILE_SIZE, (ty + 1) * Constants.TILE_SIZE))
                        }
                    }
                    workingProps.removeAll(propsToRemove)
                }

                applyStructureToTiles(workingTiles, newStructure)

                workingStructures.add(newStructure)
                workingGrid = writeToGrid(workingGrid, newStructure)

                unionDirtyRect(Rect(newStructure.x, newStructure.y - newStructure.spec.worldHeight, newStructure.x + newStructure.spec.worldWidth, newStructure.y))

                addedIds.add(id)
                changed = true
            } else if (typeId == "WALL") {
                break
            }
        }

        if (changed) {
            val newState = startState.copy(
                tiles = workingTiles,
                structures = workingStructures,
                props = workingProps,
                structureGrid = workingGrid,
                version = startState.version + 1
            )

            _worldState.value = PoiSystem.recompute(newState)
            staticLayerId++
        }

        validateInvariants()
        return addedIds
    }


    private fun applyStructureToTiles(tiles: Array<Array<TileType>>, s: Structure) {
        val spec = s.spec
        val tileType = when (spec.id) {
            "ROAD" -> TileType.ROAD
            "WALL" -> TileType.WALL
            else -> TileType.BUILDING_SOLID
        }
        val minX = (s.x / Constants.TILE_SIZE).toInt()
        val minY = ((s.y - spec.worldHeight) / Constants.TILE_SIZE).toInt()
        val maxX = ((s.x + spec.worldWidth - 1f) / Constants.TILE_SIZE).toInt()
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
                    if (current != TileType.ROAD && current != TileType.WALL && current != TileType.PROP_BLOCKED && current != TileType.BUILDING_SOLID) {
                        tiles[ix][iy] = TileType.BUILDING_LOT
                    }
                }
            }
        }
    }

    private fun markFootprint(tiles: Array<Array<TileType>>, s: Structure, tileType: TileType) {
        val spec = s.spec
        val minX = (s.x / Constants.TILE_SIZE).toInt()
        val minY = ((s.y - spec.worldHeight) / Constants.TILE_SIZE).toInt()
        val maxX = ((s.x + spec.worldWidth - 1f) / Constants.TILE_SIZE).toInt()
        val maxY = ((s.y - 1f) / Constants.TILE_SIZE).toInt()

        for (ix in minX..maxX) {
            for (iy in minY..maxY) {
                if (ix in 0 until Constants.MAP_TILES_W && iy in 0 until Constants.MAP_TILES_H) {
                    tiles[ix][iy] = tileType
                }
            }
        }
    }

    private fun isAlreadyOccupiedBySameType(tiles: Array<Array<TileType>>, typeId: String, gx: Int, gy: Int): Boolean {
        val targetType = when (typeId) {
            "ROAD" -> TileType.ROAD
            "WALL" -> TileType.WALL
            else -> return false
        }
        if (gx in 0 until Constants.MAP_TILES_W && gy in 0 until Constants.MAP_TILES_H) {
            if (tiles[gx][gy] != targetType) return false
        }
        return true
    }

    fun canPlace(typeId: String, x: Float, y: Float): Boolean {
        return canPlaceInternal(_worldState.value, typeId, x, y)
    }

    private fun canPlaceInternal(state: WorldState, typeId: String, x: Float, y: Float, isMoving: Boolean = false): Boolean {
        val spec = StructureRegistry.get(typeId)
        val footprintRect = Rect(x, y - spec.worldHeight, x + spec.worldWidth, y)
        if (footprintRect.left < 0 || footprintRect.top < 0 || footprintRect.right > Constants.WORLD_W_PX ||
            footprintRect.bottom > Constants.WORLD_H_PX) return false

        if (typeId == "TAVERN" && !isMoving) {
            val tavernCount = state.structures.count { it.spec.id == "TAVERN" }
            if (tavernCount >= 2) {
                return false
            }
        }

        if (typeId == "ROAD" || typeId == "WALL") {
            return true
        }

        for (s in state.structures) {
            if (s.spec.id == "ROAD" || s.spec.id == "WALL") continue
            val otherFootprint = Rect(s.x, s.y - s.spec.worldHeight, s.x + s.spec.worldWidth, s.y)
            if (footprintRect.overlaps(otherFootprint)) {
                return false
            }
        }

        val minGX = (footprintRect.left / Constants.TILE_SIZE).toInt()
        val minGY = (footprintRect.top / Constants.TILE_SIZE).toInt()
        val maxGX = ((footprintRect.right - 1f) / Constants.TILE_SIZE).toInt()
        val maxGY = ((footprintRect.bottom - 1f) / Constants.TILE_SIZE).toInt()

        for (gx in minGX..maxGX) {
            for (gy in minGY..maxGY) {
                if (gx in 0 until Constants.MAP_TILES_W && gy in 0 until Constants.MAP_TILES_H) {
                    val t = state.tiles[gx][gy]
                    if (t != TileType.GRASS_LIGHT && t != TileType.GRASS_DARK && t != TileType.GRASS_DARK_TUFT && t != TileType.BUILDING_LOT && t != TileType.ROAD) return false
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
            currentState = if (s.spec.behavior == PlacementBehavior.STROKE) {
                unbakeStrokeSegmentFromWorld(currentState, s)
            } else {
                unbakeStructureFromWorld(currentState, s)
            }
        }

        val roadChanged = structuresToRemove.any { it.spec.id == "ROAD" }
        val structChanged = structuresToRemove.any { it.spec.id != "ROAD" }
        val newState = currentState.copy(
            roadRevision = if (roadChanged) currentState.roadRevision + 1 else currentState.roadRevision,
            structureRevision = if (structChanged) currentState.structureRevision + 1 else currentState.structureRevision,
            version = currentState.version + 1
        )
        _worldState.value = PoiSystem.recompute(newState)
        staticLayerId++
    }

    fun removeStructureAt(x: Float, y: Float) {
        val currentState = _worldState.value
        val structure = currentState.structures.find { s ->
            val footprint = Rect(s.x, s.y - s.spec.worldHeight, s.x + s.spec.worldWidth, s.y)
            footprint.contains(Offset(x, y))
        } ?: return

        val finalState = if (structure.spec.behavior == PlacementBehavior.STROKE) {
            unbakeStrokeSegmentFromWorld(currentState, structure)
        } else {
            unbakeStructureFromWorld(currentState, structure)
        }

        _worldState.value = finalState.copy(
            roadRevision = if (structure.spec.id == "ROAD") finalState.roadRevision + 1 else finalState.roadRevision,
            structureRevision = if (structure.spec.id != "ROAD") finalState.structureRevision + 1 else finalState.structureRevision,
            version = finalState.version + 1
        )
        staticLayerId++
    }


    private fun unbakeStructureFromWorld(currentState: WorldState, structure: Structure): WorldState {
        val spec = structure.spec
        val newTiles = currentState.copyTiles()

        val footprintRect = Rect(structure.x, structure.y - spec.worldHeight, structure.x + spec.worldWidth, structure.y)
        val influenceRect = Rect(
            left = footprintRect.left - OWNERSHIP_MARGIN_X * Constants.TILE_SIZE,
            top = footprintRect.top - OWNERSHIP_MARGIN_Y * Constants.TILE_SIZE,
            right = footprintRect.right + OWNERSHIP_MARGIN_X * Constants.TILE_SIZE,
            bottom = footprintRect.bottom + OWNERSHIP_MARGIN_Y * Constants.TILE_SIZE
        )

        unionDirtyRect(influenceRect)

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

        val newGrid = clearFromGrid(currentState.structureGrid, structure)

        return currentState.copy(
            structures = currentState.structures.filter { it.id != structure.id },
            tiles = newTiles,
            structureGrid = newGrid
        )
    }

    private fun unbakeStrokeSegmentFromWorld(currentState: WorldState, structure: Structure): WorldState {
        val spec = structure.spec
        val newTiles = currentState.copyTiles()
        val gx = (structure.x / Constants.TILE_SIZE).toInt()
        val gy = ((structure.y - spec.worldHeight) / Constants.TILE_SIZE).toInt()

        if (gx in 0 until Constants.MAP_TILES_W && gy in 0 until Constants.MAP_TILES_H) {
            newTiles[gx][gy] = sampleGrassType(gx, gy)
        }

        unionDirtyRect(Rect(structure.x, structure.y - spec.worldHeight, structure.x + spec.worldWidth, structure.y))

        val newGrid = clearFromGrid(currentState.structureGrid, structure)

        return currentState.copy(
            structures = currentState.structures.filter { it.id != structure.id },
            tiles = newTiles,
            structureGrid = newGrid
        )
    }

    private fun validateInvariants() {
        val state = _worldState.value
        state.structures.forEach { s ->
            val spec = s.spec
            val expectedTile = if (spec.id == "PLAZA") TileType.PLAZA else TileType.BUILDING_SOLID

            if (spec.id != "ROAD" && spec.id != "WALL") {
                val gx = (s.x / Constants.TILE_SIZE).toInt()
                val gy = ((s.y - spec.worldHeight) / Constants.TILE_SIZE).toInt()
                if (gx >= 0 && gx < Constants.MAP_TILES_W && gy >= 0 && gy < Constants.MAP_TILES_H) {
                    if (state.tiles[gx][gy] != expectedTile && state.tiles[gx][gy] != TileType.BUILDING_FOOTPRINT) {
                        Log.e("WorldManager", "Invariant broken: Structure ${s.id} at $gx,$gy not on footprint (expected $expectedTile, got ${state.tiles[gx][gy]})")
                    }
                }
            }
        }
    }
}
