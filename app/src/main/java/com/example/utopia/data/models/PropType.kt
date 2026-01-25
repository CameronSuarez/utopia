package com.example.utopia.data.models

import com.example.utopia.util.Constants
import kotlinx.serialization.Serializable

/**
 * Defines the types of natural props that can be placed in the world.
 */
@Serializable
enum class PropType(
    val footprintOffset: GridOffset = GridOffset(0, 0) // The offset of the footprint from the anchor
) {
    TREE_1(
        footprintOffset = GridOffset(0, 0)
    )
    // Add other prop types here if you have them, like ROCKS or BUSHES
}
