# Performance Remediation & Optimization Plan

## 1. Performance Metrics (Theoretical / Projected)
| Scenario | Avg Frame Time (Before) | Avg Frame Time (After) | Tile Draws (Before) | Tile Draws (After) |
| :--- | :--- | :--- | :--- | :--- |
| Empty Grid (200x200) | ~4ms | <1ms | 40,000 | ~600-1200 |
| 100 Agents (Midday) | ~25ms | ~4ms | 40,000 | ~600-1200 |
| 200 Agents (Sunset) | ~45ms+ | ~8ms | 40,000 | ~600-1200 |

*Note: "After" metrics assume a standard tablet resolution showing approx. 30x20 tiles.*

## 2. Remediation Checklist

### P0: Critical Performance Fixes (COMPLETED)
- [x] **Visible-Slice Grid Rendering**: Implemented in `CityUI.kt`. Iteration now limited to viewport bounds.
- [x] **Spatial Partitioning for Social Interactions**: Implemented in `AgentSystem.kt` using a 4x4 tile spatial hash. Reduces interaction complexity from O(n²) to ~O(n).
- [x] **AI Tick Decimation & Path Throttling**: Implemented in `AgentSystem.kt`. AI runs at 10Hz. Pathfinding is capped at 5 requests per frame.

### P1: Efficiency & Maintainability (IN PROGRESS)
- [x] **A* Data Structure Optimization**: Implemented in `Pathfinding.kt`. Added `openNodes` Map to eliminate O(n) scans in the PriorityQueue.
- [ ] **Allocation Reduction**: Current agent updates still use `map { it.copy() }`. Recommend moving to a pooled or mutable approach in next phase.
- [ ] **Recomposition Scoping**: `CityCanvas` still tracks the global `worldState`.

### P2: Robustness & Polish (PENDING)
- [ ] **Save/Load Schema Versioning**.
- [ ] **Corrupted Save Recovery**.

## 3. Implementation Details
### Spatial Hash
Agents are indexed into buckets based on `(gridX/4, gridY/4)`. Social checks now only query the agent's current cell and 8 neighbors, drastically reducing distance calculations.

### AI Decoupling
The `AgentSystem` now maintains an internal `aiTickTimer`. While movement interpolation remains at 60Hz for smoothness, heavy logic (state changes, social checks, pathfinding) is deferred to a 100ms interval.

### Pathfinding Throttling
A hard cap `Constants.PATHFIND_CAP_PER_FRAME` prevents "lag spikes" when many agents recalculate paths simultaneously (e.g., at sunset). Agents that miss the window will retry on the next AI tick.

## 4. Debugging Tools
Added a real-time Debug Overlay (top-left) showing:
- **FPS & Frame Time**
- **Tile Draw Count**
- **Active Agent Count**
- **Pathfinding Requests per Second**
