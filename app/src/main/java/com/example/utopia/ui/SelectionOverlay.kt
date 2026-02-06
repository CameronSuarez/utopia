package com.example.utopia.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.utopia.data.models.Structure
import com.example.utopia.data.models.WorldState
import com.example.utopia.domain.AgentDecisionSystem
import com.example.utopia.domain.EconomySystem
import com.example.utopia.util.Constants

@Composable
fun SelectionOverlay(viewModel: GameViewModel) {
    val worldState = viewModel.worldManager.worldState.value
    val buildingId = viewModel.selectedStructureId
    val camera = Camera2D(viewModel.cameraOffset)

    if (buildingId != null) {
        val building = worldState.structures.find { it.id == buildingId }
        if (building != null) {
            val screenPos = worldToScreen(Offset(building.x, building.y), camera)
            BuildingInfoPanel(building, worldState, screenPos)
        }
    }
}

@Composable
fun BuildingInfoPanel(building: Structure, worldState: WorldState, screenPos: Offset) {
    Surface(
        color = Color.DarkGray.copy(alpha = 0.95f),
        shape = androidx.compose.material3.MaterialTheme.shapes.small,
        modifier = Modifier
            .offset(pxToDp(screenPos.x), pxToDp(screenPos.y - 150f))
            .width(280.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            StatusHeader(building)
            Spacer(modifier = Modifier.height(8.dp))
            ProductionInfo(building)
            Spacer(modifier = Modifier.height(8.dp))
            ConstructionInfo(building)
            Spacer(modifier = Modifier.height(8.dp))
            InventoryInfo(building)
            Spacer(modifier = Modifier.height(8.dp))
            WorkersInfo(building, worldState)
            Spacer(modifier = Modifier.height(8.dp))
            DebugInfo(building, worldState)
        }
    }
}

