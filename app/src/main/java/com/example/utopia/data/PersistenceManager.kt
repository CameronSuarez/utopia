package com.example.utopia.data

import android.content.Context
import com.example.utopia.data.models.WorldStateData
import com.example.utopia.domain.WorldManager
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class PersistenceManager(private val context: Context) {
    private val fileName = "save_game.json"
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true // Important for ensuring all fields are saved
    }

    fun save(worldManager: WorldManager) {
        try {
            val data = worldManager.toData()
            val jsonString = json.encodeToString(data)
            File(context.filesDir, fileName).writeText(jsonString)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun load(worldManager: WorldManager) {
        try {
            val file = File(context.filesDir, fileName)
            if (file.exists()) {
                val jsonString = file.readText()
                val data = json.decodeFromString<WorldStateData>(jsonString)
                worldManager.loadData(data)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
