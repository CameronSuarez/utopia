package com.example.utopia.ui

import androidx.compose.ui.geometry.Offset
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
            
            // Floating animation: moves up slightly over time
            val elapsed = timeMs - signal.timestamp
            val floatUp = (elapsed / 1000f) * 20f * camera.zoom
            
            assets.emojiPaint.textSize = 24f * camera.zoom
            assets.emojiPaint.textAlign = android.graphics.Paint.Align.CENTER
            
            drawContext.canvas.nativeCanvas.drawText(
                signal.emojiType,
                signalPos.x,
                signalPos.y - floatUp,
                assets.emojiPaint
            )
        }
    }
}
