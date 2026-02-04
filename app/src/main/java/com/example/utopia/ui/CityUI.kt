package com.example.utopia.ui

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.utopia.R
import com.example.utopia.data.StructureRegistry
import com.example.utopia.data.models.*
import com.example.utopia.debug.AgentLabelOverlay
import com.example.utopia.util.Constants

@Composable
fun CityScreen(viewModel: GameViewModel) {
    Log.d("StartupHeartbeat", "CityScreen - Recomposing")
    val worldState by viewModel.worldManager.worldState
    val pc = viewModel.placementController
    val camera = Camera2D(viewModel.cameraOffset)
    val latestWorldState by rememberUpdatedState(worldState)
    val latestCamera by rememberUpdatedState(camera)
    val latestPlacementState by rememberUpdatedState(pc.state)

    val globalResources by viewModel.globalResources.collectAsState()
    val selectedStructureInfo by viewModel.selectedStructureInfo.collectAsState()

    val grass1 = ImageBitmap.imageResource(R.drawable.grass1)
    val grass2 = ImageBitmap.imageResource(R.drawable.grass2)
    val grass3 = ImageBitmap.imageResource(R.drawable.grass3)
    val grass4 = ImageBitmap.imageResource(R.drawable.grass4)
    val grass5 = ImageBitmap.imageResource(R.drawable.grass5)
    val grass6 = ImageBitmap.imageResource(R.drawable.grass6)
    val grassBitmaps = listOf(grass1, grass2, grass3, grass4, grass5, grass6)
    val roadBitmapAsset = ImageBitmap.imageResource(R.drawable.road)

    val groundBitmap = rememberGroundCache(worldState, grassBitmaps)
    val roadBitmap = rememberRoadCache(worldState, roadBitmapAsset)
    val agentNameById = remember(worldState.agents) { worldState.agents.associate { it.id to it.name } }

    val houseLabelPaint = remember {
        android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 20f
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
            setShadowLayer(4f, 0f, 2f, android.graphics.Color.BLACK)
        }
    }

    val houseBitmap = ImageBitmap.imageResource(R.drawable.house1)
    val tavernBitmap = ImageBitmap.imageResource(R.drawable.tavern)
    val workshopBitmap = ImageBitmap.imageResource(R.drawable.workshop)
    val storeBitmap = ImageBitmap.imageResource(R.drawable.store)
    val plazaBitmap = ImageBitmap.imageResource(R.drawable.plaza)
    val constructionSiteBitmap = ImageBitmap.imageResource(R.drawable.construction_site)
    val lumberjackHutBitmap = ImageBitmap.imageResource(R.drawable.lumberjack_hut)

    val structureAssets = remember(houseLabelPaint, houseBitmap, tavernBitmap, workshopBitmap, storeBitmap, plazaBitmap, constructionSiteBitmap, lumberjackHutBitmap) {
        StructureAssets(
            houseLabelPaint = houseLabelPaint,
            houseBitmap = houseBitmap,
            tavernBitmap = tavernBitmap,
            workshopBitmap = workshopBitmap,
            storeBitmap = storeBitmap,
            plazaBitmap = plazaBitmap,
            constructionSiteBitmap = constructionSiteBitmap,
            lumberjackHutBitmap = lumberjackHutBitmap
        )
    }

    val treeBitmap = ImageBitmap.imageResource(R.drawable.tree)

    val propAssets = remember(treeBitmap) {
        PropAssets(
            tree1 = treeBitmap
        )
    }

    val emojiPaint = remember {
        android.graphics.Paint().apply {
            textSize = 24f
            textAlign = android.graphics.Paint.Align.CENTER
        }
    }
    val affinityPaint = remember {
        android.graphics.Paint().apply {
            textSize = 32f
            textAlign = android.graphics.Paint.Align.CENTER
        }
    }
    val portraitCache = remember { PortraitCache() }
    val emojiFxAssets = remember(emojiPaint, affinityPaint, portraitCache) { EmojiFxAssets(emojiPaint, affinityPaint, portraitCache) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF333333))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(0f)
                .pointerInput(pc.state) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.first()
                            val position = change.position

                            when (event.type) {
                                PointerEventType.Press -> {
                                    if (pc.state != PlacementState.IDLE) {
                                        pc.beginPointer(pc.activeTool ?: StructureRegistry.get("ROAD"), position, viewModel.cameraOffset)
                                        change.consume()
                                    }
                                }
                                PointerEventType.Move -> {
                                    if (pc.state != PlacementState.IDLE) {
                                        pc.movePointer(position, viewModel.cameraOffset)
                                        change.consume()
                                    }
                                }
                                PointerEventType.Release -> {
                                    if (pc.state != PlacementState.IDLE) {
                                        pc.endPointer()
                                        change.consume()
                                    }
                                }
                            }
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { offset ->
                            if (latestPlacementState == PlacementState.IDLE) {
                                val worldPos = screenToWorld(offset, latestCamera)
                                val stateSnapshot = latestWorldState

                                val gx = (worldPos.x / Constants.TILE_SIZE).toInt()
                                val gy = (worldPos.y / Constants.TILE_SIZE).toInt()

                                if (gx !in 0 until Constants.MAP_TILES_W || gy !in 0 until Constants.MAP_TILES_H) {
                                    viewModel.clearSelection()
                                    return@detectTapGestures
                                }

                                val structureId = stateSnapshot.structureGrid[gx][gy]
                                if (structureId != null) {
                                    viewModel.selectStructure(structureId)
                                    return@detectTapGestures
                                }

                                val hitAgent = stateSnapshot.agents
                                    .asSequence()
                                    .filter { agentHitBoundsWorld(it).contains(worldPos) }
                                    .maxByOrNull { it.y }

                                if (hitAgent != null) {
                                    viewModel.selectAgent(hitAgent.id)
                                    return@detectTapGestures
                                }
                                
                                viewModel.clearSelection()
                            }
                        },
                        onLongPress = { offset ->
                            if (latestPlacementState == PlacementState.IDLE) {
                                val worldPos = screenToWorld(offset, latestCamera)
                                val structure = latestWorldState.structures
                                    .filter { structureHitBoundsWorld(it).contains(worldPos) }
                                    .maxByOrNull { it.y + it.spec.baselineWorld }
                                if (structure != null) {
                                    pc.startMove(structure)
                                }
                            }
                        }
                    )
                }
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(pc.state) {
                        if (pc.state == PlacementState.IDLE) {
                            detectDragGestures { _, dragAmount ->
                                viewModel.onMoveCamera(dragAmount)
                            }
                        }
                    }
            ) {
                val renderContext = RenderContext(
                    camera = camera,
                    timeMs = System.currentTimeMillis(),
                    groundBitmap = groundBitmap,
                    roadBitmap = roadBitmap,
                    roadAsset = roadBitmapAsset,
                    structureAssets = structureAssets,
                    propAssets = propAssets,
                    emojiFxAssets = emojiFxAssets,
                    agentNameById = agentNameById,
                    showNavGrid = viewModel.showNavGridDebug,
                    navGrid = viewModel.navGrid
                )
                val sceneSnapshot = SceneSnapshot(
                    worldState = worldState,
                    liveRoadTiles = pc.liveRoadTiles,
                    visibleWorldObjectsYSorted = buildVisibleWorldObjects(renderContext, SceneSnapshot(worldState), size)
                )

                drawCity(renderContext, sceneSnapshot)

                drawDebugLayers(
                    viewModel = viewModel,
                    agents = worldState.agents,
                    structures = worldState.structures,
                    props = worldState.props,
                    tiles = worldState.tiles,
                    camera = camera
                )
            }

            if (pc.state != PlacementState.IDLE) {
                WorldGhostPreview(pc, camera)
            }

            SelectionOverlay(viewModel)

            if (viewModel.showAgentLabels) {
                AgentLabelOverlay(
                    agents = worldState.agents,
                    cameraOffset = viewModel.cameraOffset
                )
            }
        }

        val brightness = when (viewModel.currentPhaseName) {
            "Morning", "Evening" -> 0.9f
            "Afternoon" -> 1.0f
            "Night" -> 0.7f
            else -> 1.0f
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(5f)
                .background(Color.Black.copy(alpha = 1f - brightness))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(10f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TownStatsStrip(viewModel, onSocialLedgerToggle = {})
                    ResourceHud(resources = globalResources)
                }
                DebugPanel(viewModel = viewModel, modifier = Modifier.padding(end = 8.dp))
                TrashCan(pc)
            }

            PlacementStatus(pc)

            Spacer(modifier = Modifier.weight(1f))
            BuildToolbar(pc, viewModel.cameraOffset)
        }

        worldState.agents.find { it.id == viewModel.selectedAgentId }?.let { selectedAgent ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(9f),
                contentAlignment = Alignment.TopEnd
            ) {
                AgentProfilePanel(
                    agent = selectedAgent,
                    allAgents = worldState.agents,
                    onClose = { viewModel.clearSelection() }
                )
            }
        }

        selectedStructureInfo?.let { info ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(9f),
                contentAlignment = Alignment.TopEnd
            ) {
                StructureProfilePanel(
                    info = info,
                    onClose = { viewModel.clearSelection() }
                )
            }
        }
    }
}

