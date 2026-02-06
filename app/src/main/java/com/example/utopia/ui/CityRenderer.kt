package com.example.utopia.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import com.example.utopia.data.models.AgentRuntime
import com.example.utopia.data.models.PropInstance
import com.example.utopia.data.models.Structure
import com.example.utopia.domain.NavGrid
import com.example.utopia.util.IntOffset as UtilIntOffset
import com.example.utopia.util.Constants
import kotlin.math.floor
import kotlin.math.roundToInt

fun DrawScope.drawCity(
    context: RenderContext,
    snapshot: SceneSnapshot,
    layers: List<RenderLayer> = listOf(
        GroundLayer(),
        WorldObjectLayer(),
        DebugOverlayLayer()
    )
) {
    layers.forEach { layer ->
        with(layer) {
            draw(context, snapshot)
        }
    }
}

internal fun DrawScope.drawNavGridOverlay(navGrid: NavGrid, camera: Camera2D) {
    val (camX, camY) = -camera.offset
    val worldX = camX / camera.zoom
    val worldY = camY / camera.zoom
    val tileSize = Constants.TILE_SIZE

    val startX = floor(worldX / tileSize).toInt().coerceIn(0, navGrid.width)
    val startY = floor(worldY / tileSize).toInt().coerceIn(0, navGrid.height)
    val endX = (startX + size.width / (tileSize * camera.zoom) + 2).toInt().coerceIn(0, navGrid.width)
    val endY = (startY + size.height / (tileSize * camera.zoom) + 2).toInt().coerceIn(0, navGrid.height)

    for (x in startX until endX) {
        for (y in startY until endY) {
            val tileValue = navGrid.grid[x][y]
            val color = when (tileValue.toInt()) {
                0 -> Color.Red.copy(alpha = 0.4f)
                1 -> Color.Green.copy(alpha = 0.4f)
                2 -> Color.Blue.copy(alpha = 0.4f)
                else -> Color.Transparent
            }
            if (color != Color.Transparent) {
                drawRect(
                    color = color,
                    topLeft = Offset(x * tileSize * camera.zoom + camera.offset.x, y * tileSize * camera.zoom + camera.offset.y),
                    size = Size(tileSize * camera.zoom, tileSize * camera.zoom)
                )
            }
        }
    }
}

internal enum class InternalRenderLayer {
    STRUCTURES,
    PROPS,
    AGENTS,
    FX
}

internal data class RenderItem(
    val layer: InternalRenderLayer,
    val depthY: Float,
    val depthX: Float,
    val tieBreak: Int,
    val draw: DrawScope.() -> Unit
)

internal fun agentFootpointY(agent: AgentRuntime): Float = agent.y + Constants.TILE_SIZE

internal fun structureBaselineY(structure: Structure): Float =
    structure.y + structure.spec.baselineWorld

internal fun propBaselineY(prop: PropInstance): Float =
    prop.anchorY

internal fun DrawScope.drawRoadBitmap(bitmap: ImageBitmap, camera: Camera2D) {
    withTransform({
        translate(camera.offset.x, camera.offset.y)
        scale(camera.zoom, camera.zoom, pivot = Offset.Zero)
    }) {
        drawImage(bitmap, topLeft = Offset.Zero)
    }
}

internal fun DrawScope.drawLiveRoads(tiles: List<UtilIntOffset>, camera: Camera2D, roadAsset: ImageBitmap) {
    val tileSize = Constants.TILE_SIZE
    val dstSize = androidx.compose.ui.unit.IntSize(tileSize.roundToInt(), tileSize.roundToInt())

    withTransform({
        translate(camera.offset.x, camera.offset.y)
        scale(camera.zoom, camera.zoom, pivot = Offset.Zero)
    }) {
        for (tile in tiles) {
            val wx = tile.x * tileSize
            val wy = tile.y * tileSize

            drawImage(
                image = roadAsset,
                dstOffset = androidx.compose.ui.unit.IntOffset(wx.roundToInt(), wy.roundToInt()),
                dstSize = dstSize
            )
        }
    }
}
