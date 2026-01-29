package com.example.utopia.debug

import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import com.example.utopia.data.models.AgentRuntime
import com.example.utopia.util.Constants
import kotlin.math.roundToInt

@Composable
fun AgentLabelOverlay(
    agents: List<AgentRuntime>,
    cameraOffset: Offset
) {
    agents.forEach { agent ->
        AgentLabel(agent, cameraOffset)
    }
}

@Composable
private fun AgentLabel(
    agent: AgentRuntime,
    cameraOffset: Offset
) {
    val density = LocalDensity.current
    
    val screenX = agent.x + cameraOffset.x
    val screenY = agent.y + cameraOffset.y - Constants.TILE_SIZE * 0.6f

    val stallSeconds = agent.noProgressMs / 1000f
    val label = "${agent.state} | ${agent.currentIntent} (${stallSeconds.format(1)}s)"

    Text(
        text = label,
        color = if (agent.noProgressMs > 2000) Color.Red else Color.White,
        modifier = Modifier.offset {
            IntOffset(screenX.roundToInt(), screenY.roundToInt())
        }
    )
}

private fun Float.format(decimals: Int): String =
    "%.${decimals}f".format(this)
