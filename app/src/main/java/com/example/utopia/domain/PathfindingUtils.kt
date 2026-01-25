package com.example.utopia.domain

/**
 * PATHFINDING UTILITIES
 * 
 * DESIGN PRINCIPLE:
 * These utilities should NOT reason about TileType semantics.
 * They should rely on the rasterized truth provided by NavGrid.
 */

fun isLocationBlocked(navGrid: NavGrid, gx: Int, gy: Int): Boolean {
    return !navGrid.isWalkable(gx, gy)
}
