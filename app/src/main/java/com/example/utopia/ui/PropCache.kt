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
import androidx.compose.ui.graphics.FilterQuality // Added for consistent pixel filtering
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import com.example.utopia.R
import com.example.utopia.data.models.TileType
import com.example.utopia.data.models.WorldState
import com.example.utopia.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Random
import kotlin.math.roundToInt

@Composable
fun rememberPropCache(worldState: WorldState): ImageBitmap? {
    val density = LocalDensity.current
    val resources = LocalContext.current.resources
    val treeBitmap = remember(resources) { ImageBitmap.imageResource(resources, R.drawable.tree) }
    var propBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

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
            
            // Props only spawn on grass and aren't covered by buildings or roads.
            if (tileType.isGrass && tileType != TileType.BUILDING_FOOTPRINT && tileType != TileType.ROAD) {
                val tileSeed = (x * 73856093) xor (y * 19349663)
                val rng = Random(tileSeed.toLong())
                val noise = rng.nextFloat()
                
                val px = x * tileSize
                val py = y * tileSize

                val treeChance = if (tileType == TileType.GRASS_DARK || tileType == TileType.GRASS_DARK_TUFT) 0.08f else 0.02f
                val hasTree = rng.nextFloat() < treeChance
                if (hasTree) {
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
    val targetWidth = tileSize * 5.6f // Scaled down from 6.4f (approx 88% of original size)
    val scale = targetWidth / bitmap.width
    val targetHeight = bitmap.height * scale
    drawImage(
        image = bitmap,
        dstOffset = IntOffset(
            (center.x - targetWidth / 2f).roundToInt(),
            (center.y - targetHeight).roundToInt()
        ),
        dstSize = IntSize(targetWidth.roundToInt(), targetHeight.roundToInt()),
        filterQuality = FilterQuality.None // Use nearest-neighbor for consistent filtering (Checklist #5)
    )
}