package com.example.utopia.domain

import android.util.Log
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.example.utopia.data.models.PropInstance
import com.example.utopia.data.models.PropType
import com.example.utopia.data.models.Structure
import com.example.utopia.data.models.StructureType
import com.example.utopia.data.models.TileType
import com.example.utopia.util.Constants

private const val ENABLE_INCREMENTAL_VALIDATION = true // Set to false for production performance
private const val USE_DISTANCE_FIELD = true // Toggle for clearance optimization

class NavGrid(val width: Int = Constants.MAP_TILES_W, val height: Int = Constants.MAP_TILES_H) {

    private data class GridBounds(
        val minGX: Int,
        val maxGX: Int,
        val minGY: Int,
        val maxGY: Int
    )

    /**
     * The grid representing the navigation mesh.
     * Values:
     * 0 = Blocked
     * 1 = Walkable (e.g., Grass)
     * 2 = Road (higher cost to walk on than grass for pathfinding)
     */
    var grid: Array<ByteArray> = Array(width) { ByteArray(height) }
        private set

    /**
     * Clearance grid storing distance to nearest obstacle in tiles.
     * 0 = Blocked, 1 = Adjacent to obstacle, etc.
     */
    var clearanceGrid: IntArray = IntArray(width * height)
        private set

    /**
     * Programmatic Validation State.
     * Plain Kotlin property to keep the domain object UI-agnostic.
     */
    var lastValidationError: String? = null
        private set

    fun isWalkable(x: Int, y: Int): Boolean {
        if (x !in 0 until width || y !in 0 until height) return false
        return grid[x][y] > 0
    }

    /**
     * Checks if a circular area centered at grid coordinates (gx, gy) is entirely walkable.
     * @param radiusWorld The radius in world units.
     */
    fun hasClearance(gx: Int, gy: Int, radiusWorld: Float): Boolean {
        if (!isWalkable(gx, gy)) return false

        if (USE_DISTANCE_FIELD) {
            val radiusTiles = kotlin.math.ceil(radiusWorld / Constants.TILE_SIZE).toInt()
            return clearanceGrid[gx * height + gy] >= radiusTiles
        } else {
            return hasClearanceLegacy(gx, gy, radiusWorld)
        }
    }

    private fun hasClearanceLegacy(gx: Int, gy: Int, radiusWorld: Float): Boolean {
        // Use ceil to ensure we check every tile the radius might touch.
        // Truncating (toInt()) would lead to zero-size clearance for sub-tile radii.
        val radiusTiles = kotlin.math.ceil(radiusWorld / Constants.TILE_SIZE).toInt()
        if (radiusTiles <= 0) return true

        for (dx in -radiusTiles..radiusTiles) {
            for (dy in -radiusTiles..radiusTiles) {
                // Circular check: only check tiles within the radius
                if (dx * dx + dy * dy <= radiusTiles * radiusTiles) {
                    if (!isWalkable(gx + dx, gy + dy)) return false
                }
            }
        }
        return true
    }

    fun getCost(x: Int, y: Int): Int {
        if (x !in 0 until width || y !in 0 until height) return Int.MAX_VALUE
        return when (grid[x][y]) {
            2.toByte() -> Constants.ROAD_COST
            else -> Constants.GRASS_COST
        }
    }

    fun update(
        tiles: Array<Array<TileType>>,
        structures: List<Structure>,
        props: List<PropInstance>,
        dirtyRect: Rect? = null
    ) {
        val regionBounds = if (dirtyRect != null) {
            val bounds = rectToGridBounds(dirtyRect)
            // Inflate bounds by +/- 1 tile for safety to ensure we catch edge overlaps
            GridBounds(
                minGX = (bounds.minGX - 1).coerceAtLeast(0),
                maxGX = (bounds.maxGX + 1).coerceAtMost(width - 1),
                minGY = (bounds.minGY - 1).coerceAtLeast(0),
                maxGY = (bounds.maxGY + 1).coerceAtMost(height - 1)
            )
        } else {
            GridBounds(0, width - 1, 0, height - 1)
        }

        // Perform the actual update on the live grid
        performUpdate(grid, regionBounds, tiles, structures, props)

        // Recompute clearance field (Step 3, 4, 5)
        recalculateClearanceField(if (dirtyRect != null) regionBounds else null)

        // Validation mode: compares incremental result against a fresh full rebuild
        if (dirtyRect != null && ENABLE_INCREMENTAL_VALIDATION) {
            validateIncrementalUpdate(tiles, structures, props)
        } else if (dirtyRect == null) {
            // Full update successful, clear any previous validation error
            lastValidationError = null
        }
    }

