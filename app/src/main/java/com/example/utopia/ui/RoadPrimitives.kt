package com.example.utopia.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import java.util.Random

/**
 * The single authoritative implementation for drawing a road tile.
 * This is used by RoadCache for baking and by PlacementController for live previews.
 */
fun DrawScope.drawRoadTileInternal(pos: Offset, tileSize: Float, seed: Long) {
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
