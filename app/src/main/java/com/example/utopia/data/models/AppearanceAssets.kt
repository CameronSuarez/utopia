package com.example.utopia.data.models

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color

// --- Data Structures for Drawable Shapes ---

sealed class DrawableShape {
    data class Rect(val offset: Offset, val size: Size) : DrawableShape()
    data class RoundRect(val offset: Offset, val size: Size, val cornerRadius: CornerRadius) : DrawableShape()
    data class Circle(val radius: Float, val center: Offset) : DrawableShape()
    // In the future, this could be extended with Path, Bitmap, etc.
}

// --- Data Structures for Appearance Assets ---

sealed class AppearanceAsset(val id: String) {
    abstract val shapes: List<DrawableShape>
}

data class HairAsset(
    val assetId: String,
    override val shapes: List<DrawableShape>
) : AppearanceAsset(assetId)


// --- Central Registry for all Appearance Data ---

object AppearanceRegistry {

    // --- Color Palettes ---

    val SKIN_TONES = listOf(
        Color(0xFFfce2c4), Color(0xFFf7d3ac), Color(0xFFf3c292), Color(0xFFdfad84),
        Color(0xFFc99873), Color(0xFFb48261), Color(0xFFa16c4f), Color(0xFF8c583e),
        Color(0xFF76462d), Color(0xFF62341d)
    )

    val HAIR_COLORS = listOf(
        Color(0xFFdcdcdc), Color(0xFFc0c0c0), Color(0xFFa9a9a9), Color(0xFF909090),
        Color(0xFFf5f5dc), Color(0xFFfffacd), Color(0xFFfafad2), Color(0xFFeee8aa),
        Color(0xFFb8860b), Color(0xFFdaa520), Color(0xFFcd853f), Color(0xFFd2691e),
        Color(0xFFa0522d), Color(0xFF8b4513), Color(0xFF6b4423), Color(0xFF4a2c1a)
    )

    val TUNIC_COLORS = listOf(
        Color(0xFFb71c1c), Color(0xFF880e4f), Color(0xFF4a148c), Color(0xFF311b92),
        Color(0xFF1a237e), Color(0xFF0d47a1), Color(0xFF01579b), Color(0xFF006064),
        Color(0xFF004d40), Color(0xFF1b5e20), Color(0xFF33691e), Color(0xFF827717),
        Color(0xFFf57f17), Color(0xFFff6f00), Color(0xFFe65100), Color(0xFFbf360c)
    )

    // --- Hair Assets ---

    private val hairAssets = listOf(
        HairAsset(
            assetId = "short",
            shapes = listOf(DrawableShape.Rect(Offset(-5f, -5.5f), Size(10f, 3f)))
        ),
        HairAsset(
            assetId = "bowl",
            shapes = listOf(DrawableShape.RoundRect(Offset(-6f, -6f), Size(12f, 6f), CornerRadius(3f, 3f)))
        ),
        HairAsset(
            assetId = "long",
            shapes = listOf(
                DrawableShape.Rect(Offset(-6f, -6f), Size(12f, 3f)),
                DrawableShape.Rect(Offset(-6f, -6f), Size(3f, 11f)),
                DrawableShape.Rect(Offset(3f, -6f), Size(3f, 11f))
            )
        ),
        HairAsset(
            assetId = "cap", // Felt Cap is now an "asset"
            shapes = listOf(DrawableShape.Circle(4.5f, Offset(0f, -5f)))
        ),
        HairAsset(
            assetId = "bun", // Top Knot / Bun
            shapes = listOf(
                DrawableShape.Circle(3f, Offset(0f, -4f)),
                DrawableShape.Circle(2f, Offset(0f, -7f))
            )
        )
    )

    fun getHairAsset(id: Int): HairAsset? {
        val styleId = when (id) {
            1 -> "short"
            2 -> "bowl"
            3 -> "long"
            4 -> "cap"
            5 -> "bun"
            else -> return null
        }
        return hairAssets.find { it.assetId == styleId }
    }
}
