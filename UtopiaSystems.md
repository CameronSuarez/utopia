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
-   **`PersistenceManager`**: Handles saving and loading the game. It serializes the `WorldState` to and from a JSON file stored on the device.

---

## 4. Economic & Agent Logic
-   **Economy (`EconomySystem.kt`)**: Driven by `StructureSpec` properties. The system processes production, hauling, and construction based on these data definitions.
-   **Agent AI (`AgentIntentSystem.kt`)**: A "pressure" system calculates an agent's desire to perform certain actions.
    -   **`calculatePressures`**: The pressure for an unemployed agent to seek work (`AgentIntent.Work`) is set to a high value (`0.9f`) to ensure it competes with other pressures.
    -   **`AgentIntentSystemWrapper`**: This system contains the "demand-driven workplace assignment" logic. It runs after an agent's intent has been selected. If the intent is `Work` and the agent has no `workplaceId`, it finds a valid, complete, and available workplace and updates both the agent's `workplaceId` and the structure's `workers` list.
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
