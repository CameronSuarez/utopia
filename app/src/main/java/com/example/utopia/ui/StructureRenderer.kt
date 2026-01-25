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

fun DrawScope.drawStructureItem(
    structure: Structure,
    camera: Camera2D,
    assets: StructureAssets,
    agentNameById: Map<String, String>
) {
    val screenPos = worldToScreen(Offset(structure.x, structure.y), camera)
    val screenSize = worldSizeToScreen(Size(structure.type.worldWidth, structure.type.worldHeight), camera)

    if (screenPos.x + screenSize.width < 0f || screenPos.x > size.width || screenPos.y + screenSize.height < 0f || screenPos.y > size.height) return

    val rng = Random(structure.id.hashCode().toLong())

    when (structure.type) {
        StructureType.HOUSE -> {
            val bitmap = assets.houseBitmap
            val baseW = structure.type.worldWidth
            val baseH = structure.type.worldHeight

            val bmpW = bitmap.width.toFloat()
            val bmpH = bitmap.height.toFloat()

            // Scale uniformly so the sprite fits inside the logical world box.
            val s = minOf(baseW / bmpW, baseH / bmpH)

            // If you want a bit wider, nudge here (minor art tweak)
            val sx = s * 1.08f   // try 1.05–1.12
            val sy = s * 1.15f   // try 1.10–1.25 (fix “too low”)

            // Final draw size (preserves aspect, then applies small nudges) in WORLD UNITS
            val worldDstW = bmpW * sx
            val worldDstH = bmpH * sy

            val VISUAL_MULT = 2f
            val dstWf = worldDstW * VISUAL_MULT
            val dstHf = worldDstH * VISUAL_MULT

            // Anchor on the building’s baseline (bottom-ish), not top-left
            // This is the WORLD coordinate of the sprite's top-left corner
            val worldDrawX = structure.x + (baseW - dstWf) * 0.5f
            val worldDrawY = structure.y + structure.type.baselineWorld - dstHf

            // Convert world units to screen units for drawing
            val screenDrawSize = Size(dstWf * camera.zoom, dstHf * camera.zoom)
            val screenDrawOffset = worldToScreen(Offset(worldDrawX, worldDrawY), camera)

            drawStructureBitmap(bitmap, screenDrawOffset, screenDrawSize)

            val ownerLabel = structure.customName ?: structure.residents.firstOrNull()?.let { agentNameById[it] }?.let { "${it}'s House" }
            val label = ownerLabel ?: "House"
            val labelX = screenPos.x + screenSize.width / 2f
            val labelY = screenDrawOffset.y - 6f
            drawContext.canvas.nativeCanvas.drawText(label, labelX, labelY, assets.houseLabelPaint)
        }
        StructureType.STORE -> {
            val bitmap = assets.storeBitmap
            val baseW = structure.type.worldWidth
            val baseH = structure.type.worldHeight

            val bmpW = bitmap.width.toFloat()
            val bmpH = bitmap.height.toFloat()

            // Scale uniformly so the sprite fits inside the logical world box.
            val s = minOf(baseW / bmpW, baseH / bmpH)

            // If you want a bit wider, nudge here (minor art tweak)
            val sx = s * 1.08f
            val sy = s * 1.15f

            // Final draw size (preserves aspect, then applies small nudges) in WORLD UNITS
            val worldDstW = bmpW * sx
            val worldDstH = bmpH * sy

            val VISUAL_MULT = 2f
            val dstWf = worldDstW * VISUAL_MULT
            val dstHf = worldDstH * VISUAL_MULT

            // Anchor on the building’s baseline (bottom-ish), not top-left
            val worldDrawX = structure.x + (baseW - dstWf) * 0.5f
            val worldDrawY = structure.y + structure.type.baselineWorld - dstHf

            // Convert world units to screen units for drawing
            val screenDrawSize = Size(dstWf * camera.zoom, dstHf * camera.zoom)
            val screenDrawOffset = worldToScreen(Offset(worldDrawX, worldDrawY), camera)

            drawStructureBitmap(bitmap, screenDrawOffset, screenDrawSize)
        }
        StructureType.WORKSHOP -> {
            val bitmap = assets.workshopBitmap
            val baseW = structure.type.worldWidth
            val baseH = structure.type.worldHeight

            val bmpW = bitmap.width.toFloat()
            val bmpH = bitmap.height.toFloat()

            // Scale uniformly so the sprite fits inside the logical world box.
            val s = minOf(baseW / bmpW, baseH / bmpH)

            // If you want a bit wider, nudge here (minor art tweak)
            val sx = s * 1.08f
            val sy = s * 1.15f

            // Final draw size (preserves aspect, then applies small nudges) in WORLD UNITS
            val worldDstW = bmpW * sx
            val worldDstH = bmpH * sy

            val VISUAL_MULT = 2f
            val dstWf = worldDstW * VISUAL_MULT
            val dstHf = worldDstH * VISUAL_MULT

            // Anchor on the building’s baseline (bottom-ish), not top-left
            val worldDrawX = structure.x + (baseW - dstWf) * 0.5f
            val worldDrawY = structure.y + structure.type.baselineWorld - dstHf

            // Convert world units to screen units for drawing
            val screenDrawSize = Size(dstWf * camera.zoom, dstHf * camera.zoom)
            val screenDrawOffset = worldToScreen(Offset(worldDrawX, worldDrawY), camera)

            drawStructureBitmap(bitmap, screenDrawOffset, screenDrawSize)
        }
        StructureType.CASTLE -> drawMedievalCastle(screenPos, screenSize.width, screenSize.height, rng)
        StructureType.PLAZA -> {
            val bitmap = assets.plazaBitmap
            val baseW = structure.type.worldWidth
            val baseH = structure.type.worldHeight

            val bmpW = bitmap.width.toFloat()
            val bmpH = bitmap.height.toFloat()

            // Scale uniformly so the sprite fits inside the logical world box.
            val s = minOf(baseW / bmpW, baseH / bmpH)

            // If you want a bit wider, nudge here (minor art tweak)
            val sx = s * 1.08f
            val sy = s * 1.15f

            // Final draw size (preserves aspect, then applies small nudges) in WORLD UNITS
            val worldDstW = bmpW * sx
            val worldDstH = bmpH * sy

            val VISUAL_MULT = 2f
            val dstWf = worldDstW * VISUAL_MULT
            val dstHf = worldDstH * VISUAL_MULT

            // Anchor on the building’s baseline (bottom-ish), not top-left
            val worldDrawX = structure.x + (baseW - dstWf) * 0.5f
            val worldDrawY = structure.y + structure.type.baselineWorld - dstHf

            // Convert world units to screen units for drawing
            val screenDrawSize = Size(dstWf * camera.zoom, dstHf * camera.zoom)
            val screenDrawOffset = worldToScreen(Offset(worldDrawX, worldDrawY), camera)

            drawStructureBitmap(bitmap, screenDrawOffset, screenDrawSize)
        }
        StructureType.TAVERN -> {
            val bitmap = assets.tavernBitmap
            val baseW = structure.type.worldWidth
            val baseH = structure.type.worldHeight

            val bmpW = bitmap.width.toFloat()
            val bmpH = bitmap.height.toFloat()

            // Scale uniformly so the sprite fits inside the logical world box.
            val s = minOf(baseW / bmpW, baseH / bmpH)

            // If you want a bit wider, nudge here (minor art tweak)
            val sx = s * 1.08f
            val sy = s * 1.15f

            // Final draw size (preserves aspect, then applies small nudges) in WORLD UNITS
            val worldDstW = bmpW * sx
            val worldDstH = bmpH * sy

            val VISUAL_MULT = 2f
            val dstWf = worldDstW * VISUAL_MULT
            val dstHf = worldDstH * VISUAL_MULT

            // Anchor on the building’s baseline (bottom-ish), not top-left
            val worldDrawX = structure.x + (baseW - dstWf) * 0.5f
            val worldDrawY = structure.y + structure.type.baselineWorld - dstHf

            // Convert world units to screen units for drawing
            val screenDrawSize = Size(dstWf * camera.zoom, dstHf * camera.zoom)
            val screenDrawOffset = worldToScreen(Offset(worldDrawX, worldDrawY), camera)

            drawStructureBitmap(bitmap, screenDrawOffset, screenDrawSize)
        }
        // ROAD is handled via RoadCache and RoadPrimitives.
        // It is explicitly excluded from StructureRenderer to maintain layer integrity.
        else -> {
            val color = when (structure.type) {
                StructureType.WALL -> Color(0xFF795548)
                else -> Color(0xFF9CCC65)
            }
            drawRect(color, screenPos, Size(screenSize.width - 1, screenSize.height - 1))
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