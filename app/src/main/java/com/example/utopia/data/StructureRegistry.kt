package com.example.utopia.data

import com.example.utopia.data.models.StructureSpec
import kotlinx.serialization.json.Json
import java.io.InputStream

object StructureRegistry {

    private var specs: Map<String, StructureSpec> = emptyMap()
    private var isInitialized = false

    fun init(stream: InputStream) {
        if (isInitialized) return
        val jsonString = stream.bufferedReader().use { it.readText() }
        val structureList = Json.decodeFromString<List<StructureSpec>>(jsonString)
        specs = structureList.associateBy { it.id }
        isInitialized = true
    }

    fun get(id: String): StructureSpec {
        check(isInitialized) { "StructureRegistry has not been initialized." }
        return specs[id] ?: throw IllegalArgumentException("Unknown structure spec ID: $id")
    }

    fun all(): List<StructureSpec> {
        check(isInitialized) { "StructureRegistry has not been initialized." }
        return specs.values.toList()
    }
}
