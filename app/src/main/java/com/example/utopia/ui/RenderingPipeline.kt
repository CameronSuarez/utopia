package com.example.utopia.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.example.utopia.data.models.*
import com.example.utopia.domain.NavGrid
import com.example.utopia.util.IntOffset

/**
 * Global frame-specific state for rendering.
 * Bundles camera, caches, and flags to avoid parameter explosion.
 */
data class RenderContext(
    val camera: Camera2D,
    val timeMs: Long,
    val groundBitmap: ImageBitmap?,
    val roadBitmap: ImageBitmap?,
    val roadAsset: ImageBitmap,
    val structureAssets: StructureAssets,
    val propAssets: PropAssets,
    val emojiFxAssets: EmojiFxAssets,
    val agentNameById: Map<String, String>,
    val showNavGrid: Boolean,
    val navGrid: NavGrid? = null
)

/**
 * Identifies the type of object to be rendered.
 */
enum class RenderItemKind {
    STRUCTURE,
    PROP,
    AGENT,
    EMOJI_FX
}

/**
 * A reference to an item to be rendered, with sorting keys.
 */
data class RenderItemRef(
    val kind: RenderItemKind,
    val item: Any, // Shallow reference to Structure, PropInstance, or AgentRuntime
    val depthY: Float,
    val depthX: Float,
    val tieBreak: Int
)

/**
 * A read-only snapshot of the simulation state for the current frame.
 */
data class SceneSnapshot(
    val worldState: WorldState,
    val liveRoadTiles: List<IntOffset> = emptyList(),
    val visibleWorldObjectsYSorted: List<RenderItemRef> = emptyList()
)

/**
 * Base interface for a rendering layer.
 */
interface RenderLayer {
    fun DrawScope.draw(context: RenderContext, snapshot: SceneSnapshot)
}
