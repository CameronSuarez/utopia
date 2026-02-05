package com.example.utopia.util

object Constants {
    // --- Core World Dimensions (Required for WorldManager/NavGrid/Rendering) ---
    const val TILE_SIZE = 32f
    const val SPRITE_TILE_SIZE = 16f // The pixel dimension of a single tile in the source sprite assets.
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
    const val INTENT_COMMITMENT_WEIGHT = 0.15f // Extra pressure added to current intent during commitment window

    // --- Needs Tuning Constants ---
    const val NEEDS_SECONDS_PER_DAY = 420f

    const val NEEDS_SLEEP_DECAY_PER_DAY = 100f
    const val NEEDS_SLEEP_GAIN_PER_DAY = 400f

    const val NEEDS_SOCIAL_DECAY_PER_DAY = 60f
    const val NEEDS_SOCIAL_GAIN_PER_DAY = 300f

    const val NEEDS_FUN_DECAY_PER_DAY = 50f
    const val NEEDS_FUN_GAIN_PER_DAY = 250f

    const val NEEDS_STABILITY_DECAY_PER_DAY = 40f
    const val NEEDS_STABILITY_GAIN_PER_DAY = 200f

    const val NEEDS_STIMULATION_GAIN_PER_DAY = 70f
    const val NEEDS_STIMULATION_DECAY_PER_DAY = 300f

    // --- Social / Gossip Constants ---
    const val GOSSIP_CHANCE = 0.05f           // Chance per tick for an agent to gossip in a group
    const val GOSSIP_SPILLOVER_FACTOR = 0.5f  // How much of the speaker's opinion transfers

    // UI & Interaction
    const val TOOLBAR_DRAG_SLOP = 25f

    // Separation Constants (Required for AgentMovement.kt)
    const val AGENT_COLLISION_RADIUS = 36.8f
}