@Composable
fun ResourceHud(resources: Map<ResourceType, Int>) {
    Surface(
        color = Color.Black.copy(alpha = 0.5f),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ResourceType.entries.forEach { type ->
                Text(
                    text = "${type.name}: ${resources[type] ?: 0}",
                    color = Color.White,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun StructureProfilePanel(info: SelectedStructureInfo, onClose: () -> Unit) {
    Card(
        modifier = Modifier
            .padding(16.dp)
            .width(280.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = info.structure.spec.id, style = MaterialTheme.typography.titleMedium)
                Button(onClick = onClose) {
                    Text("X")
                }
            }
            Divider(modifier = Modifier.padding(vertical = 8.dp))

            when (info) {
                is SelectedStructureInfo.Residence -> {
                    Text("Residents:", fontWeight = FontWeight.Bold)
                    info.residents.forEach { Text(it) }
                }
                is SelectedStructureInfo.Workplace -> {
                    Text("Inventory:", fontWeight = FontWeight.Bold)
                    info.inventory.forEach { (resource, count) ->
                        Text("${resource.name}: $count")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Workers:", fontWeight = FontWeight.Bold)
                    info.workers.forEach { Text(it) }
                }
                is SelectedStructureInfo.ConstructionSite -> {
                    Text("Under Construction", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Required Resources:", fontWeight = FontWeight.Bold)
                    info.required.forEach { (resource, count) ->
                        Text("${resource.name}: ${info.delivered[resource] ?: 0} / $count")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Builders:", fontWeight = FontWeight.Bold)
                    info.workers.forEach { Text(it) }
                }
            }
        }
    }
}


@Composable
private fun PlacementStatus(pc: PlacementController) {
    val statusText = when (pc.state) {
        PlacementState.BRUSHING -> "BRUSHING..."
        PlacementState.DRAGGING_GHOST -> if (pc.movingStructure != null) "DRAG TO MOVE..." else "RELEASE TO PLACE"
        PlacementState.ARMED_STROKE -> "TAP TO START"
        else -> ""
    }

    AnimatedVisibility(
        visible = statusText.isNotEmpty(),
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(contentAlignment = Alignment.Center) {
            Surface(
                color = if (pc.state == PlacementState.DRAGGING_GHOST && pc.movingStructure != null) Color.Magenta.copy(alpha = 0.8f) else Color.Cyan.copy(alpha = 0.8f),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.padding(8.dp)
            ) {
                Text(
                    statusText,
                    color = Color.Black,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        }
    }
}
