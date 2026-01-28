package com.example.utopia.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

// REMOVED: import androidx.compose.ui.graphics.Path
// REMOVED: import com.example.utopia.debug.PathDebugOverlay

// REMOVED: NavGridDebugOverlay
// REMOVED: LotDebugOverlay
// REMOVED: AgentClearanceDebugOverlay
// REMOVED: drawDebugVector
// REMOVED: EntityHitboxDebugOverlay

@Composable
fun DebugPanel(
    viewModel: GameViewModel,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Default.BugReport,
                contentDescription = "Debug Menu",
                tint = Color.White
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text(if (viewModel.showNavGridDebug) "Hide NavGrid" else "Show NavGrid") },
                onClick = {
                    viewModel.showNavGridDebug = !viewModel.showNavGridDebug
                    expanded = false
                }
            )
            DropdownMenuItem(
                text = { Text(if (viewModel.showHitboxesDebug) "Hide Hitboxes" else "Show Hitboxes") },
                onClick = {
                    viewModel.showHitboxesDebug = !viewModel.showHitboxesDebug
                    expanded = false
                }
            )
            DropdownMenuItem(
                text = { Text(if (viewModel.showLotDebug) "Hide Lot" else "Show Lot") },
                onClick = {
                    viewModel.showLotDebug = !viewModel.showLotDebug
                    expanded = false
                }
            )
            DropdownMenuItem(
                text = { Text(if (viewModel.showAgentPaths) "Hide Paths" else "Show Paths") },
                onClick = {
                    viewModel.showAgentPaths = !viewModel.showAgentPaths
                    expanded = false
                }
            )
            DropdownMenuItem(
                text = { Text(if (viewModel.showAgentLabels) "Hide Labels" else "Show Labels") },
                onClick = {
                    viewModel.showAgentLabels = !viewModel.showAgentLabels
                    expanded = false
                }
            )
            // REMOVED: AgentClearanceDebug menu item
            // REMOVED: AgentVectorsDebug menu item
        }
    }
}