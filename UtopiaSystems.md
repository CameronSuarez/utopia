# Utopia Engine: System Architecture

## 1. Core Philosophy
-   **Data-Driven**: Game entities (structures, agents) are defined in data files (`assets/data/`), not hardcoded.
-   **Separation of Concerns**: Simulation logic (`WorldManager`) is decoupled from rendering (`CityUI`).
-   **Centralized State**: The entire simulation state is encapsulated in the `WorldState` data class.

---

## 2. Key Systems & Data Flow

### 2.1. Initialization & Persistence
The application uses a ViewModel-centric approach to manage the game's lifecycle and data persistence.
1.  **`GameViewModel`**: An `AndroidViewModel` is the first major component to initialize. It holds references to the core simulation systems.
2.  **`PersistenceManager`**: Instantiated by the `GameViewModel`, which provides the necessary `Application` context.
3.  **`persistenceManager.load()`**: Called from the `GameViewModel`'s `init` block. This ensures the game state is loaded from `save_game.json` immediately upon the ViewModel's creation.
4.  **Game Loop & Simulation**: The `GameViewModel` starts the main game loop, which periodically calls `worldManager.advanceTick()` to advance the simulation.
5.  **`persistenceManager.save()`**: The `GameViewModel` overrides `onCleared()`. This lifecycle callback is invoked when the ViewModel is about to be destroyed, guaranteeing that the current game state is saved by the `PersistenceManager`.

### 2.2. Simulation (Domain Layer)
-   **`WorldManager`**: The primary orchestrator of the simulation.
    -   **`advanceTick(deltaTimeMs)`**: The main entry point for a single step of the simulation. It runs a series of specialized sub-systems in a defined order. The `simulationPipeline` list defines this execution order, which is critical for preventing stale data issues.
    -   **`simulationPipeline`**: The `WorldAnalysisSystem` is now run immediately before the `AgentIntentSystemWrapper` to ensure that the `transient_hasAvailableWorkplace` flag is up-to-date when agents calculate their pressures.
    -   Manages the canonical `WorldState` and is the only class authorized to modify it.
-   **`WorldState`**: An immutable data class holding all simulation data (agents, structures, tiles, etc.). Any change results in a new `WorldState` instance.
-   **`PoiSystem`**: A derived system that recomputes POIs and categorized structure indexes when `structureRevision` or `inventoryRevision` changes.
    -   Produces the canonical `pois` list for intent targeting.
    -   Produces a `PoiIndex` that groups structures by role (e.g., incomplete construction sites, resource sources, resource sinks).
-   **`WorldAnalysisSystem`**: A system that computes transient, world-level flags. Its check for `hasAvailableWorkplace` is now unified with the assignment logic in `AgentIntentSystemWrapper` to include an `isComplete` check, ensuring consistency in the definition of an "assignable job".
-   **`NavGrid`**: A spatial grid representing the traversable areas of the world for pathfinding. It is updated by `WorldManager` whenever structures or tiles change.

### 2.3. UI & Rendering (UI Layer)
-   **`GameViewModel`**: Owns the `WorldManager` and `PersistenceManager`.
    -   Exposes UI-specific state (e.g., selected structure, debug flags) to Composables as `State`.
-   **`CityUI` / `CityScreen` (Composables)**:
    -   The main entry point for the UI. Responsible for setting up the canvas, loading assets, and handling user input.
    -   Observes state from `GameViewModel` to trigger recomposition.
-   **`RenderContext`**: An immutable data class that bundles all necessary data for a single frame (camera, assets, debug flags). It is created in `CityScreen` and passed down the rendering pipeline.
-   **Rendering Pipeline**: A series of `RenderLayer` interfaces executed in order.
    -   `DebugOverlayLayer`: A specific layer responsible for drawing debug information. It checks boolean flags in the `RenderContext` (e.g., `showNavGrid`, `showAgentPaths`) to decide which overlays to render.
    -   **`drawAgentPaths`**: A `DrawScope` extension function (in `PathDebugOverlay.kt`) that contains the logic for rendering agent paths. It is called by `DebugOverlayLayer` when the appropriate flag is set.

