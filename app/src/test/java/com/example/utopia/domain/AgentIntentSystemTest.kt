package com.example.utopia.domain

import com.example.utopia.data.models.AgentIntent
import com.example.utopia.data.models.AgentProfile
import com.example.utopia.data.models.AgentRuntime
import com.example.utopia.data.models.Needs
import com.example.utopia.data.models.PersonalityVector
import com.example.utopia.data.models.SerializableOffset
import com.example.utopia.data.models.SocialMemory
import com.example.utopia.util.Constants
import org.junit.Assert.assertEquals
import org.junit.Test

class AgentIntentSystemTest {
    @Test
    fun selectIntentAppliesHysteresisWithinCommitmentWindow() {
        val nowMs = 10_000L
        val intentStart = nowMs - (Constants.INTENT_COMMITMENT_MS - 1)
        val currentIntent = AgentIntent.SeekFun

        val pressures = mapOf(
            AgentIntent.SeekFun to 0.4f,
            AgentIntent.SeekSleep to 0.5f
        )

        val agent = baseAgent(
            currentIntent = currentIntent,
            intentStartTimeMs = intentStart,
            transientPressures = pressures
        )

        val selected = AgentIntentSystem.selectIntent(agent, nowMs)

        assertEquals(AgentIntent.SeekFun, selected)
    }

    @Test
    fun selectIntentDoesNotApplyHysteresisAfterCommitmentWindow() {
        val nowMs = 10_000L
        val intentStart = nowMs - (Constants.INTENT_COMMITMENT_MS + 1)
        val currentIntent = AgentIntent.SeekFun

        val pressures = mapOf(
            AgentIntent.SeekFun to 0.4f,
            AgentIntent.SeekSleep to 0.5f
        )

        val agent = baseAgent(
            currentIntent = currentIntent,
            intentStartTimeMs = intentStart,
            transientPressures = pressures
        )

        val selected = AgentIntentSystem.selectIntent(agent, nowMs)

        assertEquals(AgentIntent.SeekSleep, selected)
    }

    private fun baseAgent(
        currentIntent: AgentIntent,
        intentStartTimeMs: Long,
        transientPressures: Map<AgentIntent, Float>
    ): AgentRuntime {
        return AgentRuntime(
            id = "agent-1",
            name = "Test Agent",
            profile = AgentProfile(),
            position = SerializableOffset(0f, 0f),
            velocity = SerializableOffset(0f, 0f),
            currentIntent = currentIntent,
            personality = PersonalityVector(0f, 0f, 0f, 0f, 0f),
            needs = Needs(
                sleep = 50f,
                stability = 50f,
                social = 50f,
                `fun` = 50f,
                stimulation = 50f
            ),
            socialMemory = SocialMemory(),
            transientPressures = transientPressures,
            intentStartTimeMs = intentStartTimeMs
        )
    }
}
