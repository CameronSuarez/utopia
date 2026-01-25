package com.example.utopia.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.example.utopia.data.models.Structure
import com.example.utopia.data.models.StructureType
import com.example.utopia.util.Constants
import com.example.utopia.util.WorldGridMath
import com.example.utopia.util.IntOffset as UtopiaIntOffset
import java.util.Random

data class StructureAssets(
    val houseLabelPaint: android.graphics.Paint,
    val houseBitmap: ImageBitmap,
    val tavernBitmap: ImageBitmap,
    val workshopBitmap: ImageBitmap,
    val storeBitmap: ImageBitmap,
    val plazaBitmap: ImageBitmap
)

/**
 * Renders a single structure.
 *
 * ANCHOR CONTRACT: This function assumes the `structure.x`, `structure.y` coordinates
 * represent the BOTTOM-LEFT anchor of the structure's sprite in world space.
 * All rendering calculations are performed relative to this point.
 */
fun DrawScope.drawStructureItem(
    structure: Structure,
    camera: Camera2D,
    assets: StructureAssets,
    agentNameById: Map<String, String>
) {
    // Authoritative dimensions from the data model
    val worldW = structure.type.worldWidth
    val worldH = structure.type.worldHeight

    // ANCHOR CONVERSION: The structure's (x,y) is the bottom-left. To draw, we need the top-left.
    val worldDrawX = structure.x
    val worldDrawY = structure.y - worldH

    // Convert world coordinates and size to screen space for culling and drawing.
    val screenPos = worldToScreen(Offset(worldDrawX, worldDrawY), camera)
    val screenSize = worldSizeToScreen(Size(worldW, worldH), camera)

    // Culling
    if (screenPos.x + screenSize.width < 0f || screenPos.x > size.width || screenPos.y + screenSize.height < 0f || screenPos.y > size.height) return

    val bitmap: ImageBitmap? = when (structure.type) {
        StructureType.HOUSE -> assets.houseBitmap
        StructureType.STORE -> assets.storeBitmap
        StructureType.WORKSHOP -> assets.workshopBitmap
        StructureType.PLAZA -> assets.plazaBitmap
        StructureType.TAVERN -> assets.tavernBitmap
        else -> null
    }

    if (bitmap != null) {
        drawStructureBitmap(bitmap, screenPos, screenSize)

        if (structure.type == StructureType.HOUSE) {
            val ownerLabel = structure.customName ?: structure.residents.firstOrNull()?.let { agentNameById[it] }?.let { "${it}'s House" }
            val label = ownerLabel ?: "House"
            val labelX = screenPos.x + screenSize.width / 2f
            val labelY = screenPos.y - 6f
            drawContext.canvas.nativeCanvas.drawText(label, labelX, labelY, assets.houseLabelPaint)
        }
    } else {
        // Fallback for structures without bitmaps
        val rng = Random(structure.id.hashCode().toLong())
        when (structure.type) {
            StructureType.CASTLE -> drawMedievalCastle(screenPos, screenSize.width, screenSize.height, rng)
            StructureType.WALL -> drawRect(Color(0xFF795548), screenPos, Size(screenSize.width - 1, screenSize.height - 1))
            else -> drawRect(Color(0xFF9CCC65), screenPos, Size(screenSize.width - 1, screenSize.height - 1))
        }
    }
}


private fun DrawScope.drawStructureBitmap(bitmap: ImageBitmap, pos: Offset, size: Size) {
    val dstOffset = IntOffset(pos.x.toInt(), pos.y.toInt())
    val dstSize = IntSize(size.width.toInt(), size.height.toInt())
    this.drawImage(
        image = bitmap,
        dstOffset = dstOffset,
        dstSize = dstSize
    )
}

fun DrawScope.drawLiveRoads(liveRoadTiles: List<UtopiaIntOffset>, camera: Camera2D) {
    val tileSize = worldSizeToScreen(Size(Constants.TILE_SIZE, Constants.TILE_SIZE), camera).width
    liveRoadTiles.forEach { tile ->
        val worldPos = WorldGridMath.tileToWorld(tile.x, tile.y)
        val screenPos = worldToScreen(worldPos, camera)
        // Uses the central authoritative primitive from RoadPrimitives.kt
        drawRoadTileInternal(screenPos, tileSize, (tile.x * 31 + tile.y).toLong())
    }
}

private fun DrawScope.drawMedievalCastle(pos: Offset, w: Float, h: Float, rng: Random) {
    val stoneColor = Color(0xFF616161)
    drawRect(Color(0xFF9CCC65), pos, Size(w, h))
    val padW = w * 0.9f
    val padH = h * 0.9f
    val padOffset = Offset(pos.x + (w - padW) / 2f, pos.y + (h - padH) / 2f)
    drawRect(stoneColor, padOffset, Size(padW, padH))
    drawRect(stoneColor, Offset(pos.x - 6f, pos.y - 15f), Size(w * 0.25f, h + 15f))
    drawRect(stoneColor, Offset(pos.x + w * 0.75f + 6f, pos.y - 15f), Size(w * 0.25f, h + 15f))
    val gateW = w * 0.4f
    val gateH = h * 0.6f
    val gateX = pos.x + (w - gateW) / 2f
    val gateY = pos.y + h - gateH
    drawRect(Color(0xFF3E2723), Offset(gateX, gateY), Size(gateW, gateH))
}