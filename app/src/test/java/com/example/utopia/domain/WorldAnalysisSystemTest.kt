package com.example.utopia.domain

import com.example.utopia.data.models.TileType
import com.example.utopia.data.models.WorldState
import com.example.utopia.util.Constants
import org.junit.Assert.assertSame
import org.junit.Test

class WorldAnalysisSystemTest {
    @Test
    fun worldAnalysisReturnsSameStateWhenNoFlagsComputed() {
        val tiles = Array(Constants.MAP_TILES_W) { Array(Constants.MAP_TILES_H) { TileType.GRASS_LIGHT } }
        val state = WorldState(
            tiles = tiles
        )

        val updated = WorldAnalysisSystem.update(state, 16L, 0L)

        assertSame(state, updated)
    }
}
