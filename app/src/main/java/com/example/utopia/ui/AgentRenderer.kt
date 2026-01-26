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
import com.example.utopia.data.models.Personality
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

    val isMoving = (agent.goalPos != null || agent.pathTiles.isNotEmpty()) && agent.dwellTimerMs <= 0L && agent.state != AgentState.SOCIALIZING && agent.state != AgentState.SLEEPING
    val isSleeping = agent.state == AgentState.SLEEPING
    val isSocializing = agent.state == AgentState.SOCIALIZING

    val jobStructure = agent.jobId?.let { id -> structures.find { it.id == id } }
    val isInsideWorkplace = jobStructure?.let { s ->
        val pad = 6f
        agent.x in (s.x - pad)..(s.x + s.type.worldWidth + pad) &&
            agent.y in (s.y - pad)..(s.y + s.type.worldHeight + pad)
    } ?: false

    val isWorking = agent.state == AgentState.AT_WORK && isInsideWorkplace

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
    } else if (isSocializing) {
        bobY = sin(timeMs / 150f) * 2f
        swayX = cos(timeMs / 200f) * 1.5f
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
            AppearanceVariant.CASTLE_GUARD -> {
                swayX = sin(workTimer / 800f) * 4f
            }
            AppearanceVariant.TAVERN_KEEPER -> {
                headBobY = abs(sin(workTimer / 500f)) * 4f
            }
            else -> {
                headBobY = abs(sin(workTimer / 1000f)) * 3f
            }
        }
    }

    val legOffset = if (agent.animFrame == 1) -3f else if (agent.animFrame == 3) 3f else 0f

    renderAgentLayers(
        screenPos = screenPos,
        spec = spec,
        jobVariant = agent.appearance,
        gender = agent.profile.gender,
        personality = agent.personality,
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
    jobVariant: AppearanceVariant,
    gender: Gender,
    personality: Personality,
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

    val agentColor = when (jobVariant) {
        AppearanceVariant.DEFAULT -> baseTunicColor
        AppearanceVariant.STORE_WORKER -> Color(0xFF81D4FA)
        AppearanceVariant.WORKSHOP_WORKER -> Color(0xFFFFF176)
        AppearanceVariant.CASTLE_GUARD -> Color(0xFFBDBDBD) // Iron Plate
        AppearanceVariant.TAVERN_KEEPER -> Color(0xFF8D6E63)
    }

    withTransform({
        scale(renderParams.scale, renderParams.scale, pivot = screenPos)
    }) {
        // 1. Legs
        if (anim.showLegs) {
            drawRect(Color.DarkGray, Offset(screenPos.x - 4f + anim.legOffset, screenPos.y + 2f), Size(3f, 4f))
            drawRect(Color.DarkGray, Offset(screenPos.x + 1f - anim.legOffset, screenPos.y + 2f), Size(3f, 4f))
        }

        // 2. Body (Tunic)
        drawRoundRect(
            color = agentColor,
            topLeft = Offset(screenPos.x - baseBodyWidth / 2f - anim.squashX / 2f + anim.swayX, screenPos.y - baseBodyHeight + anim.bobY),
            size = Size(baseBodyWidth + anim.squashX, baseBodyHeight - anim.bobY / 2f),
            cornerRadius = CornerRadius(3f, 3f)
        )

        // 3. Layered Clothing (Vest/Apron/Detail)
        when (jobVariant) {
            AppearanceVariant.WORKSHOP_WORKER -> {
                // Leather Apron
                drawRect(
                    color = Color(0xFF5D4037),
                    topLeft = Offset(screenPos.x - 4f + anim.swayX, screenPos.y - 12f + anim.bobY),
                    size = Size(8f, 12f)
                )
            }
            AppearanceVariant.TAVERN_KEEPER -> {
                // White Apron
                drawRect(
                    color = Color.White.copy(alpha = 0.8f),
                    topLeft = Offset(screenPos.x - 5f + anim.swayX, screenPos.y - 10f + anim.bobY),
                    size = Size(10f, 10f)
                )
            }
            AppearanceVariant.STORE_WORKER -> {
                // Trader Sash
                val sashPath = Path().apply {
                    moveTo(screenPos.x - 6f + anim.swayX, screenPos.y - 14f + anim.bobY)
                    lineTo(screenPos.x + 6f + anim.swayX, screenPos.y - 6f + anim.bobY)
                    lineTo(screenPos.x + 6f + anim.swayX, screenPos.y - 4f + anim.bobY)
                    lineTo(screenPos.x - 6f + anim.swayX, screenPos.y - 12f + anim.bobY)
                    close()
                }
                drawPath(sashPath, Color(0xFFFFA000))
            }
            AppearanceVariant.CASTLE_GUARD -> {
                // Red Tabard over plate
                drawRect(
                    color = Color(0xFFC62828),
                    topLeft = Offset(screenPos.x - 3f + anim.swayX, screenPos.y - 15f + anim.bobY),
                    size = Size(6f, 14f)
                )
            }
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

        // Eyebrows based on personality
        when (personality) {
            Personality.GRUMPY -> {
                drawLine(Color.Black, Offset(screenPos.x + anim.swayX + (1f * lookDir), headY - 2.5f), Offset(screenPos.x + anim.swayX + (2.5f * lookDir), headY - 1.8f), strokeWidth = 0.8f)
                drawLine(Color.Black, Offset(screenPos.x + anim.swayX + (4f * lookDir), headY - 2.5f), Offset(screenPos.x + anim.swayX + (2.5f * lookDir), headY - 1.8f), strokeWidth = 0.8f)
            }
            Personality.SHY -> {
                drawLine(Color.Black, Offset(screenPos.x + anim.swayX + (1f * lookDir), headY - 2.2f), Offset(screenPos.x + anim.swayX + (2.5f * lookDir), headY - 2.2f), strokeWidth = 0.5f)
            }
            else -> {}
        }

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

        // Helmet for guards
        if (jobVariant == AppearanceVariant.CASTLE_GUARD) {
            drawRect(Color.LightGray, Offset(screenPos.x - 6f + anim.swayX, headY - 7f), Size(12f, 4f))
            drawRect(Color.LightGray, Offset(screenPos.x - 1f + anim.swayX, headY - 10f), Size(2f, 4f))
        }
    }
}
