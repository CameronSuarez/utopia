package com.example.utopia.domain

import androidx.compose.ui.geometry.Offset
import com.example.utopia.data.models.Structure
import com.example.utopia.util.Constants
import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.max

/**
 * DESIGN PRINCIPLE: THE DETERMINISTIC NAVIGATOR
 *
 * This system is responsible for calculating optimal routes between points.
 * It is a stateless "pure function" service that queries the [NavGrid]
 * to find valid paths.
 *
 * Responsibilities:
 * 1. Multi-modal Routing: Planning combined road and off-road segments.
 * 2. A* Search: Finding the shortest valid sequence of tiles.
 * 3. Path Simplification: Reducing zigzag tile paths into clean waypoints.
 * 4. Spatial Queries: Finding nearest walkable tiles or roads.
 */

object Pathfinding {
    private class Node(
        val packed: Int,
        var g: Int = 0,
        var h: Int = 0,
        var parent: Node? = null
    ) {
        val f: Int get() = g + h
        val x: Int get() = packed shr 16
        val y: Int get() = packed and 0xFFFF
    }

    enum class PathMode {
        ROAD_ONLY,
        FULL // 8-directional pathfinding
    }

    private val roadPathCache = mutableMapOf<Pair<Int, Int>, List<Int>>()

    fun clearCache() {
        roadPathCache.clear()
    }

    fun planRoute(
        startPos: Offset,
        targetStructureId: String?,
        targetWorldPos: Offset,
        navGrid: NavGrid,
        structures: List<Structure>,
        requiredClearance: Float = 0f
    ): Triple<List<Offset>, String, Long> {
        val startTime = System.currentTimeMillis()

        val startGX = (startPos.x / Constants.TILE_SIZE).toInt().coerceIn(0, Constants.MAP_TILES_W - 1)
        val startGY = (startPos.y / Constants.TILE_SIZE).toInt().coerceIn(0, Constants.MAP_TILES_H - 1)

        val finalTargetPos = if (targetStructureId != null) {
            val s = structures.find { it.id == targetStructureId }
            if (s != null) pickWalkableTileForStructure(s, startPos, navGrid) else targetWorldPos
        } else targetWorldPos

        val endGX = (finalTargetPos.x / Constants.TILE_SIZE).toInt().coerceIn(0, Constants.MAP_TILES_W - 1)
        val endGY = (finalTargetPos.y / Constants.TILE_SIZE).toInt().coerceIn(0, Constants.MAP_TILES_H - 1)

        val startRoad = findNearestRoad(startGX, startGY, navGrid)
        val endRoad = findNearestRoad(endGX, endGY, navGrid)

        if (startRoad != null && endRoad != null) {
            val roadNodes = findAStarPath(
                startRoad.first, startRoad.second, 
                endRoad.first, endRoad.second, 
                navGrid, PathMode.ROAD_ONLY,
                requiredClearance
            )
            if (roadNodes.isNotEmpty()) {
                val fullPath = mutableListOf<Offset>()
                val roadStartPos = Offset((startRoad.first + 0.5f) * Constants.TILE_SIZE, (startRoad.second + 0.5f) * Constants.TILE_SIZE)
                val roadEndPos = Offset((endRoad.first + 0.5f) * Constants.TILE_SIZE, (endRoad.second + 0.5f) * Constants.TILE_SIZE)

                fullPath.addAll(planOffroadSegment(startPos, roadStartPos, navGrid, requiredClearance))

                roadNodes.forEach { packed ->
                    val x = packed shr 16
                    val y = packed and 0xFFFF
                    fullPath.add(Offset((x + 0.5f) * Constants.TILE_SIZE, (y + 0.5f) * Constants.TILE_SIZE))
                }

                fullPath.addAll(planOffroadSegment(roadEndPos, finalTargetPos, navGrid, requiredClearance))

                return Triple(simplifyPath(fullPath), "ROAD", System.currentTimeMillis() - startTime)
            }
        }

        val path = planOffroadSegment(startPos, finalTargetPos, navGrid, requiredClearance)
        val mode = if (startRoad != null && endRoad != null) "OFFROAD_DISCONNECTED" else "OFFROAD_NO_ENDPOINT"
        return Triple(simplifyPath(path), mode, System.currentTimeMillis() - startTime)
    }

