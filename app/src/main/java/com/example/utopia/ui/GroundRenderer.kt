package com.example.utopia.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import com.example.utopia.util.Constants

fun DrawScope.drawGround(camera: Camera2D, groundBitmap: ImageBitmap?) {
    if (Constants.DISABLE_NATURAL_GROUND) {
        drawRect(Color(0xFF7CB342), Offset.Zero, size)
        return
    }

    if (groundBitmap != null) {
        withTransform({
            translate(camera.offset.x, camera.offset.y)
            scale(camera.zoom, camera.zoom, pivot = Offset.Zero)
        }) {
            drawImage(groundBitmap, topLeft = Offset.Zero)
        }
    } else {
        // Fallback while loading
        drawRect(Color(0xFF333333), Offset.Zero, size)
    }
}