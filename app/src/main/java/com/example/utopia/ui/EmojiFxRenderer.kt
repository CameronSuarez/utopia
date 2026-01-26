package com.example.utopia.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.example.utopia.data.models.AgentRuntime
import com.example.utopia.data.models.WorldState
import java.util.Random

// Removed unused imports: CornerRadius, Size, Color, Path, Stroke, nativeCanvas, AgentState, AppearanceVariant, abs, sin

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
    worldState.agents.forEach { drawAgentEmojiFxItem(it, camera, assets, timeMs) }
}

fun DrawScope.drawAgentEmojiFxItem(
    agent: AgentRuntime,
    camera: Camera2D,
    assets: EmojiFxAssets,
    timeMs: Long
) {
    val screenPos = worldToScreen(Offset(agent.x, agent.y), camera)
    val scale = 2.2f * camera.zoom
    val cullPadX = 40f * scale
    val cullPadY = 60f * scale
    if (screenPos.x < -cullPadX || screenPos.x > size.width + cullPadX || screenPos.y < -cullPadY || screenPos.y > size.height + cullPadY) return

    val rng = Random(agent.shortId.toLong())
    val bodyHeightMod = -2f + rng.nextFloat() * 4f
    val baseBodyHeight = 16f + bodyHeightMod

    var bobY = 0f

    // REMOVED: All behavior-driven animation logic (isMoving, isSleeping, isSocializing, isWorking, etc.)

    // The agent is now a brain-dead husk. We draw no status effects.
    
    // REMOVED: All social bubble drawing logic

    // REMOVED: All affinity delta drawing logic
}
