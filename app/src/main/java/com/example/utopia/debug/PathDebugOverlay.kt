package com.example.utopia.debug

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.example.utopia.data.models.AgentRuntime
import com.example.utopia.util.Constants

fun DrawScope.drawAgentPaths(
    agents: List<AgentRuntime>,
    cameraOffset: Offset
) {
    agents.forEach { agent ->
        val path = agent.pathTiles
        if (path.size < 2) return@forEach

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
}