    private fun planOffroadSegment(start: Offset, end: Offset, navGrid: NavGrid, requiredClearance: Float): List<Offset> {
        var startGX = (start.x / Constants.TILE_SIZE).toInt()
        var startGY = (start.y / Constants.TILE_SIZE).toInt()
        val endGX = (end.x / Constants.TILE_SIZE).toInt()
        val endGY = (end.y / Constants.TILE_SIZE).toInt()

        if (!navGrid.hasClearance(startGX, startGY, requiredClearance)) {
            val nudged = nudgeOutOfObstacle(startGX, startGY, navGrid, requiredClearance)
            if (nudged != null) {
                startGX = nudged.first
                startGY = nudged.second
            }
        }

        val path = findAStarPath(startGX, startGY, endGX, endGY, navGrid, PathMode.FULL, requiredClearance)
        return if (path.isNotEmpty()) {
            path.map { packed ->
                val x = packed shr 16
                val y = packed and 0xFFFF
                Offset((x + 0.5f) * Constants.TILE_SIZE, (y + 0.5f) * Constants.TILE_SIZE)
            }
        } else {
            emptyList()
        }
    }

    fun pickWalkableTileForStructure(s: Structure, referencePos: Offset, navGrid: NavGrid): Offset {
        val pad = 1
        val minGX = (s.x / Constants.TILE_SIZE).toInt() - pad
        val minGY = ((s.y - s.spec.worldHeight) / Constants.TILE_SIZE).toInt() - pad
        val maxGX = ((s.x + s.spec.worldWidth - 1f) / Constants.TILE_SIZE).toInt() + pad
        val maxGY = (s.y / Constants.TILE_SIZE).toInt() + pad

        var nearestTile: Pair<Int, Int>? = null
        var minDistSq = Float.MAX_VALUE

        for (gx in minGX..maxGX) {
            for (gy in minGY..maxGY) {
                if (navGrid.isWalkable(gx, gy)) {
                    val tx = (gx + 0.5f) * Constants.TILE_SIZE
                    val ty = (gy + 0.5f) * Constants.TILE_SIZE
                    val dx = tx - referencePos.x
                    val dy = ty - referencePos.y
                    val dSq = dx * dx + dy * dy
                    if (dSq < minDistSq) {
                        minDistSq = dSq
                        nearestTile = gx to gy
                    }
                }
            }
        }

        return if (nearestTile != null) {
            Offset((nearestTile.first + 0.5f) * Constants.TILE_SIZE, (nearestTile.second + 0.5f) * Constants.TILE_SIZE)
        } else {
            val centerX = (s.x + s.spec.worldWidth / 2f)
            val centerY = (s.y - s.spec.worldHeight / 2f)
            val cgx = (centerX / Constants.TILE_SIZE).toInt().coerceIn(0, Constants.MAP_TILES_W - 1)
            val cgy = (centerY / Constants.TILE_SIZE).toInt().coerceIn(0, Constants.MAP_TILES_H - 1)
            val nudged = nudgeOutOfObstacle(cgx, cgy, navGrid)
            if (nudged != null) {
                Offset((nudged.first + 0.5f) * Constants.TILE_SIZE, (nudged.second + 0.5f) * Constants.TILE_SIZE)
            } else {
                Offset(centerX, centerY)
            }
        }
    }

    fun nudgeOutOfObstacle(gx: Int, gy: Int, navGrid: NavGrid, requiredClearance: Float = 0f): Pair<Int, Int>? {
        for (r in 1..3) {
            for (dx in -r..r) {
                for (dy in -r..r) {
                    val nx = gx + dx
                    val ny = gy + dy
                    if (navGrid.hasClearance(nx, ny, requiredClearance)) {
                        return nx to ny
                    }
                }
            }
        }
        return null
    }

