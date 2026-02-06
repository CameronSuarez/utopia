package com.example.utopia.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.example.utopia.data.models.PropInstance
import com.example.utopia.data.models.PropType
import com.example.utopia.util.Constants
import kotlin.math.roundToInt

// --- Fast Tuning Prop Size Constants (Will be moved to PropType/Constants later) ---
private const val TREE_WIDTH_TILES = 7.3125f // Desired width in tiles (was 5.85f, increased by 25%)
// ---------------------------------------------------------------------------------

data class PropAssets(
    val tree1: ImageBitmap,
)

/**
 * Draws an individual prop instance in the world.
 */
fun DrawScope.drawPropItem(prop: PropInstance, camera: Camera2D, assets: PropAssets) {
    val propType = prop.type
    // Reconstruct world position from serializable fields
    val worldPos = Offset(prop.anchorX, prop.anchorY)
    val screenPos = worldToScreen(worldPos, camera)

    // Select the correct bitmap
    val bitmap = when (propType) {
        PropType.TREE_1 -> assets.tree1
    }

    // 1. Determine the target width in world units (based on TILE_SIZE)
    val targetWWorld = when (propType) {
        PropType.TREE_1 -> TREE_WIDTH_TILES * Constants.TILE_SIZE
    }

    // 2. Compute screen dimensions while preserving aspect ratio (no distortion)
    val targetWScreen = targetWWorld * camera.zoom
    val scale = targetWScreen / bitmap.width.toFloat()
    val targetHScreen = bitmap.height * scale

    val drawSize = Size(
        width = targetWScreen,
        height = targetHScreen
    )

    // 3. Calculate draw offset to anchor the bottom center of the sprite at screenPos (prop's anchor)
    // This ensures correct Y-sorting, as the sprite's bottom will be at the depthY baseline.
    val drawOffset = Offset(
        x = screenPos.x - drawSize.width / 2f, // Centered horizontally
        y = screenPos.y - drawSize.height      // Bottom edge aligns with screenPos.y
    )

    // The preferred drawImage overload for scaled drawing uses integer-based IntOffset/IntSize
    drawImage(
        image = bitmap,
        dstOffset = IntOffset(drawOffset.x.roundToInt(), drawOffset.y.roundToInt()),
        dstSize = IntSize(drawSize.width.roundToInt(), drawSize.height.roundToInt()),
    )
}