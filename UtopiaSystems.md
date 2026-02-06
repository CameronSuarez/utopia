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
    -   **`advanceTick(deltaTimeMs)`**: The main entry point for a single step of the simulation. It runs a series of specialized sub-systems in a defined order.
    -   **`simulationPipeline`**: Executes systems in order. The pipeline no longer relies on a `transientHasAvailableWorkplace` flag.
    -   Manages the canonical `WorldState` and is the only class authorized to modify it.
-   **`WorldState`**: An immutable data class holding all simulation data (agents, structures, tiles, etc.). Any change results in a new `WorldState` instance.
-   **`PoiSystem`**: A derived system that recomputes POIs and categorized structure indexes when `structureRevision` or `inventoryRevision` changes.
    -   Produces the canonical `pois` list for intent targeting.
    -   Produces a `PoiIndex` that groups structures by role (e.g., incomplete construction sites, resource sources, resource sinks).
-   **`WorldAnalysisSystem`**: A system that computes transient, world-level flags. It does not compute workplace availability.
-   **`AgentDecisionSystem`**: A merged system that replaces `AgentIntentSystem` and `AgentIntentSystemWrapper`.
    -   Computes intent, selects targets, and assigns workplaces in a single pass.
    -   Defines the single source of truth for `canWorkAt`.
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
-   **`AgentRuntime`**: An instance of an agent in the world. Contains dynamic state like current needs, intent (goal), `workplaceId`, and intent targets.
-   **`PoiIndex`**: A derived index of structures and POIs used by systems to query construction sites, sources, and sinks without scanning all structures each tick.
-   **`PersistenceManager`**: Handles saving and loading the game. It serializes the `WorldState` to and from a JSON file stored on the device.

---

## 4. Economic & Agent Logic
-   **Economy (`EconomySystem.kt`)**: Driven by `StructureSpec` properties. The system processes production, hauling, and construction based on these data definitions.
    -   When a sink is full, the agent retargets to another valid sink in the same tick when possible.
    -   If no valid sink exists, the agent holds the item and retries next tick without resetting intent.
    -   UI “Blocked” state uses the same criteria as the economy’s production checks.
-   **Agent Decision (`AgentDecisionSystem.kt`)**: A direct scoring model replaces the pressure ladder.
    -   **Single source of truth**: `canWorkAt(structure, state)` is defined here and used for all workability checks.
    -   **Scoring**:
        -   **Work**: `1.0` if the agent is unemployed and any workable workplace exists.
        -   **Construct/Haul**: `0.8` if there are open construction sites and required resources exist.
        -   **Needs**: Score proportional to deficit for `SeekStability`.
    -   **Selection**: Highest score wins; no priority ladder.
    -   **Commitment**: Uses the standard timing rules; no urgent-pressure bypass.
    -   **Assignment**: Intent selection and assignment happen in the same system pass.
    -   **Targeting**: Uses `PoiIndex` when current; falls back to direct structure scans when stale or unavailable.
-   **Physics (`AgentPhysics.kt`)**: The agent physics simulation is driven exclusively by the elapsed time between ticks (`deltaTimeMs`).
    -   **State Transitions**: An agent's `AgentState` is set to `WORKING` when their `currentIntent` is `Work` and they are at the assigned workplace.
    -   **Intent Satisfaction**: Uses proximity checks and lot rectangles to avoid flicker when agents are near target structures.

---

## 5. Code Refinements
The simulation systems (`SystemWrappers.kt`, `EconomySystem.kt`, `AgentPhysics.kt`) were refactored to improve code quality and maintainability. This involved:
-   Removing unused imports and function parameters.
-   Replacing mutable variables (`var`) with immutable ones (`val`) where the variable was not reassigned.
-   Simplifying conditional logic and removing redundant code.
-   Eliminating unnecessary type casts made redundant by Kotlin's smart-casting feature.

---

## 6. Explicit Changes In This Context

### System Startup and Data Loading
-   **`structures.json`**: Corrected a `JsonDecodingException` caused by a trailing comma in the "CASTLE" object definition. This crash occurred during app startup.
-   **`GameViewModel`**: The logic to load a saved world state via `worldManager.loadData()` has been disabled. This prevents the app from crashing at startup if it tries to load a save file containing data for structures that have been removed from the code.
-   **`StructureRegistry`**: Is initialized from `assets/data/structures.json` in `MainActivity`. An error in this JSON file was found to be the root cause of a fatal startup crash.

### Structure Removals
-   The **"CASTLE"**, **"WALL"**, and **"ROAD"** structures have been completely removed from the game.
-   **`structures.json`**: The JSON object definitions for "CASTLE", "WALL", and "ROAD" were deleted.
-   **`StructureRenderer.kt`**: The procedural drawing logic for "CASTLE" (`drawMedievalCastle`) and the `drawRect` logic for "WALL" have been removed. The file no longer contains any references to these structures.
-   **Rendering Pipeline**: All code related to rendering roads has been removed.
    -   **`CityUI.kt`**: No longer loads `roadBitmapAsset` or computes the `roadBitmap` cache. The `RenderContext` is no longer created with any road-related assets.
    -   **`CityRenderer.kt`**: The `RoadLayer` has been removed from the list of default layers in the `drawCity` function.
    -   **`CityLayers.kt`**: The `RoadLayer` class definition has been removed.
    -   **`RenderingPipeline.kt`**: The `RenderContext` data class no longer contains the `roadBitmap` or `roadAsset` properties.

### Placement Controller
-   **`PlacementController.kt`**: The controller has been significantly simplified to support only "STAMP" placement behavior.
    -   All logic related to "STROKE" or "BRUSHING" behavior has been removed. This includes the `BRUSHING` state, the `processStroke` function, and the `getLine` helper function.
    -   The `liveRoadTiles` property, used for previewing road placement, has been removed.
-   **Single Stamp Placement**: The behavior for placing a building has been changed. After a single building is successfully placed, the controller now automatically returns to the `IDLE` state, deselecting the active tool. This is achieved by calling `cancel()` instead of transitioning to `ARMED` in the `endPointer` function.
-   **`CityUI.kt`**: The `pointerInput` gesture handler that previously defaulted to the "ROAD" tool now does nothing if no tool is selected.
-   **`PlacementUI.kt`**: The UI status text for the `BRUSHING` state has been removed, as the state no longer exists.
