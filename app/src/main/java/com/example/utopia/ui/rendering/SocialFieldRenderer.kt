package com.example.utopia.ui.rendering

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.utopia.data.models.SocialField
import com.example.utopia.ui.Camera2D

object SocialFieldRenderer {

    fun drawSocialFields(
        drawScope: DrawScope,
        socialFields: List<SocialField>,
        camera: Camera2D
    ) {
        with(drawScope) {
            socialFields.forEach { field ->
                val wx = field.center.x * 16f // Assuming tileSize factor if needed, or just world coords
                val wy = field.center.y * 16f
                
                val screenPos = androidx.compose.ui.geometry.Offset(
                    wx * camera.zoom + camera.offset.x,
                    wy * camera.zoom + camera.offset.y
                )
                val screenRadius = field.radius * camera.zoom

                // Draw the outer radius
                drawCircle(
                    color = Color.Yellow,
                    center = screenPos,
                    radius = screenRadius,
                    style = Stroke(width = 2f)
                )

                // Draw the center
                drawCircle(
                    color = Color.Yellow,
                    center = screenPos,
                    radius = 5f
                )
            }
        }
    }
}
