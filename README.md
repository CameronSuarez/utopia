# Utopia

Utopia is a simulation game for Android, built with Jetpack Compose. It features a procedurally generated world where autonomous agents, or "villagers," live, work, and interact with each other and their environment.

## Features

*   **Procedural World Generation:** The game world, including terrain and props like trees, is generated programmatically.
*   **Autonomous Agents:** The agents in the game are driven by an AI system that manages their goals, social interactions, and navigation.
*   **Dynamic Environment:** Players can place buildings and other structures in the world, which dynamically affects the environment and the agents within it.
*   **Pathfinding:** The game uses a navigation grid and pathfinding algorithms to allow agents to move around the world and avoid obstacles.
*   **Debug Tools:** The game includes a variety of debug overlays to visualize the navigation grid, agent paths, and other internal systems.

## Architecture

The project is organized into the following main packages:

*   **`data`:** Contains the data models for the game, such as the `WorldState` and agent profiles.
*   **`domain`:** This is the core of the game, containing the game logic for managing the world, the agent AI system, and pathfinding.
    *   `WorldManager.kt`: Responsible for creating and managing the game world, including tiles, structures, and props.
    *   `AgentSystem.kt`: Manages the AI for the agents, including their goals, social interactions, and movement.
    *   `NavGrid.kt` & `Pathfinding.kt`: Handle the navigation and pathfinding for the agents.
*   **`ui`:**  Contains the Jetpack Compose UI for rendering the game world, agents, and UI elements. It also includes the `GameViewModel` which connects the UI to the game's domain layer.
*   **`debug`:** Provides tools for debugging and visualizing the game's internal state.

## How to Run

To run the project, open it in Android Studio and run the `app` configuration on an Android emulator or a physical device.
