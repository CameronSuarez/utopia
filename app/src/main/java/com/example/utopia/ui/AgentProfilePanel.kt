package com.example.utopia.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.utopia.data.models.AgentIntent
import com.example.utopia.data.models.AgentRuntime
import com.example.utopia.data.models.WorldState
import com.example.utopia.domain.AgentDecisionSystem
import kotlin.math.roundToInt

@Composable
fun AgentProfilePanel(
    agent: AgentRuntime,
    allAgents: List<AgentRuntime>,
    worldState: WorldState,
    onClose: () -> Unit
) {
    val scrollState = rememberScrollState()

    BoxWithConstraints {
        val preferredWidth = maxWidth * 0.32f
        val panelWidth = preferredWidth.coerceIn(280.dp, 360.dp)

        Surface(
            color = Color(0xFF1B1B1B),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .padding(top = 16.dp, end = 16.dp, bottom = 16.dp)
                .fillMaxHeight()
                .width(panelWidth)
        ) {
            UIContent(agent, allAgents.size, worldState, scrollState, onClose)
        }
    }
}

@Composable
private fun UIContent(
    agent: AgentRuntime,
    agentCount: Int,
    worldState: WorldState,
    scrollState: androidx.compose.foundation.ScrollState,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        HeaderRow(onClose = onClose)
        UISpacer(12)
        IdentitySection(agent)
        UISpacer(16)
        DecisionSection(agent, worldState)
        UISpacer(16)
        AssignmentSection(agent)
        UISpacer(16)
        PositionSection(agent)
        UISpacer(16)
        NeedsSection(agent)
        UISpacer(16)
        PressuresSection(agent)
        UISpacer(16)
        WorldSection(worldState, agentCount)
    }
}

@Composable
private fun UISpacer(height: Int) {
    Spacer(modifier = Modifier.height(height.dp))
}

@Composable
private fun HeaderRow(onClose: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Profile", color = Color.White, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.weight(1f))
        IconButton(onClick = onClose) {
            Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color.White)
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium)
    Spacer(modifier = Modifier.height(6.dp))
}

@Composable
private fun InfoRow(label: String, value: String, valueColor: Color = Color.White) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.LightGray, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        Text(value, color = valueColor, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun IdentitySection(agent: AgentRuntime) {
    Column {
        SectionTitle("Identity")
        InfoRow("name", agent.name)
        InfoRow("id", agent.id)
        InfoRow("role", "Villager")
    }
}

@Composable
private fun DecisionSection(agent: AgentRuntime, worldState: WorldState) {
    val intentLabel = agent.currentIntent::class.simpleName ?: "Unknown"
    val carried = agent.carriedItem?.let { "${it.type} x${it.quantity}" } ?: "none"

    Column {
        SectionTitle("Decision")
        InfoRow("state", agent.state.name)
        InfoRow("intent", intentLabel, Color.Cyan)
        InfoRow("carriedItem", carried)
    }
}

@Composable
private fun AssignmentSection(agent: AgentRuntime) {
    Column {
        SectionTitle("Assignments")
        val workplace = agent.workplaceId?.take(8) ?: "none"
        val home = agent.homeId?.take(8) ?: "none"
        InfoRow("workplaceId", workplace)
        InfoRow("homeId", home)
    }
}

@Composable
private fun PositionSection(agent: AgentRuntime) {
    Column {
        SectionTitle("Position")
        InfoRow("worldPos", "(${agent.x.roundToInt()}, ${agent.y.roundToInt()})")
        InfoRow("gridPos", "(${agent.gridX}, ${agent.gridY})")
        InfoRow("velocity", "(%.1f, %.1f)".format(agent.velocity.x, agent.velocity.y))
    }
}

@Composable
private fun NeedsSection(agent: AgentRuntime) {
    Column {
        SectionTitle("Needs")
        InfoRow("sleep", "${agent.needs.sleep.roundToInt()}%")
        InfoRow("stability", "${agent.needs.stability.roundToInt()}%")
        InfoRow("social", "${agent.needs.social.roundToInt()}%")
        InfoRow("fun", "${agent.needs.`fun`.roundToInt()}%")
    }
}

@Composable
private fun PressuresSection(agent: AgentRuntime) {
    Column {
        SectionTitle("Pressures")

        if (agent.transientPressures.isEmpty()) {
            Text("No active pressures", color = Color.Gray, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            return
        }

        val sortedPressures = agent.transientPressures.toList().sortedByDescending { it.second }
        sortedPressures.forEach { (intent, value) ->
            val label = when (intent) {
                is AgentIntent.Work -> "Work"
                else -> intent::class.simpleName ?: "Unknown"
            }
            val isWinner = intent == agent.currentIntent
            InfoRow(
                label,
                "%.2f%s".format(value, if (isWinner) " *" else ""),
                if (isWinner) Color.Cyan else Color.White
            )
        }
    }
}

@Composable
private fun WorldSection(worldState: WorldState, agentCount: Int) {
    val workAvailable = AgentDecisionSystem.hasAvailableWorkplace(worldState)
    Column {
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        Text("World", fontWeight = FontWeight.Bold, color = Color.Cyan, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        InfoRow("agents", agentCount.toString(), Color.Cyan)
        InfoRow(
            "workAvailable",
            workAvailable.toString(),
            if (workAvailable) Color.Green else Color.Red
        )
    }
}
