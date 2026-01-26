package com.example.utopia.util

object Constants {
    const val DEBUG_ASSERTS = true

    // --- Core World Dimensions (Required for WorldManager/NavGrid/Rendering) ---
    const val TILE_SIZE = 32f
    const val WORLD_SCALE = 2f

    const val MAP_TILES_W = 100
    const val MAP_TILES_H = 100

    val WORLD_W_PX = MAP_TILES_W * TILE_SIZE
    val WORLD_H_PX = MAP_TILES_H * TILE_SIZE

    const val GRID_SIZE = MAP_TILES_W 

    // --- Building/Agent Capacity (Required for Spawning/Placement) ---
    const val HOUSE_CAPACITY = 1
    const val MAX_AGENTS = 150

    // --- Time/Cycle (Kept to maintain timeOfDay loop) ---
    const val PHASE_DURATION_SEC = 120f
    const val TOTAL_CYCLE_SEC = 480f

    // --- Passive Agent Movement & Pathing (Required for AgentMovement.kt) ---
    const val ROAD_COST = 1
    const val GRASS_COST = 5
    const val OFF_ROAD_PATH_LIMIT = 20
    const val OFF_ROAD_HIGH_COST = 50
    const val OFF_ROAD_SPEED_MULT = 0.6f
    const val ON_ROAD_SPEED_MULT = 0.7f

    const val AGENT_BASE_SPEED_FACTOR = 0.075f
    const val AGENT_MOVEMENT_SCALAR = 2.0f

    // Agent Animation (Kept for AgentUpdate.kt animation logic)
    const val PHASE_STAGGER_MAX_MS = 20000L 

    // UI & Interaction
    const val TOOLBAR_DRAG_SLOP = 25f 

    // Separation Constants (Required for AgentMovement.kt)
    const val AGENT_COLLISION_RADIUS = 32f
    
}