    /**
     * Internal implementation shared by live update and validation rebuild.
     */
    private fun performUpdate(
        targetGrid: Array<ByteArray>,
        regionBounds: GridBounds,
        tiles: Array<Array<TileType>>,
        structures: List<Structure>,
        props: List<PropInstance>
    ) {
        // 1. Layer 1: Inherent Tile Blocking (Authority: TraversalRules)
        rasterizeTiles(targetGrid, tiles, regionBounds)

        // 2. Layer 2: Structure Blockage (Authority: Shared Footprint Logic)
        for (structure in structures) {
            rasterizeFootprint(targetGrid, getStructureFootprint(structure), regionBounds)
        }

        // 3. Layer 3: Prop Blockage
        for (prop in props) {
            rasterizeFootprint(targetGrid, getPropFootprint(prop), regionBounds)
        }
    }

    private fun rasterizeTiles(targetGrid: Array<ByteArray>, tiles: Array<Array<TileType>>, bounds: GridBounds) {
        for (x in bounds.minGX..bounds.maxGX) {
            for (y in bounds.minGY..bounds.maxGY) {
                val tile = tiles[x][y]
                targetGrid[x][y] = when {
                    TraversalRules.isInherentlyBlocked(tile) -> 0 // Blocked
                    tile == TileType.ROAD -> 2 // Road
                    else -> 1 // Walkable
                }
            }
        }
    }

    private fun rasterizeFootprint(targetGrid: Array<ByteArray>, footprint: Rect, regionBounds: GridBounds) {
        val bounds = rectToGridBounds(footprint)
        
        // Overlap check in grid space
        if (bounds.maxGX >= regionBounds.minGX && bounds.minGX <= regionBounds.maxGX &&
            bounds.maxGY >= regionBounds.minGY && bounds.minGY <= regionBounds.maxGY) {
            
            val startX = maxOf(bounds.minGX, regionBounds.minGX)
            val endX = minOf(bounds.maxGX, regionBounds.maxGX)
            val startY = maxOf(bounds.minGY, regionBounds.minGY)
            val endY = minOf(bounds.maxGY, regionBounds.maxGY)

            for (gx in startX..endX) {
                for (gy in startY..endY) {
                    targetGrid[gx][gy] = 0 // Blocked
                }
            }
        }
    }

    private fun recalculateClearanceField(dirtyBounds: GridBounds? = null) {
        val queue = java.util.ArrayDeque<Int>()
        val maxDist = width + height

        // Step 5: Dirty-rect safety expansion
        val evalBounds = if (dirtyBounds != null) {
            val expansion = 12 // Slightly larger margin for safety
            GridBounds(
                minGX = (dirtyBounds.minGX - expansion).coerceAtLeast(0),
                maxGX = (dirtyBounds.maxGX + expansion).coerceAtMost(width - 1),
                minGY = (dirtyBounds.minGY - expansion).coerceAtLeast(0),
                maxGY = (dirtyBounds.maxGY + expansion).coerceAtMost(height - 1)
            )
        } else {
            GridBounds(0, width - 1, 0, height - 1)
        }

        // Step 3 & 4 (Fixed): Seed blocked cells and Boundaries
        for (x in evalBounds.minGX..evalBounds.maxGX) {
            for (y in evalBounds.minGY..evalBounds.maxGY) {
                val idx = x * height + y
                
                // Distance to map edge (Virtual wall at -1 and width)
                val distToEdge = minOf(
                    minOf(x + 1, width - x),
                    minOf(y + 1, height - y)
                )

                if (grid[x][y] == 0.toByte()) {
                    clearanceGrid[idx] = 0
                    queue.add(idx)
                } else {
                    // Treat edges as sources of "low clearance"
                    clearanceGrid[idx] = if (dirtyBounds != null) {
                        val isEvalBoundary = x == evalBounds.minGX || x == evalBounds.maxGX ||
                                             y == evalBounds.minGY || y == evalBounds.maxGY
                        if (isEvalBoundary) {
                            queue.add(idx)
                            minOf(clearanceGrid[idx], distToEdge)
                        } else {
                            distToEdge.coerceAtMost(maxDist)
                        }
                    } else {
                        distToEdge
                    }
                    
                    // If this cell is constrained by an edge, it's a seed
                    if (clearanceGrid[idx] < maxDist) {
                        queue.add(idx)
                    }
                }
            }
        }

        // 8-connected BFS (Chebyshev Distance)
        // This ensures DF <= Euclidean, meaning we NEVER overestimate clearance.
        val dx = intArrayOf(0, 0, 1, -1, 1, 1, -1, -1)
        val dy = intArrayOf(1, -1, 0, 0, 1, -1, 1, -1)

        while (queue.isNotEmpty()) {
            val idx = queue.poll()!!
            val cx = idx / height
            val cy = idx % height
            val d = clearanceGrid[idx]

            for (i in 0 until 8) {
                val nx = cx + dx[i]
                val ny = cy + dy[i]

                if (nx in evalBounds.minGX..evalBounds.maxGX && ny in evalBounds.minGY..evalBounds.maxGY) {
                    val nIdx = nx * height + ny
                    if (clearanceGrid[nIdx] > d + 1) {
                        clearanceGrid[nIdx] = d + 1
                        queue.add(nIdx)
                    }
                }
            }
        }
    }

