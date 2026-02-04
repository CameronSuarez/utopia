package com.example.utopia.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SimulationPipelineTest {
    @Test(expected = IllegalArgumentException::class)
    fun pipelineRequiresWorldAnalysisBeforeAgentIntent() {
        SimulationPipeline(
            listOf(
                AgentIntentSystemWrapper,
                WorldAnalysisSystem
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
                WorldAnalysisSystem,
                AgentIntentSystemWrapper,
                EconomySystemWrapper,
                StaleTargetCleanupSystem
            )
        )

        assertEquals("SimulationPipeline", pipeline::class.java.simpleName)
    }
}
