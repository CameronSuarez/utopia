package com.example.utopia.domain

import com.example.utopia.data.StructureRegistry
import com.example.utopia.data.models.Structure
import com.example.utopia.data.models.TileType
import com.example.utopia.data.models.WorldState
import com.example.utopia.util.Constants
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class WorldAnalysisSystemTest {
    @Test
    fun worldAnalysisDoesNotMutateInputState() {
        val json = """
            [
              {
                "id": "LUMBERJACK_HUT",
                "behavior": "STAMP",
                "spriteWidthPx": 32.0,
                "spriteHeightPx": 32.0,
                "blocksNavigation": true,
                "capacity": 1,
                "baselineTileY": 1,
                "produces": { "WOOD": 1 }
              }
            ]
        """.trimIndent()
        StructureRegistry.init(ByteArrayInputStream(json.toByteArray()))

        val tiles = Array(Constants.MAP_TILES_W) { Array(Constants.MAP_TILES_H) { TileType.GRASS_LIGHT } }
        val structure = Structure(
            id = "s1",
            typeId = "LUMBERJACK_HUT",
            x = 0f,
            y = Constants.TILE_SIZE,
            isComplete = true
        )
        val state = WorldState(
            tiles = tiles,
            structures = listOf(structure)
        )

        val updated = WorldAnalysisSystem.update(state, 16L, 0L)

        assertNotSame(state, updated)
        assertTrue(updated.transient_hasAvailableWorkplace)
        assertTrue(!state.transient_hasAvailableWorkplace)
    }
}
