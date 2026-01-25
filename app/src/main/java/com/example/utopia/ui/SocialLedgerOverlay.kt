package com.example.utopia.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.utopia.data.models.AgentRuntime
import kotlin.math.abs

@Composable
fun SocialLedgerOverlay(viewModel: GameViewModel, onClose: () -> Unit) {
    val worldState by viewModel.worldManager.worldState
    val agents = worldState.agents

    // Sort relationships by absolute affinity
    val sortedRels = worldState.relationships.entries
        .sortedByDescending { abs(it.value.toInt()) }
        .take(20)

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
            .zIndex(20f),
        color = Color.Black.copy(alpha = 0.9f),
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Social Ledger", color = Color.White, style = MaterialTheme.typography.headlineSmall)
                IconButton(onClick = onClose) {
                    Text("✕", color = Color.White, fontSize = 20.sp)
                }
            }

            HorizontalDivider(color = Color.Gray, modifier = Modifier.padding(vertical = 8.dp))

            if (sortedRels.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No significant relationships yet...", color = Color.Gray)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(sortedRels) { entry ->
                        val combinedId = entry.key
                        val affinity = entry.value.toInt()

                        val idA = (combinedId shr 32).toInt()
                        val idB = (combinedId and 0xFFFFFFFFL).toInt()

                        val agentA = agents.find { it.shortId == idA }
                        val agentB = agents.find { it.shortId == idB }

                        if (agentA != null && agentB != null) {
                            RelationshipRow(agentA, agentB, affinity)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RelationshipRow(agentA: AgentRuntime, agentB: AgentRuntime, affinity: Int) {
    val color = when {
        affinity > 1 -> Color(0xFF4CAF50)
        affinity > 0 -> Color(0xFF8BC34A)
        affinity < -1 -> Color(0xFFF44336)
        affinity < 0 -> Color(0xFFFF9800)
        else -> Color.Gray
    }

    val relationshipTitle = when (affinity) {
        3 -> "Best Friends"
        2 -> "Friends"
        1 -> "Friendly"
        0 -> "Acquaintances"
        -1 -> "Cold"
        -2 -> "Dislike"
        -3 -> "Hostile"
        else -> "Neutral"
    }

    Surface(
        color = Color.DarkGray.copy(alpha = 0.5f),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("${agentA.name} & ${agentB.name}", color = Color.White, fontSize = 14.sp)
                Text(relationshipTitle, color = color, fontSize = 12.sp)
            }
            Text(
                text = if (affinity >= 0) "+$affinity" else "$affinity",
                color = color,
                style = MaterialTheme.typography.titleLarge
            )
        }
    }
}
