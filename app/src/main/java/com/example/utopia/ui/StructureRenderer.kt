package com.example.utopia.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.example.utopia.data.models.Structure
import com.example.utopia.util.Constants
import com.example.utopia.util.WorldGridMath
import java.util.Random
import com.example.utopia.util.IntOffset as UtopiaIntOffset

data class StructureAssets(
    val houseLabelPaint: android.graphics.Paint,
    val houseBitmap: ImageBitmap,
    val tavernBitmap: ImageBitmap,
    val workshopBitmap: ImageBitmap,
    val storeBitmap: ImageBitmap,
    val plazaBitmap: ImageBitmap,
    val constructionSiteBitmap: ImageBitmap,
    val lumberjackHutBitmap: ImageBitmap
)

fun DrawScope.drawStructureItem(
    structure: Structure,
    camera: Camera2D,
    assets: StructureAssets,
    agentNameById: Map<String, String>
) {
    val spec = structure.spec
    val worldW = spec.worldWidth
    val worldH = spec.worldHeight

    val worldDrawX = structure.x
    val worldDrawY = structure.y - worldH

    val screenPos = worldToScreen(Offset(worldDrawX, worldDrawY), camera)
    val screenSize = worldSizeToScreen(Size(worldW, worldH), camera)

    if (screenPos.x + screenSize.width < 0f || screenPos.x > size.width || screenPos.y + screenSize.height < 0f || screenPos.y > size.height) return

    if (!structure.isComplete) {
        drawStructureBitmap(assets.constructionSiteBitmap, screenPos, screenSize)
        return
    }

    val bitmap: ImageBitmap? = when (spec.id) {
        "HOUSE" -> assets.houseBitmap
        "STORE" -> assets.storeBitmap
        "WORKSHOP" -> assets.workshopBitmap
        "PLAZA" -> assets.plazaBitmap
        "TAVERN" -> assets.tavernBitmap
        "LUMBERJACK_HUT" -> assets.lumberjackHutBitmap
        else -> null
    }

    if (bitmap != null) {
        drawStructureBitmap(bitmap, screenPos, screenSize)

        if (spec.id == "HOUSE") {
            val ownerLabel = structure.customName ?: structure.residents.firstOrNull()?.let { agentNameById[it] }?.let { "${it}'s House" }
            val label = ownerLabel ?: "House"
            val labelX = screenPos.x + screenSize.width / 2f
            val labelY = screenPos.y - 6f
            drawContext.canvas.nativeCanvas.drawText(label, labelX, labelY, assets.houseLabelPaint)
        }
    } else {
        val rng = Random(structure.id.hashCode().toLong())
        when (spec.id) {
            "CASTLE" -> drawMedievalCastle(screenPos, screenSize.width, screenSize.height, rng)
            "WALL" -> drawRect(Color(0xFF795548), screenPos, Size(screenSize.width - 1, screenSize.height - 1))
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
