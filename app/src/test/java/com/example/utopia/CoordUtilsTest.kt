package com.example.utopia

import androidx.compose.ui.geometry.Offset
import com.example.utopia.util.Constants
import com.example.utopia.util.CoordUtils
import org.junit.Assert.assertEquals
import org.junit.Test

class CoordUtilsTest {

    @Test
    fun testGridToWorld() {
        assertEquals(0f, CoordUtils.gridToWorld(0))
        assertEquals(Constants.TILE_SIZE, CoordUtils.gridToWorld(1))
        assertEquals(Constants.TILE_SIZE * 5, CoordUtils.gridToWorld(5))
    }

    @Test
    fun testScreenToGrid() {
        val cameraOffset = Offset(100f, 200f)
        
        // Exactly at tile (0,0) world space, which is (100, 200) screen space
        val screenPos1 = Offset(100f, 200f)
        val grid1 = CoordUtils.screenToGrid(screenPos1, cameraOffset)
        assertEquals(0, grid1.x)
        assertEquals(0, grid1.y)

        // Inside tile (1,1) world space
        val screenPos2 = Offset(100f + Constants.TILE_SIZE + 10f, 200f + Constants.TILE_SIZE + 10f)
        val grid2 = CoordUtils.screenToGrid(screenPos2, cameraOffset)
        assertEquals(1, grid2.x)
        assertEquals(1, grid2.y)
        
        // Negative (clamped to 0)
        val screenPos3 = Offset(0f, 0f)
        val grid3 = CoordUtils.screenToGrid(screenPos3, cameraOffset)
        assertEquals(0, grid3.x)
        assertEquals(0, grid3.y)
        
        // Out of bounds (clamped to max)
        val screenPos4 = Offset(10000f, 10000f)
        val grid4 = CoordUtils.screenToGrid(screenPos4, cameraOffset)
        assertEquals(Constants.GRID_SIZE - 1, grid4.x)
        assertEquals(Constants.GRID_SIZE - 1, grid4.y)
    }

    @Test
    fun testGridCenterToWorld() {
        assertEquals(Constants.TILE_SIZE * 0.5f, CoordUtils.gridCenterToWorld(0))
        assertEquals(Constants.TILE_SIZE * 1.5f, CoordUtils.gridCenterToWorld(1))
    }
}
