package com.example.utopia.ui

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.graphics.createBitmap
import com.example.utopia.data.models.AppearanceSpec
import com.example.utopia.data.models.AppearanceVariant
import com.example.utopia.data.models.Gender
import com.example.utopia.data.models.Personality
import kotlin.math.roundToInt

private const val PORTRAIT_RENDER_SCALE = 4f
private const val PORTRAIT_BASE_CANVAS_PX = 64f
private const val PORTRAIT_RENDER_PX = (PORTRAIT_BASE_CANVAS_PX * PORTRAIT_RENDER_SCALE).toInt()

// Portrait framing contract (torso-up): head -> belt driven. Do not replace with fixed offsets.
// The padding constants below are the only tuning knobs for framing.
private const val PORTRAIT_HEAD_TOP_PADDING_FACTOR = 0.6f
private const val PORTRAIT_TORSO_BOTTOM_PADDING_FACTOR = 0.1f
private const val PORTRAIT_DEBUG_LOGS = false

internal data class PortraitKey(
    val skinToneId: Int,
    val hairColorId: Int,
    val tunicColorId: Int,
    val hairStyleId: Int,
    val bodyWidthMod: Float,
    val bodyHeightMod: Float,
    val hasBeard: Boolean,
    val hasHood: Boolean,
    val jobVariant: AppearanceVariant,
    val sizePx: Int
) {
    constructor(spec: AppearanceSpec, jobVariant: AppearanceVariant, sizePx: Int) : this(
        skinToneId = spec.skinToneId,
        hairColorId = spec.hairColorId,
        tunicColorId = spec.tunicColorId,
        hairStyleId = spec.hairStyleId,
        bodyWidthMod = spec.bodyWidthMod,
        bodyHeightMod = spec.bodyHeightMod,
        hasBeard = spec.hasBeard,
        hasHood = spec.hasHood,
        jobVariant = jobVariant,
        sizePx = sizePx
    )
}

internal class PortraitCache(
    private val maxEntries: Int = 128
) {
    private val cache = object : LinkedHashMap<PortraitKey, ImageBitmap>(maxEntries, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<PortraitKey, ImageBitmap>?): Boolean {
            return size > maxEntries
        }
    }

    fun getPortrait(
        key: PortraitKey,
        gender: Gender,
        personality: Personality,
        facingLeft: Boolean = false
    ): ImageBitmap {
        cache[key]?.let { return it }

        val renderBitmap = createBitmap(PORTRAIT_RENDER_PX, PORTRAIT_RENDER_PX)
        val renderImage = renderBitmap.asImageBitmap()
        val drawScope = CanvasDrawScope()
        val density = Density(1f)
        val layoutDirection = LayoutDirection.Ltr
        val canvasSize = Size(renderImage.width.toFloat(), renderImage.height.toFloat())

        val baseBodyHeight = (if (gender == Gender.MALE) 16f else 17f) + key.bodyHeightMod
        val screenPos = androidx.compose.ui.geometry.Offset(
            PORTRAIT_RENDER_PX / 2f,
            PORTRAIT_RENDER_PX / 2f
        )

        drawScope.draw(
            density = density,
            layoutDirection = layoutDirection,
            canvas = androidx.compose.ui.graphics.Canvas(renderImage),
            size = canvasSize
        ) {
            renderAgentLayers(
                screenPos = screenPos,
                spec = AppearanceSpec(
                    skinToneId = key.skinToneId,
                    hairColorId = key.hairColorId,
                    tunicColorId = key.tunicColorId,
                    hairStyleId = key.hairStyleId,
                    bodyWidthMod = key.bodyWidthMod,
                    bodyHeightMod = key.bodyHeightMod,
                    hasBeard = key.hasBeard,
                    hasHood = key.hasHood
                ),
                jobVariant = key.jobVariant,
                gender = gender,
                personality = personality,
                facingLeft = facingLeft,
                shortId = 0,
                renderParams = RenderParams(scale = PORTRAIT_RENDER_SCALE),
                anim = AnimState(
                    bobY = 0f,
                    squashX = 0f,
                    swayX = 0f,
                    headBobY = 0f,
                    legOffset = 0f,
                    showLegs = false,
                    isSleeping = false
                )
            )
        }

        // Portrait framing contract: head -> belt driven (torso-up). Do not replace with fixed offsets.
        // Adjust padding constants above for framing tweaks.
        val headRadiusPx = 5.5f * PORTRAIT_RENDER_SCALE
        val headCenterX = screenPos.x
        val headCenterY = screenPos.y - baseBodyHeight * PORTRAIT_RENDER_SCALE
        val beltY = screenPos.y - (baseBodyHeight * 0.4f) * PORTRAIT_RENDER_SCALE

        val headTopPadding = headRadiusPx * PORTRAIT_HEAD_TOP_PADDING_FACTOR
        val torsoBottomPadding = (baseBodyHeight * PORTRAIT_TORSO_BOTTOM_PADDING_FACTOR) * PORTRAIT_RENDER_SCALE
        val topY = headCenterY - headRadiusPx - headTopPadding
        val bottomY = beltY + torsoBottomPadding
        val cropCenterY = (topY + bottomY) / 2f

        val cropSize = key.sizePx.coerceAtMost(PORTRAIT_RENDER_PX)
        val cropLeft = (headCenterX - cropSize / 2f)
            .roundToInt()
            .coerceIn(0, PORTRAIT_RENDER_PX - cropSize)
        val cropTop = (cropCenterY - cropSize / 2f)
            .roundToInt()
            .coerceIn(0, PORTRAIT_RENDER_PX - cropSize)

        if (PORTRAIT_DEBUG_LOGS) {
            Log.d(
                "PortraitCache",
                "headY=$headCenterY beltY=$beltY crop=($cropLeft,$cropTop,$cropSize) render=$PORTRAIT_RENDER_PX"
            )
        }

        val croppedBitmap = Bitmap.createBitmap(renderBitmap, cropLeft, cropTop, cropSize, cropSize)
            .asImageBitmap()

        cache[key] = croppedBitmap
        return croppedBitmap
    }
}