---

## 3. Core Data Models
-   **`StructureSpec`**: Loaded from `structures.json`. Defines the static properties of a structure type (e.g., build cost, production output, capacity, `providesStability`).
-   **`Structure`**: An instance of a structure in the world. Contains dynamic state like its inventory, build progress, and a `workers` list of assigned agent IDs.
-   **`AgentRuntime`**: An instance of an agent in the world. Contains dynamic state like current needs, intent (goal), `workplaceId`, and `transientPressures`.
-   **`PoiIndex`**: A derived index of structures and POIs used by systems to query construction sites, sources, and sinks without scanning all structures each tick.
-   **`PersistenceManager`**: Handles saving and loading the game. It serializes the `WorldState` to and from a JSON file stored on the device.

---

## 4. Economic & Agent Logic
-   **Economy (`EconomySystem.kt`)**: Driven by `StructureSpec` properties. The system processes production, hauling, and construction based on these data definitions.
-   **Agent AI (`AgentIntentSystem.kt`)**: A "pressure" system calculates an agent's desire to perform certain actions.
    -   **`calculatePressures`**: The pressure for an unemployed agent to seek work (`AgentIntent.Work`) is set to a high value (`0.9f`) to ensure it competes with other pressures.
    -   **`AgentIntentSystemWrapper`**: This system contains the "demand-driven workplace assignment" logic. It runs after an agent's intent has been selected. If the intent is `Work` and the agent has no `workplaceId`, it finds a valid, complete, and available workplace and updates both the agent's `workplaceId` and the structure's `workers` list.
    -   **POI-driven targeting**: Construction hauling, resource sources, and resource sinks are resolved through the `PoiIndex` when it is current.
-   **Physics (`AgentPhysics.kt`)**: The agent physics simulation is driven exclusively by the elapsed time between ticks (`deltaTimeMs`).
    -   **State Transitions**: An agent's `AgentState` is only set to `WORKING` if their `currentIntent` is `AgentIntent.Work` and they are physically located at their assigned `workplaceId`. This makes the "WORKING" state require a real data assignment, not just physical presence.
    -   **Intent Satisfaction**: The `isIntentSatisfied` function was corrected to recognize that workplaces like `LUMBERJACK_HUT` and `WORKSHOP` can satisfy the `SeekStability` need, as defined in `structures.json`. This prevents agents from getting stuck in a "traveling" loop.

---

## 5. Code Refinements
The simulation systems (`SystemWrappers.kt`, `EconomySystem.kt`, `AgentPhysics.kt`) were refactored to improve code quality and maintainability. This involved:
-   Removing unused imports and function parameters.
-   Replacing mutable variables (`var`) with immutable ones (`val`) where the variable was not reassigned.
-   Simplifying conditional logic and removing redundant code.
-   Eliminating unnecessary type casts made redundant by Kotlin's smart-casting feature.

---

## 6. Explicit Changes In This Context
-   **`SimulationSystem`** (`app/src/main/java/com/example/utopia/domain/SimulationSystem.kt`): Added a stable `id` property used for ordering and diagnostics.
-   **`SimulationPipeline`** (`app/src/main/java/com/example/utopia/domain/SimulationPipeline.kt`): Added a container that executes a list of `SimulationSystem` instances in order.
    -   **`run(state, deltaTimeMs, nowMs)`**: Executes each system sequentially, passing the updated `WorldState` to the next system.
    -   **`validateOrderInvariants()`**: Enforces the order invariant that `WorldAnalysisSystem` must run before `AgentIntentSystemWrapper`.
