package com.example.utopia.data.models

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = AgentIntent.AgentIntentSerializer::class)
sealed class AgentIntent {
    object SeekSleep : AgentIntent() { override fun toString(): String = "SeekSleep" }
    object SeekFun : AgentIntent() { override fun toString(): String = "SeekFun" }
    object SeekStability : AgentIntent() { override fun toString(): String = "SeekStability" }
    object SeekStimulation : AgentIntent() { override fun toString(): String = "SeekStimulation" }
    object Idle : AgentIntent() { override fun toString(): String = "Idle" }
    object Wandering : AgentIntent() { override fun toString(): String = "Wandering" }
    object Work : AgentIntent() { override fun toString(): String = "Work" }
    data class GetResource(val targetId: String, val resource: ResourceType) : AgentIntent()
    data class StoreResource(val targetId: String) : AgentIntent()
    data class Construct(val targetId: String) : AgentIntent()


    // Serializer for the sealed class to maintain save file compatibility.
    object AgentIntentSerializer : KSerializer<AgentIntent> {
        override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("AgentIntent", PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: AgentIntent) {
            val stringValue = when (value) {
                is GetResource -> "get_resource|${value.targetId}|${value.resource}"
                is StoreResource -> "store_resource|${value.targetId}"
                is Construct -> "construct|${value.targetId}"
                SeekSleep -> "seek_sleep"
                SeekFun -> "seek_fun"
                SeekStability -> "seek_stability"
                SeekStimulation -> "seek_stimulation"
                Idle -> "Idle"
                Wandering -> "Wandering"
                Work -> "Work"
            }
            encoder.encodeString(stringValue)
        }

        override fun deserialize(decoder: Decoder): AgentIntent {
            val stringValue = decoder.decodeString()
            val parts = stringValue.split("|")
            return when (parts[0]) {
                "get_resource" -> GetResource(parts[1], ResourceType.valueOf(parts[2]))
                "store_resource" -> StoreResource(parts[1])
                "construct" -> Construct(parts[1])
                "seek_sleep" -> SeekSleep
                "seek_fun" -> SeekFun
                "seek_stability" -> SeekStability
                "seek_stimulation" -> SeekStimulation
                "Idle", "IDLE" -> Idle // Handle legacy "IDLE" from save files
                "Wandering" -> Wandering
                "Work" -> Work
                else -> Wandering // Default for unknown intents
            }
        }
    }
}
