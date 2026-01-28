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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.utopia.R
import com.example.utopia.data.models.*
import com.example.utopia.debug.AgentLabelOverlay
// REMOVED: import com.example.utopia.util.Constants

/**
 * The high-level orchestrator for the City screen.
 * Handles UI wiring, input plumbing, and delegates rendering to specific sub-systems.
 */
@Composable
fun CityScreen(viewModel: GameViewModel) {
    Log.d("StartupHeartbeat", "CityScreen - Recomposing")
    val worldState by viewModel.worldManager.worldState
    val pc = viewModel.placementController
    val camera = Camera2D(viewModel.cameraOffset)
    val latestWorldState by rememberUpdatedState(worldState)
    val latestCamera by rememberUpdatedState(camera)
    val latestPlacementState by rememberUpdatedState(pc.state)

    // Load Assets
    val grassBitmap = ImageBitmap.imageResource(R.drawable.grass)
    val roadBitmapAsset = ImageBitmap.imageResource(R.drawable.road)

    // Caches & Asset Management
    val groundBitmap = rememberGroundCache(worldState, grassBitmap)
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

    // Structure Assets
    val houseBitmap = ImageBitmap.imageResource(R.drawable.house1)
    val tavernBitmap = ImageBitmap.imageResource(R.drawable.tavern)
    val workshopBitmap = ImageBitmap.imageResource(R.drawable.workshop)
    val storeBitmap = ImageBitmap.imageResource(R.drawable.store)
    val plazaBitmap = ImageBitmap.imageResource(R.drawable.plaza)

    val structureAssets = remember(houseLabelPaint, houseBitmap, tavernBitmap, workshopBitmap, storeBitmap, plazaBitmap) {
        StructureAssets(
            houseLabelPaint = houseLabelPaint,
            houseBitmap = houseBitmap,
            tavernBitmap = tavernBitmap,
            workshopBitmap = workshopBitmap,
            storeBitmap = storeBitmap,
            plazaBitmap = plazaBitmap
        )
    }

    // Prop Assets
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
    val emojiFxAssets = remember(emojiPaint, affinityPaint) { EmojiFxAssets(emojiPaint, affinityPaint) }

    // UI Overlay State
    // REMOVED: var showSocialLedger by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF333333))
    ) {
        // Map Interaction Layer
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
                                        pc.beginPointer(pc.activeTool ?: StructureType.ROAD, position, viewModel.cameraOffset)
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

                                val hitAgent = stateSnapshot.agents
                                    .asSequence()
                                    .filter { agentHitBoundsWorld(it).contains(worldPos) }
                                    .maxByOrNull { it.y }

                                if (hitAgent != null) {
                                    viewModel.selectAgent(hitAgent.id)
                                    return@detectTapGestures
                                }

                                val hitBuilding = stateSnapshot.structures
                                    .asSequence()
                                    .filter { structureHitBoundsWorld(it).contains(worldPos) }
                                    .maxByOrNull { it.y + it.type.baselineWorld }

                                if (hitBuilding != null) {
                                    viewModel.selectBuilding(hitBuilding.id)
                                } else {
                                    viewModel.clearSelection()
                                }
                            }
                        },
                        onLongPress = { offset ->
                            if (latestPlacementState == PlacementState.IDLE) {
                                val worldPos = screenToWorld(offset, latestCamera)
                                val structure = latestWorldState.structures
                                    .filter { structureHitBoundsWorld(it).contains(worldPos) }
                                    .maxByOrNull { it.y + it.type.baselineWorld }
                                if (structure != null) {
                                    pc.startMove(structure)
                                }
                            }
                        }
                    )
                }
        ) {
            // Main World Rendering Surface
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
                drawCity(
                    worldState = worldState,
                    camera = camera,
                    groundBitmap = groundBitmap,
                    roadBitmap = roadBitmap,
                    roadAsset = roadBitmapAsset,
                    structureAssets = structureAssets,
                    propAssets = propAssets,
                    emojiFxAssets = emojiFxAssets,
                    agentNameById = agentNameById,
                    timeMs = System.currentTimeMillis(),
                    liveRoadTiles = pc.liveRoadTiles,
                    navGrid = viewModel.navGrid,
                    showNavGrid = viewModel.showNavGridDebug
                )

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

            // Agent Label Overlay
            if (viewModel.showAgentLabels) {
                AgentLabelOverlay(
                    agents = worldState.agents,
                    cameraOffset = viewModel.cameraOffset
                )
            }
        }

        // REMOVED: Environmental Effects Layer (Atmospheric Tints)

        // HUD Layer (Stats, Tools, Overlays)
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
                TownStatsStrip(viewModel, onSocialLedgerToggle = {}) // Cleaned up lambda
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

        // REMOVED: SocialLedgerOverlay display logic
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