-   **`WorldManager`** (`app/src/main/java/com/example/utopia/domain/WorldManager.kt`): `simulationPipeline` is now a `SimulationPipeline` instance, and `advanceTick` delegates execution to `SimulationPipeline.run(...)`.
-   **`SimulationPipelineTest`** (`app/src/test/java/com/example/utopia/domain/SimulationPipelineTest.kt`): Added tests that assert the pipeline order invariant and accept the current ordering.
-   **`AgentPhysics`** (`app/src/main/java/com/example/utopia/domain/AgentPhysics.kt`): `updateAgents`, `updateAgentTick`, and `calculateWanderForce` now take `nowMs`, and wandering randomness is seeded with `nowMs` instead of `System.currentTimeMillis()`.
-   **`AgentPhysicsWrapper`** (`app/src/main/java/com/example/utopia/domain/SystemWrappers.kt`): Passes `nowMs` into `updateAgents`.
-   **`AgentPhysics`** (`app/src/main/java/com/example/utopia/domain/AgentPhysics.kt`): `SeekStability` and `SeekStimulation` state transitions now align with intent satisfaction (stability at `STORE`/`WORKSHOP`/`LUMBERJACK_HUT`, stimulation when `providesStimulation` is true).
-   **`WorldState`** (`app/src/main/java/com/example/utopia/data/models/Models.kt`): `transient_hasAvailableWorkplace` is now `private set`, and updates are done via `withTransientHasAvailableWorkplace(value)` which returns a new `WorldState`.
-   **`WorldAnalysisSystem`** (`app/src/main/java/com/example/utopia/domain/SystemWrappers.kt`): Uses `withTransientHasAvailableWorkplace(value)` instead of mutating state directly.
-   **`WorldAnalysisSystemTest`** (`app/src/test/java/com/example/utopia/domain/WorldAnalysisSystemTest.kt`): Added a unit test asserting that `WorldAnalysisSystem` returns a new state and does not mutate the input state.
-   **`EconomySystem`** (`app/src/main/java/com/example/utopia/domain/EconomySystem.kt`): When a `StoreResource` intent targets a full sink, the agent now resets intent to `Idle` (keeping the carried item) to re-evaluate next tick.
-   **`AgentIntentSystem`** (`app/src/main/java/com/example/utopia/domain/AgentIntentSystem.kt`): `Work` pressure is only applied when the assigned workplace can currently produce or transform; blocked workplaces yield `Work` pressure of 0.
-   **`WorldManager`** (`app/src/main/java/com/example/utopia/domain/WorldManager.kt`): `loadData` now regenerates `pois` from loaded structures instead of trusting `data.pois`.
-   **`structures.json`** (`app/src/main/assets/data/structures.json`): `LUMBERJACK_HUT` now has `providesStimulation: true`.
-   **`PoiSystem`** (`app/src/main/java/com/example/utopia/domain/PoiSystem.kt`): Added a derived POI system that builds `PoiIndex` categories and the canonical `pois` list when `structureRevision` or `inventoryRevision` changes.
-   **`WorldState`** (`app/src/main/java/com/example/utopia/data/models/Models.kt`): Added `poiIndex` (transient) and `inventoryRevision` to track inventory changes for POI indexing.
-   **`WorldStateData`** (`app/src/main/java/com/example/utopia/data/models/Models.kt`): Added `inventoryRevision` for persistence.
-   **`WorldManager`** (`app/src/main/java/com/example/utopia/domain/WorldManager.kt`): `PoiSystem` runs in the simulation pipeline and is used to recompute POIs after load and structure mutations.
-   **`AgentIntentSystem`** (`app/src/main/java/com/example/utopia/domain/AgentIntentSystem.kt`): Construction hauling, resource sourcing, and resource sink selection now use `PoiIndex` when it matches the current revisions.
-   **`EconomySystem`** (`app/src/main/java/com/example/utopia/domain/EconomySystem.kt`): Inventory-changing actions now increment `inventoryRevision`.
-   **`AgentPhysics`** (`app/src/main/java/com/example/utopia/domain/AgentPhysics.kt`): Added a proximity-based target satisfaction check with `TARGET_MARGIN_PX` and the helper `isNearTargetStructure(...)`.
    -   **`isNearTargetStructure`**: Treats an agent as “at target” when their position is within the target structure’s footprint expanded by a small margin.
    -   **`intentSatisfiedState` / `isIntentSatisfied`**: Work, construct, and resource-transfer intents now use the proximity check to avoid flickering when agents sit just outside a structure’s influence rectangle.
