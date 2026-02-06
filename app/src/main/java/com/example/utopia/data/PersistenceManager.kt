package com.example.utopia.data

import android.content.Context
import com.example.utopia.data.models.WorldStateData
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class PersistenceManager(private val context: Context) {
    private val fileName = "save_game.json"
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true // Important for ensuring all fields are saved
    }

    fun saveData(data: WorldStateData) {
        try {
            val jsonString = json.encodeToString(data)
            File(context.filesDir, fileName).writeText(jsonString)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadData(): WorldStateData? {
        try {
            val file = File(context.filesDir, fileName)
            if (file.exists()) {
                val jsonString = file.readText()
                return json.decodeFromString<WorldStateData>(jsonString)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
}