@Composable
fun StatusHeader(building: Structure) {
    val gx = (building.x / Constants.TILE_SIZE).toInt()
    val gy = (building.y / Constants.TILE_SIZE).toInt()

    val state: String
    val reason: String

    if (!building.isComplete) {
        state = "Under Construction"
        reason = "Waiting for builders and materials."
    } else if (building.spec.produces.isNotEmpty()) {
        val inventoryFull = EconomySystem.isOutputCapped(building)
        val missingInputs = EconomySystem.isMissingInputs(building)

        if (inventoryFull) {
            state = "Blocked"
            reason = "Storage is full."
        } else if (missingInputs) {
            state = "Blocked"
            reason = "Missing input materials."
        } else if (building.workers.isEmpty() && building.spec.capacity > 0) {
            state = "Idle"
            reason = "No workers assigned."
        } else {
            state = "Producing"
            reason = "Working as expected."
        }
    } else {
        state = "Idle"
        reason = "Not a production building."
    }

    Text(building.customName ?: building.spec.id, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    Text("ID: ${building.id.substring(0, 8)} at ($gx, $gy)", color = Color.Gray, fontSize = 10.sp)
    Text("State: $state", color = Color.White, fontSize = 12.sp)
    if (building.spec.produces.isNotEmpty() || building.spec.consumes.isNotEmpty()) {
        val inputs = if (building.spec.consumes.isNotEmpty()) {
            "in: " + building.spec.consumes.keys.joinToString()
        } else {
            "in: none"
        }
        val outputs = if (building.spec.produces.isNotEmpty()) {
            "out: " + building.spec.produces.keys.joinToString()
        } else {
            "out: none"
        }
        Text("Recipe: $inputs / $outputs", color = Color.White, fontSize = 12.sp)
    }
    Text("Workers: ${building.workers.size} / ${building.spec.capacity}", color = Color.White, fontSize = 12.sp)
    Text(reason, color = Color.Yellow, fontSize = 10.sp)
}

@Composable
fun ProductionInfo(building: Structure) {
    if (building.spec.produces.isEmpty() && building.spec.consumes.isEmpty()) return

    Column {
        Text("Production", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 12.sp)

        if (building.spec.productionIntervalMs > 0) {
            val progress = (building.productionAccMs.toFloat() / building.spec.productionIntervalMs) * 100
            val nextOutputMs = building.spec.productionIntervalMs - building.productionAccMs

            Text("Interval: ${building.spec.productionIntervalMs}ms", fontSize = 10.sp, color = Color.LightGray)
            building.spec.maxEffectiveWorkers?.let {
                Text("Max effective workers: $it", fontSize = 10.sp, color = Color.LightGray)
            }
            Text("Accumulator: ${building.productionAccMs}ms (${"%.1f".format(progress)}%)", fontSize = 10.sp, color = Color.LightGray)
            Text("Next output in: ${nextOutputMs}ms", fontSize = 10.sp, color = Color.LightGray)
        } else {
            Text("Interval: none", fontSize = 10.sp, color = Color.LightGray)
        }
    }
}

@Composable
fun ConstructionInfo(building: Structure) {
    if (building.isComplete) return

    Column {
        Text("Construction", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 12.sp)
        Text("Progress: ${"%.1f".format(building.buildProgress)}%", fontSize = 10.sp, color = Color.LightGray)
        Text("Build started: ${building.buildStarted}", fontSize = 10.sp, color = Color.LightGray)
        if (building.spec.buildCost.isNotEmpty()) {
            building.spec.buildCost.forEach { (resourceType, required) ->
                val delivered = building.inventory[resourceType] ?: 0
                Text("$resourceType: $delivered / $required", fontSize = 10.sp, color = Color.LightGray)
            }
        }
    }
}

@Composable
fun InventoryInfo(building: Structure) {
    if (building.spec.inventoryCapacity.isEmpty()) return

    Column {
        Text("Inventory", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 12.sp)
        building.spec.inventoryCapacity.keys.forEach { resourceType ->
            val current = building.inventory[resourceType] ?: 0
            val max = building.spec.inventoryCapacity[resourceType] ?: 0
            Text("$resourceType: $current / $max", fontSize = 10.sp, color = Color.LightGray)
        }
    }
}

@Composable
fun WorkersInfo(building: Structure, worldState: WorldState) {
    if (building.spec.capacity <= 0) return

    val lotRect = building.getInfluenceRect()
    val agentsInLotCount = worldState.agents.count { lotRect.contains(it.position.toOffset()) }

    Column {
        Text("Workers", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 12.sp)
        Text("Assigned: ${building.workers.size} / ${building.spec.capacity}", fontSize = 10.sp, color = Color.LightGray)
        Text("Agents in Lot: $agentsInLotCount", fontSize = 10.sp, color = Color.LightGray)

        if (building.workers.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            building.workers.forEach { workerId ->
                val agent = worldState.agents.find { it.id == workerId }
                if (agent != null) {
                    val workplaceMatch = agent.workplaceId == building.id
                    Text(
                        "▶ ${agent.name} (${agent.id.substring(0, 4)})",
                        fontSize = 10.sp,
                        color = if (workplaceMatch) Color.Green else Color.Red
                    )
                    Text(
                        "   - Intent: ${agent.currentIntent::class.simpleName}",
                        fontSize = 9.sp,
                        color = Color.LightGray
                    )
                    Text(
                        "   - Agent says workplace: ${agent.workplaceId?.substring(0, 4) ?: "null"}",
                        fontSize = 9.sp,
                        color = if (workplaceMatch) Color.Green else Color.Red
                    )
                } else {
                    Text("▶ ${workerId.substring(0, 4)} (Agent data not found)", fontSize = 10.sp, color = Color.Red)
                }
            }
        }
    }
}

@Composable
fun DebugInfo(building: Structure, worldState: WorldState) {
    val isEligible = building.isComplete &&
            (building.spec.produces.isNotEmpty() || building.spec.consumes.isNotEmpty()) &&
            building.spec.capacity > 0 &&
            building.workers.size < building.spec.capacity &&
            AgentDecisionSystem.canWorkAt(building)
    val openSlots = building.spec.capacity - building.workers.size

    Column {
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        Text("Debug Info", fontWeight = FontWeight.Bold, color = Color.Cyan, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        Text("eligibleWorkplace: $isEligible", fontSize = 9.sp, color = if (isEligible) Color.Green else Color.Red, fontFamily = FontFamily.Monospace)
        Text("openSlots: $openSlots", fontSize = 9.sp, color = Color.Cyan, fontFamily = FontFamily.Monospace)
        Text("Assigned IDs: [${building.workers.joinToString { it.substring(0, 4) }}]", fontSize = 9.sp, color = Color.Cyan, fontFamily = FontFamily.Monospace)
        val lotRect = building.getInfluenceRect()
        Text("Lot Rect: [${lotRect.left.toInt()}, ${lotRect.top.toInt()}, ${lotRect.right.toInt()}, ${lotRect.bottom.toInt()}]", fontSize = 9.sp, color = Color.Cyan, fontFamily = FontFamily.Monospace)
        worldState.agents.find { it.workplaceId == building.id }?.let { agent ->
             Text("Agent '${agent.name}' Pos: (${agent.position.x.toInt()}, ${agent.position.y.toInt()})", fontSize = 9.sp, color = Color.Cyan, fontFamily = FontFamily.Monospace)
        }
        Text("TILE_SIZE: ${Constants.TILE_SIZE}", fontSize = 9.sp, color = Color.Cyan, fontFamily = FontFamily.Monospace)
    }
}


