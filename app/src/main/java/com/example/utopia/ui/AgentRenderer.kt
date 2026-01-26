package com.example.utopia.ui

import android.util.Log
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import com.example.utopia.data.models.AgentRuntime
import com.example.utopia.data.models.AgentState
import com.example.utopia.data.models.AppearanceSpec
import com.example.utopia.data.models.AppearanceVariant
import com.example.utopia.data.models.Gender
// REMOVED: import com.example.utopia.data.models.Personality
import com.example.utopia.data.models.Structure
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// NOTE: Visual scale only. Logical agent size is unchanged by design.
private const val AGENT_VISUAL_SCALE = 1.76f

internal data class RenderParams(
    val scale: Float
)

internal data class AnimState(
    val bobY: Float,
    val squashX: Float,
    val swayX: Float,
    val headBobY: Float,
    val legOffset: Float,
    val showLegs: Boolean,
    val isSleeping: Boolean
)

private val loggedMissingAppearance = mutableSetOf<Int>()

fun DrawScope.drawAgents(
    agents: List<AgentRuntime>,
    structures: List<Structure>,
    camera: Camera2D,
    timeMs: Long
) {
    agents.forEach { drawAgentItem(it, structures, camera, timeMs) }
}

fun DrawScope.drawAgentItem(
    agent: AgentRuntime,
    structures: List<Structure>,
    camera: Camera2D,
    timeMs: Long
) {
    val screenPos = worldToScreen(Offset(agent.x, agent.y), camera)
    val scale = AGENT_VISUAL_SCALE * camera.zoom
    val cullPadX = 40f * scale
    val cullPadY = 60f * scale
    if (screenPos.x < -cullPadX || screenPos.x > size.width + cullPadX || screenPos.y < -cullPadY || screenPos.y > size.height + cullPadY) return

    val isMoving = (agent.goalPos != null || agent.pathTiles.isNotEmpty()) && agent.dwellTimerMs <= 0L && agent.state != AgentState.SLEEPING
    val isSleeping = agent.state == AgentState.SLEEPING

    val spec = agent.profile.appearance ?: run {
        if (loggedMissingAppearance.add(agent.shortId)) {
            Log.w("AgentRenderer", "Missing appearance for agent ${agent.id}, falling back to deterministic spec.")
        }
        generateAppearanceSpec(agent.profile.gender, Random(agent.shortId.toLong()))
    }

    var bobY = 0f
    var squashX = 0f
    var swayX = 0f
    var headBobY = 0f

    if (isSleeping) {
        bobY = 4f
    } else if (isMoving) {
        if (agent.animFrame % 2 == 1) {
            bobY = -3f
            squashX = -1f
        }
    }

    val legOffset = if (agent.animFrame == 1) -3f else if (agent.animFrame == 3) 3f else 0f

    renderAgentLayers(
        screenPos = screenPos,
        spec = spec,
        jobVariant = AppearanceVariant.DEFAULT,
        gender = agent.profile.gender,
        facingLeft = agent.facingLeft,
        shortId = agent.shortId,
        renderParams = RenderParams(scale = scale),
        anim = AnimState(
            bobY = bobY,
            squashX = squashX,
            swayX = swayX,
            headBobY = headBobY,
            legOffset = legOffset,
            showLegs = isMoving && !isSleeping,
            isSleeping = isSleeping
        )
    )
}

