package com.example.utopia.domain

internal class AgentTelemetry {

    // Navigation
    var pathRequests = 0
    var pathFailures = 0
    var pathSuccesses = 0
    var goalRetries = 0

    // Social
    var socialTriggers = 0
    var bumps = 0

    // Work / Idle
    var idleAtWork = 0

    fun resetCycle() {
        pathRequests = 0
        pathFailures = 0
        pathSuccesses = 0
        goalRetries = 0

        socialTriggers = 0
        bumps = 0
        idleAtWork = 0
    }
}
