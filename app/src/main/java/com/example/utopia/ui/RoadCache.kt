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
import androidx.compose.ui.graphics.FilterQuality // Added for pixel-perfect drawing
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import com.example.utopia.data.models.TileType
import com.example.utopia.data.models.WorldState
import com.example.utopia.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * A dedicated cache for roads, matching the GroundCache approach.
 * Rebuilds only when worldState.roadRevision changes.
 */
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
                
                // Draw the road tile asset instead of calling drawRoadTileInternal
                drawImage(
                    image = roadBitmapAsset,
                    dstOffset = IntOffset(px.roundToInt(), py.roundToInt()),
                    dstSize = dstSize,
                    filterQuality = FilterQuality.None // Use nearest-neighbor to fix grid edges (Checklist #5)
                )
            }
        }
    }
}