    private fun findAStarPath(
        startX: Int, startY: Int,
        endX: Int, endY: Int,
        navGrid: NavGrid,
        mode: PathMode,
        requiredClearance: Float = 0f
    ): List<Int> {
        if (!navGrid.hasClearance(endX, endY, requiredClearance)) return emptyList()

        val startPacked = (startX shl 16) or (startY and 0xFFFF)
        val endPacked = (endX shl 16) or (endY and 0xFFFF)

        if (mode == PathMode.ROAD_ONLY) {
            val cacheKey = startPacked to endPacked
            roadPathCache[cacheKey]?.let { return it }
        }

        val openList = PriorityQueue<Node>(compareBy { it.f })
        val openNodes = mutableMapOf<Int, Node>()
        val closedList = HashSet<Int>()

        openList.add(Node(startPacked, 0, heuristic(startX, startY, endX, endY)))

        val (dx, dy, costs) = when (mode) {
            PathMode.FULL -> Triple(
                intArrayOf(0, 0, 1, -1, 1, 1, -1, -1),
                intArrayOf(1, -1, 0, 0, 1, -1, 1, -1),
                intArrayOf(10, 10, 10, 10, 14, 14, 14, 14)
            )
            PathMode.ROAD_ONLY -> Triple(
                intArrayOf(0, 0, 1, -1),
                intArrayOf(1, -1, 0, 0),
                intArrayOf(10, 10, 10, 10)
            )
        }

        while (openList.isNotEmpty()) {
            val current = openList.poll() ?: break
            if (current.x == endX && current.y == endY) {
                val path = reconstructPath(current)
                if (mode == PathMode.ROAD_ONLY) {
                    roadPathCache[startPacked to endPacked] = path
                }
                return path
            }
            closedList.add(current.packed)

            for (i in dx.indices) {
                val nx = current.x + dx[i]
                val ny = current.y + dy[i]

                if (!navGrid.hasClearance(nx, ny, requiredClearance)) continue

                if (mode == PathMode.ROAD_ONLY && navGrid.grid[nx][ny] != 2.toByte()) continue

                if (mode == PathMode.FULL && i >= 4) { // Diagonal check
                    if (!navGrid.hasClearance(current.x + dx[i], current.y, requiredClearance) || 
                        !navGrid.hasClearance(current.x, current.y + dy[i], requiredClearance)) {
                        continue
                    }
                }

                val neighborPacked = (nx shl 16) or (ny and 0xFFFF)
                if (closedList.contains(neighborPacked)) continue
                
                var turnPenalty = 0
                if (mode == PathMode.FULL && current.parent != null) {
                    val p = current.parent!!
                    if ((current.x - p.x) * (ny - current.y) != (current.y - p.y) * (nx - current.x)) {
                        turnPenalty = 20
                    }
                }

                val moveCost = costs[i] * (navGrid.getCost(nx, ny) / 10)
                val tentativeG = current.g + moveCost + turnPenalty
                val existing = openNodes[neighborPacked]

                if (existing == null || tentativeG < existing.g) {
                    val node = Node(neighborPacked, tentativeG, heuristic(nx, ny, endX, endY) * 10, current)
                    openList.add(node)
                    openNodes[neighborPacked] = node
                }
            }
        }

        if (mode == PathMode.ROAD_ONLY) {
            roadPathCache[startPacked to endPacked] = emptyList()
        }
        return emptyList()
    }

    private fun findNearestRoad(startX: Int, startY: Int, navGrid: NavGrid): Pair<Int, Int>? {
        if (startX !in 0 until navGrid.width || startY !in 0 until navGrid.height) return null
        if (navGrid.grid[startX][startY] == 2.toByte()) return Pair(startX, startY)

        var nearest: Pair<Int, Int>? = null
        var minDistSq = Float.MAX_VALUE

        for (radius in 1..12) {
            for (dx in -radius..radius) {
                for (dy in -radius..radius) {
                    if (abs(dx) != radius && abs(dy) != radius) continue
                    val nx = startX + dx
                    val ny = startY + dy

                    if (nx in 0 until navGrid.width && ny in 0 until navGrid.height) {
                        if (navGrid.grid[nx][ny] == 2.toByte()) {
                            val distSq = (dx * dx + dy * dy).toFloat()
                            if (distSq < minDistSq) {
                                minDistSq = distSq
                                nearest = Pair(nx, ny)
                            }
                        }
                    }
                }
            }
            if (nearest != null) return nearest
        }
        return null
    }


    private fun heuristic(x1: Int, y1: Int, x2: Int, y2: Int): Int {
        val dx = abs(x1 - x2)
        val dy = abs(y1 - y2)
        return max(dx, dy)
    }

    private fun reconstructPath(node: Node): List<Int> {
        val path = mutableListOf<Int>()
        var current: Node? = node
        while (current != null) {
            path.add(current.packed)
            current = current.parent
        }
        return path.reversed()
    }

    private fun simplifyPath(path: List<Offset>): List<Offset> {
        if (path.size < 3) return path
        val uniquePath = path.distinct()
        if (uniquePath.size < 3) return uniquePath

        val simplified = mutableListOf<Offset>()
        simplified.add(uniquePath.first())

        for (i in 1 until uniquePath.size - 1) {
            val p1 = simplified.last()
            val p2 = uniquePath[i]
            val p3 = uniquePath[i + 1]

            val crossProduct = (p2.y - p1.y) * (p3.x - p2.x) - (p2.x - p1.x) * (p3.y - p2.y)
            if (abs(crossProduct) > 0.001f) { // Not collinear
                simplified.add(p2)
            }
        }
        simplified.add(uniquePath.last())
        return simplified
    }
}
