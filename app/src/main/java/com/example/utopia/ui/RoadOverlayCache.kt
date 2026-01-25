package com.example.utopia.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.example.utopia.data.models.TileType
import com.example.utopia.util.Constants
import com.example.utopia.util.IntOffset
import java.util.Random

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

    fun stampTiles(tiles: List<IntOffset>) {
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

    fun redrawTiles(tiles: List<IntOffset>, worldTiles: Array<Array<TileType>>) {
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
