package com.example.utopia.domain

import android.util.Log
import com.example.utopia.data.models.DayPhase

internal fun AgentSystem.logDebugStats(phase: DayPhase) {
    Log.d("AgentSystemStats", "--- AI TICK (Phase: $phase) ---")
    Log.d("AgentSystemStats", "PATHFIND: Total=${telemetry.pathRequests} [Success:${telemetry.pathSuccesses} | Fail:${telemetry.pathFailures}]")
    Log.d("AgentSystemStats", "INTENT: AtWorkIdle=${telemetry.idleAtWork}, GoalRetries=${telemetry.goalRetries}")
    Log.d("AgentSystemStats", "WORK: Actions=${workActionCounts.entries.joinToString()}")

    // We don't reset here anymore, it's done via telemetry.resetCycle() elsewhere if needed
}

internal fun AgentSystem.resetCycleTelemetry() {
    telemetry.resetCycle()
    workActionCounts.clear()
}
