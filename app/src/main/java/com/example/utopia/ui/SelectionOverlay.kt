package com.example.utopia.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.utopia.data.models.Structure

@Composable
fun SelectionOverlay(viewModel: GameViewModel) {
    val worldState = viewModel.worldManager.worldState.value
    val buildingId = viewModel.selectedBuildingId
    val camera = Camera2D(viewModel.cameraOffset)

    if (buildingId != null) {
        val building = worldState.structures.find { it.id == buildingId }
        if (building != null) {
            val screenPos = worldToScreen(Offset(building.x, building.y), camera)
            BuildingInfo(building, screenPos)
        }
    }
}

@Composable
fun BuildingInfo(building: Structure, screenPos: Offset) {
    Surface(
        color = Color.DarkGray.copy(alpha = 0.9f),
        shape = androidx.compose.material3.MaterialTheme.shapes.small,
        modifier = Modifier.offset(pxToDp(screenPos.x), pxToDp(screenPos.y - 60f))
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(building.customName ?: building.type.name, color = Color.White, fontSize = 12.sp)
            // REMOVED: Job slot and worker display logic

            if (building.type.capacity > 0) {
                Row {
                    repeat(building.type.capacity) { i ->
                        Text(if (i < building.residents.size) "🏠" else "⚪", fontSize = 14.sp)
                    }
                }
            }
        }
    }
}