    private fun validateIncrementalUpdate(
        tiles: Array<Array<TileType>>,
        structures: List<Structure>,
        props: List<PropInstance>
    ) {
        val referenceGrid = Array(width) { ByteArray(height) }
        val fullBounds = GridBounds(0, width - 1, 0, height - 1)
        
        // Build a fresh reference from scratch
        performUpdate(referenceGrid, fullBounds, tiles, structures, props)

        // Compare Grid
        var mismatchCount = 0
        for (x in 0 until width) {
            for (y in 0 until height) {
                if (grid[x][y] != referenceGrid[x][y]) {
                    if (mismatchCount < 10) {
                        Log.e("NavGrid", "Mismatch at $x,$y: Live=${grid[x][y]}, Ref=${referenceGrid[x][y]}")
                    }
                    mismatchCount++
                }
            }
        }

        if (mismatchCount > 0) {
            val errorMsg = "GRID UPDATE FAILURE: $mismatchCount mismatches!"
            Log.e("NavGrid", errorMsg)
            lastValidationError = errorMsg
            return
        }

        // Cross-validate Clearance Field (Step 8)
        val rng = java.util.Random()
        for (i in 0 until 50) {
            val rx = rng.nextInt(width)
            val ry = rng.nextInt(height)
            val testRadiusWorld = (rng.nextFloat() * 2f * Constants.TILE_SIZE) // Test up to 2 tiles radius
            
            val legacyResult = hasClearanceLegacy(rx, ry, testRadiusWorld)
            val dfResult = if (!isWalkable(rx, ry)) false else {
                val radiusTiles = kotlin.math.ceil(testRadiusWorld / Constants.TILE_SIZE).toInt()
                clearanceGrid[rx * height + ry] >= radiusTiles
            }

            if (legacyResult != dfResult) {
                val errorMsg = "CLEARANCE VALIDATION FAILURE at $rx,$ry (R=${testRadiusWorld}) DF=$dfResult Legacy=$legacyResult"
                Log.e("NavGrid", errorMsg)
                lastValidationError = errorMsg
                return
            }
        }

        Log.d("NavGrid", "Incremental update & clearance validated successfully.")
        // Don't clear error here to keep the last failure visible in UI
    }

    private fun rectToGridBounds(rect: Rect): GridBounds {
        return GridBounds(
            minGX = (rect.left / Constants.TILE_SIZE).toInt().coerceIn(0, width - 1),
            maxGX = ((rect.right - 0.001f) / Constants.TILE_SIZE).toInt().coerceIn(0, width - 1),
            minGY = (rect.top / Constants.TILE_SIZE).toInt().coerceIn(0, height - 1),
            maxGY = ((rect.bottom - 0.001f) / Constants.TILE_SIZE).toInt().coerceIn(0, height - 1)
        )
    }

    private fun getStructureFootprint(structure: Structure): Rect {
        val type = structure.type
        val footprintHeight = Constants.TILE_SIZE * 0.2f // Make it 40% of a tile high

        val x = structure.x + (type.footprintOffset.x * Constants.TILE_SIZE)
        val y = structure.y + type.worldHeight - footprintHeight + (type.footprintOffset.y * Constants.TILE_SIZE)

        return Rect(
            offset = Offset(x, y),
            size = Size(type.worldWidth, footprintHeight)
        )
    }

    private fun getPropFootprint(prop: PropInstance): Rect {
        val type = prop.type
        val footprintWidth = if (type == PropType.TREE_1) {
            Constants.TILE_SIZE * 0.10f // smaller footprint for trees (reduced from 0.2f)
        } else {
            Constants.TILE_SIZE * 0.4f
        }
        val footprintHeight = footprintWidth * 0.5f

        val x = prop.anchorX - (footprintWidth / 2f) + (type.footprintOffset.x * Constants.TILE_SIZE)
        // Shift up accounting for footprint height
        val y = prop.anchorY - footprintHeight + (type.footprintOffset.y * Constants.TILE_SIZE)

        return Rect(
            offset = Offset(x, y),
            size = Size(footprintWidth, footprintHeight)
        )
    }
}
