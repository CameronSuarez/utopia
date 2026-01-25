package com.example.utopia.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import com.example.utopia.data.models.AgentRuntime
import com.example.utopia.data.models.AgentState
import com.example.utopia.data.models.AppearanceVariant
import com.example.utopia.data.models.WorldState
import java.util.Random
import kotlin.math.abs
import kotlin.math.sin

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

    val isMoving = (agent.goalPos != null || agent.pathTiles.isNotEmpty()) && agent.dwellTimerMs <= 0L && agent.state != AgentState.SOCIALIZING && agent.state != AgentState.SLEEPING
    val isSleeping = agent.state == AgentState.SLEEPING
    val isSocializing = agent.state == AgentState.SOCIALIZING
    val isWorking = agent.state == AgentState.AT_WORK || agent.state == AgentState.EXCURSION_VISITING

    if (isSleeping) {
        bobY = 4f
    } else if (isMoving) {
        if (agent.animFrame % 2 == 1) {
            bobY = -3f
        }
    } else if (isSocializing) {
        bobY = sin(timeMs / 150f) * 2f
    } else if (isWorking && timeMs < agent.workAnimEndTimeMs) {
        val workTimer = timeMs + agent.phaseStaggerMs
        when (agent.appearance) {
            AppearanceVariant.WORKSHOP_WORKER -> {
                val cycle = (workTimer % 600) / 600f
                bobY = if (cycle > 0.8f) (1f - cycle) * 20f else -cycle * 2f
            }
            AppearanceVariant.STORE_WORKER -> {
                bobY = sin(workTimer / 300f) * 3f
            }
            else -> {}
        }
    }

    val showSocialBubble = agent.state == AgentState.SOCIALIZING && timeMs < agent.socialEmojiUntilMs && agent.emoji != null
    if (showSocialBubble) {
        val emoji = agent.emoji!!
        val emojiSize = assets.emojiPaint.textSize
        val padding = emojiSize * 0.4f
        val bubbleWidth = emojiSize + padding * 2f
        val bubbleHeight = emojiSize + padding * 2f
        val agentHeight = (baseBodyHeight + 10f) * scale
        val bubbleX = (screenPos.x - bubbleWidth / 2f).coerceIn(4f, size.width - bubbleWidth - 4f)
        val bubbleY = (screenPos.y - agentHeight - bubbleHeight - 6f + bobY * scale).coerceIn(8f, size.height - bubbleHeight - 8f)

        val bubbleFill = Color(0xFFF3E9D2)
        val bubbleStroke = Color(0xFF5D4037)

        drawRoundRect(
            color = bubbleFill,
            topLeft = Offset(bubbleX, bubbleY),
            size = Size(bubbleWidth, bubbleHeight),
            cornerRadius = CornerRadius(6f, 6f)
        )
        drawRoundRect(
            color = bubbleStroke,
            topLeft = Offset(bubbleX, bubbleY),
            size = Size(bubbleWidth, bubbleHeight),
            cornerRadius = CornerRadius(6f, 6f),
            style = Stroke(width = 1.5f)
        )

        val tailWidth = 6f
        val tailHeight = 4f
        val tailCenterX = screenPos.x.coerceIn(bubbleX + tailWidth, bubbleX + bubbleWidth - tailWidth)
        val tailPath = Path().apply {
            moveTo(tailCenterX - tailWidth / 2f, bubbleY + bubbleHeight)
            lineTo(tailCenterX + tailWidth / 2f, bubbleY + bubbleHeight)
            lineTo(tailCenterX, bubbleY + bubbleHeight + tailHeight)
            close()
        }
        drawPath(tailPath, bubbleFill)
        drawPath(tailPath, bubbleStroke, style = Stroke(width = 1.5f))

        val metrics = assets.emojiPaint.fontMetrics
        val textY = bubbleY + bubbleHeight / 2f - (metrics.ascent + metrics.descent) / 2f
        drawContext.canvas.nativeCanvas.drawText(
            emoji,
            bubbleX + bubbleWidth / 2f,
            textY,
            assets.emojiPaint
        )
    }

    if (timeMs < agent.affinityDeltaUiUntilMs) {
        val duration = 800f
        val remaining = agent.affinityDeltaUiUntilMs - timeMs
        val fraction = 1f - (remaining / duration)
        val alpha = (1f - fraction).coerceIn(0f, 1f)
        val driftY = fraction * 25f
        val glyph = if (agent.lastAffinityDelta > 0) "+" else if (agent.lastAffinityDelta < 0) "−" else null
        if (glyph != null) {
            assets.affinityPaint.alpha = (alpha * 255).toInt()
            assets.affinityPaint.color = if (agent.lastAffinityDelta > 0) android.graphics.Color.GREEN else android.graphics.Color.RED
            drawContext.canvas.nativeCanvas.drawText(glyph, screenPos.x, screenPos.y - 55f - driftY, assets.affinityPaint)
        }
    }
}
