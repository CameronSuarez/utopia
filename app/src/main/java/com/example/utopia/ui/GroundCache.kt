package com.example.utopia.ui

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
import androidx.compose.ui.graphics.FilterQuality // Added for pixel-perfect drawing
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import com.example.utopia.data.models.TileType
import com.example.utopia.data.models.WorldState
import com.example.utopia.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Random
import kotlin.math.roundToInt

/**
 * GroundBaseCache: natural terrain (grass) and building footprints ONLY.
 * Trees and rocks have been moved to PropCache to allow roads to render underneath them.
 */
@Composable
fun rememberGroundCache(worldState: WorldState, grassBitmap: ImageBitmap): ImageBitmap? {
    val density = LocalDensity.current
    var groundBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    // Key on structureRevision to rebuild when building footprints change.
    LaunchedEffect(worldState.structureRevision, Constants.GROUND_CACHE_REV) {
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

private fun noise2D(x: Float, y: Float): Float {
    val ix = x.toInt()
    val iy = y.toInt()
    val fx = x - ix
    val fy = y - iy

    fun hash(ix: Int, iy: Int): Float {
        val seed = (ix * 73856093) xor (iy * 19349663)
        return Random(seed.toLong()).nextFloat()
    }

    val v00 = hash(ix, iy)
    val v10 = hash(ix + 1, iy)
    val v01 = hash(ix, iy + 1)
    val v11 = hash(ix + 1, iy + 1)

    val nx0 = v00 * (1 - fx) + v10 * fx
    val nx1 = v01 * (1 - fx) + v11 * fx
    return nx0 * (1 - fy) + nx1 * fy
}

private fun getNaturalTileType(x: Int, y: Int): TileType {
    val scale = 0.08f
    val n = noise2D(x * scale, y * scale)
    val isLight = n > 0.5f

    if (isLight) return TileType.GRASS_LIGHT

    val seed = (x * 73856093) xor (y * 19349663)
    val rng = Random(seed.toLong())
    return if (rng.nextFloat() < 0.05f) TileType.GRASS_DARK_TUFT else TileType.GRASS_DARK
}

/**
 * Draws the ground once into a cache, using grassBitmap for the natural ground.
 * Roads and natural props (trees/rocks) are excluded.
 * Color variation and tufts are removed as requested.
 */
private fun DrawScope.drawNaturalGroundInternal(
    tiles: Array<Array<TileType>>,
    grassBitmap: ImageBitmap
) {
    val tileSize = Constants.TILE_SIZE
    // Revert to original size, removing overlap logic
    val sizePx = Size(tileSize, tileSize)
    val dstIntSize = androidx.compose.ui.unit.IntSize(tileSize.roundToInt(), tileSize.roundToInt())

    // Removed: drawRect to clear canvas with grass color

    for (x in 0 until Constants.MAP_TILES_W) {
        for (y in 0 until Constants.MAP_TILES_H) {
            val px = x * tileSize
            val py = y * tileSize

            val tileType = tiles[x][y]
            
            // 1. Draw the base tile
            val topLeftOffset = Offset(px, py)

            // Only WALL tiles remain a solid color. Building footprints and all others get the grass asset.
            if (tileType == TileType.WALL) {
                drawRect(Color(0xFF795548), topLeftOffset, sizePx) // Wall is a solid color
            } else {
                // Draw the grass bitmap for all natural grass and footprint tiles
                drawImage(
                    image = grassBitmap,
                    dstOffset = IntOffset(px.roundToInt(), py.roundToInt()),
                    dstSize = dstIntSize, // Use original size
                    filterQuality = FilterQuality.None // Use nearest-neighbor to fix grid edges (Checklist #5)
                )
            }

            // Removed: Section 2. Apply light/dark tinting on top of the image for visual variety
            // Removed: Section 3. Draw natural details (tufts) on top
        }
    }
}