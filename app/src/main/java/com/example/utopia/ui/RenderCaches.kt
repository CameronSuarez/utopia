package com.example.utopia.ui

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.graphics.createBitmap
import com.example.utopia.data.models.AppearanceSpec
import com.example.utopia.data.models.AppearanceVariant
import com.example.utopia.data.models.Gender
import com.example.utopia.data.models.TileType
import com.example.utopia.data.models.WorldState
import com.example.utopia.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

// --- Ground Cache ---

@Composable
fun rememberGroundCache(worldState: WorldState, grassBitmaps: List<ImageBitmap>): ImageBitmap? {
    val density = LocalDensity.current
    var groundBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(worldState.structureRevision, grassBitmaps) {
        val bitmap = withContext(Dispatchers.Default) {
            val width = Constants.WORLD_W_PX.toInt()
            val height = Constants.WORLD_H_PX.toInt()
            val targetBitmap = ImageBitmap(width, height)
            val canvas = Canvas(targetBitmap)
            val drawScope = CanvasDrawScope()

            drawScope.draw(
                density = density,
                layoutDirection = LayoutDirection.Ltr,
                canvas = canvas,
                size = Size(width.toFloat(), height.toFloat())
            ) {
                drawNaturalGroundInternal(worldState.tiles, grassBitmaps)
            }
            targetBitmap
        }
        groundBitmap = bitmap
    }

    return groundBitmap
}

private fun DrawScope.drawNaturalGroundInternal(
    tiles: Array<Array<TileType>>,
    grassBitmaps: List<ImageBitmap>
) {
    val tileSize = Constants.TILE_SIZE
    val dstIntSize = IntSize(tileSize.roundToInt(), tileSize.roundToInt())

    if (grassBitmaps.isEmpty()) {
        return
    }

    for (x in 0 until Constants.MAP_TILES_W) {
        for (y in 0 until Constants.MAP_TILES_H) {
            val px = x * tileSize
            val py = y * tileSize
            val tileType = tiles[x][y]

            if (tileType == TileType.WALL) {
                drawRect(Color(0xFF795548L), Offset(px, py), Size(tileSize, tileSize))
            } else {
                drawImage(
                    image = grassBitmaps.random(),
                    dstOffset = IntOffset(px.roundToInt(), py.roundToInt()),
                    dstSize = dstIntSize,
                    filterQuality = FilterQuality.None
                )
            }
        }
    }
}

// --- Road Cache ---

@Composable
fun rememberRoadCache(worldState: WorldState, roadBitmapAsset: ImageBitmap): ImageBitmap? {
    val density = LocalDensity.current
    var roadBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(worldState.roadRevision, roadBitmapAsset) {
        val bitmap = withContext(Dispatchers.Default) {
            val width = Constants.WORLD_W_PX.toInt()
            val height = Constants.WORLD_H_PX.toInt()
            val targetBitmap = ImageBitmap(width, height)
            val canvas = Canvas(targetBitmap)
            val drawScope = CanvasDrawScope()

            drawScope.draw(
                density = density,
                layoutDirection = LayoutDirection.Ltr,
                canvas = canvas,
                size = Size(width.toFloat(), height.toFloat())
            ) {
                drawRoadsInternal(worldState.tiles, roadBitmapAsset)
            }
            targetBitmap
        }
        roadBitmap = bitmap
    }

    return roadBitmap
}

private fun DrawScope.drawRoadsInternal(tiles: Array<Array<TileType>>, roadBitmapAsset: ImageBitmap) {
    val tileSize = Constants.TILE_SIZE
    val dstSize = IntSize(tileSize.roundToInt(), tileSize.roundToInt())

    for (x in 0 until Constants.MAP_TILES_W) {
        for (y in 0 until Constants.MAP_TILES_H) {
            if (tiles[x][y] == TileType.ROAD) {
                val px = x * tileSize
                val py = y * tileSize
                
                drawImage(
                    image = roadBitmapAsset,
                    dstOffset = IntOffset(px.roundToInt(), py.roundToInt()),
                    dstSize = dstSize,
                    filterQuality = FilterQuality.None
                )
            }
        }
    }
}

// --- Portrait Cache ---

private const val PORTRAIT_RENDER_SCALE = 4f
private const val PORTRAIT_BASE_CANVAS_PX = 64f
private const val PORTRAIT_RENDER_PX = (PORTRAIT_BASE_CANVAS_PX * PORTRAIT_RENDER_SCALE).toInt()
private const val PORTRAIT_HEAD_TOP_PADDING_FACTOR = 0.6f
private const val PORTRAIT_TORSO_BOTTOM_PADDING_FACTOR = 0.1f
private const val PORTRAIT_DEBUG_LOGS = false

data class PortraitKey(
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

class PortraitCache(
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
        val screenPos = Offset(
            PORTRAIT_RENDER_PX / 2f,
            PORTRAIT_RENDER_PX / 2f
        )

        drawScope.draw(
            density = density,
            layoutDirection = layoutDirection,
            canvas = Canvas(renderImage),
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
