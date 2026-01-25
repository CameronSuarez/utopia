package com.example.utopia.data.models

import kotlinx.serialization.Serializable

/**
 * An instance of a natural prop (e.g., a tree or a rock) in the world.
 * These are rendered in the depth-sorted pass to allow proper occlusion.
 */
@Serializable
data class PropInstance(
    val id: String, // Use String ID for now, as it's consistent with Structure/Agent IDs
    val type: PropType,
    val homeTileX: Int, // The tile coordinate of the prop's base (trunk/anchor)
    val homeTileY: Int,
    val anchorX: Float, // The world X coordinate of the prop's Y-sort anchor point
    val anchorY: Float, // The world Y coordinate of the prop's Y-sort anchor point
)
