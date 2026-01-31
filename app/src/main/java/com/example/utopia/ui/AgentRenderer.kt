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
import com.example.utopia.data.models.AppearanceRegistry
import com.example.utopia.data.models.AppearanceSpec
import com.example.utopia.data.models.AppearanceVariant
import com.example.utopia.data.models.DrawableShape
import com.example.utopia.data.models.Gender
// REMOVED: import com.example.utopia.data.models.Personality
import kotlin.random.Random

// NOTE: Visual scale only. Logical agent size is unchanged by design.
private const val AGENT_VISUAL_SCALE = 2.024f

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

fun DrawScope.drawAgentItem(
    agent: AgentRuntime,
    camera: Camera2D
) {
    val screenPos = worldToScreen(Offset(agent.x, agent.y), camera)
    val scale = AGENT_VISUAL_SCALE * camera.zoom
    val cullPadX = 40f * scale
    val cullPadY = 60f * scale
    if (screenPos.x < -cullPadX || screenPos.x > size.width + cullPadX || screenPos.y < -cullPadY || screenPos.y > size.height + cullPadY) return

    val isSleeping = agent.state == AgentState.SLEEPING

    val spec = agent.profile.appearance ?: run {
        if (loggedMissingAppearance.add(agent.shortId)) {
            Log.w("AgentRenderer", "Missing appearance for agent ${agent.id}, falling back to deterministic spec.")
        }
        generateAppearanceSpec(agent.profile.gender, Random(agent.shortId.toLong()))
    }

    var bobY = 0f
    var squashX = 0f
    val swayX = 0f
    val headBobY = 0f

    if (isSleeping) {
        bobY = 4f
    } else {
        if (agent.animFrame % 2 == 1) {
            bobY = -1.5f
            squashX = -0.5f
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
            showLegs = !isSleeping,
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
    val skinTone = AppearanceRegistry.SKIN_TONES[spec.skinToneId]
    val hairColor = AppearanceRegistry.HAIR_COLORS[spec.hairColorId]
    val baseTunicColor = AppearanceRegistry.TUNIC_COLORS[spec.tunicColorId]
    val hairStyle = spec.hairStyleId
    val hasBeard = spec.hasBeard
    val hasHood = spec.hasHood

    val baseBodyWidth = (if (gender == Gender.MALE) 12.5f else 11f) + spec.bodyWidthMod
    val baseBodyHeight = (if (gender == Gender.MALE) 16f else 17f) + spec.bodyHeightMod

    withTransform({
        scale(renderParams.scale, renderParams.scale, pivot = screenPos)
    }) {
        // Shadow
        drawOval(
            color = Color.Black.copy(alpha = 0.2f),
            topLeft = Offset(screenPos.x - baseBodyWidth / 2f + anim.swayX, screenPos.y + 3f),
            size = Size(baseBodyWidth, 4f)
        )

        if (anim.showLegs) {
            drawRect(Color.DarkGray, Offset(screenPos.x - 4f + anim.legOffset, screenPos.y + 2f), Size(3f, 4f))
            drawRect(Color.DarkGray, Offset(screenPos.x + 1f - anim.legOffset, screenPos.y + 2f), Size(3f, 4f))
        }

        drawRoundRect(
            color = baseTunicColor,
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
        val headCenter = Offset(screenPos.x + anim.swayX, headY)
        drawCircle(
            color = skinTone,
            radius = 5.5f,
            center = headCenter
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
            drawPath(hoodPath, baseTunicColor.copy(alpha = 0.9f))
        } else {
            val hairAsset = AppearanceRegistry.getHairAsset(hairStyle)
            val assetColor = if (hairAsset?.assetId == "cap") Color(0xFF455A64) else hairColor

            hairAsset?.shapes?.forEach { shape ->
                when (shape) {
                    is DrawableShape.Rect -> drawRect(
                        color = assetColor,
                        topLeft = headCenter + shape.offset,
                        size = shape.size
                    )
                    is DrawableShape.RoundRect -> drawRoundRect(
                        color = assetColor,
                        topLeft = headCenter + shape.offset,
                        size = shape.size,
                        cornerRadius = shape.cornerRadius
                    )
                    is DrawableShape.Circle -> drawCircle(
                        color = assetColor,
                        radius = shape.radius,
                        center = headCenter + shape.center
                    )
                }
            }
        }
    }
}
