package com.example.utopia

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.utopia.data.StructureRegistry
import com.example.utopia.ui.CityScreen
import com.example.utopia.ui.GameViewModel
import com.example.utopia.ui.theme.UtopiaTheme
import java.io.IOException

class MainActivity : ComponentActivity() {
    private val viewModel: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d("StartupHeartbeat", "MainActivity.onCreate - START")
        super.onCreate(savedInstanceState)

        // Initialize data registries
        try {
            assets.open("data/structures.json").use { stream ->
                StructureRegistry.init(stream)
            }
            Log.d("Startup", "StructureRegistry initialized successfully.")
        } catch (e: IOException) {
            Log.e("Startup", "Failed to load structures.json", e)
            // Depending on the app's requirements, you might want to
            // show an error screen or close the app.
        }

        enableEdgeToEdge()
        Log.d("StartupHeartbeat", "MainActivity.onCreate - setContent calling")
        setContent {
            UtopiaTheme {
                CityScreen(viewModel)
            }
        }
        Log.d("StartupHeartbeat", "MainActivity.onCreate - END")
    }
}
