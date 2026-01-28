package com.example.utopia.util

object Constants {
    // --- Core World Dimensions (Required for WorldManager/NavGrid/Rendering) ---
    const val TILE_SIZE = 32f
    const val WORLD_SCALE = 2f

    const val MAP_TILES_W = 100
    const val MAP_TILES_H = 100

    const val WORLD_W_PX = MAP_TILES_W * TILE_SIZE
    const val WORLD_H_PX = MAP_TILES_H * TILE_SIZE

    const val GRID_SIZE = MAP_TILES_W

    // --- Building/Agent Capacity (Required for Spawning/Placement) ---
    const val HOUSE_CAPACITY = 1
    const val MAX_AGENTS = 150

    // --- Passive Agent Movement & Pathing (Required for AgentMovement.kt) ---
    const val ROAD_COST = 1
    const val GRASS_COST = 5
    const val OFF_ROAD_SPEED_MULT = 0.6f
    const val ON_ROAD_SPEED_MULT = 0.7f

    const val AGENT_BASE_SPEED_FACTOR = 0.075f

    // --- Force-Based Movement Constants ---
    const val INTENT_FORCE = 3.5f
    const val WANDER_FORCE = 1.2f
    const val SEPARATION_FORCE = 6.0f

    const val MAX_SPEED = 2.5f
    const val MAX_FORCE = 10.0f

    // --- AI / Intent Constants ---
    const val INTENT_COMMITMENT_MS = 8000L // 8 seconds of sticking to a task
    const val MOMENTUM_BIAS = 0.15f        // Extra pressure added to current intent

    // UI & Interaction
    const val TOOLBAR_DRAG_SLOP = 25f

    // Separation Constants (Required for AgentMovement.kt)
    const val AGENT_COLLISION_RADIUS = 32f
}
