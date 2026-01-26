package com.example.utopia.ui

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.graphics.createBitmap
import com.example.utopia.R
import com.example.utopia.data.models.AppearanceSpec
import com.example.utopia.data.models.AppearanceVariant
import com.example.utopia.data.models.Gender
import com.example.utopia.data.models.TileType
import com.example.utopia.data.models.WorldState
import com.example.utopia.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Random
import kotlin.math.roundToInt

// --- Ground Cache ---

@Composable
fun rememberGroundCache(worldState: WorldState, grassBitmap: ImageBitmap): ImageBitmap? {
    val density = LocalDensity.current
    var groundBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(worldState.structureRevision) {
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
                drawNaturalGroundInternal(worldState.tiles, grassBitmap)
            }
            targetBitmap
        }
        groundBitmap = bitmap
    }

    return groundBitmap
}

private fun DrawScope.drawNaturalGroundInternal(
    tiles: Array<Array<TileType>>,
    grassBitmap: ImageBitmap
) {
    val tileSize = Constants.TILE_SIZE
    val dstIntSize = IntSize(tileSize.roundToInt(), tileSize.roundToInt())

    for (x in 0 until Constants.MAP_TILES_W) {
        for (y in 0 until Constants.MAP_TILES_H) {
            val px = x * tileSize
            val py = y * tileSize
            val tileType = tiles[x][y]

            if (tileType == TileType.WALL) {
                drawRect(Color(0xFF795548), Offset(px, py), Size(tileSize, tileSize))
            } else {
                drawImage(
                    image = grassBitmap,
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

// --- Prop Cache ---

@Composable
fun rememberPropCache(worldState: WorldState): ImageBitmap? {
    val density = LocalDensity.current
    val resources = LocalContext.current.resources
    val treeBitmap = remember(resources) { ImageBitmap.imageResource(resources, R.drawable.tree) }
    var propBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(worldState.structureRevision) {
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
                drawPropsInternal(worldState.tiles, treeBitmap)
            }
            targetBitmap
        }
        propBitmap = bitmap
    }

    return propBitmap
}

private fun DrawScope.drawPropsInternal(
    tiles: Array<Array<TileType>>,
    treeBitmap: ImageBitmap
) {
    val tileSize = Constants.TILE_SIZE
    val treePositions = mutableListOf<Offset>()

    for (x in 0 until Constants.MAP_TILES_W) {
        for (y in 0 until Constants.MAP_TILES_H) {
            val tileType = tiles[x][y]
            
            if (tileType.isGrass && tileType != TileType.BUILDING_FOOTPRINT && tileType != TileType.ROAD) {
                val tileSeed = (x * 73856093) xor (y * 19349663)
                val rng = Random(tileSeed.toLong())
                
                val px = x * tileSize
                val py = y * tileSize

                val treeChance = if (tileType == TileType.GRASS_DARK || tileType == TileType.GRASS_DARK_TUFT) 0.08f else 0.02f
                if (rng.nextFloat() < treeChance) {
                    treePositions.add(Offset(px + tileSize / 2f, py + tileSize))
                }
            }
        }
    }

    treePositions
        .sortedWith(compareBy<Offset> { it.y }.thenBy { it.x })
        .forEach { drawTreeSprite(it, treeBitmap, tileSize) }
}

private fun DrawScope.drawTreeSprite(center: Offset, bitmap: ImageBitmap, tileSize: Float) {
    val targetWidth = tileSize * 5.6f
    val scale = targetWidth / bitmap.width
    val targetHeight = bitmap.height * scale
    drawImage(
        image = bitmap,
        dstOffset = IntOffset(
            (center.x - targetWidth / 2f).roundToInt(),
            (center.y - targetHeight).roundToInt()
        ),
        dstSize = IntSize(targetWidth.roundToInt(), targetHeight.roundToInt()),
        filterQuality = FilterQuality.None
    )
}

// --- Road Overlay Cache ---

class RoadOverlayCache {
    var bitmap by mutableStateOf<ImageBitmap?>(null)
        private set

    var revision by mutableIntStateOf(0)
        private set

    private val density = Density(1f)
    private val drawScope = CanvasDrawScope()
    private var canvas: Canvas? = null
    private var sizeXPx: Int = 0
    private var sizeYPx: Int = 0

    fun stampTiles(tiles: List<com.example.utopia.util.IntOffset>) {
        if (tiles.isEmpty()) return
        ensureBitmap()
        val targetCanvas = canvas ?: return
        val currentBitmap = bitmap ?: return
        val size = Size(currentBitmap.width.toFloat(), currentBitmap.height.toFloat())

        drawScope.draw(
            density = density,
            layoutDirection = LayoutDirection.Ltr,
            canvas = targetCanvas,
            size = size
        ) {
            tiles.forEach { tile ->
                val worldPos = Offset(tile.x * Constants.TILE_SIZE, tile.y * Constants.TILE_SIZE)
                val seed = ((tile.x * 73856093) xor (tile.y * 19349663)).toLong()
                drawRoadOverlayTileInternal(worldPos, Constants.TILE_SIZE, seed)
            }
        }

        revision++
    }

    fun rebuildFromTiles(tiles: Array<Array<TileType>>) {
        ensureBitmap()
        val targetCanvas = canvas ?: return
        val currentBitmap = bitmap ?: return
        val size = Size(currentBitmap.width.toFloat(), currentBitmap.height.toFloat())

        drawScope.draw(
            density = density,
            layoutDirection = LayoutDirection.Ltr,
            canvas = targetCanvas,
            size = size
        ) {
            drawRect(Color.Transparent, size = size, blendMode = BlendMode.Clear)

            for (x in tiles.indices) {
                for (y in tiles[x].indices) {
                    if (tiles[x][y] == TileType.ROAD) {
                        val worldPos = Offset(x * Constants.TILE_SIZE, y * Constants.TILE_SIZE)
                        val seed = ((x * 73856093) xor (y * 19349663)).toLong()
                        drawRoadOverlayTileInternal(worldPos, Constants.TILE_SIZE, seed)
                    }
                }
            }
        }

        revision++
    }

    fun redrawTiles(tiles: List<com.example.utopia.util.IntOffset>, worldTiles: Array<Array<TileType>>) {
        if (tiles.isEmpty()) return
        ensureBitmap()
        val targetCanvas = canvas ?: return
        val currentBitmap = bitmap ?: return
        val size = Size(currentBitmap.width.toFloat(), currentBitmap.height.toFloat())

        drawScope.draw(
            density = density,
            layoutDirection = LayoutDirection.Ltr,
            canvas = targetCanvas,
            size = size
        ) {
            tiles.forEach { tile ->
                if (tile.x !in 0 until Constants.MAP_TILES_W || tile.y !in 0 until Constants.MAP_TILES_H) return@forEach
                val worldPos = Offset(tile.x * Constants.TILE_SIZE, tile.y * Constants.TILE_SIZE)
                drawRect(
                    Color.Transparent,
                    topLeft = worldPos,
                    size = Size(Constants.TILE_SIZE, Constants.TILE_SIZE),
                    blendMode = BlendMode.Clear
                )
                if (worldTiles[tile.x][tile.y] == TileType.ROAD) {
                    val seed = ((tile.x * 73856093) xor (tile.y * 19349663)).toLong()
                    drawRoadOverlayTileInternal(worldPos, Constants.TILE_SIZE, seed)
                }
            }
        }

        revision++
    }

    private fun ensureBitmap() {
        val targetSizeX = Constants.WORLD_W_PX.toInt()
        val targetSizeY = Constants.WORLD_H_PX.toInt()
        if (bitmap == null || sizeXPx != targetSizeX || sizeYPx != targetSizeY) {
            sizeXPx = targetSizeX
            sizeYPx = targetSizeY
            val newBitmap = ImageBitmap(sizeXPx, sizeYPx)
            bitmap = newBitmap
            canvas = Canvas(newBitmap)
        }
    }
}

private fun DrawScope.drawRoadOverlayTileInternal(pos: Offset, tileSize: Float, seed: Long) {
    val roadColor = Color(0xFFBCAAA4)
    val detailColor = Color(0xFF8D6E63).copy(alpha = 0.3f)

    drawRect(roadColor, pos, Size(tileSize, tileSize))

    val rng = Random(seed)
    repeat(6) {
        val rx = pos.x + rng.nextFloat() * (tileSize * 0.7f) + (tileSize * 0.15f)
        val ry = pos.y + rng.nextFloat() * (tileSize * 0.7f) + (tileSize * 0.15f)
        val rw = 2f + rng.nextFloat() * 6f
        val rh = 1f + rng.nextFloat() * 4f
        drawRoundRect(detailColor, Offset(rx, ry), Size(rw, rh), CornerRadius(1f, 1f))
    }
}

// --- Portrait Cache ---

private const val PORTRAIT_RENDER_SCALE = 4f
private const val PORTRAIT_BASE_CANVAS_PX = 64f
private const val PORTRAIT_RENDER_PX = (PORTRAIT_BASE_CANVAS_PX * PORTRAIT_RENDER_SCALE).toInt()
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
