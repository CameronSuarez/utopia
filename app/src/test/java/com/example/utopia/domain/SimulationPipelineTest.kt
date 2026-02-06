package com.example.utopia.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SimulationPipelineTest {
    @Test(expected = IllegalArgumentException::class)
    fun pipelineRequiresPoiSystemBeforeDecisionSystem() {
        SimulationPipeline(
            listOf(
                AgentDecisionSystem,
                PoiSystem
            )
        )
    }

    @Test
    fun pipelineAcceptsCurrentOrdering() {
        val pipeline = SimulationPipeline(
            listOf(
                AgentAssignmentSystem,
                AgentNeedsSystemWrapper,
                AgentSocialSystemWrapper,
                AgentGossipSystemWrapper,
                AgentEmojiSystemWrapper,
                AgentRelationshipSystemWrapper,
                PoiSystem,
                WorldAnalysisSystem,
                AgentDecisionSystem,
                EconomySystemWrapper,
                StaleTargetCleanupSystem
            )
        )

        assertEquals("SimulationPipeline", pipeline::class.java.simpleName)
    }
}