internal fun DrawScope.renderAgentLayers(
    screenPos: Offset,
    spec: AppearanceSpec,
    jobVariant: AppearanceVariant, // This will always be DEFAULT now, but keep for drawing function signature
    gender: Gender,
    // Removed Personality parameter
    facingLeft: Boolean,
    shortId: Int,
    renderParams: RenderParams,
    anim: AnimState
) {
    val skinTone = SKIN_TONES[spec.skinToneId]
    val hairColor = HAIR_COLORS[spec.hairColorId]
    val baseTunicColor = TUNIC_COLORS[spec.tunicColorId]
    val hairStyle = spec.hairStyleId
    val hasBeard = spec.hasBeard
    val hasHood = spec.hasHood

    val baseBodyWidth = (if (gender == Gender.MALE) 12.5f else 11f) + spec.bodyWidthMod
    val baseBodyHeight = (if (gender == Gender.MALE) 16f else 17f) + spec.bodyHeightMod

    val agentColor = baseTunicColor

    withTransform({
        scale(renderParams.scale, renderParams.scale, pivot = screenPos)
    }) {
        if (anim.showLegs) {
            drawRect(Color.DarkGray, Offset(screenPos.x - 4f + anim.legOffset, screenPos.y + 2f), Size(3f, 4f))
            drawRect(Color.DarkGray, Offset(screenPos.x + 1f - anim.legOffset, screenPos.y + 2f), Size(3f, 4f))
        }

        drawRoundRect(
            color = agentColor,
            topLeft = Offset(screenPos.x - baseBodyWidth / 2f - anim.squashX / 2f + anim.swayX, screenPos.y - baseBodyHeight + anim.bobY),
            size = Size(baseBodyWidth + anim.squashX, baseBodyHeight - anim.bobY / 2f),
            cornerRadius = CornerRadius(3f, 3f)
        )

        // 3. Layered Clothing (Vest/Apron/Detail)
        when (jobVariant) {
            AppearanceVariant.DEFAULT -> {
                // Simple Vest for some
                if (shortId % 3 == 0) {
                    drawRect(
                        color = Color.Black.copy(alpha = 0.2f),
                        topLeft = Offset(screenPos.x - baseBodyWidth / 2f + anim.swayX, screenPos.y - baseBodyHeight + anim.bobY),
                        size = Size(baseBodyWidth, baseBodyHeight * 0.6f)
                    )
                }
            }
            // Removed other variants
            else -> {}
        }

        // 4. Belt
        if (!anim.isSleeping) {
            drawRect(
                color = Color(0xFF3E2723),
                topLeft = Offset(screenPos.x - baseBodyWidth / 2f + anim.swayX, screenPos.y - baseBodyHeight * 0.4f + anim.bobY),
                size = Size(baseBodyWidth, 1.5f)
            )
        }

        // 5. Head
        val headY = screenPos.y - baseBodyHeight + anim.bobY + anim.headBobY
        drawCircle(
            color = skinTone,
            radius = 5.5f,
            center = Offset(screenPos.x + anim.swayX, headY)
        )

        // 6. Facial Features
        val lookDir = if (facingLeft) -1f else 1f
        // Two Eyes
        drawCircle(Color.Black, 0.8f, Offset(screenPos.x + anim.swayX + (1.5f * lookDir), headY - 1f))
        drawCircle(Color.Black, 0.8f, Offset(screenPos.x + anim.swayX + (3.5f * lookDir), headY - 1f))

        // REMOVED: Eyebrows based on personality
        /*
        when (personality) {
            Personality.GRUMPY -> { ... }
            Personality.SHY -> { ... }
            else -> {}
        }
        */

        // Mouth (straight)
        drawLine(
            color = Color.Black.copy(alpha = 0.7f),
            start = Offset(screenPos.x + anim.swayX + (1.5f * lookDir), headY + 2.5f),
            end = Offset(screenPos.x + anim.swayX + (3.5f * lookDir), headY + 2.5f),
            strokeWidth = 1f
        )

        // Beard
        if (hasBeard) {
            drawRoundRect(
                color = hairColor,
                topLeft = Offset(screenPos.x - 4f + anim.swayX + (1f * lookDir), headY + 1f),
                size = Size(7f, 4f),
                cornerRadius = CornerRadius(2f, 2f)
            )
        }

        // 7. Hair / Hat / Hood
        if (hasHood && jobVariant == AppearanceVariant.DEFAULT) {
            val hoodPath = Path().apply {
                moveTo(screenPos.x - 6f + anim.swayX, headY + 2f)
                quadraticTo(screenPos.x - 7f + anim.swayX, headY - 8f, screenPos.x + anim.swayX, headY - 9f)
                quadraticTo(screenPos.x + 7f + anim.swayX, headY - 8f, screenPos.x + 6f + anim.swayX, headY + 2f)
            }
            drawPath(hoodPath, agentColor.copy(alpha = 0.9f))
        } else {
            when (hairStyle) {
                1 -> drawRect(hairColor, Offset(screenPos.x - 5f + anim.swayX, headY - 5.5f), Size(10f, 3f)) // Short
                2 -> drawRoundRect(hairColor, Offset(screenPos.x - 6f + anim.swayX, headY - 6f), Size(12f, 6f), CornerRadius(3f, 3f)) // Bowl
                3 -> { // Long
                    drawRect(hairColor, Offset(screenPos.x - 6f + anim.swayX, headY - 6f), Size(12f, 3f))
                    drawRect(hairColor, Offset(screenPos.x - 6f + anim.swayX, headY - 6f), Size(3f, 11f))
                    drawRect(hairColor, Offset(screenPos.x + 3f + anim.swayX, headY - 6f), Size(3f, 11f))
                }
                4 -> drawCircle(Color(0xFF455A64), 4.5f, Offset(screenPos.x + anim.swayX, headY - 5f)) // Felt Cap
                5 -> { // Top Knot / Bun
                    drawCircle(hairColor, 3f, Offset(screenPos.x + anim.swayX, headY - 4f))
                    drawCircle(hairColor, 2f, Offset(screenPos.x + anim.swayX, headY - 7f))
                }
            }
        }

    }
}
