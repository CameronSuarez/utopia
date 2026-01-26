package com.example.utopia.util

object Constants {
    const val DEBUG_ASSERTS = true
    const val USE_SIMPLE_NAV = false

    const val TILE_SIZE = 32f
    const val WORLD_SCALE = 2f

    const val MAP_TILES_W = 100
    const val MAP_TILES_H = 100

    val WORLD_W_PX = MAP_TILES_W * TILE_SIZE
    val WORLD_H_PX = MAP_TILES_H * TILE_SIZE

    const val GRID_SIZE = MAP_TILES_W // only if you truly assume square worlds everywhere

    // Cache Revision - Increment to force GroundCache rebuild
    const val GROUND_CACHE_REV = 6 // Increment to force cache rebuild after structure scale changes

    // Global Building Sprite Scale

    const val HOUSE_SPRITE_MULT = 1f
    const val STORE_SPRITE_MULT = 1f
    const val WORKSHOP_SPRITE_MULT = 1f
    const val TAVERN_SPRITE_MULT = 1f

    // Performance Toggles
    const val DISABLE_NATURAL_GROUND = false // Set to false to use caching

    const val HOUSE_CAPACITY = 1 // Reduced from 4 to 1 per user request
    const val HOUSE_SPAWN_INTERVAL_MS = 10000L
    const val MAX_AGENTS = 150 // Slightly reduced to match smaller grid

    const val MAX_TAVERNS_PER_TOWN = 2
    const val MAX_PLAZAS_PER_TOWN = 2
    const val MAX_TAVERN_OCCUPANTS = 10
    const val TAVERN_ELIGIBLE_AT = 9

    const val MAX_PLAZA_OCCUPANTS = 10
    const val PLAZA_ELIGIBLE_AT = 9
    const val PLAZA_EVENING_OVERFLOW_CHANCE = 0.30f

    // Phase 4: Tavern Social Buffs
    const val TAVERN_SOCIAL_RANGE_MULT = 1.33f
    const val TAVERN_SOCIAL_COOLDOWN_MULT = 0.70f
    const val TAVERN_SOCIAL_DURATION_BONUS_MS = 1000L
    const val TAVERN_SOCIAL_TRIGGER_BONUS_PROB = 0.20f

    // Plaza Phase P4: Social Modifiers
    const val PLAZA_BUMP_COOLDOWN_MULT = 0.60f
    const val PLAZA_LINGER_CHANCE_DAY = 0.12f
    const val PLAZA_LINGER_CHANCE_EVENING = 0.05f
    const val PLAZA_LINGER_MS_MIN = 1000L
    const val PLAZA_LINGER_MS_MAX = 3000L

    // Phase 5: Tavern Relationship Bias
    const val TAVERN_FRIENDLY_PLUS1_CHANCE = 0.85f
    const val TAVERN_HOSTILE_MINUS1_CHANCE = 0.60f
    const val TAVERN_MIXED_PLUS1_CHANCE = 0.60f

    // Village AI v2: 8-minute total cycle (480 seconds)
    // 2 minutes per phase = 120 seconds
    const val PHASE_DURATION_SEC = 120f
    const val TOTAL_CYCLE_SEC = 480f
    const val TOTAL_CYCLE_MS = 480000L
    const val MS_PER_SEC = 1000L

    const val ROAD_COST = 1
    const val GRASS_COST = 5
    const val OFF_ROAD_PATH_LIMIT = 20
    const val OFF_ROAD_HIGH_COST = 50
    const val OFF_ROAD_SPEED_MULT = 0.6f
    const val ON_ROAD_SPEED_MULT = 0.7f

    // Agent Movement
    const val AGENT_BASE_SPEED_FACTOR = 0.075f
    const val AGENT_MOVEMENT_SCALAR = 2.0f

    // Agent Animation
    const val BASE_ANIMATION_WALK_SPEED = 0.09f
    const val MIN_ANIMATION_SPEED_MULT = 0.9f
    const val MAX_ANIMATION_SPEED_MULT = 1.15f


    const val SOCIAL_DISTANCE = 1.2f
    const val SOCIAL_DURATION_MS = 5000L
    const val SOCIAL_COOLDOWN_MS = 15000L

    const val RANDOM_VISIT_CHANCE = 0.02f // Reduced for intentional feel
    const val DETOUR_COOLDOWN_MS = 45000L
    const val PHASE_STAGGER_MAX_MS = 20000L // Up to 20s stagger
    const val INTERSECTION_PAUSE_MS = 800L

    // UI & Interaction
    const val TOOLBAR_DRAG_SLOP = 25f // Threshold to distinguish vertical drag-out from toolbar scrolling

    // Optimization Constants
    const val AI_TICK_MS = 100L // 10Hz AI
    const val PATHFIND_CAP_PER_FRAME = 5
    const val SPATIAL_HASH_CELL_SIZE = 4 // Tiles per cell for social interaction

    // Separation Constants
    const val AGENT_COLLISION_RADIUS = 32f
    const val SEPARATION_STRENGTH = 0.35f
    const val MAX_SEPARATION_STEP = 6f

    // Relationship Constants
    const val MIN_RELATIONSHIP = -3
    const val MAX_RELATIONSHIP = 3

    // Stuck Detection
    const val STUCK_WINDOW_MS = 1200L
    const val STUCK_PROGRESS_EPS = 8f // approx 0.125 tiles
}