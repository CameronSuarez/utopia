package com.example.utopia.debug

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import com.example.utopia.data.models.AgentRuntime
import com.example.utopia.util.Constants

@Composable
fun PathDebugOverlay(
    agents: List<AgentRuntime>,
    cameraOffset: Offset,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        agents.forEach { agent ->
            drawAgentPath(agent, cameraOffset)
        }
    }
}

private fun DrawScope.drawAgentPath(
    agent: AgentRuntime,
    cameraOffset: Offset
) {
    val path = agent.pathTiles
    if (path.size < 2) return

    var prev: Offset? = null

    for (i in agent.pathIndex until path.size) {
        val packed = path[i]
        val gx = packed shr 16
        val gy = packed and 0xFFFF

        val worldPos = Offset(
            gx * Constants.TILE_SIZE + Constants.TILE_SIZE * 0.5f + cameraOffset.x,
            gy * Constants.TILE_SIZE + Constants.TILE_SIZE * 0.5f + cameraOffset.y
        )

        prev?.let {
            drawLine(
                color = if (i == agent.pathIndex) Color.Green else Color.Yellow,
                start = it,
                end = worldPos,
                strokeWidth = 2.dp.toPx()
            )
        }

        prev = worldPos
    }
}
