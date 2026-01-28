package com.example.utopia.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import com.example.utopia.data.models.AgentRuntime
import com.example.utopia.data.models.WorldState

data class EmojiFxAssets(
    val emojiPaint: android.graphics.Paint,
    val affinityPaint: android.graphics.Paint
)

fun DrawScope.drawEmojiFx(
    worldState: WorldState,
    camera: Camera2D,
    assets: EmojiFxAssets,
    timeMs: Long
) {
    worldState.agents.forEach { drawAgentEmojiFxItem(it, worldState, camera, assets, timeMs) }
}

fun DrawScope.drawAgentEmojiFxItem(
    agent: AgentRuntime,
    worldState: WorldState,
    camera: Camera2D,
    assets: EmojiFxAssets,
    timeMs: Long
) {
    val screenPos = worldToScreen(Offset(agent.x, agent.y), camera)
    val scale = 2.2f * camera.zoom
    val cullPadX = 40f * scale
    val cullPadY = 60f * scale
    if (screenPos.x < -cullPadX || screenPos.x > size.width + cullPadX || screenPos.y < -cullPadY || screenPos.y > size.height + cullPadY) return

    // Draw Active Emoji Signals
    worldState.emojiSignals.forEach { signal ->
        if (signal.senderId == agent.id) {
            val signalPos = worldToScreen(signal.position.toOffset(), camera)
            
            // Speech Bubble Params
            val bubbleSize = 32f * camera.zoom
            val cornerRadius = 8f * camera.zoom
            
            // Draw Bubble Background
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(signalPos.x - bubbleSize / 2f, signalPos.y - bubbleSize),
                size = Size(bubbleSize, bubbleSize),
                cornerRadius = CornerRadius(cornerRadius, cornerRadius)
            )
            
            // Draw Bubble Tail
            val path = Path().apply {
                moveTo(signalPos.x - 4f * camera.zoom, signalPos.y - 1f)
                lineTo(signalPos.x + 4f * camera.zoom, signalPos.y - 1f)
                lineTo(signalPos.x, signalPos.y + 6f * camera.zoom)
                close()
            }
            drawPath(path, Color.White)

            assets.emojiPaint.textSize = 22f * camera.zoom
            assets.emojiPaint.textAlign = android.graphics.Paint.Align.CENTER
            
            // Center emoji in bubble
            drawContext.canvas.nativeCanvas.drawText(
                signal.emojiType,
                signalPos.x,
                signalPos.y - bubbleSize * 0.25f,
                assets.emojiPaint
            )
        }
    }
    
    // Draw Affinity Delta (+ / -)
    if (agent.affinityDeltaTimerMs > 0) {
        val screenPos = worldToScreen(Offset(agent.x, agent.y), camera)
        val delta = agent.lastAffinityDelta
        val text = if (delta > 0) "+" else "-"
        val color = if (delta > 0) android.graphics.Color.GREEN else android.graphics.Color.RED
        
        // Floating animation: moves up over time
        val elapsedMs = 2000L - agent.affinityDeltaTimerMs
        val floatUp = (elapsedMs / 1000f) * 40f * camera.zoom
        
        assets.affinityPaint.color = color
        assets.affinityPaint.textSize = 32f * camera.zoom
        assets.affinityPaint.alpha = (agent.affinityDeltaTimerMs / 2000f * 255).toInt()
        
        // Offset to the side of the agent (head level) and float upwards
        drawContext.canvas.nativeCanvas.drawText(
            text,
            screenPos.x + 20f * camera.zoom,
            screenPos.y - 65f * camera.zoom - floatUp, // Moved up from -30f to be at head level
            assets.affinityPaint
        )
    }
}
