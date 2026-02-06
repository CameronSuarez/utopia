package com.example.utopia.ui

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.utopia.data.StructureRegistry
import com.example.utopia.data.models.StructureSpec
import com.example.utopia.util.Constants
import com.example.utopia.util.WorldGridMath
import com.example.utopia.data.models.PlacementBehavior
import kotlin.math.absoluteValue

@Composable
fun BuildToolbar(pc: PlacementController, cameraOffset: Offset) {
    Surface(
        color = Color.Black.copy(alpha = 0.8f),
        modifier = Modifier.fillMaxWidth().height(100.dp)
    ) {
        LazyRow(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(StructureRegistry.all()) { spec ->
                var itemPosInRoot by remember { mutableStateOf(Offset.Zero) }

                Surface(
                    color = if (pc.activeTool?.id == spec.id) Color.DarkGray else Color.Gray,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .size(80.dp)
                        .padding(4.dp)
                        .onGloballyPositioned { coords ->
                            itemPosInRoot = coords.positionInRoot()
                        }
                        .pointerInput(spec) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                Log.d("CityUI", "TOOLBAR down: type=${spec.id} pos=${down.position}")
                                var isDragging = false
                                var isScrolling = false
                                val slop = Constants.TOOLBAR_DRAG_SLOP
                                var totalDrag = Offset.Zero

                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.find { it.id == down.id } ?: break

                                    if (!change.pressed) {
                                        if (isDragging) {
                                            Log.d("CityUI", "TOOLBAR release (drag end)")
                                            pc.endPointer()
                                        } else {
                                            Log.d("CityUI", "TOOLBAR release (tap): selecting ${spec.id}")
                                            pc.selectTool(spec)
                                        }
                                        break
                                    }

                                    val currentPosInRoot = itemPosInRoot + change.position

                                    if (!isDragging && !isScrolling) {
                                        totalDrag += change.position - down.position
                                        if (totalDrag.y.absoluteValue > slop) {
                                            Log.d("CityUI", "TOOLBAR vertical-lock: tool=${spec.id} pos=$currentPosInRoot")
                                            isDragging = true
                                            pc.beginPointer(spec, currentPosInRoot, cameraOffset)
                                            change.consume()
                                        } else if (totalDrag.x.absoluteValue > slop) {
                                            isScrolling = true
                                        }
                                    }

                                    if (isDragging) {
                                        pc.movePointer(currentPosInRoot, cameraOffset)
                                        change.consume()
                                    }
                                 }
                            }
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(spec.id, color = Color.White, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun TrashCan(pc: PlacementController) {
    Surface(
        onClick = { pc.cancel() },
        color = if (pc.activeTool != null) Color.Red else Color.Gray.copy(alpha = 0.5f),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.size(80.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text("CANCEL", color = Color.White)
        }
    }
}

@Composable
fun WorldGhostPreview(pc: PlacementController, camera: Camera2D) {
    val spec = pc.activeTool ?: return
    val worldPos = pc.worldPos ?: return
    
    val ghostWorldSize: Size
    val ghostAnchorWorld: Offset

    ghostWorldSize = Size(spec.worldWidth, spec.worldHeight)
    ghostAnchorWorld = worldPos
    

    val screenPos = worldToScreen(ghostAnchorWorld, camera)
    val screenSize = worldSizeToScreen(ghostWorldSize, camera)

    val color = when (pc.state) {
        PlacementState.DRAGGING_GHOST -> {
            if (pc.isValid) Color.Cyan.copy(alpha = 0.5f) else Color.Red.copy(alpha = 0.5f)
        }
        else -> {
            if (pc.isValid) Color.Cyan.copy(alpha = 0.4f) else Color.Red.copy(alpha = 0.4f)
        }
    }

    Box(
        modifier = Modifier
            .offset(pxToDp(screenPos.x), pxToDp(screenPos.y))
            .size(pxToDp(screenSize.width), pxToDp(screenSize.height))
            .background(color)
    )
}
